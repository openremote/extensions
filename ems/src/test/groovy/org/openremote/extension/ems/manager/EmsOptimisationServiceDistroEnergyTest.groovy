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
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.model.Container
import org.openremote.model.PersistenceEvent
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
  EmsOptimisationService service

  def setupSpec() {
    // Populates the asset model registry (asset type -> attribute descriptors) from this
    // extension's own AssetModelProvider SPI registration, which real asset construction needs.
    // A plain Specification has no container to do this at startup, unlike production and unlike
    // ManagerContainerTrait-based tests.
    ValueUtil.initialise(null)
  }

  def setup() {
    def assetStorageService = Mock(AssetStorageService)
    assetStorageService.find(PARENT_ID, false, EmsEnergyOptimisationAsset.class) >>
            new EmsEnergyOptimisationAsset("parent")

    def handlerContainer = Stub(Container) {
      getConfig() >> [(DistroEnergyHandler.DISTRO_ENERGY_CLIENT_KEY): "test-key"]
      getService(TimerService) >> Stub(TimerService)
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
      getService(AssetPredictedDatapointService) >> Stub(AssetPredictedDatapointService)
    }

    createdHandlers = []

    service = new EmsOptimisationService()
    service.services = Services.builder().withAssetStorageService(assetStorageService).build()
    service.distroEnergyHandlerFactory = new DistroEnergyHandler.Factory(handlerContainer) {
              @Override
              DistroEnergyHandler createHandler(AttributeRef powerNetAttributeRef, String portfolio) {
                def handler =
                        new RecordingDistroEnergyHandler(powerNetAttributeRef, portfolio, handlerContainer)
                createdHandlers << handler
                return handler
              }
            }
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

  // Records deploy()/undeploy() calls instead of scheduling submissions or closing a real client.
  static class RecordingDistroEnergyHandler extends DistroEnergyHandler {
    int deployCount = 0
    int undeployCount = 0

    RecordingDistroEnergyHandler(AttributeRef powerNetAttributeRef, String portfolio, Container container) {
      super(powerNetAttributeRef, portfolio, container)
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
