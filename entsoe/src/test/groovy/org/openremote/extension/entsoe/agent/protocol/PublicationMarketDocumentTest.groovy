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

import jakarta.xml.bind.JAXBContext
import spock.lang.Shared
import spock.lang.Specification

class PublicationMarketDocumentTest extends Specification {

  @Shared
  JAXBContext context = JAXBContext.newInstance(PublicationMarketDocument)

  def "unmarshals curve type when #description"() {
    given:
    def xml = """
<Publication_MarketDocument xmlns="urn:iec62325.351:tc57wg16:451-3:publicationdocument:7:3">
  <TimeSeries>
    ${curveTypeXml}
    <Period>
      <timeInterval>
        <start>2026-02-16T23:00Z</start>
        <end>2026-02-17T00:00Z</end>
      </timeInterval>
      <resolution>PT15M</resolution>
      <Point>
        <position>1</position>
        <price.amount>73.24</price.amount>
      </Point>
    </Period>
  </TimeSeries>
</Publication_MarketDocument>
"""

    when:
    def document = context.createUnmarshaller().unmarshal(new StringReader(xml)) as PublicationMarketDocument

    then:
    document.timeSeries.size() == 1
    document.timeSeries[0].getCurveType() == expectedCurveType

    where:
    description | curveTypeXml | expectedCurveType
    "A01 is explicit" | "<curveType>A01</curveType>" | "A01"
    "A03 is explicit" | "<curveType>A03</curveType>" | "A03"
    "curveType is absent" | "" | "A01"
    "an unknown code is supplied" | "<curveType>A99</curveType>" | "A99"
  }
}
