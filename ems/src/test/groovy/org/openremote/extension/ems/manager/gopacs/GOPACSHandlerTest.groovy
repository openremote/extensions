/*
 * Copyright 2026, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.extension.ems.manager.gopacs

import com.goterl.lazysodium.LazySodiumJava
import com.goterl.lazysodium.SodiumJava
import com.goterl.lazysodium.utils.KeyPair
import org.lfenergy.shapeshifter.api.*
import org.lfenergy.shapeshifter.api.model.UftpParticipantInformation
import org.lfenergy.shapeshifter.core.model.OutgoingUftpMessage
import org.lfenergy.shapeshifter.core.model.UftpParticipant
import org.openremote.container.timer.TimerService
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Exercises the day-ahead UFTP message flow handled by {@link GOPACSHandler} on the AGR (EMS) side.
 *
 * Real signed messages are driven through the handler's actual entry point ({@code processRawMessage})
 * using libsodium crypto -- no database, no container. The embedded payload XML matches the wire format
 * of the example messages (an outbound FlexRequestResponse + its SignedMessage envelope), with the
 * inbound direction being DSO (nilsgrid.net) -> AGR (openremote.io). Outbound messages -- both the
 * library-generated responses and the handler-built FlexOffer -- are captured by overriding
 * {@code notifyNewOutgoingMessage}.
 */
class GOPACSHandlerTest extends Specification {

    static final String CONTRACTED_EAN = "ean.871234567890123456"
    static final String ASSET_ID = "0abcDEFghiJKLmnoPQRstu"
    static final String AGR_DOMAIN = "openremote.io"   // our AGR: recipient of inbound, sender of replies
    static final String DSO_DOMAIN = "nilsgrid.net"    // the DSO: sender of inbound, recipient of replies
    static final LocalDate PERIOD = LocalDate.of(2026, 6, 4)
    // IDs taken from the provided example payload.xml so that file is literally the expected FlexRequest reply.
    static final String FLEX_REQUEST_MESSAGE_ID = "b3030b8b-2f45-43f4-8bf3-f00ef6fd74fc"
    static final String CONVERSATION_ID = "5f7c9c4d-5988-4a17-a479-2f716051fd6d"

    AssetProcessingService assetProcessingService
    AssetPredictedDatapointService assetPredictedDatapointService
    TimerService timerService
    ScheduledExecutorService executor
    RecordingGOPACSHandler handler
    String privKeyB64
    String pubB64

    def setup() {
        assetProcessingService = Mock(AssetProcessingService)
        assetPredictedDatapointService = Mock(AssetPredictedDatapointService)
        timerService = Stub(TimerService) {
            getCurrentTimeMillis() >> PERIOD.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        // Run every scheduled task inline so processRawMessage is fully synchronous.
        executor = Stub(ScheduledExecutorService) {
            schedule(_ as Runnable, _ as Long, _ as TimeUnit) >> { Runnable r, long d, TimeUnit u ->
                r.run()
                Stub(ScheduledFuture)
            }
        }

        // Generate an ed25519 keypair. The handler's crypto pool (LazySodiumBase64Pool) decodes both the
        // signing secret key and the verifying public key as base64.
        def lazySodium = new LazySodiumJava(new SodiumJava())
        KeyPair kp = lazySodium.cryptoSignKeypair()
        privKeyB64 = Base64.encoder.encodeToString(kp.secretKey.asBytes)
        pubB64 = Base64.encoder.encodeToString(kp.publicKey.asBytes)

        handler = newHandler(CONTRACTED_EAN)
    }

    // Builds a handler for the given contracted EAN and pre-seeds the DSO participant so inbound
    // signature verification never makes an HTTP call.
    private RecordingGOPACSHandler newHandler(String contractedEan) {
        def h = new RecordingGOPACSHandler(contractedEan, "master", ASSET_ID,
                assetProcessingService, assetPredictedDatapointService, timerService, executor, privKeyB64)
        h.participants.put(DSO_DOMAIN,
                new UftpParticipantInformation(DSO_DOMAIN, pubB64, "https://nilsgrid.net/shapeshifter/api/v3/message", true))
        return h
    }

    def "FlexRequest updates the asset from request ISPs and replies with FlexRequestResponse then FlexOffer"() {
        given: "a signed in-scope FlexRequest in the example wire format"
        def xml = flexRequestXml(CONTRACTED_EAN)

        when: "it is processed"
        signAndProcess(xml)

        then: "predicted datapoints are written: max=importMax, min=exportMax"
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerMaximumFlexRequest", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [5.0d, 6.0d]
        })
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerMinimumFlexRequest", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [3.0d, 4.0d]
        })

        and: "a FlexRequestResponse is sent, followed by a FlexOffer for the same congestion point"
        handler.sent.size() == 2
        handler.sent[0] instanceof FlexRequestResponse
        handler.sent[1] instanceof FlexOffer
        ((FlexOffer) handler.sent[1]).congestionPoint == CONTRACTED_EAN
        !((FlexOffer) handler.sent[1]).offerOptions.isEmpty()

        and: "the emitted FlexRequestResponse matches the example payload.xml shape"
        def response = (FlexRequestResponse) handler.sent[0]
        response.result == AcceptedRejectedType.ACCEPTED
        response.flexRequestMessageID == FLEX_REQUEST_MESSAGE_ID
        response.senderDomain == AGR_DOMAIN
        response.recipientDomain == DSO_DOMAIN
        response.version == "3.0.0"
        response.conversationID == CONVERSATION_ID
    }

    def "FlexOfferResponse (#result) is handled without mutating the asset or sending a reply"() {
        when: "a signed FlexOfferResponse is processed"
        signAndProcess(flexOfferResponseXml(result))

        then: "no asset mutation and no outbound message"
        0 * assetPredictedDatapointService.updateValues(_, _, _)
        0 * assetProcessingService.sendAttributeEvent(_, _)
        handler.sent.isEmpty()

        where:
        result << [AcceptedRejectedType.ACCEPTED, AcceptedRejectedType.REJECTED]
    }

    def "FlexOrder with offtake power updates currentPower and the max-profile and replies with FlexOrderResponse"() {
        when: "a signed in-scope FlexOrder with positive (offtake) power is processed"
        signAndProcess(flexOrderXml(CONTRACTED_EAN, [4000, 8000]))   // 4.0, 8.0 kW

        then: "current power and the offtake (max) profile are written; the feed-in (min) profile is not"
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "currentPowerFlexRequest", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [4.0d, 8.0d]
        })
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerLimitMaximumProfileFlexOrder", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [4.0d, 8.0d]
        })
        0 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerLimitMinimumProfileFlexOrder", _)

        and: "a FlexOrderResponse (Accepted) is sent back"
        handler.sent.size() == 1
        handler.sent[0] instanceof FlexOrderResponse
        ((FlexOrderResponse) handler.sent[0]).result == AcceptedRejectedType.ACCEPTED
    }

    def "FlexOrder with feed-in power updates currentPower and the min-profile"() {
        when: "a signed in-scope FlexOrder with negative (feed-in) power is processed"
        signAndProcess(flexOrderXml(CONTRACTED_EAN, [-2000, -5000]))  // -2.0, -5.0 kW

        then: "current power and the feed-in (min) profile are written; the offtake (max) profile is not"
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "currentPowerFlexRequest", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [-2.0d, -5.0d]
        })
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerLimitMinimumProfileFlexOrder", { List dps ->
            dps.size() == 2 && dps.collect { it.value as double } == [-2.0d, -5.0d]
        })
        0 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerLimitMaximumProfileFlexOrder", _)

        and: "a FlexOrderResponse (Accepted) is sent back"
        handler.sent.size() == 1
        handler.sent[0] instanceof FlexOrderResponse
        ((FlexOrderResponse) handler.sent[0]).result == AcceptedRejectedType.ACCEPTED
    }

    def "a validly-signed FlexRequest for a different congestion point is dropped with no mutation and no reply"() {
        when: "a signed out-of-scope FlexRequest (different EAN) is processed"
        signAndProcess(flexRequestXml("ean.999999999999999999"))

        then: "the asset is not mutated and nothing is sent back"
        0 * assetPredictedDatapointService.updateValues(_, _, _)
        0 * assetProcessingService.sendAttributeEvent(_, _)
        handler.sent.isEmpty()
    }

    def "toCongestionPoint canonicalises an EAN to the GOPACS ean.<code> format (#input -> #expected)"() {
        expect: "the optional, case-insensitive ean. prefix is normalised to lower-case and added when missing"
        GOPACSHandler.toCongestionPoint(input) == expected

        where:
        input                        || expected
        "ean.265987182507322951"     || "ean.265987182507322951"
        "265987182507322951"         || "ean.265987182507322951"
        "EAN.265987182507322951"     || "ean.265987182507322951"
        "Ean.265987182507322951"     || "ean.265987182507322951"
        "  ean.265987182507322951  " || "ean.265987182507322951"
        "  265987182507322951  "     || "ean.265987182507322951"
        ""                           || "ean."
        null                         || null
    }

    def "FlexRequest is in scope when the contracted EAN is configured without the ean. prefix"() {
        given: "a handler whose contracted EAN omits the ean. prefix, and a standard prefixed FlexRequest"
        def bareEan = CONTRACTED_EAN.substring(GOPACSHandler.EAN_PREFIX.length())
        def bareHandler = newHandler(bareEan)
        def sender = new UftpParticipant(DSO_DOMAIN, USEFRoleType.DSO)
        def signed = bareHandler.cryptoService.signMessage(flexRequestXml(CONTRACTED_EAN), sender, bareHandler.privateKey)

        when: "the signed FlexRequest is processed"
        bareHandler.processRawMessage(GOPACSHandler.serializer.toXml(signed))

        then: "it is treated as in scope: the asset is updated and both replies are sent"
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerMaximumFlexRequest", _)
        1 * assetPredictedDatapointService.updateValues(ASSET_ID, "powerMinimumFlexRequest", _)
        bareHandler.sent.size() == 2
        bareHandler.sent[0] instanceof FlexRequestResponse
        bareHandler.sent[1] instanceof FlexOffer
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

    private static String flexOfferResponseXml(AcceptedRejectedType result) {
        def reason = result == AcceptedRejectedType.REJECTED ? ' RejectionReason="insufficient flexibility"' : ''
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<FlexOfferResponse Version="3.0.0" SenderDomain="${DSO_DOMAIN}" RecipientDomain="${AGR_DOMAIN}"
    TimeStamp="2026-06-03T14:30:00+02:00" MessageID="7c3d9e40-0000-4000-8000-000000000003" ConversationID="${CONVERSATION_ID}"
    FlexOfferMessageID="9b2e7d10-0000-4000-8000-000000000002" Result="${result.value()}"${reason}/>"""
    }

    // Signs the payload XML as the DSO and feeds the transport XML through the real entry point.
    private void signAndProcess(String payloadXml) {
        def sender = new UftpParticipant(DSO_DOMAIN, USEFRoleType.DSO)
        SignedMessage signed = handler.cryptoService.signMessage(payloadXml, sender, handler.privateKey)
        handler.processRawMessage(GOPACSHandler.serializer.toXml(signed))
    }

    // Records outbound messages (library-generated responses + handler-built FlexOffer) instead of sending them.
    static class RecordingGOPACSHandler extends GOPACSHandler {
        final List<PayloadMessageType> sent = new ArrayList<>()

        RecordingGOPACSHandler(String ean, String realm, String assetId,
                               AssetProcessingService aps, AssetPredictedDatapointService apds,
                               TimerService ts, ScheduledExecutorService exec, String privateKey) {
            super(ean, realm, assetId, aps, apds, ts, exec, privateKey)
        }

        @Override
        void notifyNewOutgoingMessage(OutgoingUftpMessage<? extends PayloadMessageType> message) {
            sent.add(message.payloadMessage())
        }
    }
}
