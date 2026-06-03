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
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.container.timer.TimerService
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class GOPACSHandlerTest extends Specification {

    static final String CONTRACTED_EAN = "871234567890123456"
    static final String ASSET_ID = "0abcDEFghiJKLmnoPQRstu"
    static final String AGR_DOMAIN = "agr.example.com"
    static final String DSO_DOMAIN = "dso.example.com"
    static final String TIME_ZONE = "Europe/Amsterdam"
    static final LocalDate PERIOD = LocalDate.of(2026, 6, 4)

    AssetProcessingService assetProcessingService
    AssetPredictedDatapointService assetPredictedDatapointService
    TimerService timerService
    ScheduledExecutorService executor
    RecordingGOPACSHandler handler
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

        // Generate an ed25519 keypair; hex secret signs, base64 public verifies.
        def lazySodium = new LazySodiumJava(new SodiumJava())
        KeyPair kp = lazySodium.cryptoSignKeypair()
        String privHex = kp.secretKey.asHexString
        pubB64 = Base64.encoder.encodeToString(kp.publicKey.asBytes)

        handler = new RecordingGOPACSHandler(CONTRACTED_EAN, "master", ASSET_ID,
                assetProcessingService, assetPredictedDatapointService, timerService, executor, privHex)

        // Pre-seed the DSO participant so signature verification never makes an HTTP call.
        handler.participants.put(DSO_DOMAIN,
                new UftpParticipantInformation(DSO_DOMAIN, pubB64, "https://dso.example.com/endpoint", true))
    }

    def "handler is constructed via the test-support constructor and is in scope for its contracted EAN"() {
        expect:
        handler != null
        handler.isWithinContractedScope("FlexRequest", "conv-1", CONTRACTED_EAN)
        !handler.isWithinContractedScope("FlexRequest", "conv-1", "999999999999999999")
    }

    // ---- Test subclass: records outbound messages instead of signing/sending them ----
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
