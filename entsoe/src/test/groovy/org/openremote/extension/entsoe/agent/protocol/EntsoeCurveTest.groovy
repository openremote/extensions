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

  def "#curveType emits unavailable intervals for price-less points: #description"() {
    given:
    def document = document(curveType, period(START, START.plusSeconds(3600), "PT15M", points))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == (0..3).collect {
      START.plusSeconds(it * 900L).toEpochMilli()
    }
    values.collect { it.value } == expected

    where:
    curveType | description | points | expected
    "A01" | "middle" | [[1, 80], [2, null], [3, null], [4, 90]] | [80G, null, null, 90G]
    null | "default curve" | [[1, 80], [2, null], [3, null], [4, 90]] | [80G, null, null, 90G]
    "A01" | "whole period" | [[1, null], [2, null], [3, null], [4, null]] | [null, null, null, null]
    "A03" | "middle block" | [[1, 80], [2, null], [4, 90]] | [80G, null, null, 90G]
    "A03" | "first block" | [[1, null], [3, 90]] | [null, null, 90G, 90G]
    "A03" | "final block" | [[1, 80], [3, null]] | [80G, 80G, null, null]
    "A03" | "whole period" | [[1, null]] | [null, null, null, null]
  }

  def "#curveType rejects a period containing malformed price '#amount' without partial updates"() {
    given:
    def points = curveType == "A01" ? [[1, 80], [2, amount], [3, 82], [4, 90]] : [[1, 80], [2, amount], [4, 90]]
    def document = document(curveType,
            period(START, START.plusSeconds(3600), "PT15M", points) +
            period(START.plusSeconds(3600), START.plusSeconds(4500), "PT15M", [[1, 100]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then: "the entire malformed period is rejected while the valid period is still decoded"
    values.collect { it.timestamp } == [START.plusSeconds(3600).toEpochMilli()]
    values.collect { it.value } == [100G]

    where:
    [curveType, amount] << [["A01", "A03"], ["broken", "", "   ", "NaN"]].combinations()
  }

  def "#curveType filters past unavailable intervals"() {
    given:
    setNow(START.plusSeconds(1200))
    def document = document(curveType, period(START, START.plusSeconds(3600), "PT15M", points))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect { it.timestamp } == [1800, 2700].collect { START.plusSeconds(it).toEpochMilli() }
    values.collect { it.value } == [null, null]

    where:
    curveType | points
    "A01" | [[1, null], [2, null], [3, null], [4, null]]
    "A03" | [[1, null]]
  }

  def "complete fixed-size periods support #curveType at #resolution resolution"() {
    given:
    def step = Duration.parse(resolution)
    def document = document(curveType, period(START, START.plus(step.multipliedBy(4)), resolution,
            [[4, 90], [2, 0], [1, -5], [3, 80]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == (0..3).collect {
      START.plus(step.multipliedBy(it)).toEpochMilli()
    }
    values.collect { it.value } == [-5G, 0G, 80G, 90G]

    where:
    [curveType, resolution] << [["A01", null], ["PT15M", "PT30M", "PT60M"]].combinations()
  }

  def "A01 filters past intervals after validating the complete period"() {
    given:
    setNow(START.plusSeconds(1200))
    def document = document("A01", period(START, START.plusSeconds(3600), "PT15M",
            [[1, 80], [2, 81], [3, 82], [4, 83]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect { it.timestamp } == [1800, 2700].collect { START.plusSeconds(it).toEpochMilli() }
    values.collect { it.value } == [82G, 83G]
  }

  def "incomplete fixed-size periods are rejected for #curveType and positions #positions"() {
    given:
    def document = document(curveType, period(START, START.plusSeconds(3600), "PT15M",
            positions.collect { [it, 80] }))

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    [curveType, positions] << [["A01", null], [[2, 3, 4], [1, 3, 4], [1, 2, 3]]].combinations()
  }

  def "#curveType rejects invalid positions #positions without partial output"() {
    given:
    def periodXml = period(START, START.plusSeconds(3600), "PT15M", positions.collect { [it, 80] })
    .replace("<position>null</position>", "")
    def document = document(curveType, periodXml)

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    [curveType, positions] << [
      ["A01", "A03"],
      [[1, 2, 2, 4], [0, 2, 3, 4], [-1, 2, 3, 4], [1, 2, 3, 5], [1, 2, 3, 2147483647], [1, 2, 3, null]]
    ].combinations()
  }

  def "A03 requires a block starting at position one"() {
    given:
    def document = document("A03", period(START, START.plusSeconds(3600), "PT15M", [[2, 80], [4, 90]]))

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()
  }

  def "#curveType rejects invalid period timing: #description"() {
    given:
    def periodXml = period(START, START.plusSeconds(endOffset), resolution, [[1, 80], [2, 81], [3, 82], [4, 83]])
    def document = document(curveType, periodXml)

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    [curveType, description, endOffset, resolution] << ["A01", "A03"].collectMany { type ->
      [
        [type, "end equals start", 0, "PT15M"],
        [type, "end precedes start", -3600, "PT15M"],
        [type, "end is off the resolution grid", 3500, "PT15M"],
        [type, "period is shorter than resolution", 600, "PT15M"],
        [type, "zero resolution", 3600, "PT0S"],
        [type, "negative resolution", 3600, "PT-15M"],
        [type, "unparseable resolution", 3600, "invalid"]
      ]
    }
  }

  def "#curveType requires its own complete time interval and resolution: #description"() {
    given:
    def periodXml = period(START, START.plusSeconds(3600), "PT15M", [[1, 80], [2, 81], [3, 82], [4, 83]])
    .replace(removedXml, replacementXml)
    def header = "<period.timeInterval><start>${START}</start><end>${START.plusSeconds(3600)}</end></period.timeInterval>"
    def document = document(curveType, periodXml, header)

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    [curveType, description, removedXml, replacementXml] << ["A01", "A03"].collectMany { type ->
      [
        [
          type,
          "missing interval",
          "<timeInterval><start>${START}</start><end>${START.plusSeconds(3600)}</end></timeInterval>",
          ""
        ],
        [type, "missing start", "<start>${START}</start>", ""],
        [type, "missing end", "<end>${START.plusSeconds(3600)}</end>", ""],
        [type, "missing resolution", "<resolution>PT15M</resolution>", ""],
        [type, "unparseable start", "<start>${START}</start>", "<start>invalid</start>"],
        [type, "unparseable end", "<end>${START.plusSeconds(3600)}</end>", "<end>invalid</end>"]
      ]
    }
  }

  def "invalid #curveType periods do not prevent valid periods in the same series from being decoded"() {
    given:
    def document = document(curveType,
            period(START, START.plusSeconds(1800), "PT15M", [[1, 80], [2, 81]]) +
            period(START.plusSeconds(1800), START.plusSeconds(3600), "PT15M", [[1, 999], [1, 999]]) +
            period(START.plusSeconds(3600), START.plusSeconds(5400), "PT15M", [[1, 90], [2, 91]]))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect {
      it.timestamp
    } == [0, 900, 3600, 4500].collect {
      START.plusSeconds(it).toEpochMilli()
    }
    values.collect { it.value } == [80G, 81G, 90G, 91G]

    where:
    curveType << ["A01", "A03"]
  }

  def "#curveType supports a #hours hour period without assuming 24 hours"() {
    given:
    def points = curveType == "A01" ? (1..hours).collect { [it, 80] } : [[1, 80]]
    def document = document(curveType, period(START, START.plusSeconds(hours * 3600L), "PT60M", points))

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.size() == hours
    values.collect {
      it.timestamp
    } == (0..<hours).collect {
      START.plusSeconds(it * 3600L).toEpochMilli()
    }
    values.every { it.value == 80G }

    where:
    [curveType, hours] << [["A01", "A03"], [23, 25]].combinations()
  }

  def "unsupported or empty curve type '#curveType' produces no predictions"() {
    given:
    def document = document(curveType, period(START, START.plusSeconds(3600), "PT15M", [[1, 80]]))

    expect:
    protocol.buildPredictedDatapoints(document).isEmpty()

    where:
    curveType << ["A02", "A04", "A05", "A99", "", " "]
  }

  def "rejected series do not prevent valid series in the same document from being decoded"() {
    given:
    def xml = """
<Publication_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3">
  <TimeSeries>
    <curveType>A01</curveType>
    ${period(START, START.plusSeconds(1800), "PT15M", [[1, 80], [2, 81]])}
  </TimeSeries>
  <TimeSeries>
    <curveType>A99</curveType>
    ${period(START.plusSeconds(1800), START.plusSeconds(3600), "PT15M", [[1, 999]])}
  </TimeSeries>
  <TimeSeries>
    <curveType>A03</curveType>
    ${period(START.plusSeconds(3600), START.plusSeconds(5400), "PT15M", [[1, 90]])}
  </TimeSeries>
  <TimeSeries>
    <curveType/>
    ${period(START.plusSeconds(5400), START.plusSeconds(7200), "PT15M", [[1, 999]])}
  </TimeSeries>
  <TimeSeries>
    ${period(START.plusSeconds(7200), START.plusSeconds(9000), "PT15M", [[1, 100], [2, 101]])}
  </TimeSeries>
</Publication_MarketDocument>
"""
    def document = protocol.unmarshalPublicationMarketDocument(xml)

    when:
    def values = protocol.buildPredictedDatapoints(document)

    then:
    values.collect { it.timestamp } == [0, 900, 3600, 4500, 7200, 8100].collect {
      START.plusSeconds(it).toEpochMilli()
    }
    values.collect { it.value } == [80G, 81G, 90G, 90G, 100G, 101G]
  }

  private void setNow(Instant now) {
    protocol.timerService = Stub(TimerService) {
      getCurrentTimeMillis() >> now.toEpochMilli()
    }
  }

  private PublicationMarketDocument document(String curveType, String periods, String header = "") {
    def curveTypeXml = curveType == null ? "" : "<curveType>${curveType}</curveType>"
    def xml = """
<Publication_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3">
  ${header}
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
