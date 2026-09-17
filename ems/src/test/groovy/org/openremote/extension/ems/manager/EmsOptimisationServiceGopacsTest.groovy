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
import org.openremote.extension.ems.manager.gopacs.GOPACSRedispatchHandler
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.model.Container
import org.openremote.model.PersistenceEvent
import org.openremote.model.attribute.Attribute
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.util.ValueUtil
import org.openremote.model.value.AttributeDescriptor
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ScheduledExecutorService

/**
 * Exercises {@link EmsOptimisationService#processAssetChange} and {@link EmsOptimisationService#stop}
 * for {@link EmsGOPACSAsset} persistence events, via a recording {@code GOPACSHandler} subclass
 * rather than a mock of the class. The real constructor runs, so the fake {@code Container} carries
 * the OAuth client config and a readable private-key file; {@code deploy()}/{@code undeploy()} are
 * overridden so no JAX-RS deployment happens and no client is closed. The redispatch poller gets
 * the same treatment with {@code startPolling()}/{@code stopPolling()}.
 *
 * {@code gopacsHandlerMap} is keyed by contracted EAN. These tests catch stop calls that use a key
 * the running handler was never registered under: {@code Map.remove} with the wrong key is a silent
 * no-op, so the old handler keeps its endpoint and client alive next to its replacement.
 *
 * Saving an asset reaches this service twice: once as the persistence event, and again as the
 * attribute events {@code AssetStorageService.publishModificationEvents()} raises from the same
 * merge. {@link #save} plays back both, so the tests below cover the whole save rather than the
 * persistence event on its own.
 */
class EmsOptimisationServiceGopacsTest extends Specification {

  static final String ASSET_ID = "gopacsAsset1"
  static final String OTHER_ASSET_ID = "gopacsAsset2"
  static final String REALM = "master"
  static final String EAN = "ean.265987182507322951"
  static final String OTHER_EAN = "ean.265987182507322952"

  @Shared File privateKeyFile
  List<RecordingGOPACSHandler> createdHandlers
  List<RecordingRedispatchHandler> createdRedispatchHandlers
  Map<String, EmsGOPACSAsset> storedAssets
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
      getService(AssetStorageService) >> Stub(AssetStorageService)
      getService(AssetPredictedDatapointService) >> Stub(AssetPredictedDatapointService)
      getService(TimerService) >> Stub(TimerService)
      getService(WebService) >> Stub(WebService)
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
    }

    createdHandlers = []
    createdRedispatchHandlers = []
    storedAssets = [:]

    service = new EmsOptimisationService()
    // The attribute event path reads the saved asset back, so the tests keep the assets they save
    // in storedAssets and hand them out here.
    service.services = Services.builder()
            .withAssetStorageService(Stub(AssetStorageService) {
              find(_ as String) >> { String assetId -> storedAssets[assetId] }
            })
            .build()
    service.gopacsHandlerFactory = new GOPACSHandler.Factory(handlerContainer) {
              @Override
              GOPACSHandler createHandler(String contractedEan, String realm, String assetId) {
                def handler =
                        new RecordingGOPACSHandler(contractedEan, realm, assetId, handlerContainer)
                createdHandlers << handler
                return handler
              }
            }
    service.gopacsRedispatchHandlerFactory =
            new GOPACSRedispatchHandler.Factory(handlerContainer) {
              @Override
              GOPACSRedispatchHandler createHandler(
                      String contractedEan, String realm, String assetId) {
                def handler =
                        new RecordingRedispatchHandler(contractedEan, realm, assetId, handlerContainer)
                createdRedispatchHandlers << handler
                return handler
              }
            }
  }

  private static EmsGOPACSAsset gopacsAsset(
          String ean = EAN, String assetId = ASSET_ID, boolean redispatchEnabled = false) {
    def asset = new EmsGOPACSAsset("gopacs").setId(assetId).setRealm(REALM)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.CONTRACTED_EAN).setValue(ean)
    asset.setRedispatchEnabled(redispatchEnabled)
    return asset
  }

  private static PersistenceEvent<EmsGOPACSAsset> event(
          PersistenceEvent.Cause cause, EmsGOPACSAsset asset) {
    return new PersistenceEvent<>(cause, asset, null, null, null)
  }

  /**
   * The event {@code AssetStorageService.publishModificationEvents()} raises for one attribute of a
   * saved asset.
   */
  private static AttributeEvent attributeEvent(
          EmsGOPACSAsset asset, Attribute<?> attribute, Object oldValue) {
    return new AttributeEvent(asset, attribute, "AssetStorageService",
            attribute.getValue().orElse(null), attribute.getTimestamp().orElse(0L), oldValue, 0L)
  }

  private static AttributeEvent attributeEvent(
          EmsGOPACSAsset asset, AttributeDescriptor<?> descriptor, Object oldValue) {
    return attributeEvent(asset, asset.getAttributes().get(descriptor.getName()).orElseThrow(),
            oldValue)
  }

  /**
   * Saves the asset the way the manager does: the persistence event for the merge, then an attribute
   * event per attribute. A save that touches anything besides the attributes, a rename for instance,
   * republishes every attribute whether its value changed or not, which is the case these tests are
   * mostly about. {@code oldEan} is the EAN the save replaced, or null when the EAN is unchanged.
   * {@code oldRedispatchEnabled} is the toggle's previous value, or null when it is unchanged.
   */
  private void save(
          PersistenceEvent.Cause cause, EmsGOPACSAsset asset, String oldEan = null,
          Boolean oldRedispatchEnabled = null) {
    storedAssets[asset.getId()] = asset
    service.processAssetChange(event(cause, asset))
    asset.getAttributes().values().each { attribute ->
      def oldValue
      if (attribute.getName() == EmsGOPACSAsset.CONTRACTED_EAN.getName() && oldEan != null) {
        oldValue = oldEan
      } else if (attribute.getName() == EmsGOPACSAsset.REDISPATCH_ENABLED.getName()
              && oldRedispatchEnabled != null) {
        oldValue = oldRedispatchEnabled
      } else {
        oldValue = attribute.getValue().orElse(null)
      }
      service.processAttributeEvent(attributeEvent(asset, attribute, oldValue))
    }
  }

  def "CREATE deploys a handler for the asset's EAN"() {
    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))

    then:
    createdHandlers.size() == 1
    createdHandlers[0].contractedEAN == EAN
    createdHandlers[0].deployCount == 1

    and: "no redispatch poller, since redispatch is not enabled on the asset"
    createdRedispatchHandlers.isEmpty()
  }

  def "CREATE with redispatch enabled also starts a redispatch poller for the EAN"() {
    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))

    then:
    createdRedispatchHandlers.size() == 1
    createdRedispatchHandlers[0].contractedEAN == EAN
    createdRedispatchHandlers[0].startCount == 1
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

  def "DELETE stops the redispatch poller"() {
    given: "a running poller"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.DELETE, gopacsAsset(EAN, ASSET_ID, true)))

    then:
    original.stopCount == 1
  }

  def "DELETE undeploys the handler registered under the EAN it was created with"() {
    given: "a handler deployed for the original EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN)))
    def original = createdHandlers[0]

    when: "the asset is deleted carrying a different EAN than the one it was registered under"
    service.processAssetChange(event(PersistenceEvent.Cause.DELETE, gopacsAsset(OTHER_EAN)))

    then: "the handler is stopped by asset id rather than missed by key"
    original.undeployCount == 1
  }

  def "DELETE stops the redispatch poller registered under the EAN it was started with"() {
    given: "a poller running for the original EAN"
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "the asset is deleted carrying a different EAN than the one it was registered under"
    service.processAssetChange(
            event(PersistenceEvent.Cause.DELETE, gopacsAsset(OTHER_EAN, ASSET_ID, true)))

    then: "the poller is stopped by asset id rather than missed by key"
    original.stopCount == 1
  }

  def "UPDATE with the same EAN leaves the deployed handler alone"() {
    given: "a deployed handler"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))
    def original = createdHandlers[0]

    when: "the asset is saved without changing the EAN, for instance a rename"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset()))

    then: "the handler keeps its participant cache and any UFTP conversation in flight"
    original.undeployCount == 0
    createdHandlers.size() == 1
  }

  def "UPDATE that clears the EAN undeploys the handler without deploying a replacement"() {
    given: "a deployed handler"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset()))
    def original = createdHandlers[0]

    when: "the asset is saved with the contracted EAN emptied"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset("")))

    then: "no EAN means no endpoint, so the handler is stopped and none takes its place"
    original.undeployCount == 1
    createdHandlers.size() == 1
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

  def "UPDATE with the same EAN leaves the redispatch poller running"() {
    given: "a running poller"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved without changing the EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, true)))

    then: "the poller keeps its in-memory announcement bookkeeping rather than being restarted"
    original.stopCount == 0
    createdRedispatchHandlers.size() == 1
  }

  def "UPDATE that enables redispatch on save starts a poller"() {
    given: "an asset without redispatch"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, false)))

    when: "the asset is saved with redispatch switched on"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, true)))

    then:
    createdRedispatchHandlers.size() == 1
    createdRedispatchHandlers[0].contractedEAN == EAN
    createdRedispatchHandlers[0].startCount == 1
  }

  def "UPDATE that disables redispatch on save stops the poller"() {
    given: "a running poller"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved with redispatch switched off"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, false)))

    then:
    original.stopCount == 1
    createdRedispatchHandlers.size() == 1
  }

  def "UPDATE with a changed EAN re-keys the redispatch poller"() {
    given: "a running poller for the original EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved with a different EAN"
    service.processAssetChange(
            event(PersistenceEvent.Cause.UPDATE, gopacsAsset(OTHER_EAN, ASSET_ID, true)))

    then: "the stale poller is stopped and a new one polls under the new EAN"
    original.stopCount == 1
    createdRedispatchHandlers.size() == 2
    createdRedispatchHandlers[1].contractedEAN == OTHER_EAN
    createdRedispatchHandlers[1].startCount == 1
  }

  def "UPDATE with a changed EAN and redispatch disabled stops the stale poller without starting one"() {
    given: "a running poller for the original EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved with a different EAN and redispatch switched off"
    service.processAssetChange(
            event(PersistenceEvent.Cause.UPDATE, gopacsAsset(OTHER_EAN, ASSET_ID, false)))

    then:
    original.stopCount == 1
    createdRedispatchHandlers.size() == 1
  }

  def "a save that leaves the EAN alone keeps the handler through the attribute events it raises"() {
    given: "a deployed handler"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset())
    def original = createdHandlers[0]

    when: "the asset is saved without changing the EAN, for instance a rename"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset())

    then: "the republished contractedEan does not undo what the persistence event reconciled"
    original.undeployCount == 0
    createdHandlers.size() == 1
  }

  def "a save that leaves redispatch enabled keeps the poller through the attribute events it raises"() {
    given: "a running poller"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved without changing the EAN or the redispatch toggle"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, true))

    then: "the poller keeps its in-memory announcement bookkeeping"
    original.stopCount == 0
    createdRedispatchHandlers.size() == 1
  }

  def "a save that changes the EAN ends with one handler, for the new EAN"() {
    given: "a deployed handler for the original EAN"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN))
    def original = createdHandlers[0]

    when: "the asset is saved with a different EAN"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset(OTHER_EAN), EAN)

    then: "the persistence event makes the swap and the attribute event leaves it standing"
    original.undeployCount == 1
    createdHandlers.size() == 2
    createdHandlers[1].contractedEAN == OTHER_EAN
    createdHandlers[1].deployCount == 1
    createdHandlers[1].undeployCount == 0
  }

  def "a save that clears the EAN through the attribute events it raises undeploys the handler without deploying a replacement"() {
    given: "a deployed handler"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset())
    def original = createdHandlers[0]

    when: "the asset is saved with the contracted EAN emptied"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset(""), EAN)

    then: "the republished empty EAN does not deploy a handler behind the stop the persistence event already made"
    original.undeployCount == 1
    createdHandlers.size() == 1
  }

  def "a save that enables redispatch through the attribute events it raises starts a poller"() {
    given: "an asset without redispatch"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, false))

    when: "the asset is saved with redispatch switched on"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, true), null, false)

    then: "the republished toggle does not stop the poller the persistence event already started"
    createdRedispatchHandlers.size() == 1
    createdRedispatchHandlers[0].contractedEAN == EAN
    createdRedispatchHandlers[0].startCount == 1
  }

  def "a save that disables redispatch through the attribute events it raises stops the poller"() {
    given: "a running poller"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true))
    def original = createdRedispatchHandlers[0]

    when: "the asset is saved with redispatch switched off"
    save(PersistenceEvent.Cause.UPDATE, gopacsAsset(EAN, ASSET_ID, false), null, true)

    then: "the republished toggle does not start a second poller behind the stop the persistence event already made"
    original.stopCount == 1
    createdRedispatchHandlers.size() == 1
    createdHandlers[0].undeployCount == 0
  }

  def "an attribute write that enables redispatch starts a poller"() {
    given: "a deployed handler with redispatch off"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, false))

    when: "redispatch is switched on, which raises an attribute event and no persistence event"
    def asset = gopacsAsset(EAN, ASSET_ID, true)
    storedAssets[ASSET_ID] = asset
    service.processAttributeEvent(attributeEvent(asset, EmsGOPACSAsset.REDISPATCH_ENABLED, false))

    then: "the attribute event path still drives the lifecycle on its own"
    createdRedispatchHandlers.size() == 1
    createdRedispatchHandlers[0].contractedEAN == EAN
    createdRedispatchHandlers[0].startCount == 1
  }

  def "an attribute write that disables redispatch stops the poller"() {
    given: "a running poller"
    save(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true))
    def original = createdRedispatchHandlers[0]

    when: "redispatch is switched off through an attribute write"
    def asset = gopacsAsset(EAN, ASSET_ID, false)
    storedAssets[ASSET_ID] = asset
    service.processAttributeEvent(attributeEvent(asset, EmsGOPACSAsset.REDISPATCH_ENABLED, true))

    then: "the poller stops and the GOPACS handler is left deployed"
    original.stopCount == 1
    createdRedispatchHandlers.size() == 1
    createdHandlers[0].undeployCount == 0
  }

  def "a handler deployed for an EAN already in use undeploys the one it displaces"() {
    given: "a deployed handler for the EAN"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID)))
    def original = createdHandlers[0]

    when: "a second asset is created with the same contracted EAN"
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, OTHER_ASSET_ID)))

    then: "the displaced handler is undeployed instead of being left with no key to stop it by"
    original.undeployCount == 1
    createdHandlers.size() == 2
    createdHandlers[1].assetId == OTHER_ASSET_ID
  }

  def "a redispatch poller started for an EAN already in use stops the one it displaces"() {
    given: "a running poller for the EAN"
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    def original = createdRedispatchHandlers[0]

    when: "a second asset is created with the same contracted EAN and redispatch enabled"
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, OTHER_ASSET_ID, true)))

    then: "the displaced poller is stopped and its client closed"
    original.stopCount == 1
    createdRedispatchHandlers.size() == 2
    createdRedispatchHandlers[1].assetId == OTHER_ASSET_ID
  }

  def "stop undeploys every deployed handler"() {
    given: "handlers for two assets, one of them polling redispatch"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(EAN, ASSET_ID, true)))
    service.processAssetChange(
            event(PersistenceEvent.Cause.CREATE, gopacsAsset(OTHER_EAN, OTHER_ASSET_ID)))

    when:
    service.stop(null)

    then:
    createdHandlers.size() == 2
    createdHandlers.every { it.undeployCount == 1 }
    createdRedispatchHandlers.size() == 1
    createdRedispatchHandlers[0].stopCount == 1
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

  // Records startPolling()/stopPolling() calls instead of scheduling polls or closing the client.
  static class RecordingRedispatchHandler extends GOPACSRedispatchHandler {
    int startCount = 0
    int stopCount = 0

    RecordingRedispatchHandler(
    String contractedEan, String realm, String assetId, Container container) {
      super(contractedEan, realm, assetId, container)
    }

    @Override
    void startPolling() {
      startCount++
    }

    @Override
    void stopPolling() {
      stopCount++
    }
  }
}
