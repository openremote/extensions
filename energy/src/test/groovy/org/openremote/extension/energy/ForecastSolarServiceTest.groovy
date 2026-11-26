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
package org.openremote.extension.energy

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.openremote.container.timer.TimerService
import org.openremote.container.web.WebTargetBuilder
import org.openremote.extension.energy.manager.EnergyOptimisationService
import org.openremote.extension.energy.manager.ForecastSolarService
import org.openremote.extension.energy.manager.ForecastWindService
import org.openremote.extension.energy.model.ElectricityProducerSolarAsset
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.manager.setup.SetupService
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeRef
import org.openremote.model.geo.GeoJSONPoint
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

import static org.openremote.extension.energy.manager.ForecastSolarService.OR_FORECAST_SOLAR_API_KEY

class ForecastSolarServiceTest extends Specification implements ManagerContainerTrait {

  private static void stubForecast(ClientRequestContext request) {
    if (request.uri.host != "api.forecast.solar") {
      return
    }

    def now = Instant.ofEpochMilli(TimerService.Clock.PSEUDO.currentTimeMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    def interval = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(now.minute.intdiv(15) * 15)
    def payload = ForecastSolarServiceTest.getResourceAsStream("/forecasts/forecast-solar.json").withCloseable {
      new JsonSlurper().parse(it)
    }

    // The sample starts with zero output. Move that point before the current interval.
    def formatter = ForecastSolarService.ISO_LOCAL_DATE_TIME_WITHOUT_T
    def sampleStart = LocalDateTime.parse(payload.result.watts.keySet().min(), formatter)
    def forecastStart = interval.minusMinutes(30)
    def shift = Duration.between(sampleStart, forecastStart)
    ["watts", "watt_hours"].each { field ->
      payload.result[field] = payload.result[field].collectEntries { timestamp, value ->
        def shifted = LocalDateTime.parse(timestamp, formatter).plus(shift).format(formatter)
        [(shifted): value]
      }
    }
    long dayShift = ChronoUnit.DAYS.between(sampleStart.toLocalDate(), forecastStart.toLocalDate())
    payload.result.watt_hours_day = payload.result.watt_hours_day.collectEntries { date, value ->
      def shifted = LocalDate.parse(date).plusDays(dayShift)
      [(shifted.toString()): value]
    }

    def body = JsonOutput.toJson(payload)
    request.abortWith(Response.ok(body, MediaType.APPLICATION_JSON_TYPE).build())
  }

  def "Test adding and removing asset with enabled attributes"() {
    given: "the container environment is started"
    def conditions = new PollingConditions(timeout: 10, delay: 0.2)
    def config = defaultConfig()
    config << [(OR_FORECAST_SOLAR_API_KEY): "test-key"]
    // A container restart closes the shared JAX-RS client, so stop it before installing the stub.
    stopContainer()
    WebTargetBuilder.getClient().register({ ClientRequestContext request ->
      stubForecast(request)
    } as ClientRequestFilter)

    def services = defaultServices().findAll {
      !(it instanceof EnergyOptimisationService) && !(it instanceof ForecastWindService)
    }
    def container = startContainer(config, services)
    def managerTestSetup = container.getService(SetupService.class).getTaskOfType(ManagerTestSetup.class)
    def assetStorageService = container.getService(AssetStorageService.class)
    def assetPredictedDatapointService = container.getService(AssetPredictedDatapointService.class)
    def assetProcessingService = container.getService(AssetProcessingService.class)

    expect: "the asset should have filled in value for power and powerForecast"
    conditions.eventually {
      def solarAsset = assetStorageService.find(managerTestSetup.electricitySolarAssetId)
      assert solarAsset.getAttribute(ElectricityProducerSolarAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert solarAsset.getAttribute(ElectricityProducerSolarAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(solarAsset.getId(), ElectricityProducerSolarAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(solarAsset.getId(), ElectricityProducerSolarAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset is added with includeForecastSolarService set to true"
    def newSolarAsset = new ElectricityProducerSolarAsset("SolarAsset")
    newSolarAsset.setParentId(managerTestSetup.electricityOptimisationAssetId)
    newSolarAsset.setRealm(managerTestSetup.realmEnergyName)
    newSolarAsset.setPanelAzimuth(0)
    newSolarAsset.setPanelPitch(30)
    newSolarAsset.setEfficiencyExport(100)
    newSolarAsset.setPowerExportMax(2.5)
    newSolarAsset.setLocation(new GeoJSONPoint(9.195295, 48.787418))
    newSolarAsset.setSetActualSolarValueWithForecast(true)
    newSolarAsset.setIncludeForecastSolarService(true)
    newSolarAsset = assetStorageService.merge(newSolarAsset)

    then: "the asset attributes and datapoints should be updated"
    conditions.eventually {
      newSolarAsset = assetStorageService.find(newSolarAsset.getId())
      assert newSolarAsset.getAttribute(ElectricityProducerSolarAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert newSolarAsset.getAttribute(ElectricityProducerSolarAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newSolarAsset.getId(), ElectricityProducerSolarAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newSolarAsset.getId(), ElectricityProducerSolarAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset is added with includeForecastSolarService set to false"
    def newSolarAsset2 = new ElectricityProducerSolarAsset("SolarAsset2")
    newSolarAsset2.setParentId(managerTestSetup.electricityOptimisationAssetId)
    newSolarAsset2.setRealm(managerTestSetup.realmEnergyName)
    newSolarAsset2.setPanelAzimuth(0)
    newSolarAsset2.setPanelPitch(30)
    newSolarAsset2.setEfficiencyExport(100)
    newSolarAsset2.setPowerExportMax(2.5)
    newSolarAsset2.setLocation(new GeoJSONPoint(9.195275, 48.787418))
    newSolarAsset2.setSetActualSolarValueWithForecast(false)
    newSolarAsset2.setIncludeForecastSolarService(false)
    newSolarAsset2 = assetStorageService.merge(newSolarAsset2)

    and: "an asset updated its includeForecastSolarService to true"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newSolarAsset2.getId(), ElectricityProducerSolarAsset.INCLUDE_FORECAST_SOLAR_SERVICE.name, true))

    then: "the asset attributes and datapoints should be updated"
    conditions.eventually {
      newSolarAsset2 = assetStorageService.find(newSolarAsset2.getId())
      assert newSolarAsset2.getAttribute(ElectricityProducerSolarAsset.POWER).flatMap {
        it.value
      }.orElse(0d) == 0d
      assert newSolarAsset2.getAttribute(ElectricityProducerSolarAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newSolarAsset2.getId(), ElectricityProducerSolarAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newSolarAsset2.getId(), ElectricityProducerSolarAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset updated it's setActualValueWithForecast to true"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newSolarAsset2.getId(), ElectricityProducerSolarAsset.SET_ACTUAL_SOLAR_VALUE_WITH_FORECAST.name, true))

    then: "it power should be updated too"
    conditions.eventually {
      newSolarAsset2 = assetStorageService.find(newSolarAsset2.getId())
      assert newSolarAsset2.getAttribute(ElectricityProducerSolarAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
    }

    cleanup: "stop forecast tasks and close the client with the test filter"
    stopContainer()
  }
}
