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
package org.openremote.extension.ems.manager.distroenergy

import org.lfenergy.shapeshifter.api.datetime.DateTimeCalculation
import org.openremote.model.datapoint.ValueDatapoint
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit test for {@link DistroEnergyHandler#buildSubmissionData}.
 *
 * Deliberately a plain {@code Specification} with no {@code ManagerContainerTrait}: that trait forces
 * {@code TIMER_CLOCK_TYPE: PSEUDO}, whose {@code init()} calls {@code TimeZone.setDefault("UTC")}
 * JVM-wide. Running under a forced-UTC JVM would hide exactly the DST behaviour under test here.
 *
 * The Distro Energy day-ahead API requires exactly one entry per quarter-hour of the market day: 96
 * normally, 92 on the spring-forward day and 100 on the fall-back day. Expected counts are
 * cross-checked against shapeshifter's {@code DateTimeCalculation.numberOfIspsOnDay} so they are not
 * computed by the same arithmetic under test.
 */
class DistroEnergyHandlerTest extends Specification {

  static final ZoneId MARKET = ZoneId.of("Europe/Amsterdam")
  static final ZoneId AMSTERDAM_STORAGE = ZoneId.of("Europe/Amsterdam")
  static final ZoneId UTC_STORAGE = ZoneId.of("UTC")
  static final Duration ISP = Duration.ofMinutes(15)

  static final LocalDate NORMAL_DAY = LocalDate.of(2026, 9, 15)
  static final LocalDate SPRING_FORWARD = LocalDate.of(2026, 3, 29) // last Sunday of March
  static final LocalDate FALL_BACK = LocalDate.of(2026, 10, 25) // last Sunday of October

  /** Every ISP instant of the market day, which is what the forecast writer would have produced. */
  static List<ZonedDateTime> ispInstants(LocalDate marketDate) {
    def start = marketDate.atStartOfDay(MARKET)
    def end = start.plusDays(1)
    def instants = []
    for (def isp = start; isp.isBefore(end); isp = isp.plus(ISP)) {
      instants << isp
    }
    instants
  }

  /**
   * Simulates the predicted datapoint table for a full day of forecast.
   *
   * Datapoints come back keyed by epoch millis, but the row they were read from is keyed by the naive
   * local time in the storage zone. Under a DST-observing storage zone two instants of the fall-back
   * hour share one row, so this collapses them the way the upsert primary key does.
   */
  static List<ValueDatapoint<?>> datapointsFor(LocalDate marketDate, ZoneId storageZone,
          Closure<Double> valueFor = { int i ->
            (i + 1) * 1.0d
          }) {
    Map<java.time.LocalDateTime, ValueDatapoint<?>> byStorageKey = [:]
    ispInstants(marketDate).eachWithIndex { ZonedDateTime isp, int i ->
      def storageKey = isp.withZoneSameInstant(storageZone).toLocalDateTime()
      // Last write wins, exactly like ON CONFLICT ... DO UPDATE SET value = excluded.value
      byStorageKey[storageKey] = new ValueDatapoint<Double>(isp.toInstant().toEpochMilli(), valueFor(i))
    }
    new ArrayList<>(byStorageKey.values())
  }

  /**
   * The same datapoints with the given ISP indices of the day removed, the way a producer that
   * writes a fixed 96-slot day leaves the extra ISPs of the fall-back hour unwritten.
   *
   * Only meaningful under a storage zone that keeps every ISP on its own row, so the index dropped
   * is the position emptied.
   */
  static List<ValueDatapoint<?>> withoutIsps(List<ValueDatapoint<?>> datapoints, LocalDate marketDate,
          List<Integer> indices) {
    def dropped = indices.collect { ispInstants(marketDate)[it].toInstant().toEpochMilli() } as Set
    datapoints.findAll { !dropped.contains(it.timestamp) }
  }

  /** The volume a forecast value goes out as; the fill tests below reason in forecast kW. */
  static double volume(double powerNetKw) {
    DistroEnergyHandler.toVolume(powerNetKw)
  }

  @Unroll
  def "a forecast of #powerNetKw kW over one ISP goes out as #expected kWh"() {
    expect: "energy over 15 minutes, import signed as a buy (negative), in 0.01 kWh steps"
    DistroEnergyHandler.toVolume(powerNetKw) == expected

    where:
    powerNetKw | expected
    40.0d | -10.0d
    -12.0d | 3.0d
    53.18236237777778d | -13.3d
    0.0d | 0.0d
  }

  static long expectedIsps(LocalDate marketDate) {
    DateTimeCalculation.numberOfIspsOnDay(marketDate, ISP, MARKET.id)
  }

  @Unroll
  def "submission has one entry per market ISP on #marketDate with #storageZone storage"() {
    given: "a full day of predicted datapoints as stored in the given frame"
    def datapoints = datapointsFor(marketDate, storageZone)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(marketDate, MARKET, storageZone, datapoints)

    then: "the length matches the market day, independent of the storage frame"
    data.size() == expected
    expected == expectedIsps(marketDate)

    and: "positions are 1-based and ascending, as the API requires"
    data*.position == (1..expected).toList()

    where:
    marketDate | storageZone || expected
    NORMAL_DAY | AMSTERDAM_STORAGE || 96
    SPRING_FORWARD | AMSTERDAM_STORAGE || 92
    FALL_BACK | AMSTERDAM_STORAGE || 100
    NORMAL_DAY | UTC_STORAGE || 96
    SPRING_FORWARD | UTC_STORAGE || 92
    FALL_BACK | UTC_STORAGE || 100
  }

  def "UTC storage recovers every distinct value on the fall-back day"() {
    given:
    def datapoints = datapointsFor(FALL_BACK, UTC_STORAGE)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, UTC_STORAGE, datapoints)

    then: "no row collision, so all 100 forecast values survive"
    datapoints.size() == 100
    data*.volume == (1..100).collect { volume(it) }
  }

  def "Amsterdam storage still fills 100 entries when the repeated hour collapses"() {
    given: "the fall-back hour collapses onto four shared rows"
    def datapoints = datapointsFor(FALL_BACK, AMSTERDAM_STORAGE)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "four quarter-hours were lost at write time"
    datapoints.size() == 96

    and: "the submission is still the full 100 entries the API requires"
    data.size() == 100

    and: "and none of them defaulted to 0.0, they reuse the surviving twin"
    data*.volume.every { it != 0.0d }

    and: "each pass carries the four distinct values of the row that survived the collapse"
    data[8..11]*.volume == [13, 14, 15, 16].collect { volume(it) }
    data[12..15]*.volume == [13, 14, 15, 16].collect { volume(it) }
  }

  @Unroll
  def "a repeated DST hour with no datapoints of its own reuses the next forecast value: #label"() {
    given: "a UTC storage frame, where the two passes of the fall-back hour keep separate rows"
    def datapoints = withoutIsps(datapointsFor(FALL_BACK, UTC_STORAGE), FALL_BACK, dropped)

    expect: "the producer covered 96 of the 100 ISPs, as a fixed 96-slot day would"
    datapoints.size() == 96

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, UTC_STORAGE, datapoints)

    then: "the submission is still the full 100 entries the API requires"
    data.size() == 100

    and: "the emptied positions all took the next real value rather than trading the hour away"
    data[dropped]*.volume == [volume(expectedKw)] * dropped.size()

    and: "the pass that kept its rows is untouched, so the fill never overwrites a real value"
    data[kept]*.volume == kept.collect { volume(it + 1) }

    and: "nothing outside the repeated hour moved"
    data[0..7]*.volume == (1..8).collect { volume(it) }
    data[16..99]*.volume == (17..100).collect { volume(it) }

    where:
    label | dropped | kept || expectedKw
    "first pass, 02:00-02:45 CEST" | [8, 9, 10, 11] | [12, 13, 14, 15] || 13.0d
    "second pass, 02:00-02:45 CET" | [12, 13, 14, 15] | [8, 9, 10, 11] || 17.0d
  }

  def "the repeated hour looks past filled 0.0 positions for a real value"() {
    given: "the repeated hour and the two ISPs after it are both unwritten"
    def datapoints = withoutIsps(datapointsFor(FALL_BACK, UTC_STORAGE), FALL_BACK,
            [12, 13, 14, 15, 16, 17])

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, UTC_STORAGE, datapoints)

    then: "03:00 and 03:15 are ordinary gaps, so they are 0.0 and not a source to borrow from"
    data[16..17]*.volume == [0.0d, 0.0d]

    and: "the repeated hour reaches past them to 03:30, the next value that is real"
    data[12..15]*.volume == [volume(19)] * 4
    data[18].volume == volume(19)
  }

  def "a repeated DST hour with nothing left to reuse falls back to 0.0"() {
    given: "the forecast horizon ends before the repeated hour begins"
    def datapoints = datapointsFor(FALL_BACK, UTC_STORAGE).take(8)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, UTC_STORAGE, datapoints)

    then: "the day is still submitted in full"
    data.size() == 100

    and: "the API requires a volume, so the repeated hour is 0.0 like the rest of the tail"
    data[8..15]*.volume.every { it == 0.0d }
    data[16..99]*.volume.every { it == 0.0d }
  }

  def "a gap outside the repeated hour is still 0.0 on the fall-back day"() {
    given: "one afternoon ISP is missing on a day that does carry a DST transition"
    def datapoints = withoutIsps(datapointsFor(FALL_BACK, UTC_STORAGE), FALL_BACK, [60])

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, UTC_STORAGE, datapoints)

    then: "only the repeated hour borrows a neighbour; an ordinary gap is a real trading position"
    data.size() == 100
    data[60].volume == 0.0d

    and: "its neighbours are untouched"
    data[59].volume == volume(60)
    data[61].volume == volume(62)
  }

  def "spring-forward day never looks up the non-existent local hour"() {
    given:
    def datapoints = datapointsFor(SPRING_FORWARD, AMSTERDAM_STORAGE)

    expect: "the missing hour simply is not part of the day"
    datapoints.size() == 92

    when:
    def data = DistroEnergyHandler.buildSubmissionData(SPRING_FORWARD, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "so nothing is filled and every value is real"
    data.size() == 92
    data*.volume == (1..92).collect { volume(it) }
  }

  def "missing forecast intervals become 0.0 rather than throwing"() {
    given: "only the first two intervals have a forecast"
    def datapoints = datapointsFor(NORMAL_DAY, AMSTERDAM_STORAGE).take(2)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "the day is still complete, gaps filled with 0.0"
    data.size() == 96
    data.findAll { it.volume == 0.0d }.size() == 94
  }

  def "a day of only gap-filled null buckets is not submitted"() {
    given: "the query returned buckets with null values, as gapFill does"
    def datapoints = ispInstants(NORMAL_DAY).collect {
      new ValueDatapoint<Double>(it.toInstant().toEpochMilli(), (Double) null)
    }

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "a non-empty result set with no real values still means no forecast"
    noExceptionThrown()
    datapoints.size() == 96
    data.isEmpty()
  }

  def "a day with no forecast at all is not submitted"() {
    when: "the forecast horizon has not reached this day"
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, [])

    then: "there is nothing to submit, rather than a day of zeros"
    data.isEmpty()
  }

  def "a genuine all-zero forecast is still submitted"() {
    given: "the producer wrote 0.0 for every interval, which is a real trading position"
    def datapoints = datapointsFor(NORMAL_DAY, AMSTERDAM_STORAGE, { int i -> 0.0d })

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "a forecast 0.0 and a filled 0.0 are not confused"
    data.size() == expectedIsps(NORMAL_DAY)
    data*.volume.every { it == 0.0d }
  }

  def "a forecast that stops mid-day is filled to midnight rather than truncated"() {
    given: "the forecast horizon ends after 40 of the 96 intervals"
    def datapoints = datapointsFor(NORMAL_DAY, AMSTERDAM_STORAGE).take(40)

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "the API still receives the complete day"
    data.size() == expectedIsps(NORMAL_DAY)
    data*.position == (1..96).toList()

    and: "the covered intervals keep their real values"
    data[0..39]*.volume == (1..40).collect { volume(it) }

    and: "and the tail to midnight is filled with 0.0"
    data[40..95]*.volume.every { it == 0.0d }
  }

  @Unroll
  def "a single real value at position #realPosition still submits the whole day"() {
    given: "exactly one interval of the day was forecast"
    def instant = ispInstants(NORMAL_DAY)[realPosition - 1]
    def datapoints = [new ValueDatapoint<Double>(instant.toInstant().toEpochMilli(), 7.5d)]

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "the day is complete, with that one value in place and the rest filled"
    data.size() == 96
    data[realPosition - 1].volume == volume(7.5)
    data.findAll { it.volume == 0.0d }.size() == 95

    where:
    realPosition << [1, 48, 96]
  }

  def "a value outside the market day does not make the day submittable"() {
    given: "the only datapoint lies one ISP before the start of the market day"
    def beforeDay = NORMAL_DAY.atStartOfDay(MARKET).minus(ISP)
    def datapoints = [new ValueDatapoint<Double>(beforeDay.toInstant().toEpochMilli(), 12.0d)]

    when:
    def data = DistroEnergyHandler.buildSubmissionData(NORMAL_DAY, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "the decision follows the ISP grid, not whatever the query happened to return"
    data.isEmpty()
  }

  @Unroll
  def "the submit-or-skip decision is independent of the DST arithmetic on #marketDate"() {
    given: "the same day with and without a forecast"
    def covered = datapointsFor(marketDate, AMSTERDAM_STORAGE)
    def uncovered = ispInstants(marketDate).collect {
      new ValueDatapoint<Double>(it.toInstant().toEpochMilli(), (Double) null)
    }

    expect: "an uncovered day is skipped, and a covered one is still exactly the right length"
    DistroEnergyHandler.buildSubmissionData(marketDate, MARKET, AMSTERDAM_STORAGE, uncovered).isEmpty()
    DistroEnergyHandler.buildSubmissionData(marketDate, MARKET, AMSTERDAM_STORAGE, covered).size() ==
            expectedIsps(marketDate)

    where:
    marketDate << [NORMAL_DAY, SPRING_FORWARD, FALL_BACK]
  }

  def "the fall-back collapse cannot turn a day with a forecast into a skip"() {
    given: "the only value sits in the repeated hour, where two ISPs share one stored row"
    def repeatedHour = ispInstants(FALL_BACK)[8] // 02:00 CEST, the first pass of the repeat
    def datapoints = [new ValueDatapoint<Double>(repeatedHour.toInstant().toEpochMilli(), 3.25d)]

    when:
    def data = DistroEnergyHandler.buildSubmissionData(FALL_BACK, MARKET, AMSTERDAM_STORAGE, datapoints)

    then: "the day is submitted in full"
    data.size() == 100

    and: "both passes read the surviving row, so the collapse only ever adds a read"
    data[8].volume == volume(3.25) // 02:00 CEST, the pass that wrote the row
    data[12].volume == volume(3.25) // 02:00 CET, the same row read a second time
  }
}
