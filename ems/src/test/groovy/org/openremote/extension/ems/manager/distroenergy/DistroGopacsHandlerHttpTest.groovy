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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static org.openremote.container.web.WebTargetBuilder.createClient

/**
 * HTTP contract of the Distro GOPACS client built by {@link DistroGopacsHandler#createResource}:
 * the client-credentials token request, bearer reuse across calls, the JSON wire format of an order
 * and how error statuses surface. The unit spec covers what the handler does with the results.
 */
class DistroGopacsHandlerHttpTest extends Specification {

  static final String TENANT = "slimstrandnet"
  static final String TOKEN_PATH = "/oauth2/token"
  static final String ORDERS_PATH = "/v1/gopacs/${TENANT}/orders"
  static final String ORDER_JSON = '''{
    "reference": "ref-1", "orderId": "etpa-1", "tenantId": "slimstrandnet", "status": "PENDING",
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
    def created = resource.createOrder(TENANT, request)
    def fetched = resource.getOrder(TENANT, "ref-1")

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
    def stored = resource.createOrder(TENANT, new GopacsOrderRequest("ref-1", "SELL", 0.2,
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
    resource.replaceOrder(TENANT, "ref-1", new GopacsOrderRequest("ref-1", "SELL", 0.2,
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
    resource.createOrder(TENANT, new GopacsOrderRequest("ref-1", "SELL", 0.2,
            1792677600, 1792684800, 45, "871685920001768800", null, null))

    then:
    def e = thrown(WebApplicationException)
    DistroGopacsHandler.describe(e) == 'HTTP 403: {"message": "Forbidden"}'
  }

  def "a cancel is a DELETE answered with 204"() {
    given:
    wireMock.stubFor(delete(urlPathEqualTo("${ORDERS_PATH}/ref-1")).willReturn(aResponse().withStatus(204)))

    when:
    resource.cancelOrder(TENANT, "ref-1")

    then:
    wireMock.verify(deleteRequestedFor(urlPathEqualTo("${ORDERS_PATH}/ref-1")))
  }

  def "settlements are listed for one order over its delivery window"() {
    given:
    wireMock.stubFor(get(urlPathEqualTo("/v1/settlements/gopacs/${TENANT}")).willReturn(okJson('''{
      "items": [{"tradeId": "trade-1", "orderReference": "ref-1", "tenantId": "slimstrandnet",
        "orderId": "etpa-1", "ean": "871685920001768800", "direction": "SELL",
        "start": 1792677600, "end": 1792684800, "capacity": 0.1, "volume": 0.2, "price": 45,
        "cost": 9, "volumeUnit": "MWH", "capacityUnit": "MW", "priceUnit": "EUR/MWH",
        "currency": "EUR", "matchedAt": 1792670000, "settledAt": 1792690000}],
      "limit": 25, "offset": 0, "total": 1}''')))

    when:
    def page = resource.listSettlements(TENANT, 1792677600, 1792684800, "ref-1", 100, 0)

    then:
    page.total() == 1
    page.items()*.tradeId() == ["trade-1"]
    page.items()[0].cost() == 9d
    wireMock.verify(getRequestedFor(urlPathEqualTo("/v1/settlements/gopacs/${TENANT}"))
            .withQueryParam("start", equalTo("1792677600"))
            .withQueryParam("end", equalTo("1792684800"))
            .withQueryParam("orderReference", equalTo("ref-1"))
            .withQueryParam("limit", equalTo("100"))
            .withQueryParam("offset", equalTo("0")))
  }
}
