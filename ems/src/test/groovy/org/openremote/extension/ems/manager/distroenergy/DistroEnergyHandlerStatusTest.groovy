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

import org.openremote.container.timer.TimerService
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.model.Container
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeRef
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ScheduledExecutorService

/**
 * Checks the status attributes a submission run writes to the Distro Energy asset. The HTTP side
 * is bypassed by overriding {@link DistroEnergyHandler#submitDayAheadForecast} to report a horizon
 * of a fixed number of days, so only the bookkeeping around the loop is under test.
 */
class DistroEnergyHandlerStatusTest extends Specification {

  static final String ASSET_ID = "distroAsset1"
  static final long NOW = Instant.parse("2026-09-16T10:00:00Z").toEpochMilli()

  AssetProcessingService assetProcessingService
  Container container
  FixedHorizonHandler handler

  def setup() {
    assetProcessingService = Mock(AssetProcessingService)
    def timerService = Stub(TimerService) {
      getCurrentTimeMillis() >> NOW
      getNow() >> Instant.ofEpochMilli(NOW)
    }
    container = Stub(Container) {
      getConfig() >> [(DistroEnergyHandler.DISTRO_ENERGY_CLIENT_KEY): "test-key"]
      getService(TimerService) >> timerService
      getScheduledExecutor() >> Stub(ScheduledExecutorService)
      getService(AssetPredictedDatapointService) >> Stub(AssetPredictedDatapointService)
      getService(AssetProcessingService) >> assetProcessingService
    }
  }

  def cleanup() {
    // Closes the real ResteasyClient the constructor built.
    handler?.undeploy()
  }

  def "a run that submitted days writes daysSubmitted and lastSubmission"() {
    given: "a handler whose forecast horizon is two market days"
    handler = new FixedHorizonHandler(2, container)

    when:
    handler.submitDayAheadForecasts()

    then:
    1 * assetProcessingService.sendAttributeEvent({ AttributeEvent e ->
      e.id == ASSET_ID && e.name == EmsDistroEnergyAsset.DAYS_SUBMITTED.name && e.value.get() == 2
    }, _)
    1 * assetProcessingService.sendAttributeEvent({ AttributeEvent e ->
      e.id == ASSET_ID && e.name == EmsDistroEnergyAsset.LAST_SUBMISSION.name && e.value.get() == NOW
    }, _)
    0 * assetProcessingService._
  }

  def "a run that found no forecast writes daysSubmitted 0 and leaves lastSubmission alone"() {
    given: "a handler whose forecast horizon is empty"
    handler = new FixedHorizonHandler(0, container)

    when:
    handler.submitDayAheadForecasts()

    then:
    1 * assetProcessingService.sendAttributeEvent({ AttributeEvent e ->
      e.name == EmsDistroEnergyAsset.DAYS_SUBMITTED.name && e.value.get() == 0
    }, _)
    0 * assetProcessingService._
  }

  // Reports a forecast covering exactly `horizonDays` market days without touching the API.
  static class FixedHorizonHandler extends DistroEnergyHandler {
    final int horizonDays
    int calls = 0

    FixedHorizonHandler(int horizonDays, Container container) {
      super(ASSET_ID, new AttributeRef("parent", "powerNet"), "portfolio-a", container)
      this.horizonDays = horizonDays
    }

    @Override
    protected boolean submitDayAheadForecast(LocalDate marketDate) {
      return calls++ <horizonDays
    }
  }
}
