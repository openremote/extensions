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
package org.openremote.extension.entsoe.agent.protocol

import org.openremote.container.timer.TimerService
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

class EntsoeCurveTest extends Specification {

  private static final Instant START = Instant.parse("2026-02-16T23:00:00Z")
  private EntsoeProtocol protocol

  def setup() {
    protocol = new EntsoeProtocol(new EntsoeAgent())
    setNow(START)
  }

  def cleanup() {
    EntsoeProtocol.client.getAndSet(null)?.close()
  }

  def "A03 expands skipped positions and the final block in position order"() {
    given:
    def document = document("A03", period(START, START.plusSeconds(3600), "PT15M", [[3, 0], [1, -5]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == (0..3).collect {
      START.plusSeconds(it * 900L).toEpochMilli()
    }
    values.collect { it.value } == [-5G, -5G, 0G, 0G]
  }

  def "a single A03 point covers the full period at #resolution resolution"() {
    given:
    def document = document("A03", period(START, START.plusSeconds(14400), resolution, [[1, 80]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.size() == count
    values.collect { it.timestamp } == (0..<count).collect {
      START.plus(Duration.parse(resolution).multipliedBy(it)).toEpochMilli()
    }
    values.every { it.value == 80G }

    where:
    resolution | count
    "PT15M" | 16
    "PT30M" | 8
    "PT60M" | 4
  }

  def "A03 preserves future intervals from a block that starts before now"() {
    given:
    setNow(START.plusSeconds(1200))
    def document = document("A03", period(START, START.plusSeconds(3600), "PT15M", [[1, 80]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == [START.plusSeconds(1800).toEpochMilli(), START.plusSeconds(2700).toEpochMilli()]
    values.collect { it.value } == [80G, 80G]
  }

  def "A03 expansion stops at each period end and restarts positions in the next period"() {
    given:
    def document = document("A03",
            period(START, START.plusSeconds(1800), "PT15M", [[1, 80]]) +
            period(START.plusSeconds(3600), START.plusSeconds(5400), "PT15M", [[1, 90]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == [0, 900, 3600, 4500].collect {
      START.plusSeconds(it).toEpochMilli()
    }
    values.collect { it.value } == [80G, 80G, 90G, 90G]
  }

  def "a price-less A03 point stops the preceding price block"() {
    given:
    def document = document("A03", period(START, START.plusSeconds(4500), "PT15M", [[1, 80], [3, null], [5, 90]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == [0, 900, 3600].collect {
      START.plusSeconds(it).toEpochMilli()
    }
    values.collect { it.value } == [80G, 80G, 90G]
  }

  def "#curveType keeps fixed-size point behavior"() {
    given:
    def document = document(curveType, period(START, START.plusSeconds(3600), "PT15M", [[1, 80], [3, 90]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == [START.toEpochMilli(), START.plusSeconds(1800).toEpochMilli()]
    values.collect { it.value } == [80G, 90G]

    where:
    curveType << ["A01", null]
  }

  def "A03 does not expand a non-positive resolution #resolution"() {
    given:
    def document = document("A03", period(START, START.plusSeconds(3600), resolution, [[1, 80]]))

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    resolution << ["PT0S", "PT-15M"]
  }

  def "A03 does not expand a period without an end"() {
    given:
    def periodXml = period(START, START.plusSeconds(3600), "PT15M", [[1, 80]])
    .replace("<end>${START.plusSeconds(3600)}</end>", "")
    def document = document("A03", periodXml)

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()
  }

  private void setNow(Instant now) {
    protocol.timerService = Stub(TimerService) {
      getCurrentTimeMillis() >> now.toEpochMilli()
    }
  }

  private PublicationMarketDocument document(String curveType, String periods) {
    def curveTypeXml = curveType == null ? "" : "<curveType>${curveType}</curveType>"
    def xml = """
<Publication_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3">
  <TimeSeries>
    ${curveTypeXml}
    ${periods}
  </TimeSeries>
</Publication_MarketDocument>
"""
    protocol.unmarshalPublicationMarketDocument(xml)
  }

  private static String period(Instant start, Instant end, String resolution, List<List> points) {
    def pointXml = points.collect { point ->
      "<Point><position>${point[0]}</position>" +
      (point[1] == null ? "" : "<price.amount>${point[1]}</price.amount>") + "</Point>"
    }.join("\n")
    """
<Period>
  <timeInterval><start>${start}</start><end>${end}</end></timeInterval>
  <resolution>${resolution}</resolution>
  ${pointXml}
</Period>
"""
  }
}
