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
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset
import org.openremote.extension.ems.agent.EmsEnergyOptimisationAsset
import org.openremote.extension.ems.manager.distroenergy.DistroEnergyHandler
import org.openremote.extension.ems.manager.distroenergy.DistroGopacsHandler
import org.openremote.extension.ems.manager.distroenergy.DistroGopacsResource
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.model.Container
import org.openremote.model.PersistenceEvent
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeRef
import org.openremote.model.util.ValueUtil
import spock.lang.Specification

import java.util.concurrent.ScheduledExecutorService

/**
 * Exercises {@link EmsOptimisationService#processAssetChange} for {@link EmsDistroEnergyAsset}
 * persistence events, via a recording {@code DistroEnergyHandler} subclass -- the same pattern
 * {@code GOPACSHandlerTest} uses for {@code GOPACSHandler} -- rather than mocking the class.
 * {@code DistroEnergyHandler} builds a real {@code ResteasyClient} from its {@code Container} in
 * the constructor, so the fake {@code Container} just needs to be enough for that constructor to
 * succeed; {@code deploy()}/{@code undeploy()} are overridden so nothing is actually scheduled or
 * submitted.
 *
 * {@code distroEnergyHandlerMap} is keyed by asset id (it has no external routing key, and the
 * asset id survives a portfolio edit -- see the map's own comment). These tests catch stop calls
 * made with the wrong key: a wrong key makes {@code Map.remove} a silent no-op, so the old
 * handler is never undeployed and keeps submitting concurrently with its replacement.
 */
class EmsOptimisationServiceDistroEnergyTest extends Specification {

  static final String ASSET_ID = "distroAsset1"
  static final String PARENT_ID = "optimisationAsset1"

  List<RecordingDistroEnergyHandler> createdHandlers
  List<RecordingDistroGopacsHandler> createdGopacsHandlers
  Map<String, String> gopacsConfig
  Map<String, String> handlerConfig
  AssetStorageService assetStorageService
  EmsOptimisationService service

  def setupSpec() {
    // Populates the asset model registry (asset type -> attribute descriptors) from this
    // extension's own AssetModelProvider SPI registration, which real asset construction needs.
    // A plain Specification has no container to do this at startup, unlike production and unlike
    // ManagerContainerTrait-based tests.
    ValueUtil.initialise(null)
  }

  def setup() {
    assetStorageService = Mock(AssetStorageService)
    assetStorageService.find(PARENT_ID, false, EmsEnergyOptimisationAsset.class) >>
            new EmsEnergyOptimisationAsset("parent")
    // Saving a Distro asset looks for a GOPACS sibling to copy the announcement inputs from.
    assetStorageService.findAll(_) >> []

    handlerConfig = [(DistroEnergyHandler.DISTRO_ENERGY_CLIENT_KEY): "test-key"]
    def handlerContainer = Stub(Container) {
      getConfig() >> { handlerConfig }
      getService(TimerService) >> Stub(TimerService)
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
      getService(AssetPredictedDatapointService) >> Stub(AssetPredictedDatapointService)
      getService(AssetProcessingService) >> Stub(AssetProcessingService)
    }

    createdHandlers = []
    createdGopacsHandlers = []

    gopacsConfig = [(DistroGopacsHandler.DISTRO_GOPACS_TENANT_ID): "tenant"]
    def gopacsContainer = Stub(Container) {
      getConfig() >> { gopacsConfig }
      getService(TimerService) >> Stub(TimerService)
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
      getService(AssetStorageService) >> Stub(AssetStorageService)
      getService(AssetProcessingService) >> Stub(AssetProcessingService)
    }

    service = new EmsOptimisationService()
    service.services = Services.builder().withAssetStorageService(assetStorageService).build()
    service.distroEnergyHandlerFactory = new DistroEnergyHandler.Factory(handlerContainer) {
              @Override
              DistroEnergyHandler createHandler(
                      String assetId, AttributeRef powerNetAttributeRef, String portfolio) {
                def handler = new RecordingDistroEnergyHandler(
                        assetId, powerNetAttributeRef, portfolio, handlerContainer)
                createdHandlers << handler
                return handler
              }
            }
    def gopacsResource = Stub(DistroGopacsResource)
    service.distroGopacsHandlerFactory = new DistroGopacsHandler.Factory(gopacsContainer) {
              @Override
              DistroGopacsHandler createHandler(String assetId) {
                def handler = new RecordingDistroGopacsHandler(assetId, gopacsContainer, gopacsResource)
                createdGopacsHandlers << handler
                return handler
              }
            }
  }

  private static EmsDistroEnergyAsset gopacsAsset(Boolean enabled) {
    return new EmsDistroEnergyAsset("distro").setId(ASSET_ID).setParentId(PARENT_ID)
            .setGopacsEnabled(enabled)
  }

  private static EmsDistroEnergyAsset distroAsset(String portfolio = "portfolio-a") {
    def asset = new EmsDistroEnergyAsset("distro").setId(ASSET_ID).setParentId(PARENT_ID)
    asset.setPortfolio(portfolio)
    return asset
  }

  private static PersistenceEvent<EmsDistroEnergyAsset> event(
          PersistenceEvent.Cause cause, EmsDistroEnergyAsset asset) {
    return new PersistenceEvent<>(cause, asset, null, null, null)
  }

  def "CREATE deploys a handler for the asset"() {
    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distroAsset()))

    then:
    createdHandlers.size() == 1
    createdHandlers[0].deployCount == 1
  }

  def "DELETE undeploys the handler keyed by asset id, not portfolio"() {
    given: "a deployed handler"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distroAsset()))
    def original = createdHandlers[0]

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.DELETE, distroAsset()))

    then: "the handler for this asset id is undeployed rather than leaked"
    original.undeployCount == 1
  }

  def "UPDATE undeploys the old handler before deploying its replacement"() {
    given: "a deployed handler for the original portfolio"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distroAsset("portfolio-a")))
    def original = createdHandlers[0]

    when: "the portfolio changes"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, distroAsset("portfolio-b")))

    then: "the old handler is stopped and exactly one new handler replaces it"
    original.undeployCount == 1
    createdHandlers.size() == 2
    createdHandlers[1].deployCount == 1
  }

  def "config the handler rejects is caught by the service rather than failing it: #label"() {
    given:
    handlerConfig[key] = value

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distroAsset()))

    then: "the constructor throws before a handler exists, and the service keeps going"
    noExceptionThrown()
    createdHandlers.isEmpty()

    where:
    label | key | value
    "blank client key" | DistroEnergyHandler.DISTRO_ENERGY_CLIENT_KEY | "  "
    "zero interval" | DistroEnergyHandler.DISTRO_ENERGY_REQUEST_INTERVAL | "0"
    "negative interval" | DistroEnergyHandler.DISTRO_ENERGY_REQUEST_INTERVAL | "-5"
    "non-numeric interval" | DistroEnergyHandler.DISTRO_ENERGY_REQUEST_INTERVAL | "hourly"
  }

  def "the GOPACS order handler runs while gopacsEnabled is true, independent of day-ahead"() {
    when: "a GOPACS-only asset without a portfolio is created"
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(true)))

    then: "only the GOPACS handler is deployed"
    createdHandlers.isEmpty()
    createdGopacsHandlers.size() == 1
    createdGopacsHandlers[0].deployCount == 1

    when: "the asset is saved again while still enabled"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(true)))

    then: "the running handler is left alone"
    createdGopacsHandlers.size() == 1
    createdGopacsHandlers[0].undeployCalls.isEmpty()

    when: "it is disabled"
    service.processAssetChange(event(PersistenceEvent.Cause.UPDATE, gopacsAsset(false)))

    then: "it is stopped for good, cancelling its open orders"
    createdGopacsHandlers[0].undeployCalls == [true]
  }

  def "deleting the asset undeploys its GOPACS order handler"() {
    given:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(true)))

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.DELETE, gopacsAsset(true)))

    then: "it is stopped for good, cancelling its open orders"
    createdGopacsHandlers[0].undeployCalls == [true]
  }

  def "stopping the service leaves the open orders for the next start"() {
    given:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(true)))

    when:
    service.stop(null)

    then:
    createdGopacsHandlers[0].undeployCalls == [false]
  }

  def "only the GOPACS order inputs are routed to the handler, not its own writes"() {
    given:
    def asset = gopacsAsset(true)
    assetStorageService.find(ASSET_ID) >> asset
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, asset))

    when:
    [
      EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER,
      EmsDistroEnergyAsset.GOPACS_ORDERS,
      EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY,
      EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY
    ].each {
      service.processAttributeEventEmsDistroEnergyAsset(new AttributeEvent(ASSET_ID, it.name, null))
    }

    then:
    createdGopacsHandlers[0].events == [EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name]
  }

  def "a missing GOPACS tenant is caught by the service rather than failing it"() {
    given:
    gopacsConfig.clear()

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacsAsset(true)))

    then:
    noExceptionThrown()
    createdGopacsHandlers.isEmpty()
  }

  // Records deploy()/undeploy() calls instead of polling Distro.
  static class RecordingDistroGopacsHandler extends DistroGopacsHandler {
    int deployCount = 0
    List<Boolean> undeployCalls = []
    List<String> events = []

    RecordingDistroGopacsHandler(
    String assetId, Container container, DistroGopacsResource resource) {
      super(assetId, container, resource)
    }

    @Override
    void deploy() {
      deployCount++
    }

    @Override
    void undeploy(boolean cancelOpenOrders) {
      undeployCalls << cancelOpenOrders
    }

    @Override
    void onAttributeEvent(AttributeEvent event) {
      events << event.name
    }
  }

  // Records deploy()/undeploy() calls instead of scheduling submissions or closing a real client.
  static class RecordingDistroEnergyHandler extends DistroEnergyHandler {
    int deployCount = 0
    int undeployCount = 0

    RecordingDistroEnergyHandler(
    String assetId, AttributeRef powerNetAttributeRef, String portfolio, Container container) {
      super(assetId, powerNetAttributeRef, portfolio, container)
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
