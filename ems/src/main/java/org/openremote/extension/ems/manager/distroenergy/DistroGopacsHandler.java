/*
 * Copyright 2026, OpenRemote Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openremote.extension.ems.manager.distroenergy;

import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType.BUY;
import static org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType.SELL;
import static org.openremote.model.syslog.SyslogCategory.API;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.openremote.container.timer.TimerService;
import org.openremote.container.web.WebTargetBuilder;
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset;
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType;
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset;
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType;
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrder;
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrderRequest;
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsSettlement;
import org.openremote.extension.ems.manager.gopacs.GOPACSHandler;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.model.Container;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.auth.OAuthClientCredentialsGrant;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.AttributeDescriptor;

/**
 * Places GOPACS redispatch orders through the Distro Energy GOPACS Connector API for one {@link
 * EmsDistroEnergyAsset}.
 *
 * <p>The announcement arrives on the asset's {@code gopacs*} input attributes, which {@code
 * EmsOptimisationService} copies from the {@code EmsGOPACSAsset} with the same parent, so this
 * handler never talks to GOPACS itself. From those inputs it pre-fills an order type and quantity,
 * which the operator may override before setting a price and confirming. Tracked orders are polled
 * until they reach a final status.
 */
public class DistroGopacsHandler {

  private static final Logger LOG = SyslogCategory.getLogger(API, DistroGopacsHandler.class);

  public static final String DISTRO_GOPACS_CLIENT_ID = "DISTRO_GOPACS_CLIENT_ID";
  public static final String DISTRO_GOPACS_CLIENT_SECRET = "DISTRO_GOPACS_CLIENT_SECRET";
  public static final String DISTRO_GOPACS_URL = "DISTRO_GOPACS_URL";
  public static final String DISTRO_GOPACS_URL_DEFAULT = "https://api.distro.energy/v1";
  public static final String DISTRO_GOPACS_TOKEN_URL = "DISTRO_GOPACS_TOKEN_URL";
  public static final String DISTRO_GOPACS_TOKEN_URL_DEFAULT =
      "https://auth.distro.energy/oauth2/token";

  protected static final String SCOPE = "distro-api/gopacs.access";
  protected static final Duration POLL_INTERVAL = Duration.ofMinutes(5);
  protected static final long ISP_SECONDS = Duration.ofMinutes(15).toSeconds();

  /**
   * How long after an order's window ends a failed fetch of its trades is still retried. Distro
   * stores the trades when the order matches, so they normally load on the first try.
   */
  protected static final Duration SETTLEMENT_RETRY_WINDOW = Duration.ofDays(1);

  /** Attempts at cancelling the open orders when the handler stops for good. */
  protected static final int CANCEL_ATTEMPTS = 5;

  /**
   * Delay before the first retry of those cancels. It doubles after each attempt, so the attempts
   * span 15 minutes, enough to ride out a short Distro outage without a stopped handler lingering
   * for hours. An order still open after that can match until it expires, which is logged.
   */
  protected static final Duration CANCEL_RETRY_DELAY = Duration.ofMinutes(1);

  /** Distro takes quantities of at least 0.1 MW with at most one decimal, so steps of 100 kW. */
  protected static final double MIN_QUANTITY_KW = 100;

  /** Distro's error bodies are short JSON; anything longer is cut off in logs and history. */
  private static final int MAX_ERROR_BODY_LENGTH = 500;

  public static final String METADATA_ANNOUNCEMENT_ID = "announcementId";

  /** Orders that can still be cancelled. Only SUBMITTED ones can be replaced. */
  protected static final Set<String> OPEN_STATUSES = Set.of("PENDING", "SUBMITTED");

  protected static final Set<String> REPLACEABLE_STATUSES = Set.of("SUBMITTED");

  /** Statuses after which an order no longer changes and is no longer tracked. */
  protected static final Set<String> FINAL_STATUSES =
      Set.of("SETTLED", "REJECTED", "CANCELED", "EXPIRED");

  /** Relative effectivities in a GOPACS EAN effectivity category name, weakest first. */
  protected static final List<String> EFFECTIVITY_RANKS =
      List.of("NONE", "VERY_LOW", "LOW", "MODERATE", "HIGH", "VERY_HIGH", "MAXIMUM");

  /** Inputs the pre-filled order type and quantity are derived from. */
  protected static final Set<String> SUGGESTION_INPUTS =
      Set.of(
          EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.getName(),
          EmsDistroEnergyAsset.GOPACS_START_TIME.getName(),
          EmsDistroEnergyAsset.GOPACS_END_TIME.getName(),
          EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY.getName());

  /**
   * Every attribute {@link #onAttributeEvent} reacts to. The handler's own writes are left out, so
   * they are not routed back to it.
   */
  public static final Set<String> INPUTS =
      Stream.concat(
              SUGGESTION_INPUTS.stream(),
              Stream.of(
                  EmsDistroEnergyAsset.GOPACS_ORDER_PRICE.getName(),
                  EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.getName(),
                  EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.getName()))
          .collect(Collectors.toUnmodifiableSet());

  protected final String assetId;
  protected final String portfolio;
  protected final ResteasyClient client;
  protected final DistroGopacsResource resource;

  protected final TimerService timerService;
  protected final ScheduledExecutorService scheduledExecutorService;
  protected final AssetStorageService assetStorageService;
  protected final AssetProcessingService assetProcessingService;

  /** Tracked orders by reference, mirrored to {@link EmsDistroEnergyAsset#GOPACS_ORDERS}. */
  protected final Map<String, GopacsOrder> orders = new LinkedHashMap<>();

  protected ScheduledFuture<?> pollFuture;

  // Tasks already queued on the shared executor still run after undeploy(); they check this first.
  protected volatile boolean undeployed;

  public static class Factory {
    protected Container container;

    public Factory(Container container) {
      this.container = container;
    }

    public DistroGopacsHandler createHandler(String assetId, String portfolio) {
      return new DistroGopacsHandler(assetId, portfolio, container, null);
    }
  }

  /**
   * @param portfolio the asset's Distro portfolio, the same one the day-ahead submissions use.
   * @param resource used as is when not null, which is how tests stand in for the HTTP client;
   *     otherwise one is built from the container config.
   */
  protected DistroGopacsHandler(
      String assetId, String portfolio, Container container, DistroGopacsResource resource) {
    this.assetId = assetId;
    this.portfolio = portfolio;
    this.timerService = container.getService(TimerService.class);
    this.scheduledExecutorService = container.getScheduledExecutor();
    this.assetStorageService = container.getService(AssetStorageService.class);
    this.assetProcessingService = container.getService(AssetProcessingService.class);

    if (resource != null) {
      this.client = null;
      this.resource = resource;
    } else {
      Map<String, String> config = container.getConfig();
      String clientId = requireConfig(config, DISTRO_GOPACS_CLIENT_ID);
      String clientSecret = requireConfig(config, DISTRO_GOPACS_CLIENT_SECRET);
      String baseUrl = config.getOrDefault(DISTRO_GOPACS_URL, DISTRO_GOPACS_URL_DEFAULT);
      String tokenUrl =
          config.getOrDefault(DISTRO_GOPACS_TOKEN_URL, DISTRO_GOPACS_TOKEN_URL_DEFAULT);
      this.client = createClient(org.openremote.container.Container.EXECUTOR);
      try {
        this.resource = createResource(client, baseUrl, tokenUrl, clientId, clientSecret);
      } catch (RuntimeException e) {
        // No reference escapes a constructor that threw, so undeploy() can never close this client.
        client.close();
        throw e;
      }
      // The client ID is not a secret; the secret itself is never logged.
      LOG.info(
          String.format(
              "Configured Distro GOPACS handler for asset %s: baseUrl=%s, tokenUrl=%s, portfolio=%s,"
                  + " clientId=%s",
              assetId, baseUrl, tokenUrl, portfolio, clientId));
    }
  }

  /**
   * Builds the API proxy. OpenRemote's OAuth filter fetches a client-credentials token with HTTP
   * Basic authentication, caches it and refreshes it shortly before it expires.
   */
  static DistroGopacsResource createResource(
      ResteasyClient client,
      String baseUrl,
      String tokenUrl,
      String clientId,
      String clientSecret) {
    return new WebTargetBuilder(client, URI.create(baseUrl))
        .setOAuthAuthentication(
            new OAuthClientCredentialsGrant(tokenUrl, clientId, clientSecret, SCOPE)
                .setBasicAuthHeader(true))
        .build()
        .proxy(DistroGopacsResource.class);
  }

  private static String requireConfig(Map<String, String> config, String key) {
    String value = config.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(key + " not defined, cannot place Distro GOPACS orders.");
    }
    return value;
  }

  /** The portfolio this handler places its orders under. */
  public String getPortfolio() {
    return portfolio;
  }

  /** Restores the tracked orders saved on the asset and starts polling them. */
  public synchronized void deploy() {
    EmsDistroEnergyAsset asset = findAsset();
    if (asset != null) {
      orders.putAll(savedOrders(asset));
      // An undeploy within a second of a toggle skips its reset, and a flag left true would make
      // the next toggle an unchanged value that is ignored.
      for (AttributeDescriptor<Boolean> flag :
          List.of(
              EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER,
              EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS)) {
        if (asset.getAttributes().getValue(flag).orElse(false)) {
          sendAttributeEvent(flag, false);
        }
      }
    }
    long pollMillis = POLL_INTERVAL.toMillis();
    pollFuture =
        scheduledExecutorService.scheduleAtFixedRate(
            this::pollOrders, pollMillis, pollMillis, TimeUnit.MILLISECONDS);
    LOG.info(
        "Deployed Distro GOPACS handler for asset " + assetId + " (" + orders.size() + " tracked)");
  }

  /**
   * Stops polling and closes the client.
   *
   * @param cancelOpenOrders cancels the open orders first, for when the handler stops for good
   *     (disabled or asset deleted) rather than for a manager restart, which picks them up again.
   *     Runs on the executor because the caller is an event thread and cancelling calls Distro. See
   *     {@link #cancelBeforeClose}.
   */
  public void undeploy(boolean cancelOpenOrders) {
    undeployed = true;
    if (pollFuture != null) {
      // Interrupt to abort any in-flight HTTP call before closing the client
      pollFuture.cancel(true);
      pollFuture = null;
    }
    if (cancelOpenOrders) {
      scheduledExecutorService.execute(() -> cancelBeforeClose(null, 1));
    } else {
      // Wait for a running order action to finish so it never uses a closed client.
      synchronized (this) {
        closeClient();
      }
    }
    LOG.info("Undeployed Distro GOPACS handler for asset " + assetId);
  }

  /**
   * Cancels the open orders of a handler that stopped for good, then closes the client. Nothing
   * polls these orders anymore and an order left open at Distro can still match until it expires,
   * so cancels that fail for a reason a retry may fix are tried again, with a doubling delay, up to
   * {@link #CANCEL_ATTEMPTS} attempts. Orders that may still be open when the client closes are
   * logged as severe: those whose cancel kept failing or was refused, and LOCKED ones, which cannot
   * be cancelled.
   *
   * <p>Only the outcome for the orders it cancels is written to the orders saved on the asset, see
   * {@link #saveOutcomes}.
   *
   * <p>Synchronized so it waits for a running order action, like the close in {@link #undeploy}.
   *
   * @param toCancel the orders to cancel, or null for every open order.
   */
  protected synchronized void cancelBeforeClose(List<GopacsOrder> toCancel, int attempt) {
    List<GopacsOrder> attempted = toCancel == null ? ordersFor(null, OPEN_STATUSES) : toCancel;
    List<GopacsOrder> failed = List.of();
    try {
      failed = cancel(attempted);
      saveOutcomes(attempted);
    } catch (RuntimeException e) {
      LOG.log(Level.WARNING, "Failed to cancel open Distro GOPACS orders for asset " + assetId, e);
    }
    // A shut-down executor silently drops what is scheduled on it, so the retry would never run.
    if (!failed.isEmpty() && attempt < CANCEL_ATTEMPTS && !scheduledExecutorService.isShutdown()) {
      long delayMillis = CANCEL_RETRY_DELAY.toMillis() << (attempt - 1);
      LOG.warning(
          String.format(
              "Cancelling Distro GOPACS orders %s for asset %s failed, retrying in %d s (attempt %d"
                  + " of %d)",
              failed.stream().map(GopacsOrder::reference).toList(),
              assetId,
              delayMillis / 1000,
              attempt + 1,
              CANCEL_ATTEMPTS));
      List<GopacsOrder> retry = failed;
      scheduledExecutorService.schedule(
          () -> cancelBeforeClose(retry, attempt + 1), delayMillis, TimeUnit.MILLISECONDS);
      return;
    }
    List<String> mayBeOpen =
        orders.values().stream()
            .filter(order -> !FINAL_STATUSES.contains(order.status()))
            .map(GopacsOrder::reference)
            .toList();
    if (!mayBeOpen.isEmpty()) {
      LOG.severe(
          String.format(
              "Stopped Distro GOPACS handler for asset %s without cancelling orders %s: they may"
                  + " still be open and matched until they expire. Cancel them in Distro.",
              assetId, mayBeOpen));
    }
    closeClient();
  }

  /**
   * Writes the outcome of cancelling the orders into the orders saved on the asset and leaves the
   * rest as saved, as a handler started for the asset in the meantime saves its own orders there.
   * An order that is finished with is removed; one still tracked is saved as it is now, so the next
   * handler neither records its cancel as a status change nor its trades a second time.
   *
   * <p>Known gap: a handler started while these cancels are still being retried has already
   * restored the orders, so it records a cancelled order's status change and trades again.
   *
   * <p>Does nothing without orders: the saved orders read here could then predate a save still on
   * its way, and writing them back would undo it.
   */
  private void saveOutcomes(List<GopacsOrder> attempted) {
    if (attempted.isEmpty()) {
      return;
    }
    EmsDistroEnergyAsset asset = findAsset();
    if (asset == null) {
      return;
    }
    Map<String, GopacsOrder> saved = savedOrders(asset);
    for (GopacsOrder order : attempted) {
      GopacsOrder current = orders.get(order.reference());
      if (current == null) {
        saved.remove(order.reference());
      } else if (saved.containsKey(order.reference())) {
        saved.put(order.reference(), current);
      }
    }
    sendOrders(saved);
  }

  private void closeClient() {
    if (client != null) {
      client.close();
    }
  }

  /**
   * Reacts to a changed {@link #INPUTS} attribute of the asset. Runs on the executor because every
   * reaction may call Distro, and the caller is the client event thread.
   *
   * <p>Events whose value did not change are ignored: a save of the asset republishes all of its
   * attributes, and an attribute link on the inputs would forward every unchanged update the
   * redispatch poller writes on each poll.
   */
  public void onAttributeEvent(AttributeEvent event) {
    Object oldValue = event.getOldValue().orElse(null);
    Object value = event.getValue().orElse(null);
    if (Objects.equals(oldValue, value)) {
      if (Boolean.TRUE.equals(value) && isFlag(event.getName())) {
        LOG.info(
            String.format(
                "Ignoring %s for Distro GOPACS asset %s: it is already true, set it to false first",
                event.getName(), assetId));
      } else {
        LOG.finest(
            String.format(
                "Ignoring unchanged %s for Distro GOPACS asset %s", event.getName(), assetId));
      }
      return;
    }
    LOG.fine(
        String.format(
            "Distro GOPACS asset %s: %s changed from %s to %s",
            assetId, event.getName(), oldValue, value));
    scheduledExecutorService.execute(
        () -> {
          try {
            processAttributeChange(event.getName(), oldValue, value);
          } catch (Exception e) {
            LOG.log(
                Level.SEVERE,
                "Failed to process " + event.getName() + " for Distro GOPACS asset " + assetId,
                e);
          }
        });
  }

  private static boolean isFlag(String attributeName) {
    return attributeName.equals(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.getName())
        || attributeName.equals(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.getName());
  }

  protected synchronized void processAttributeChange(
      String attributeName, Object oldValue, Object value) {
    if (undeployed) {
      LOG.fine(
          String.format(
              "Dropping %s for Distro GOPACS asset %s: the handler is undeployed",
              attributeName, assetId));
      return;
    }
    if (attributeName.equals(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.getName())) {
      // The announcement closed or was superseded: what is still open for it can no longer help.
      // Compared with the asset's announcement rather than this event's value, because the
      // executor may run the reactions to two quick changes in either order.
      EmsDistroEnergyAsset asset = findAsset();
      if (asset != null
          && cancelStaleOrders(orders.values(), asset.getGopacsAnnouncementId().orElse(null))) {
        saveOrders();
      }
    }

    if (SUGGESTION_INPUTS.contains(attributeName)) {
      updateSuggestion();
    } else if (attributeName.equals(EmsDistroEnergyAsset.GOPACS_ORDER_PRICE.getName())
        && value instanceof Number price) {
      replaceOrders(price.doubleValue());
    } else if (attributeName.equals(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.getName())
        && Boolean.TRUE.equals(value)) {
      LOG.info("Order confirmed for Distro GOPACS asset " + assetId);
      placeOrder();
      resetFlag(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER);
    } else if (attributeName.equals(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.getName())
        && Boolean.TRUE.equals(value)) {
      LOG.info("Cancel of all open orders requested for Distro GOPACS asset " + assetId);
      cancel(ordersFor(null, OPEN_STATUSES));
      saveOrders();
      resetFlag(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS);
    }
  }

  /** Pre-fills order type and quantity for the current announcement, or clears both. */
  protected void updateSuggestion() {
    EmsDistroEnergyAsset asset = findAsset();
    if (asset == null) {
      return;
    }
    String category = asset.getGopacsEanEffectivity().orElse(null);
    Long start = asset.getGopacsStartTime().orElse(null);
    Long end = asset.getGopacsEndTime().orElse(null);

    GopacsOrderTypeValueType orderType = null;
    Double quantityKw = null;
    if (asset.getGopacsAnnouncementId().isPresent()
        && category != null
        && start != null
        && end != null) {
      double windowHours = (end - start) / 3_600_000.0;
      List<EmsElectricityBatteryAsset> batteries = findBatteries(asset.getParentId());
      double buyKw = quantityKw(batteries, BUY, windowHours);
      double sellKw = quantityKw(batteries, SELL, windowHours);
      orderType = orderTypeFor(category, buyKw, sellKw);
      double sideKw = orderType == BUY ? buyKw : sellKw;
      if (orderType == null) {
        LOG.info(
            String.format(
                "No order suggested for Distro GOPACS asset %s: effectivity category '%s' favours"
                    + " neither side",
                assetId, category));
      } else if (sideKw < MIN_QUANTITY_KW) {
        LOG.info(
            String.format(
                "No order suggested for Distro GOPACS asset %s: the batteries can hold %s kW to %s,"
                    + " below the 100 kW minimum",
                assetId, sideKw, orderType));
        orderType = null;
      } else {
        quantityKw = sideKw;
        LOG.info(
            String.format(
                "Suggested %s of %s kW for Distro GOPACS asset %s (category %s, buy/sell %s/%s kW)",
                orderType, quantityKw, assetId, category, buyKw, sellKw));
      }
    } else {
      LOG.fine(
          String.format(
              "No order suggested for Distro GOPACS asset %s: announcement incomplete (id=%s,"
                  + " category=%s, start=%s, end=%s)",
              assetId, asset.getGopacsAnnouncementId().orElse(null), category, start, end));
    }
    sendAttributeEvent(EmsDistroEnergyAsset.GOPACS_ORDER_TYPE, orderType);
    sendAttributeEvent(EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY, quantityKw);
  }

  protected List<EmsElectricityBatteryAsset> findBatteries(String parentId) {
    if (parentId == null) {
      return List.of();
    }
    return assetStorageService
        .findAll(new AssetQuery().parents(parentId).types(EmsElectricityBatteryAsset.class))
        .stream()
        .map(asset -> (EmsElectricityBatteryAsset) asset)
        .toList();
  }

  /**
   * Maps a GOPACS EAN effectivity category ({@code BUY_<effectivity>_SELL_<effectivity>}) to the
   * side an order on the EAN helps most. When both sides are equally effective the side the
   * batteries can offer more of wins, BUY on a draw.
   *
   * @return BUY, SELL, or null when neither side helps or the category is not recognised.
   */
  static GopacsOrderTypeValueType orderTypeFor(String category, double buyKw, double sellKw) {
    if (category == null || !category.startsWith("BUY_")) {
      return null;
    }
    int separator = category.indexOf("_SELL_");
    if (separator < 0) {
      return null;
    }
    int buy = EFFECTIVITY_RANKS.indexOf(category.substring("BUY_".length(), separator));
    int sell = EFFECTIVITY_RANKS.indexOf(category.substring(separator + "_SELL_".length()));
    if (buy < 0 || sell < 0 || (buy == 0 && sell == 0)) {
      return null;
    }
    if (buy != sell) {
      return buy > sell ? BUY : SELL;
    }
    return buyKw >= sellKw ? BUY : SELL;
  }

  /**
   * The constant power the batteries can hold for the whole window on one side: per battery its
   * maximum power, limited so its energy over the window fits its own state-of-charge headroom
   * (seen from the grid, so through the charge or discharge efficiency), summed over the batteries.
   * Each battery is limited on its own because one battery's spare energy cannot make up for
   * another's power limit. Batteries that are not allowed on that side, are disconnected or lack
   * the data to size them are left out. The total is rounded down to the 0.1 MW steps Distro
   * accepts.
   */
  static double quantityKw(
      List<EmsElectricityBatteryAsset> batteries,
      GopacsOrderTypeValueType orderType,
      double windowHours) {
    if (windowHours <= 0) {
      return 0;
    }
    boolean buy = orderType == BUY;
    double totalKw = 0;

    for (EmsElectricityBatteryAsset battery : batteries) {
      boolean allowed =
          (buy ? battery.getAllowCharging() : battery.getAllowDischarging()).orElse(false);
      boolean disconnected =
          battery.getConnectionStatus().orElse(null)
              == EmsElectricityBatteryConnectionStatusValueType.disconnected;
      Double capacityKwh = battery.getEnergyCapacity().orElse(null);
      Double levelPercentage = battery.getEnergyLevelPercentage().orElse(null);
      Integer limitPercentage =
          (buy
                  ? battery.getEnergyLevelPercentageMaximum()
                  : battery.getEnergyLevelPercentageMinimum())
              .orElse(null);
      if (!allowed
          || disconnected
          || capacityKwh == null
          || levelPercentage == null
          || limitPercentage == null) {
        continue;
      }

      double headroomKwh =
          capacityKwh
              * (buy ? limitPercentage - levelPercentage : levelPercentage - limitPercentage)
              / 100.0;
      if (headroomKwh <= 0) {
        continue;
      }
      double efficiency =
          (buy ? battery.getChargeEfficiency() : battery.getDischargeEfficiency()).orElse(100)
              / 100.0;
      // Charging draws more from the grid than ends up stored; discharging delivers less.
      double energyKwh = buy ? headroomKwh / efficiency : headroomKwh * efficiency;
      // Discharge power is stored as a negative number.
      double powerKw =
          Math.abs(
              (buy ? battery.getChargePowerMaximum() : battery.getDischargePowerMaximum())
                  .orElse(0.0));
      totalKw += Math.min(powerKw, energyKwh / windowHours);
    }

    return roundDownToStep(totalKw);
  }

  /** Rounded down to the 100 kW steps Distro accepts, so an order never offers more than asked. */
  static double roundDownToStep(double kw) {
    return Math.floor(kw / MIN_QUANTITY_KW) * MIN_QUANTITY_KW;
  }

  /**
   * The bare EAN code Distro takes. The EAN is copied from the GOPACS asset's contracted EAN, which
   * may carry GOPACS's optional, case-insensitive "ean." prefix.
   */
  static String eanCode(String ean) {
    String trimmed = ean.trim();
    return trimmed.regionMatches(
            true, 0, GOPACSHandler.EAN_PREFIX, 0, GOPACSHandler.EAN_PREFIX.length())
        ? trimmed.substring(GOPACSHandler.EAN_PREFIX.length())
        : trimmed;
  }

  /** Distro takes prices with at most two decimals. */
  static double roundToCents(double price) {
    return Math.round(price * 100) / 100.0;
  }

  /**
   * The bid validity end as order expiration, rounded down to a quarter-hour. Left to Distro's
   * default (15 minutes before start) when it is unknown, already passed or later than that.
   */
  static Long expirationSeconds(long startSeconds, Long validityEndSeconds, long nowSeconds) {
    if (validityEndSeconds == null) {
      return null;
    }
    long expiration = validityEndSeconds - Math.floorMod(validityEndSeconds, ISP_SECONDS);
    return expiration > nowSeconds && expiration <= startSeconds - ISP_SECONDS ? expiration : null;
  }

  /**
   * Checks an order against what Distro accepts. The window comes from the GOPACS announcement, so
   * it is trusted to be quarter-hour aligned.
   *
   * @return why the order cannot be placed, or null when it is valid.
   */
  static String validateInput(
      String announcementId,
      String ean,
      GopacsOrderTypeValueType orderType,
      Double enteredKw,
      Double price,
      Long start,
      Long end,
      Long validityEndSeconds,
      long nowSeconds) {
    if (announcementId == null) {
      return "no announcement";
    }
    if (ean == null || !ean.matches("\\d{18}")) {
      return "EAN '" + ean + "' is not an 18-digit EAN";
    }
    if (orderType == null) {
      return "no order type";
    }
    if (enteredKw == null || roundDownToStep(enteredKw) < MIN_QUANTITY_KW) {
      return "quantity " + enteredKw + " kW is below the 100 kW minimum";
    }
    if (price == null) {
      return "no price";
    }
    if (start == null || end == null || end <= start) {
      return "window " + start + " to " + end + " is empty";
    }
    // Distro needs the order to expire in the future and at least 15 minutes before start.
    if (start / 1000 - ISP_SECONDS <= nowSeconds) {
      return "window starts in less than 15 minutes";
    }
    // GOPACS no longer takes bids for the announcement.
    if (validityEndSeconds != null && validityEndSeconds <= nowSeconds) {
      return "bid validity end has passed";
    }
    return null;
  }

  /** Places an order for the current announcement from the asset's order attributes. */
  protected void placeOrder() {
    EmsDistroEnergyAsset asset = findAsset();
    if (asset == null) {
      return;
    }
    String announcementId = asset.getGopacsAnnouncementId().orElse(null);
    String ean = asset.getGopacsEan().map(DistroGopacsHandler::eanCode).orElse(null);
    GopacsOrderTypeValueType orderType = asset.getGopacsOrderType().orElse(null);
    Double enteredKw = asset.getGopacsOrderQuantity().orElse(null);
    Double price = asset.getGopacsOrderPrice().map(DistroGopacsHandler::roundToCents).orElse(null);
    Long start = asset.getGopacsStartTime().orElse(null);
    Long end = asset.getGopacsEndTime().orElse(null);
    Long validityEndSeconds = asset.getGopacsBidValidityEnd().map(ms -> ms / 1000).orElse(null);
    long nowSeconds = timerService.getNow().getEpochSecond();

    String invalid =
        validateInput(
            announcementId,
            ean,
            orderType,
            enteredKw,
            price,
            start,
            end,
            validityEndSeconds,
            nowSeconds);
    if (invalid != null) {
      LOG.warning("Not placing Distro GOPACS order for asset " + assetId + ": " + invalid);
      recordHistory("REFUSED", null, invalid);
      return;
    }

    long startSeconds = start / 1000;
    GopacsOrderRequest request =
        new GopacsOrderRequest(
            UUID.randomUUID().toString(),
            orderType.name(),
            roundDownToStep(enteredKw) / 1000,
            startSeconds,
            end / 1000,
            price,
            ean,
            Map.of(METADATA_ANNOUNCEMENT_ID, announcementId),
            expirationSeconds(startSeconds, validityEndSeconds, nowSeconds));
    LOG.info(
        String.format(
            "Placing Distro GOPACS order for asset %s (entered %s kW): %s",
            assetId, enteredKw, ValueUtil.asJSON(request).orElse(request.toString())));
    // Tracked before posting: a timeout can hide an order Distro did accept. Polling then finds
    // its real status, or untracks it when Distro answers 404.
    orders.put(request.reference(), GopacsOrder.pending(request));
    try {
      GopacsOrder order = resource.createOrder(portfolio, request);
      LOG.info(
          String.format(
              "Placed Distro GOPACS order %s for asset %s: status %s",
              order.reference(), assetId, order.status()));
      orders.put(order.reference(), order);
      recordHistory("CREATED", order, null);
    } catch (RuntimeException e) {
      String failure = logFailure("Failed to place Distro GOPACS order for asset " + assetId, e);
      if (isClientError(e)) {
        // Rejected outright, so Distro holds no order for this reference.
        orders.remove(request.reference());
      }
      recordHistory("CREATE_FAILED", orders.get(request.reference()), failure);
    }
    saveOrders();
  }

  /** Re-submits the replaceable orders for the current announcement at the new price. */
  protected void replaceOrders(double price) {
    EmsDistroEnergyAsset asset = findAsset();
    String announcementId = asset == null ? null : asset.getGopacsAnnouncementId().orElse(null);
    if (announcementId == null) {
      return;
    }
    List<GopacsOrder> toReplace = ordersFor(announcementId, REPLACEABLE_STATUSES);
    if (toReplace.isEmpty()) {
      LOG.fine(
          String.format(
              "Price change for Distro GOPACS asset %s: no replaceable orders for announcement %s",
              assetId, announcementId));
      return;
    }
    price = roundToCents(price);
    for (GopacsOrder order : toReplace) {
      GopacsOrderRequest request = order.toRequest(price);
      try {
        GopacsOrder replaced = resource.replaceOrder(portfolio, order.reference(), request);
        LOG.info(
            String.format(
                "Replaced Distro GOPACS order %s for asset %s at %s EUR/MWh",
                order.reference(), assetId, price));
        orders.put(replaced.reference(), replaced);
        recordHistory("REPLACED", replaced, null);
      } catch (RuntimeException e) {
        // 423 means the order is locked for matching; it keeps its old price.
        String failure =
            logFailure(
                "Failed to replace Distro GOPACS order "
                    + order.reference()
                    + " for asset "
                    + assetId,
                e);
        recordHistory("REPLACE_FAILED", order, failure);
      }
    }
    saveOrders();
  }

  /**
   * Cancels those of the candidates that are open and placed for another announcement than the
   * current one, or every open one when there is none. Runs on every change of announcement and
   * again after every poll, so a cancel that failed is retried until it succeeds or the order is no
   * longer open, at the latest when it expires. Does not save the tracked orders.
   *
   * @return whether there were any such orders.
   */
  protected boolean cancelStaleOrders(
      Collection<GopacsOrder> candidates, String currentAnnouncementId) {
    List<GopacsOrder> stale =
        candidates.stream()
            .filter(
                order ->
                    OPEN_STATUSES.contains(order.status())
                        && !Objects.equals(announcementOf(order), currentAnnouncementId))
            .toList();
    if (stale.isEmpty()) {
      return false;
    }
    LOG.info(
        String.format(
            "Cancelling %d open Distro GOPACS orders for asset %s that are not for announcement %s",
            stale.size(), assetId, currentAnnouncementId));
    cancel(stale);
    return true;
  }

  /**
   * Cancels the orders at Distro. A cancelled order is finished like any order in a final status,
   * as it may have matched in part before the cancel took effect. Does not save the tracked orders.
   *
   * @return the orders whose cancel failed for a reason a retry may fix, see {@link #isPermanent}.
   *     Distro answers 423 when the order is no longer SUBMITTED or PENDING.
   */
  protected List<GopacsOrder> cancel(List<GopacsOrder> toCancel) {
    List<GopacsOrder> failed = new ArrayList<>();
    for (GopacsOrder order : toCancel) {
      try {
        resource.cancelOrder(portfolio, order.reference());
        // Also the only record once the asset is deleted, as the writes below then go nowhere.
        LOG.info("Cancelled Distro GOPACS order " + order.reference() + " for asset " + assetId);
        GopacsOrder canceled = order.withStatus("CANCELED");
        recordHistory("CANCELED", canceled, null);
        finishOrder(canceled);
      } catch (RuntimeException e) {
        String failure =
            logFailure(
                "Failed to cancel Distro GOPACS order "
                    + order.reference()
                    + " for asset "
                    + assetId,
                e);
        recordHistory("CANCEL_FAILED", order, failure);
        if (!isPermanent(e)) {
          failed.add(order);
        }
      }
    }
    return failed;
  }

  /** The tracked orders in one of the statuses, for the announcement or for any when null. */
  protected List<GopacsOrder> ordersFor(String announcementId, Set<String> statuses) {
    List<GopacsOrder> matching = new ArrayList<>();
    for (GopacsOrder order : orders.values()) {
      if (statuses.contains(order.status())
          && (announcementId == null || announcementId.equals(announcementOf(order)))) {
        matching.add(order);
      }
    }
    return matching;
  }

  /** The announcement an order was placed for. */
  private static String announcementOf(GopacsOrder order) {
    return order.metadata() == null ? null : order.metadata().get(METADATA_ANNOUNCEMENT_ID);
  }

  /**
   * Refreshes every tracked order. A status change is recorded in the history and an order in a
   * final status is finished with, see {@link #finishOrder}. Then, with the statuses fresh, the
   * open orders of an announcement that is no longer current are cancelled, which retries a cancel
   * that failed when the announcement changed. Only orders refreshed in this poll are cancelled, so
   * a Distro outage does not add a failed cancel to the history every poll.
   */
  protected synchronized void pollOrders() {
    if (undeployed || orders.isEmpty()) {
      return;
    }
    LOG.fine(String.format("Polling %d Distro GOPACS orders for asset %s", orders.size(), assetId));
    List<GopacsOrder> refreshed = new ArrayList<>();
    for (GopacsOrder tracked : List.copyOf(orders.values())) {
      try {
        GopacsOrder order = resource.getOrder(portfolio, tracked.reference());
        if (!Objects.equals(order.status(), tracked.status())) {
          LOG.info(
              String.format(
                  "Distro GOPACS order %s for asset %s went from %s to %s%s",
                  order.reference(),
                  assetId,
                  tracked.status(),
                  order.status(),
                  order.reason() == null ? "" : " (" + order.reason() + ")"));
          recordHistory("STATUS", order, order.reason());
        }
        if (FINAL_STATUSES.contains(order.status())) {
          finishOrder(order);
        } else {
          orders.put(order.reference(), order);
          refreshed.add(order);
        }
      } catch (WebApplicationException e) {
        if (e.getResponse().getStatus() == 404) {
          LOG.warning(
              "Distro no longer knows GOPACS order " + tracked.reference() + "; untracking it");
          orders.remove(tracked.reference());
          recordHistory("NOT_FOUND", tracked, null);
        } else {
          logFailure("Failed to refresh Distro GOPACS order " + tracked.reference(), e);
        }
      } catch (RuntimeException e) {
        logFailure("Failed to refresh Distro GOPACS order " + tracked.reference(), e);
      }
    }
    EmsDistroEnergyAsset asset = findAsset();
    if (asset != null) {
      cancelStaleOrders(refreshed, asset.getGopacsAnnouncementId().orElse(null));
    }
    saveOrders();
    sendAttributeEvent(EmsDistroEnergyAsset.GOPACS_LAST_SYNC, timerService.getCurrentTimeMillis());
  }

  /**
   * Stops tracking an order in a final status once its trades are recorded. Any final order can
   * have trades: Distro reports them when the order matches, and a partly matched order can still
   * end EXPIRED, CANCELED or REJECTED. While they cannot be fetched the order stays tracked in its
   * final status, so the next poll, also after a restart, tries again without recording the status
   * change twice.
   */
  protected void finishOrder(GopacsOrder order) {
    if (recordSettlements(order)) {
      orders.remove(order.reference());
    } else {
      orders.put(order.reference(), order);
    }
  }

  /**
   * Records the trades of an order in a final status as one data point. A final order's trades no
   * longer change and the order is untracked right after, so they are recorded once, unless the
   * manager stops between recording them and saving the tracked orders.
   *
   * <p>A failed fetch is retried until {@link #SETTLEMENT_RETRY_WINDOW} after the order's window
   * ends, and so is an empty answer for a SETTLED order, which always has trades. An empty answer
   * for any other final status means it never matched. A permanent failure, see {@link
   * #isPermanent}, is not retried.
   *
   * @return true when the order is done with: its trades are recorded or will not be.
   */
  protected boolean recordSettlements(GopacsOrder order) {
    List<GopacsSettlement> settlements;
    try {
      settlements = resource.listSettlements(portfolio, order.reference());
    } catch (RuntimeException e) {
      String failure =
          logFailure(
              "Failed to fetch the trades of "
                  + order.status()
                  + " Distro GOPACS order "
                  + order.reference(),
              e);
      return settlementsFailed(order, failure, isPermanent(e));
    }
    if (settlements == null || settlements.isEmpty()) {
      if (!"SETTLED".equals(order.status())) {
        LOG.fine(
            String.format(
                "%s Distro GOPACS order %s has no trades", order.status(), order.reference()));
        return true;
      }
      LOG.warning("Distro returned no trades for settled GOPACS order " + order.reference());
      return settlementsFailed(order, "Distro returned no trades", false);
    }

    // One data point for all trades: OpenRemote stamps events with the current millisecond and
    // keeps one data point per timestamp, so trades sent one by one would overwrite each other.
    Map<String, Object> entry = new HashMap<>();
    entry.put("reference", order.reference());
    entry.put(
        "trades",
        ValueUtil.JSON.convertValue(
            settlements, new TypeReference<List<Map<String, Object>>>() {}));
    entry.put("recordedAt", timerService.getCurrentTimeMillis());
    sendAttributeEvent(EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY, entry);
    return true;
  }

  /**
   * Gives up on an order's trades when the failure is permanent or the retry window has passed.
   *
   * @return true when given up, false when the next poll should try again.
   */
  private boolean settlementsFailed(GopacsOrder order, String failure, boolean permanent) {
    long giveUpAt = order.end() * 1000 + SETTLEMENT_RETRY_WINDOW.toMillis();
    if (!permanent && timerService.getCurrentTimeMillis() < giveUpAt) {
      LOG.info(
          "Keeping Distro GOPACS order "
              + order.reference()
              + " tracked to fetch its trades again later");
      return false;
    }
    LOG.severe(
        String.format(
            "Giving up on the trades of Distro GOPACS order %s for asset %s, they are not recorded:"
                + " %s",
            order.reference(), assetId, failure));
    recordHistory("SETTLEMENTS_FAILED", order, failure);
    return true;
  }

  protected void recordHistory(String action, GopacsOrder order, String detail) {
    Map<String, Object> entry = new HashMap<>();
    entry.put("action", action);
    if (order != null) {
      entry.putAll(ValueUtil.JSON.convertValue(order, new TypeReference<Map<String, Object>>() {}));
    }
    if (detail != null) {
      entry.put("detail", detail);
    }
    entry.put("timestamp", timerService.getCurrentTimeMillis());
    sendAttributeEvent(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY, entry);
  }

  protected void saveOrders() {
    sendOrders(orders);
  }

  private void sendOrders(Map<String, GopacsOrder> toSave) {
    sendAttributeEvent(
        EmsDistroEnergyAsset.GOPACS_ORDERS,
        ValueUtil.JSON.convertValue(toSave, new TypeReference<Map<String, Object>>() {}));
  }

  /** The orders saved on the asset, by reference. */
  private static Map<String, GopacsOrder> savedOrders(EmsDistroEnergyAsset asset) {
    return asset
        .getGopacsOrders()
        .map(
            saved ->
                ValueUtil.JSON.convertValue(
                    saved, new TypeReference<LinkedHashMap<String, GopacsOrder>>() {}))
        .orElseGet(LinkedHashMap::new);
  }

  /**
   * Logs a failed Distro call and returns a short description for the history. An HTTP error is
   * logged with Distro's error body and without a stack trace, which adds nothing for it.
   */
  private static String logFailure(String message, RuntimeException e) {
    String failure = describe(e);
    if (e instanceof WebApplicationException) {
      LOG.warning(message + ": " + failure);
    } else {
      LOG.log(Level.WARNING, message, e);
    }
    return failure;
  }

  /** Whether Distro answered with an HTTP 4xx, so it did not act on the request. */
  static boolean isClientError(RuntimeException e) {
    return e instanceof WebApplicationException wae
        && wae.getResponse().getStatus() >= 400
        && wae.getResponse().getStatus() < 500;
  }

  /**
   * Whether a retry of the request cannot succeed: an HTTP 4xx other than 401. The OAuth filter
   * only fetches a new token when the cached one expires, so a rejected token is fixed in time.
   */
  static boolean isPermanent(RuntimeException e) {
    return isClientError(e) && ((WebApplicationException) e).getResponse().getStatus() != 401;
  }

  /** The HTTP status and the error body Distro sent, or the exception without a response. */
  static String describe(RuntimeException e) {
    if (!(e instanceof WebApplicationException wae)) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    Response response = wae.getResponse();
    String body = null;
    try {
      body = response.hasEntity() ? response.readEntity(String.class) : null;
    } catch (RuntimeException ignored) {
      // Already consumed or not readable: the status alone has to do.
    }
    if (body == null || body.isBlank()) {
      return "HTTP " + response.getStatus();
    }
    body = body.strip();
    return "HTTP "
        + response.getStatus()
        + ": "
        + (body.length() > MAX_ERROR_BODY_LENGTH
            ? body.substring(0, MAX_ERROR_BODY_LENGTH) + "..."
            : body);
  }

  private void resetFlag(AttributeDescriptor<Boolean> flag) {
    // Delayed like the redispatch confirmation so the UI shows the toggle before it springs back.
    scheduledExecutorService.schedule(
        () -> {
          if (!undeployed) {
            sendAttributeEvent(flag, false);
          }
        },
        1,
        TimeUnit.SECONDS);
  }

  protected EmsDistroEnergyAsset findAsset() {
    EmsDistroEnergyAsset asset = (EmsDistroEnergyAsset) assetStorageService.find(assetId);
    if (asset == null) {
      LOG.warning("Distro GOPACS asset not found: " + assetId);
    }
    return asset;
  }

  protected <T> void sendAttributeEvent(AttributeDescriptor<T> attribute, Object value) {
    assetProcessingService.sendAttributeEvent(
        new AttributeEvent(assetId, attribute.getName(), value), getClass().getSimpleName());
  }
}
