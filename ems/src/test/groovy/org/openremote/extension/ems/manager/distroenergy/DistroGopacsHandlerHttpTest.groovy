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

import com.github.tomakehurst.wiremock.WireMockServer
import jakarta.ws.rs.WebApplicationException
import org.jboss.resteasy.client.jaxrs.ResteasyClient
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrderRequest
import spock.lang.Shared
import spock.lang.Specification

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.delete
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.okJson
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.put
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static org.openremote.container.web.WebTargetBuilder.createClient

/**
 * HTTP contract of the Distro GOPACS client built by {@link DistroGopacsHandler#createResource}:
 * the client-credentials token request, bearer reuse across calls, the JSON wire format of an order
 * and how error statuses surface. The unit spec covers what the handler does with the results.
 */
class DistroGopacsHandlerHttpTest extends Specification {

  static final String PORTFOLIO = "slimstrandnet"
  static final String TOKEN_PATH = "/oauth2/token"
  static final String ORDERS_PATH = "/v1/gopacs/${PORTFOLIO}/orders"
  static final String ORDER_JSON = '''{
    "reference": "ref-1", "orderId": "etpa-1", "portfolio": "slimstrandnet", "status": "PENDING",
    "orderType": "SELL", "quantity": 0.2, "price": 45, "start": 1792677600, "end": 1792684800,
    "ean": "871685920001768800", "metadata": {"announcementId": "a-1"},
    "createdAt": 1792663200, "updatedAt": 1792663200, "lastSyncedAt": 1792663200,
    "someFutureField": true
  }'''

  @Shared
  WireMockServer wireMock
  ResteasyClient client
  DistroGopacsResource resource

  def setupSpec() {
    wireMock = new WireMockServer(0)
    wireMock.start()
  }

  def cleanupSpec() {
    wireMock?.stop()
  }

  def setup() {
    wireMock.resetAll()
    wireMock.stubFor(post(urlPathEqualTo(TOKEN_PATH))
            .willReturn(okJson('{"access_token": "token-1", "token_type": "Bearer", "expires_in": 3600}')))
    String base = "http://localhost:${wireMock.port()}"
    client = createClient(org.openremote.container.Container.EXECUTOR)
    resource = DistroGopacsHandler.createResource(
            client, "${base}/v1", "${base}${TOKEN_PATH}", "client-id", "client-secret")
  }

  def cleanup() {
    client?.close()
  }

  def "an order is posted as JSON in epoch seconds with a cached bearer token"() {
    given:
    wireMock.stubFor(post(urlPathEqualTo(ORDERS_PATH))
            .willReturn(aResponse().withStatus(202).withHeader("Content-Type", "application/json")
            .withBody(ORDER_JSON)))
    wireMock.stubFor(get(urlPathEqualTo("${ORDERS_PATH}/ref-1")).willReturn(okJson(ORDER_JSON)))
    def request = new GopacsOrderRequest("ref-1", "SELL", 0.2, 1792677600, 1792684800, 45,
            "871685920001768800", [announcementId: "a-1"], null)

    when:
    def created = resource.createOrder(PORTFOLIO, request)
    def fetched = resource.getOrder(PORTFOLIO, "ref-1")

    then: "the response is read, unknown fields ignored"
    created.status() == "PENDING"
    created.orderId() == "etpa-1"
    created.metadata() == [announcementId: "a-1"]
    fetched.reference() == "ref-1"

    and: "the body carries the order and leaves out the unset expiration"
    wireMock.verify(postRequestedFor(urlPathEqualTo(ORDERS_PATH))
            .withHeader("Authorization", equalTo("Bearer token-1"))
            .withRequestBody(equalToJson('''{
              "reference": "ref-1", "orderType": "SELL", "quantity": 0.2,
              "start": 1792677600, "end": 1792684800, "price": 45.0,
              "ean": "871685920001768800", "metadata": {"announcementId": "a-1"}
            }''')))

    and: "the token is fetched once with HTTP Basic client credentials and the GOPACS scope"
    wireMock.verify(1, postRequestedFor(urlPathEqualTo(TOKEN_PATH))
            .withHeader("Authorization", equalTo("Basic " + "client-id:client-secret".bytes.encodeBase64()))
            .withRequestBody(containing("grant_type=client_credentials"))
            .withRequestBody(containing("scope=distro-api%2Fgopacs.access")))
    wireMock.verify(getRequestedFor(urlPathEqualTo("${ORDERS_PATH}/ref-1"))
            .withHeader("Authorization", equalTo("Bearer token-1")))
  }

  def "posting an existing reference returns the stored order"() {
    given:
    wireMock.stubFor(post(urlPathEqualTo(ORDERS_PATH)).willReturn(okJson(ORDER_JSON)))

    when:
    def stored = resource.createOrder(PORTFOLIO, new GopacsOrderRequest("ref-1", "SELL", 0.2,
            1792677600, 1792684800, 45, "871685920001768800", [announcementId: "a-1"], null))

    then:
    stored.reference() == "ref-1"
    stored.status() == "PENDING"
  }

  def "a locked order surfaces its 423 status"() {
    given:
    wireMock.stubFor(put(urlPathEqualTo("${ORDERS_PATH}/ref-1")).willReturn(aResponse()
            .withStatus(423).withHeader("Content-Type", "application/json")
            .withBody('{"code": "ORDER_LOCKED", "message": "locked", "details": [], "requestId": "r"}')))

    when:
    resource.replaceOrder(PORTFOLIO, "ref-1", new GopacsOrderRequest("ref-1", "SELL", 0.2,
            1792677600, 1792684800, 60, "871685920001768800", null, null))

    then:
    def e = thrown(WebApplicationException)
    e.response.status == 423
  }

  def "a rejected call is described with the error body Distro sent"() {
    given:
    wireMock.stubFor(post(urlPathEqualTo(ORDERS_PATH)).willReturn(aResponse()
            .withStatus(403).withHeader("Content-Type", "application/json")
            .withBody('{"message": "Forbidden"}')))

    when:
    resource.createOrder(PORTFOLIO, new GopacsOrderRequest("ref-1", "SELL", 0.2,
            1792677600, 1792684800, 45, "871685920001768800", null, null))

    then:
    def e = thrown(WebApplicationException)
    DistroGopacsHandler.describe(e) == 'HTTP 403: {"message": "Forbidden"}'
  }

  def "a cancel is a DELETE answered with 204"() {
    given:
    wireMock.stubFor(delete(urlPathEqualTo("${ORDERS_PATH}/ref-1")).willReturn(aResponse().withStatus(204)))

    when:
    resource.cancelOrder(PORTFOLIO, "ref-1")

    then:
    wireMock.verify(deleteRequestedFor(urlPathEqualTo("${ORDERS_PATH}/ref-1")))
  }

  def "settlements are listed for one order reference"() {
    given:
    wireMock.stubFor(get(urlPathEqualTo("/v1/settlements/gopacs/${PORTFOLIO}/ref-1")).willReturn(okJson('''[
      {"portfolio": "slimstrandnet", "orderReference": "ref-1", "broker": "etpa",
        "ean": "871234567891234567", "tradeId": "ada69bb6-9a01-4c5f-a061-287760472a65",
        "settledAt": 1790978400, "start": 1790978400, "end": 1790979300, "modifiedAt": 1790978500,
        "capacity": 0.5, "volume": 0.125, "price": 40, "cost": 5,
        "capacityUnit": "MW", "volumeUnit": "MWH", "priceUnit": "EUR/MWH", "direction": "BUY"},
      {"portfolio": "slimstrandnet", "orderReference": "ref-1", "broker": "etpa",
        "ean": "871234567891234567", "tradeId": "0b8c4c1e-58f5-4b8e-9a3e-0d7c9c1f2a11",
        "settledAt": 1790978400, "start": 1790979300, "end": 1790980200, "modifiedAt": 1790978500,
        "capacity": 200, "volume": 50, "price": 0.04, "cost": 2,
        "capacityUnit": "KW", "volumeUnit": "KWH", "priceUnit": "EUR/KWH", "direction": "BUY"}]''')))

    when:
    def settlements = resource.listSettlements(PORTFOLIO, "ref-1")

    then:
    settlements*.tradeId() == ["ada69bb6-9a01-4c5f-a061-287760472a65", "0b8c4c1e-58f5-4b8e-9a3e-0d7c9c1f2a11"]
    settlements*.ean() == ["871234567891234567"] * 2
    settlements*.capacityUnit() == ["MW", "KW"]
    settlements*.volumeUnit() == ["MWH", "KWH"]
    settlements*.priceUnit() == ["EUR/MWH", "EUR/KWH"]
    settlements[0].cost() == 5d
    settlements[0].modifiedAt() == 1790978500L
    wireMock.verify(getRequestedFor(urlEqualTo("/v1/settlements/gopacs/${PORTFOLIO}/ref-1")))
  }
}
