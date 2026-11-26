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
package org.openremote.extension.energy.manager

import org.apache.camel.impl.DefaultCamelContext
import org.openremote.container.message.MessageBrokerService
import org.openremote.container.timer.TimerService
import org.openremote.extension.energy.model.ElectricityProducerSolarAsset
import org.openremote.extension.energy.model.ElectricityProducerWindAsset
import org.openremote.extension.energy.model.EnergyOptimisationAsset
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.event.ClientEventService
import org.openremote.model.Container
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.geo.GeoJSONPoint
import org.openremote.model.util.ValueUtil
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class EnergyServiceStartupTest extends Specification {

  static final long NOW = Instant.parse("2026-01-05T12:15:00Z").toEpochMilli()

  Map<String, String> config = [:]
  AssetStorageService storage = Mock()
  ClientEventService clientEvents = Mock()
  DefaultCamelContext context = Mock()
  ExecutorService executor = Mock()
  ScheduledExecutorService scheduler = Mock()
  ScheduledFuture<?> job = Stub()
  Container container

  def setupSpec() {
    ValueUtil.initialise(null)
  }

  def setup() {
    def broker = Stub(MessageBrokerService) {
      getContext() >> context
    }
    def timer = Stub(TimerService) {
      getCurrentTimeMillis() >> NOW
    }
    container = Mock(Container) {
      getConfig() >> config
      getService(AssetStorageService) >> storage
      getService(ClientEventService) >> clientEvents
      getService(MessageBrokerService) >> broker
      getService(TimerService) >> timer
      getExecutor() >> executor
      getScheduledExecutor() >> scheduler
    }
  }

  def "optimization registers consumers first and starts each saved optimizer only once"() {
    given:
    def service = new EnergyOptimisationService()
    def enabled = new EnergyOptimisationAsset("Enabled").setId("enabled")
    def disabled = new EnergyOptimisationAsset("Disabled").setId("disabled")
    disabled.setOptimisationDisabled(true)
    service.init(container)

    when: "the manager registers consumers"
    service.start(container)

    then:
    1 * context.addRoutes(service)
    1 * clientEvents.addSubscription(AttributeEvent, null, _)
    0 * storage._
    0 * scheduler._
    0 * executor._

    when: "the producer hook loads the same saved assets twice"
    service.startProducers(container)
    service.startProducers(container)

    then:
    2 * storage.findAll(_) >> [enabled, disabled]
    1 * scheduler.scheduleAtFixedRate(_ as Runnable, _ as Long, 900L, TimeUnit.SECONDS) >> job
    1 * executor.execute(_ as Runnable)
    service.assetOptimisationInstanceMap.keySet() == [enabled.id] as Set
  }

  def "solar publishes the initial forecast in the producer phase before scheduling refreshes"() {
    given:
    config[ForecastSolarService.OR_FORECAST_SOLAR_API_KEY] = "test-key"
    def service = Spy(ForecastSolarService)
    def solar = new ElectricityProducerSolarAsset("Solar").setId("solar")
            .setIncludeForecastSolarService(true)
    service.init(container)

    when:
    service.start(container)

    then:
    1 * context.addRoutes(service)
    1 * clientEvents.addSubscription(AttributeEvent, _, _)
    0 * storage._
    0 * scheduler._

    when:
    service.startProducers(container)

    then:
    1 * storage.findAll(_) >> [solar]
    1 * service.getSolarForecast(solar) >> { }

    then:
    1 * service.updateSolarForecastAttribute(solar) >> { }

    then:
    1 * scheduler.scheduleAtFixedRate(_ as Runnable, 1L, 1L, TimeUnit.MINUTES) >> job
  }

  def "wind schedules each enabled saved asset only once, in the producer phase"() {
    given:
    config[ForecastWindService.OR_OPEN_WEATHER_API_APP_ID] = "test-key"
    def service = new ForecastWindService()
    def wind = new ElectricityProducerWindAsset("Wind").setId("wind")
            .setIncludeForecastWindService(true)
            .setLocation(new GeoJSONPoint(5.0, 52.0))
    service.init(container)

    when:
    service.start(container)

    then:
    1 * context.addRoutes(service)
    1 * clientEvents.addSubscription(AttributeEvent, null, _)
    0 * storage._
    0 * scheduler._

    when:
    service.startProducers(container)
    service.startProducers(container)

    then:
    2 * storage.findAll(_) >> [wind]
    1 * scheduler.scheduleAtFixedRate(_ as Runnable, 0L, 1L, TimeUnit.HOURS) >> job
    service.calculationFutures.keySet() == [wind.id] as Set
  }

  def "#serviceType.simpleName leaves saved forecasts alone when credentials are missing"() {
    given:
    def service = serviceType.getConstructor().newInstance()
    service.init(container)

    when:
    service.start(container)
    service.startProducers(container)

    then:
    0 * context._
    0 * clientEvents._
    0 * storage._
    0 * scheduler._

    where:
    serviceType << [ForecastSolarService, ForecastWindService]
  }
}
