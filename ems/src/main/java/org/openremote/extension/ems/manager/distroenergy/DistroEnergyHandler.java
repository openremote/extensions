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
import org.openremote.extension.ems.manager.distroenergy.dto.DayAheadSubmission;
import org.openremote.extension.ems.manager.distroenergy.dto.SubmissionData;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.model.Container;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.datapoint.query.AssetDatapointIntervalQuery;
import org.openremote.model.syslog.SyslogCategory;

public class DistroEnergyHandler {

  private static final Logger LOG = SyslogCategory.getLogger(API, DistroEnergyHandler.class);
  public static final String DISTRO_ENERGY_CLIENT_KEY = "DISTRO_ENERGY_CLIENT_KEY";
  public static final String DISTRO_ENERGY_BASE_URL = "DISTRO_ENERGY_BASE_URL";
  public static final String DISTRO_ENERGY_BASE_URL_DEFAULT = "https://ibt.dev.distro.energy/";
  public static final String DISTRO_ENERGY_TIMEZONE = "DISTRO_ENERGY_TIMEZONE";
  public static final String DISTRO_ENERGY_TIMEZONE_DEFAULT = "Europe/Amsterdam";
  public static final String REQUEST_INTERVAL_MINUTES = "REQUEST_INTERVAL";
  public static final String REQUEST_INTERVAL_MINUTES_DEFAULT = "60";

  /** Market settlement interval. One submission entry per ISP. */
  protected static final Duration ISP_DURATION = Duration.ofMinutes(15);

  protected static final int DAYS_AHEAD = 5;

  protected final AttributeRef powerNetAttributeRef;
  protected final String distroEnergyBaseUrl;
  protected final String portfolio;
  protected final String clientKey;
  protected final ZoneId marketZone;
  protected final long requestIntervalMinutes;
  protected final DayAheadResource dayAheadResource;

  protected final TimerService timerService;
  protected final ScheduledExecutorService scheduledExecutorService;
  protected final AssetPredictedDatapointService assetPredictedDatapointService;
  protected ScheduledFuture<?> nextRequestFuture;

  public static class Factory {
    protected Container container;

    public Factory(Container container) {
      this.container = container;
    }

    public DistroEnergyHandler createHandler(AttributeRef powerNetAttributeRef, String portfolio) {
      return new DistroEnergyHandler(powerNetAttributeRef, portfolio, container);
    }
  }

  public DistroEnergyHandler(
      AttributeRef powerNetAttributeRef, String portfolio, Container container) {
    this.powerNetAttributeRef = powerNetAttributeRef;
    this.portfolio = portfolio;

    this.timerService = container.getService(TimerService.class);
    this.scheduledExecutorService = container.getScheduledExecutor();
    this.assetPredictedDatapointService =
        container.getService(AssetPredictedDatapointService.class);

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

    ResteasyClient client = createClient(org.openremote.container.Container.EXECUTOR);
    this.dayAheadResource = client.target(this.distroEnergyBaseUrl).proxy(DayAheadResource.class);

    this.nextRequestFuture =
        scheduledExecutorService.scheduleAtFixedRate(
            this::submitDayAheadForecasts,
            getFirstRequestDelayMillis(),
            Duration.ofMinutes(requestIntervalMinutes).toMillis(),
            TimeUnit.MILLISECONDS);
    LOG.info(
        "DistroEnergyHandler instance for distro energy deployed for portfolio: " + this.portfolio);
  }

  protected void submitDayAheadForecasts() {
    LocalDate firstDay = timerService.getNow().atZone(marketZone).toLocalDate().plusDays(1);

    for (int day = 0; day < DAYS_AHEAD; day++) {
      LocalDate marketDate = firstDay.plusDays(day);
      try {
        submitDayAheadForecast(marketDate);
      } catch (Exception e) {
        // Keep going: one bad day must not lose the other four, nor kill the recurring task.
        LOG.log(
            Level.WARNING,
            "Failed to submit day-ahead forecast for portfolio "
                + portfolio
                + " and day "
                + marketDate,
            e);
      }
    }
  }

  protected void submitDayAheadForecast(LocalDate marketDate) {
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

    List<SubmissionData> submissionData =
        buildSubmissionData(marketDate, marketZone, storageZone, datapoints);

    dayAheadResource.postDayAhead(
        portfolio,
        clientKey,
        new DayAheadSubmission(
            submissionData.toArray(new SubmissionData[0]),
            Long.parseLong(marketDate.format(BASIC_ISO_DATE)),
            timerService.getCurrentTimeMillis()));
  }

  /**
   * Builds one submission entry per ISP of the given market day, in ascending position order.
   *
   * <p>The entries are driven by a grid of real instants stepping from the start to the end of the
   * market day, so the count is 92, 96 or 100 depending on whether the day carries a DST
   * transition. Each instant is mapped back into the frame the predicted datapoint table is written
   * in, which is the JVM default zone (see {@code AbstractDatapointService}), and looked up there.
   *
   * <p>Under a JVM zone that observes DST the storage frame is not monotonic, so on the fall-back
   * day the two instants of the repeated hour collapse onto a single stored row and both read the
   * same value. That is a consequence of the naive primary key upstream
   * (openremote/openremote#3292); once predicted datapoints are stored in UTC every instant maps to
   * a distinct row and this method becomes exact without changing.
   */
  static List<SubmissionData> buildSubmissionData(
      LocalDate marketDate,
      ZoneId marketZone,
      ZoneId storageZone,
      List<ValueDatapoint<?>> datapoints) {

    Map<LocalDateTime, Double> valuesByStorageKey = new HashMap<>();
    for (ValueDatapoint<?> datapoint : datapoints) {
      // Gap-filled buckets carry a null value; skip them so they fall through to the default below.
      if (datapoint.getValue() instanceof Number value) {
        valuesByStorageKey.put(
            Instant.ofEpochMilli(datapoint.getTimestamp()).atZone(storageZone).toLocalDateTime(),
            value.doubleValue());
      }
    }

    ZonedDateTime dayStart = marketDate.atStartOfDay(marketZone);
    ZonedDateTime dayEnd = dayStart.plusDays(1);

    List<SubmissionData> submissionData = new ArrayList<>();
    Set<LocalDateTime> keysRead = new HashSet<>();
    int missing = 0;
    int position = 1;

    for (ZonedDateTime isp = dayStart; isp.isBefore(dayEnd); isp = isp.plus(ISP_DURATION)) {
      LocalDateTime storageKey = isp.withZoneSameInstant(storageZone).toLocalDateTime();
      Double value = valuesByStorageKey.get(storageKey);

      if (value == null) {
        missing++;
      } else if (!keysRead.add(storageKey)) {
        LOG.warning(
            "Day-ahead position "
                + position
                + " on "
                + marketDate
                + " reuses the value stored at "
                + storageKey
                + " because the repeated DST hour"
                + " collapses onto one predicted datapoint row (openremote/openremote#3292)");
      }

      submissionData.add(new SubmissionData(position++, null, null, value != null ? value : 0.0));
    }

    if (missing > 0) {
      LOG.warning(
          "Day-ahead submission for "
              + marketDate
              + " has "
              + missing
              + " of "
              + submissionData.size()
              + " intervals without a forecast; submitted as 0.0");
    }

    return submissionData;
  }

  protected long getFirstRequestDelayMillis() {
    long firstRequestMillis =
        timerService
            .getNow()
            .truncatedTo(ChronoUnit.HOURS)
            .plus(30, ChronoUnit.MINUTES)
            .toEpochMilli();
    return Math.max(0L, firstRequestMillis - timerService.getCurrentTimeMillis());
  }

  public void undeploy() {
    if (nextRequestFuture != null) {
      nextRequestFuture.cancel(true);
    }
  }
}
