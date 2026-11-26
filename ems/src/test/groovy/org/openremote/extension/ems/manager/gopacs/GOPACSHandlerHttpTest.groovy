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
package org.openremote.extension.ems.manager.gopacs

import com.github.tomakehurst.wiremock.WireMockServer
import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.KeyPair
import jakarta.ws.rs.client.Entity
import org.lfenergy.shapeshifter.api.USEFRoleType
import org.lfenergy.shapeshifter.api.SignedMessage
import org.lfenergy.shapeshifter.core.model.UftpParticipant
import org.openremote.container.Container
import org.openremote.extension.ems.manager.EmsOptimisationService
import org.openremote.extension.ems.manager.EmsOptimisationSetupService
import org.openremote.model.ContainerService
import org.openremote.test.ManagerContainerTrait
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.LocalDate
import java.util.concurrent.TimeUnit

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse
import static com.github.tomakehurst.wiremock.client.WireMock.containing
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo
import static com.github.tomakehurst.wiremock.client.WireMock.get
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.okJson
import static com.github.tomakehurst.wiremock.client.WireMock.post
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import static org.openremote.model.Constants.MASTER_REALM

/**
 * HTTP-contract integration test for {@link GOPACSHandler}, complementing the in-process message-flow
 * unit test in {@code GOPACSHandlerTest}. Where that test calls {@code processRawMessage} directly and
 * records outgoing payload objects, this test exercises the real transport chain:
 *
 * <ul>
 *   <li><b>Inbound:</b> a real {@code POST} to the deployed JAX-RS resource ({@code /gopacs/message}),
 *       asserting the accepted media type, JAX-RS delegation and {@code WebApplicationException} status
 *       mapping in {@link GOPACSHandler#processRawMessage}.</li>
 *   <li><b>Outbound:</b> the real broker delivery via {@code UftpSendMessageService} (which posts over
 *       {@code java.net.http.HttpClient}), plus the OAuth2 token and address-book lookups via the
 *       handler's RESTEasy client. All three are pointed at a single WireMock server, so signed XML,
 *       the synthesised broker endpoint and the {@code Authorization: Bearer} header are asserted on the
 *       wire.</li>
 * </ul>
 *
 * The broker send uses the JDK {@code HttpClient}, which a JAX-RS {@code ClientRequestFilter} (the style
 * used by {@code EntsoeProtocolTest}) cannot intercept; hence the WireMock server, matching the approach
 * shapeshifter itself uses to test {@code UftpSendMessageService}.
 *
 * Like the other manager-container integration tests in this repository (e.g. {@code EntsoeProtocolTest}),
 * this uses the OpenRemote dev-testing stack.
 *
 * Asset mutation (predicted datapoints / attribute events) is intentionally not re-asserted here -- those
 * writes are scheduled asynchronously and are already covered precisely by {@code GOPACSHandlerTest}. This
 * test focuses on the HTTP contract, so a placeholder asset id is sufficient.
 */
class GOPACSHandlerHttpTest extends Specification implements ManagerContainerTrait {

  static final String CONTRACTED_EAN = "ean.871234567890123456"
  static final String ASSET_ID = "0abcDEFghiJKLmnoPQRstu"
  static final String AGR_DOMAIN = "openremote.io" // our AGR: recipient of inbound, sender of replies
  static final String DSO_DOMAIN = "nilsgrid.net" // the DSO: sender of inbound, recipient of replies
  static final LocalDate PERIOD = LocalDate.of(2026, 6, 4)
  static final String FLEX_REQUEST_MESSAGE_ID = "b3030b8b-2f45-43f4-8bf3-f00ef6fd74fc"
  static final String CONVERSATION_ID = "5f7c9c4d-5988-4a17-a479-2f716051fd6d"
  static final String ACCESS_TOKEN = "test-access-token"

  static final String OAUTH_PATH = "/oauth/token"
  static final String ADDRESS_BOOK_PATH = "/uftp-participants/v3/participants/" + DSO_DOMAIN
  static final String BROKER_PATH = "/shapeshifter/api/v3/message"

  @Shared
  WireMockServer wireMock
  @Shared
  String dsoPrivateKeyB64
  @Shared
  String dsoPublicKeyB64
  @Shared
  File privateKeyFile
  @Shared
  Container runningContainer
  GOPACSHandler handler

  def setupSpec() {
    // Two ed25519 keypairs: the DSO signs the inbound messages (its public key is served by the
    // address-book stub so the handler can verify them), and the AGR signs its outbound replies.
    def lazySodium = new LazySodiumJava(new SodiumJava())
    KeyPair dso = lazySodium.cryptoSignKeypair()
    dsoPrivateKeyB64 = Base64.encoder.encodeToString(dso.secretKey.asBytes)
    dsoPublicKeyB64 = Base64.encoder.encodeToString(dso.publicKey.asBytes)

    KeyPair agr = lazySodium.cryptoSignKeypair()
    privateKeyFile = File.createTempFile("gopacs-agr-private-key", ".txt")
    privateKeyFile.text = Base64.encoder.encodeToString(agr.secretKey.asBytes)

    wireMock = new WireMockServer(0)
    wireMock.start()

    String base = "http://127.0.0.1:${wireMock.port()}"
    def config = defaultConfig() << [
      (GOPACSHandler.GOPACS_CLIENT_ID) : "test-client",
      (GOPACSHandler.GOPACS_CLIENT_SECRET) : "test-secret",
      (GOPACSHandler.GOPACS_PRIVATE_KEY_FILE) : privateKeyFile.absolutePath,
      (GOPACSHandler.GOPACS_OAUTH2_URL) : base + OAUTH_PATH,
      (GOPACSHandler.GOPACS_PARTICIPANT_URL) : base,
      (GOPACSHandler.GOPACS_BROKER_URL) : base,
      // Send replies immediately so assertions don't wait on the production response/offer delays.
      (GOPACSHandler.GOPACS_RESPONSE_DELAY_SECONDS) : "0",
      (GOPACSHandler.GOPACS_FLEX_OFFER_DELAY_SECONDS): "0"
    ]

    runningContainer = startContainer(config, gopacsServices())
  }

  def cleanupSpec() {
    stopContainer()
    wireMock?.stop()
    privateKeyFile?.delete()
  }

  def setup() {
    wireMock.resetAll()
    stubOAuthToken(ACCESS_TOKEN)
    stubAddressBook(dsoPublicKeyB64)
    stubBrokerAccepts()

    // Each feature gets its own OAuth client, participant cache, and scheduled replies.
    handler = new GOPACSHandler(CONTRACTED_EAN, MASTER_REALM, ASSET_ID, runningContainer)
    handler.deploy()
  }

  def cleanup() {
    handler?.undeploy()
  }

  // The manager services minus the EMS optimization services, which would otherwise deploy their own
  // GOPACS handler (on the same /gopacs path) from any persisted EmsGOPACSAsset.
  private Iterable<ContainerService> gopacsServices() {
    defaultServices().findAll {
      !(it instanceof EmsOptimisationService) && !(it instanceof EmsOptimisationSetupService)
    }
  }

  def "a signed in-scope FlexRequest POSTed to /gopacs/message is accepted and the reply is delivered to the broker with a bearer token"() {
    given: "polling for the asynchronous outbound deliveries"
    def conditions = new PollingConditions(timeout: 10, delay: 0.2)

    when: "a signed FlexRequest is POSTed to the deployed endpoint"
    int status = postSignedAsDso(flexRequestXml(CONTRACTED_EAN))

    then: "the transport call succeeds"
    status == 204

    and: "verifying the signature fetched an OAuth2 token and resolved the DSO via the address book"
    conditions.eventually {
      wireMock.verify(postRequestedFor(urlPathEqualTo(OAUTH_PATH))
              .withRequestBody(containing("grant_type=client_credentials")))
      wireMock.verify(getRequestedFor(urlPathEqualTo(ADDRESS_BOOK_PATH))
              .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN)))
    }

    and: "the FlexRequestResponse and FlexOffer are actually POSTed to the broker as signed XML with a bearer token"
    conditions.eventually {
      wireMock.verify(postRequestedFor(urlPathEqualTo(BROKER_PATH))
              .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
              .withRequestBody(containing("SignedMessage")))
      def payloads = decodedBrokerPayloads()
      assert payloads.any { it.contains("FlexRequestResponse") }
      assert payloads.any { it.contains("FlexOffer") }
    }
  }

  def "a signed in-scope FlexOrder POSTed to /gopacs/message is accepted and a FlexOrderResponse is delivered to the broker"() {
    given:
    def conditions = new PollingConditions(timeout: 10, delay: 0.2)

    when: "a signed FlexOrder is POSTed to the deployed endpoint"
    int status = postSignedAsDso(flexOrderXml(CONTRACTED_EAN, [4000, 8000]))

    then: "the transport call succeeds"
    status == 204

    and: "a FlexOrderResponse is POSTed to the broker as signed XML with a bearer token"
    conditions.eventually {
      wireMock.verify(postRequestedFor(urlPathEqualTo(BROKER_PATH))
              .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
              .withRequestBody(containing("SignedMessage")))
      assert decodedBrokerPayloads().any { it.contains("FlexOrderResponse") }
    }
  }

  def "a validly-signed FlexRequest for a different congestion point is rejected via the broker without a FlexOffer"() {
    when: "a signed out-of-scope FlexRequest is POSTed and the scheduled replies finish"
    int status = postSignedAsDso(flexRequestXml("ean.999999999999999999"))
    awaitScheduledReplies()

    then: "the signed envelope is accepted"
    status == 204

    and: "the only broker delivery is a signed rejection with a reason"
    wireMock.verify(1, postRequestedFor(urlPathEqualTo(BROKER_PATH))
            .withHeader("Authorization", equalTo("Bearer " + ACCESS_TOKEN))
            .withRequestBody(containing("SignedMessage")))
    def payloads = decodedBrokerPayloads()
    payloads.size() == 1
    payloads[0].contains("FlexRequestResponse")
    payloads[0].contains('Result="Rejected"')
    payloads[0].contains("RejectionReason=")
  }

  def "malformed transport XML is rejected with 400"() {
    when: "a body that is not a SignedMessage envelope is POSTed"
    int status = postXml("<not-a-signed-message/>")

    then: "the endpoint maps the failure to 400 Bad Request"
    status == 400

    and: "nothing is delivered to the broker"
    wireMock.verify(0, postRequestedFor(urlPathEqualTo(BROKER_PATH)))
  }

  def "a correctly-formed message whose signature does not match the resolved public key is rejected with 400"() {
    given: "the address book returns a public key that does not match the DSO signing key"
    def lazySodium = new LazySodiumJava(new SodiumJava())
    def wrongPublicKeyB64 = Base64.encoder.encodeToString(lazySodium.cryptoSignKeypair().publicKey.asBytes)
    stubAddressBook(wrongPublicKeyB64)

    when: "a validly-signed FlexRequest is POSTed"
    int status = postSignedAsDso(flexRequestXml(CONTRACTED_EAN))

    then: "signature verification fails and the endpoint returns 400 (the UftpConnectorException branch)"
    status == 400

    and: "nothing is delivered to the broker"
    wireMock.verify(0, postRequestedFor(urlPathEqualTo(BROKER_PATH)))
  }

  def "an OAuth2 token failure (#scenario) prevents participant resolution so the inbound message is rejected with 400"() {
    given: "the token endpoint is unhealthy"
    wireMock.stubFor(post(urlPathEqualTo(OAUTH_PATH)).willReturn(tokenResponse))

    when: "a validly-signed FlexRequest is POSTed"
    int status = postSignedAsDso(flexRequestXml(CONTRACTED_EAN))

    then: "without a bearer token the DSO cannot be resolved, verification fails and the endpoint returns 400"
    status == 400

    and: "nothing is delivered to the broker"
    wireMock.verify(0, postRequestedFor(urlPathEqualTo(BROKER_PATH)))

    where:
    scenario | tokenResponse
    "500 error" | aResponse().withStatus(500)
    "invalid token JSON" | okJson("not-json")
  }

  def "an address-book 404 for the sender domain is rejected with 400"() {
    given: "the address book does not know the DSO"
    wireMock.stubFor(get(urlPathEqualTo(ADDRESS_BOOK_PATH)).willReturn(aResponse().withStatus(404)))

    when: "a validly-signed FlexRequest is POSTed"
    int status = postSignedAsDso(flexRequestXml(CONTRACTED_EAN))

    then: "the sender cannot be resolved, verification fails and the endpoint returns 400"
    status == 400

    and: "nothing is delivered to the broker"
    wireMock.verify(0, postRequestedFor(urlPathEqualTo(BROKER_PATH)))
  }

  def "the resolved participant is cached so a second inbound message does not trigger a second address-book lookup"() {
    when: "two signed requests from the same sender are POSTed"
    def xml = flexRequestXml("ean.999999999999999999")
    int firstStatus = postSignedAsDso(xml)
    int secondStatus = postSignedAsDso(xml)
    awaitScheduledReplies()

    then: "both requests are accepted but the address book is queried once"
    firstStatus == 204
    secondStatus == 204
    wireMock.verify(1, getRequestedFor(urlPathEqualTo(ADDRESS_BOOK_PATH)))
  }

  private void awaitScheduledReplies() {
    // These requests only schedule zero-delay broker sends. Snapshot after the HTTP response,
    // when the handler has queued all work for the request.
    handler.@scheduledFutureList.toArray().each { it.get(10, TimeUnit.SECONDS) }
  }

  // ---- WireMock stubs ----

  private void stubOAuthToken(String token) {
    wireMock.stubFor(post(urlPathEqualTo(OAUTH_PATH)).willReturn(
                    okJson("""{"access_token":"${token}","token_type":"Bearer","expires_in":3600,"scope":"uftp"}""")))
  }

  private void stubAddressBook(String publicKeyB64) {
    wireMock.stubFor(get(urlPathEqualTo(ADDRESS_BOOK_PATH)).willReturn(
                    okJson("""{"domain":"${DSO_DOMAIN}","publicKey":"${publicKeyB64}"}""")))
  }

  private void stubBrokerAccepts() {
    wireMock.stubFor(post(urlPathEqualTo(BROKER_PATH)).willReturn(aResponse().withStatus(200)))
  }

  // ---- HTTP helpers ----

  // Signs the payload XML as the DSO and POSTs the resulting transport XML to the deployed endpoint.
  private int postSignedAsDso(String payloadXml) {
    def sender = new UftpParticipant(DSO_DOMAIN, USEFRoleType.DSO)
    SignedMessage signed = handler.cryptoService.signMessage(payloadXml, sender, dsoPrivateKeyB64)
    return postXml(GOPACSHandler.serializer.toXml(signed))
  }

  // The broker receives SignedMessage envelopes carrying the payload in the base64 "Body" attribute. The
  // decoded bytes are the libsodium-signed payload (a 64-byte signature prefixed to the payload XML), so the
  // outgoing message type is still a substring of the decoded text and can be asserted on the wire.
  private List<String> decodedBrokerPayloads() {
    wireMock.findAll(postRequestedFor(urlPathEqualTo(BROKER_PATH))).collect { req ->
      def matcher = (req.bodyAsString =~ /Body="([^"]+)"/)
      matcher ? new String(Base64.decoder.decode(matcher[0][1] as String)) : ""
    }
  }

  private int postXml(String transportXml) {
    // The deployment registers a realm-path-extractor filter (realmIndex 0), so the realm is the first
    // segment after the /gopacs context path: /gopacs/{realm}/message -> resource @Path("message").
    createClient(null)
            .target(serverUri(getServerPort()).path("gopacs").path(MASTER_REALM).path("message"))
            .request()
            .post(Entity.entity(transportXml, "text/xml"))
            .withCloseable { it.status }
  }

  // ---- Embedded UFTP payload fixtures (attribute-style XML, matching the example message format) ----

  private static String flexRequestXml(String congestionPoint) {
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<FlexRequest Version="3.0.0" SenderDomain="${DSO_DOMAIN}" RecipientDomain="${AGR_DOMAIN}"
    TimeStamp="2026-06-03T14:30:00+02:00" MessageID="${FLEX_REQUEST_MESSAGE_ID}" ConversationID="${CONVERSATION_ID}"
    ISP-Duration="PT15M" TimeZone="Europe/Amsterdam" Period="${PERIOD}" CongestionPoint="${congestionPoint}"
    Revision="1" ExpirationDateTime="2026-06-04T12:00:00+02:00" ContractID="contract-1">
    <ISP Disposition="Requested" Start="1" Duration="1" MinPower="-3000" MaxPower="5000"/>
    <ISP Disposition="Requested" Start="2" Duration="1" MinPower="-4000" MaxPower="6000"/>
</FlexRequest>"""
  }

  private static String flexOrderXml(String congestionPoint, List<Integer> powers) {
    def isps = (0..<powers.size()).collect { i ->
      """    <ISP Power="${powers[i]}" Start="${i + 1}" Duration="1"/>"""
    }.join("\n")
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<FlexOrder Version="3.0.0" SenderDomain="${DSO_DOMAIN}" RecipientDomain="${AGR_DOMAIN}"
    TimeStamp="2026-06-03T14:30:00+02:00" MessageID="3a1f8c20-0000-4000-8000-000000000001" ConversationID="${CONVERSATION_ID}"
    ISP-Duration="PT15M" TimeZone="Europe/Amsterdam" Period="${PERIOD}" CongestionPoint="${congestionPoint}"
    FlexOfferMessageID="9b2e7d10-0000-4000-8000-000000000002" OrderReference="order-1" Price="0.00" Currency="EUR">
${isps}
</FlexOrder>"""
  }
}
