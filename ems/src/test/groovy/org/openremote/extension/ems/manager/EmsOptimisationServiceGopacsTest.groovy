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
package org.openremote.extension.ems.manager

import org.openremote.container.timer.TimerService
import org.openremote.container.web.WebService
import org.openremote.extension.ems.agent.EmsGOPACSAsset
import org.openremote.extension.ems.manager.gopacs.GOPACSHandler
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.model.Container
import org.openremote.model.PersistenceEvent
import org.openremote.model.util.ValueUtil
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ScheduledExecutorService

/**
 * Exercises {@link EmsOptimisationService#processAssetChange} and {@link EmsOptimisationService#stop}
 * for {@link EmsGOPACSAsset} persistence events, via a recording {@code GOPACSHandler} subclass
 * rather than a mock of the class. The real constructor runs, so the fake {@code Container} carries
 * the OAuth client config and a readable private-key file; {@code deploy()}/{@code undeploy()} are
 * overridden so no JAX-RS deployment happens and no client is closed.
 *
 * {@code gopacsHandlerMap} is keyed by contracted EAN. These tests catch stop calls that use a key
 * the running handler was never registered under: {@code Map.remove} with the wrong key is a silent
 * no-op, so the old handler keeps its endpoint and client alive next to its replacement.
 */
class EmsOptimisationServiceGopacsTest extends Specification {

  static final String ASSET_ID = "gopacsAsset1"
  static final String OTHER_ASSET_ID = "gopacsAsset2"
  static final String REALM = "master"
  static final String EAN = "ean.265987182507322951"
  static final String OTHER_EAN = "ean.265987182507322952"

  @Shared File privateKeyFile
  List<RecordingGOPACSHandler> createdHandlers
  EmsOptimisationService service

  def setupSpec() {
    // Populates the asset model registry from this extension's own AssetModelProvider SPI
    // registration, which real asset construction needs; a plain Specification has no container
    // to do this at startup.
    ValueUtil.initialise(null)
    // The constructor only checks the file is readable and reads it into a field; the key is never
    // used because the recording subclass never sends anything.
    privateKeyFile = File.createTempFile("gopacs-test-key", ".txt")
    privateKeyFile.text = "not-a-real-key"
  }

  def cleanupSpec() {
    privateKeyFile.delete()
  }

  def setup() {
    def handlerContainer = Stub(Container) {
      getConfig() >> [
        (GOPACSHandler.GOPACS_CLIENT_ID): "client-id",
        (GOPACSHandler.GOPACS_CLIENT_SECRET): "client-secret",
        (GOPACSHandler.GOPACS_PRIVATE_KEY_FILE): privateKeyFile.absolutePath,
      ]
      getService(AssetProcessingService) >> Stub(AssetProcessingService)
      getService(AssetPredictedDatapointService) >> Stub(AssetPredictedDatapointService)
      getService(TimerService) >> Stub(TimerService)
      getService(WebService) >> Stub(WebService)
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
    }

    createdHandlers = []

    service = new EmsOptimisationService()
    service.gopacsHandlerFactory = new GOPACSHandler.Factory(handlerContainer) {
              @Override
              GOPACSHandler createHandler(String contractedEan, String realm, String assetId) {
                def handler =
                        new RecordingGOPACSHandler(contractedEan, realm, assetId, handlerContainer)
                createdHandlers << handler
                return handler
              }
            }
  }

  private static EmsGOPACSAsset gopacsAsset(String ean = EAN, String assetId = ASSET_ID) {
    def asset = new EmsGOPACSAsset("gopacs").setId(assetId).setRealm(REALM)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.CONTRACTED_EAN).setValue(ean)
    return asset
  }

  private static PersistenceEvent<EmsGOPACSAsset> event(
          PersistenceEvent.Cause cause, EmsGOPACSAsset asset) {
    return new PersistenceEvent<>(cause, asset, null, null, null)
  }

  def "CREATE deploys a handler for the asset's EAN"() {
    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))

    then:
    createdHandlers.size() == 1
    createdHandlers[0].contractedEAN == EAN
    createdHandlers[0].deployCount == 1
  }

  def "DELETE undeploys the handler"() {
    given: "a deployed handler"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))
    def original = createdHandlers[0]

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.DELETE, gopacsAsset()))

    then:
    original.undeployCount == 1
  }

  def "UPDATE with the same EAN undeploys the old handler before deploying its replacement"() {
    given: "a deployed handler"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))
    def original = createdHandlers[0]

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset()))

    then: "the old handler is stopped and exactly one new handler replaces it"
    original.undeployCount == 1
    createdHandlers.size() == 2
    createdHandlers[1].deployCount == 1
  }

  def "UPDATE with a changed EAN undeploys the handler registered under the old EAN"() {
    given: "a deployed handler for the original EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN)))
    def original = createdHandlers[0]

    when: "the asset is saved with a different EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(OTHER_EAN)))

    then: "the old handler is stopped rather than left running under the old key"
    original.undeployCount == 1
    createdHandlers.size() == 2
    createdHandlers[1].contractedEAN == OTHER_EAN
    createdHandlers[1].deployCount == 1
  }

  def "stop undeploys every deployed handler"() {
    given: "handlers for two assets"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID)))
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(OTHER_EAN, OTHER_ASSET_ID)))

    when:
    service.stop(null)

    then:
    createdHandlers.size() == 2
    createdHandlers.every { it.undeployCount == 1 }
  }

  // Records deploy()/undeploy() calls instead of deploying the JAX-RS endpoint or closing the client.
  static class RecordingGOPACSHandler extends GOPACSHandler {
    int deployCount = 0
    int undeployCount = 0

    RecordingGOPACSHandler(
    String contractedEan, String realm, String assetId, Container container) {
      super(contractedEan, realm, assetId, container)
    }

    @Override
    void deploy() {
      deployCount++
    }

    @Override
    void undeploy() {
      undeployCount++
    }
  }
}
