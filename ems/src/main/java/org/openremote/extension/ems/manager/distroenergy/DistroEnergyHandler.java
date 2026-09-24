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

import static java.time.format.DateTimeFormatter.BASIC_ISO_DATE;
import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.model.syslog.SyslogCategory.API;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset;
import org.openremote.extension.ems.manager.distroenergy.dto.DayAheadSubmission;
import org.openremote.extension.ems.manager.distroenergy.dto.SubmissionData;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.model.Container;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.datapoint.query.AssetDatapointIntervalQuery;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.value.AttributeDescriptor;

public class DistroEnergyHandler {

  private static final Logger LOG = SyslogCategory.getLogger(API, DistroEnergyHandler.class);
  public static final String DISTRO_ENERGY_CLIENT_KEY = "DISTRO_ENERGY_CLIENT_KEY";
  public static final String DISTRO_ENERGY_BASE_URL = "DISTRO_ENERGY_BASE_URL";
  public static final String DISTRO_ENERGY_BASE_URL_DEFAULT = "https://ibt.dev.distro.energy/api/v1";
  public static final String DISTRO_ENERGY_TIMEZONE = "DISTRO_ENERGY_TIMEZONE";
  public static final String DISTRO_ENERGY_TIMEZONE_DEFAULT = "Europe/Amsterdam";
  public static final String REQUEST_INTERVAL_MINUTES = "REQUEST_INTERVAL";
  public static final String REQUEST_INTERVAL_MINUTES_DEFAULT = "60";

  /** Market settlement interval. One submission entry per ISP. */
  protected static final Duration ISP_DURATION = Duration.ofMinutes(15);

  /**
   * Defensive ceiling on one run, not a business window. The loop stops at the first day with no
   * forecast, so this only bounds the damage if a stray far-future datapoint makes the horizon look
   * unbounded.
   */
  protected static final int MAX_DAYS_AHEAD = 14;

  /** The {@link EmsDistroEnergyAsset} this handler reports its status on. */
  protected final String assetId;

  protected final AttributeRef powerNetAttributeRef;
  protected final String distroEnergyBaseUrl;
  protected final String portfolio;
  protected final String clientKey;
  protected final ZoneId marketZone;
  protected final long requestIntervalMinutes;
  protected final ResteasyClient client;
  protected final DayAheadResource dayAheadResource;

  protected final TimerService timerService;
  protected final ScheduledExecutorService scheduledExecutorService;
  protected final AssetPredictedDatapointService assetPredictedDatapointService;
  protected final AssetProcessingService assetProcessingService;
  protected ScheduledFuture<?> nextRequestFuture;

  public static class Factory {
    protected Container container;

    public Factory(Container container) {
      this.container = container;
    }

    public DistroEnergyHandler createHandler(
        String assetId, AttributeRef powerNetAttributeRef, String portfolio) {
      return new DistroEnergyHandler(assetId, powerNetAttributeRef, portfolio, container);
    }
  }

  public DistroEnergyHandler(
      String assetId, AttributeRef powerNetAttributeRef, String portfolio, Container container) {
    this.assetId = assetId;
    this.powerNetAttributeRef = powerNetAttributeRef;
    this.portfolio = portfolio;

    this.timerService = container.getService(TimerService.class);
    this.scheduledExecutorService = container.getScheduledExecutor();
    this.assetPredictedDatapointService =
        container.getService(AssetPredictedDatapointService.class);
    this.assetProcessingService = container.getService(AssetProcessingService.class);

    this.distroEnergyBaseUrl =
        container.getConfig().getOrDefault(DISTRO_ENERGY_BASE_URL, DISTRO_ENERGY_BASE_URL_DEFAULT);
    this.marketZone =
        ZoneId.of(
            container
                .getConfig()
                .getOrDefault(DISTRO_ENERGY_TIMEZONE, DISTRO_ENERGY_TIMEZONE_DEFAULT));
    this.requestIntervalMinutes =
        Integer.parseInt(
            container
                .getConfig()
                .getOrDefault(REQUEST_INTERVAL_MINUTES, REQUEST_INTERVAL_MINUTES_DEFAULT));
    this.clientKey = container.getConfig().get(DISTRO_ENERGY_CLIENT_KEY);

    if (clientKey == null) {
      throw new RuntimeException(
          DISTRO_ENERGY_CLIENT_KEY + " not defined, cannot use Distro Energy.");
    }

    this.client = createClient(org.openremote.container.Container.EXECUTOR);
    try {
      this.dayAheadResource = client.target(this.distroEnergyBaseUrl).proxy(DayAheadResource.class);
    } catch (RuntimeException e) {
      // No reference escapes a constructor that threw, so undeploy() can never close this client.
      client.close();
      throw e;
    }

    LOG.fine(
        "Configured Distro Energy handler for portfolio "
            + portfolio
            + ": baseUrl="
            + distroEnergyBaseUrl
            + ", marketZone="
            + marketZone
            + ", requestIntervalMinutes="
            + requestIntervalMinutes);
  }

  /**
   * Starts the recurring submission.
   *
   * <p>Separate from the constructor so the scheduled task cannot observe a partly constructed
   * handler: {@link #getFirstRequestDelayMillis()} clamps to zero, so any deploy after the half
   * hour would otherwise start the task on an executor thread while the constructor was still
   * running.
   */
  public void deploy() {
    long firstRequestDelayMillis = getFirstRequestDelayMillis();
    LOG.fine(
        "First Distro Energy day-ahead submission for portfolio "
            + portfolio
            + " scheduled at "
            + Instant.ofEpochMilli(timerService.getCurrentTimeMillis() + firstRequestDelayMillis)
            + " (delay "
            + firstRequestDelayMillis
            + "ms), repeating every "
            + requestIntervalMinutes
            + " minute(s)");
    nextRequestFuture =
        scheduledExecutorService.scheduleAtFixedRate(
            this::submitDayAheadForecasts,
            firstRequestDelayMillis,
            Duration.ofMinutes(requestIntervalMinutes).toMillis(),
            TimeUnit.MILLISECONDS);
    LOG.info("Deployed Distro Energy handler for portfolio: " + portfolio);
  }

  protected void submitDayAheadForecasts() {
    LocalDate firstDay = timerService.getNow().atZone(marketZone).toLocalDate().plusDays(1);
    LOG.fine(
        "Starting day-ahead submission run for portfolio "
            + portfolio
            + "; first market day "
            + firstDay
            + ", horizon up to "
            + MAX_DAYS_AHEAD
            + " day(s)");
    int submitted = 0;

    for (int day = 0; day < MAX_DAYS_AHEAD; day++) {
      LocalDate marketDate = firstDay.plusDays(day);
      try {
        if (!submitDayAheadForecast(marketDate)) {
          // First uncovered day is the end of the forecast horizon; nothing beyond it to send.
          break;
        }
        submitted++;
      } catch (Exception e) {
        // Keep going: one bad day must not lose the days after it, nor kill the recurring task.
        LOG.log(
            Level.WARNING,
            "Failed to submit day-ahead forecast for portfolio "
                + portfolio
                + " and day "
                + marketDate,
            e);
      }
    }

    // Status on the asset, so a stalled handler is visible without the logs. daysSubmitted is
    // written on every run; lastSubmission only when something was sent, so a run that found no
    // forecast at all leaves a fresh daysSubmitted of 0 next to a stale lastSubmission.
    sendAttributeEvent(EmsDistroEnergyAsset.DAYS_SUBMITTED, submitted);
    if (submitted > 0) {
      sendAttributeEvent(EmsDistroEnergyAsset.LAST_SUBMISSION, timerService.getCurrentTimeMillis());
    }

    // Reaching the ceiling means the horizon looked unbounded, which the forecast producer cannot
    // legitimately do; the run was truncated and later days were not sent.
    if (submitted >= MAX_DAYS_AHEAD) {
      LOG.warning(
          "Day-ahead run for portfolio "
              + portfolio
              + " hit the ceiling of "
              + MAX_DAYS_AHEAD
              + " days; check the predicted "
              + powerNetAttributeRef.getName()
              + " data for far-future values");
    } else {
      LOG.fine("Day-ahead run for portfolio " + portfolio + " submitted " + submitted + " day(s)");
    }
  }

  /**
   * @return whether a submission was actually POSTed for this day.
   */
  protected boolean submitDayAheadForecast(LocalDate marketDate) {
    ZoneId storageZone = ZoneId.systemDefault();
    ZonedDateTime dayStart = marketDate.atStartOfDay(marketZone);
    ZonedDateTime dayEnd = dayStart.plusDays(1);

    // The interval query bounds are inclusive at both ends, so the upper bound is the start of the
    // last ISP rather than the end of the day. Otherwise the query returns one bucket too many.
    List<ValueDatapoint<?>> datapoints =
        assetPredictedDatapointService.queryDatapoints(
            powerNetAttributeRef.getId(),
            powerNetAttributeRef.getName(),
            new AssetDatapointIntervalQuery(
                dayStart.withZoneSameInstant(storageZone).toLocalDateTime(),
                dayEnd.minus(ISP_DURATION).withZoneSameInstant(storageZone).toLocalDateTime(),
                "15 minutes",
                AssetDatapointIntervalQuery.Formula.AVG,
                true));

    LOG.fine(
        "Queried "
            + datapoints.size()
            + " predicted datapoint(s) for portfolio "
            + portfolio
            + " and day "
            + marketDate);

    List<SubmissionData> submissionData =
        buildSubmissionData(marketDate, marketZone, storageZone, datapoints);

    if (submissionData.isEmpty()) {
      // Beyond the forecast horizon. The task repeats and the API overwrites a day on every
      // submission, so the day is sent by the first run after the horizon reaches it; there is no
      // catch-up state to keep here.
      LOG.fine(
          "No forecast yet for portfolio "
              + portfolio
              + " and day "
              + marketDate
              + "; not sending");
      return false;
    }

    dayAheadResource.postDayAhead(
        portfolio,
        clientKey,
        new DayAheadSubmission(
            submissionData.toArray(new SubmissionData[0]),
            Long.parseLong(marketDate.format(BASIC_ISO_DATE)),
            timerService.getCurrentTimeMillis()));
    LOG.fine(
        "Submitted day-ahead forecast for portfolio "
            + portfolio
            + " and day "
            + marketDate
            + " ("
            + submissionData.size()
            + " interval(s))");
    return true;
  }

  /**
   * Builds one submission entry per ISP of the given market day, in ascending position order, or an
   * empty list when the day carries no forecast at all.
   *
   * <p>The entries are driven by a grid of real instants stepping from the start to the end of the
   * market day, so a submitted day is always 92, 96 or 100 entries depending on whether the day
   * carries a DST transition. Each instant is mapped back into the frame the predicted datapoint
   * table is written in, which is the JVM default zone (see {@code AbstractDatapointService}), and
   * looked up there.
   *
   * <p>An empty result means "nothing to submit" and cannot be confused with a real day, which is
   * never shorter than 92 entries. A day beyond the external forecast producer's horizon
   * legitimately has no data, and submitting it would post a zero net power trading position for a
   * day we know nothing about. Within a day that does have a forecast every gap is filled, interior
   * and trailing alike, because the API requires the complete day and a volume on every entry.
   *
   * <p>A gap goes out as 0.0, which is a real trading position, except in the repeated hour of the
   * fall-back day. An ISP qualifies when its market local time occurs more than once in the day's
   * grid: four ISPs against a whole-hour transition, two against a half-hour one such as
   * Australia/Lord_Howe. Those ISPs exist only because of the transition, and a producer writing a
   * fixed 96-slot day leaves them without a row of their own, so they reuse the next forecast value
   * of the day rather than trade the hour away. With nothing left in the day to reuse they fall
   * back to 0.0 like any other gap.
   *
   * <p>They all reuse that same next value, so the hour goes out flat. That is deliberate.
   * Borrowing from the ISP that shares the market local time would keep the hour's shape, at the
   * price of a second fill rule, and one quarter-hour of the repeated hour is no better a guess for
   * the others than the value that follows them.
   *
   * <p>The decision is taken from the ISP grid rather than from whatever the query returned, so a
   * value belonging to a neighbouring day can never make this day look covered.
   *
   * <p>Under a JVM zone that observes DST the storage frame is not monotonic, so on the fall-back
   * day the two instants of the repeated hour collapse onto a single stored row and both read it.
   * That is a consequence of the naive primary key upstream (openremote/openremote#3292). The
   * collapse can only duplicate a read, never erase one, so it cannot turn a day with a forecast
   * into a skip, and while it lasts the repeated hour is never a gap at all: both passes read the
   * one surviving row, so the hour carries as many distinct values as it has ISPs. Once predicted
   * datapoints are stored in UTC every instant maps to a row of its own, a producer that writes the
   * hour once leaves the other pass empty, and that pass is filled flat as above. The day submitted
   * on the fall-back date therefore changes when #3292 lands; no other day is affected.
   */
  static List<SubmissionData> buildSubmissionData(
      LocalDate marketDate,
      ZoneId marketZone,
      ZoneId storageZone,
      List<ValueDatapoint<?>> datapoints) {

    Map<LocalDateTime, Double> valuesByStorageKey = new HashMap<>();
    for (ValueDatapoint<?> datapoint : datapoints) {
      // Gap-filled buckets carry a null value; skip them so they fall through to the fill below.
      if (datapoint.getValue() instanceof Number value) {
        valuesByStorageKey.put(
            Instant.ofEpochMilli(datapoint.getTimestamp()).atZone(storageZone).toLocalDateTime(),
            value.doubleValue());
      }
    }

    ZonedDateTime dayStart = marketDate.atStartOfDay(marketZone);
    ZonedDateTime dayEnd = dayStart.plusDays(1);

    List<ZonedDateTime> isps = new ArrayList<>();
    for (ZonedDateTime isp = dayStart; isp.isBefore(dayEnd); isp = isp.plus(ISP_DURATION)) {
      isps.add(isp);
    }

    // The repeated hour of the fall-back day is the only stretch whose market local time is not
    // unique within the day, so on every other day this stays empty and nothing borrows a value.
    Set<LocalDateTime> seenLocalTimes = new HashSet<>();
    Set<LocalDateTime> repeatedLocalTimes = new HashSet<>();
    for (ZonedDateTime isp : isps) {
      LocalDateTime localTime = isp.toLocalDateTime();
      if (!seenLocalTimes.add(localTime)) {
        repeatedLocalTimes.add(localTime);
      }
    }

    Double[] values = new Double[isps.size()];
    Set<LocalDateTime> keysRead = new HashSet<>();
    int collapsed = 0;
    int lastRealPosition = 0;

    for (int i = 0; i < isps.size(); i++) {
      LocalDateTime storageKey = isps.get(i).withZoneSameInstant(storageZone).toLocalDateTime();
      Double value = valuesByStorageKey.get(storageKey);
      values[i] = value;

      if (value != null) {
        lastRealPosition = i + 1;
        if (!keysRead.add(storageKey)) {
          collapsed++;
        }
      }
    }

    // Nothing at all was forecast for this day. Hand the caller the empty sentinel and stay silent
    // here: the caller knows the portfolio and owns the log line.
    if (lastRealPosition == 0) {
      return List.of();
    }

    // Walk backwards so every gap has the next forecast value of the day to hand. Only the repeated
    // DST hour takes it. A filled position never becomes a source, so what is borrowed is always a
    // real forecast value rather than another gap's 0.0.
    int dstFilled = 0;
    int interiorMissing = 0;
    int trailingMissing = 0;
    Double nextRealValue = null;

    for (int i = values.length - 1; i >= 0; i--) {
      if (values[i] != null) {
        nextRealValue = values[i];
      } else if (nextRealValue != null
          && repeatedLocalTimes.contains(isps.get(i).toLocalDateTime())) {
        values[i] = nextRealValue;
        dstFilled++;
      } else {
        values[i] = 0.0;
        if (i + 1 > lastRealPosition) {
          trailingMissing++;
        } else {
          interiorMissing++;
        }
      }
    }

    List<SubmissionData> submissionData = new ArrayList<>(values.length);
    for (int i = 0; i < values.length; i++) {
      submissionData.add(new SubmissionData(i + 1, null, null, values[i]));
    }

    if (collapsed > 0) {
      // One line per day rather than per position: on the fall-back day every position in the
      // repeated hour reads the same stored row.
      LOG.warning(
          "Day-ahead submission for "
              + marketDate
              + " has "
              + collapsed
              + " of "
              + submissionData.size()
              + " positions reusing a value from the repeated DST hour, which collapses onto one"
              + " predicted datapoint row (openremote/openremote#3292)");
    }

    if (dstFilled > 0) {
      // Expected against a producer that writes a fixed 96-slot day: the four extra ISPs of the
      // fall-back hour have no row of their own to read.
      LOG.warning(
          "Day-ahead submission for "
              + marketDate
              + " has "
              + dstFilled
              + " of "
              + submissionData.size()
              + " positions in the repeated DST hour without a predicted datapoint; each reuses the"
              + " next forecast value of the day");
    }

    if (interiorMissing > 0) {
      // A hole before the end of the forecast means the producer skipped intervals it did cover,
      // and those positions go out as 0.0, which is a real trading value.
      LOG.warning(
          "Day-ahead submission for "
              + marketDate
              + " has "
              + interiorMissing
              + " of "
              + submissionData.size()
              + " intervals with a gap inside the forecast; submitted as 0.0");
    }
    if (trailingMissing > 0) {
      // Expected once the forecast horizon ends inside this day. Logged with the boundary so a
      // horizon that is systematically short by a fixed number of ISPs is still diagnosable.
      LOG.fine(
          "Forecast for "
              + marketDate
              + " ends at position "
              + lastRealPosition
              + " of "
              + submissionData.size()
              + "; the remainder is filled with 0.0 up to midnight");
    }

    return submissionData;
  }

  protected <T> void sendAttributeEvent(AttributeDescriptor<T> attribute, T value) {
    assetProcessingService.sendAttributeEvent(
        new AttributeEvent(assetId, attribute.getName(), value), getClass().getSimpleName());
  }

  protected long getFirstRequestDelayMillis() {
    Instant now = timerService.getNow();
    Instant nextHalfHour = now.truncatedTo(ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES);
    // Already past this hour's :30 mark: roll to next hour's, so the first run always lands on :30
    // rather than firing immediately.
    if (!nextHalfHour.isAfter(now)) {
      nextHalfHour = nextHalfHour.plus(1, ChronoUnit.HOURS);
    }
    return Math.max(0L, nextHalfHour.toEpochMilli() - timerService.getCurrentTimeMillis());
  }

  public void undeploy() {
    if (nextRequestFuture != null) {
      LOG.fine("Cancelling scheduled day-ahead submissions for portfolio " + portfolio);
      // Interrupt to abort any in-flight HTTP call before closing the client
      nextRequestFuture.cancel(true);
      nextRequestFuture = null;
    }
    client.close();
    LOG.info("Undeployed Distro Energy handler for portfolio: " + portfolio);
  }
}
