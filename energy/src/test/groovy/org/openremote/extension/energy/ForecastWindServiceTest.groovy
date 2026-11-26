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
import jakarta.ws.rs.client.ClientRequestContext
import jakarta.ws.rs.client.ClientRequestFilter
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.openremote.container.web.WebTargetBuilder
import org.openremote.extension.energy.manager.EnergyOptimisationService
import org.openremote.extension.energy.manager.ForecastSolarService
import org.openremote.extension.energy.manager.ForecastWindService
import org.openremote.extension.energy.model.ElectricityProducerWindAsset
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.datapoint.AssetPredictedDatapointService
import org.openremote.manager.setup.SetupService
import org.openremote.model.asset.Asset
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.attribute.AttributeRef
import org.openremote.model.geo.GeoJSONPoint
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.Instant
import java.time.temporal.ChronoUnit

import static org.openremote.extension.energy.manager.ForecastWindService.OR_OPEN_WEATHER_API_APP_ID

class ForecastWindServiceTest extends Specification implements ManagerContainerTrait {

  private static void stubForecast(ClientRequestContext request) {
    if (request.uri.host != "api.openweathermap.org" || request.uri.path != "/data/2.5/onecall") {
      return
    }

    def hour = Instant.now().truncatedTo(ChronoUnit.HOURS)
    def current = [dt: hour.epochSecond, wind_speed: 6.0]
    def hourly = [current, [dt: hour.plus(1, ChronoUnit.HOURS).epochSecond, wind_speed: 8.0]]
    def body = JsonOutput.toJson([current: current, hourly: hourly])
    request.abortWith(Response.ok(body, MediaType.APPLICATION_JSON_TYPE).build())
  }

  def "Test adding and removing asset with enabled attributes"() {
    given: "the container environment is started"
    def conditions = new PollingConditions(timeout: 10, delay: 0.2)
    def config = defaultConfig()
    config << [(OR_OPEN_WEATHER_API_APP_ID): "test-key"]
    // A container restart closes the shared JAX-RS client, so stop it before installing the stub.
    stopContainer()
    WebTargetBuilder.getClient().register({ ClientRequestContext request ->
      stubForecast(request)
    } as ClientRequestFilter)

    def services = defaultServices().findAll {
      !(it instanceof EnergyOptimisationService) && !(it instanceof ForecastSolarService)
    }
    def container = startContainer(config, services)
    def managerTestSetup = container.getService(SetupService.class).getTaskOfType(ManagerTestSetup.class)
    def assetStorageService = container.getService(AssetStorageService.class)
    def assetPredictedDatapointService = container.getService(AssetPredictedDatapointService.class)
    def assetProcessingService = container.getService(AssetProcessingService.class)
    def forecastWindService = container.getService(ForecastWindService.class)

    expect: "a Future for calculation should exist and the asset should have filled in value for power and powerForecast"
    conditions.eventually {
      assert !forecastWindService.calculationFutures.isEmpty()
      assert forecastWindService.calculationFutures.get(managerTestSetup.electricityWindAssetId) != null
      def windAsset = assetStorageService.find(managerTestSetup.electricityWindAssetId)
      assert windAsset.getAttribute(ElectricityProducerWindAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert windAsset.getAttribute(ElectricityProducerWindAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(windAsset.getId(), ElectricityProducerWindAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(windAsset.getId(), ElectricityProducerWindAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset is added with includeForecastWindService set to true"
    def newWindAsset = new ElectricityProducerWindAsset("WindAsset")
    newWindAsset.setParentId(managerTestSetup.electricityOptimisationAssetId)
    newWindAsset.setRealm(managerTestSetup.realmEnergyName)
    newWindAsset.setWindSpeedMax(18d)
    newWindAsset.setWindSpeedMin(2d)
    newWindAsset.setWindSpeedReference(12d)
    newWindAsset.setEfficiencyExport(100)
    newWindAsset.setPowerExportMax(2.5)
    newWindAsset.setLocation(new GeoJSONPoint(9.195295, 48.787418))
    newWindAsset.setSetActualWindValueWithForecast(true)
    newWindAsset.setIncludeForecastWindService(true)
    newWindAsset = assetStorageService.merge(newWindAsset)

    then: "the assetId should be present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset.getId()) != null
      newWindAsset = assetStorageService.find(newWindAsset.getId())
      assert newWindAsset.getAttribute(ElectricityProducerWindAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert newWindAsset.getAttribute(ElectricityProducerWindAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset.getId(), ElectricityProducerWindAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset.getId(), ElectricityProducerWindAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset is added with includeForecastWindService set to false"
    def newWindAsset2 = new ElectricityProducerWindAsset("WindAsset2")
    newWindAsset2.setParentId(managerTestSetup.electricityOptimisationAssetId)
    newWindAsset2.setRealm(managerTestSetup.realmEnergyName)
    newWindAsset2.setWindSpeedMax(18d)
    newWindAsset2.setWindSpeedMin(2d)
    newWindAsset2.setWindSpeedReference(12d)
    newWindAsset2.setEfficiencyExport(100)
    newWindAsset2.setPowerExportMax(2.5)
    newWindAsset2.setLocation(new GeoJSONPoint(9.195275, 48.787418))
    newWindAsset2.setSetActualWindValueWithForecast(false)
    newWindAsset2.setIncludeForecastWindService(false)
    newWindAsset2 = assetStorageService.merge(newWindAsset2)

    then: "the assetId shouldn't be present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset2.getId()) == null
    }

    when: "an asset updated it's includeForecastWindService to true"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newWindAsset2.getId(), ElectricityProducerWindAsset.INCLUDE_FORECAST_WIND_SERVICE.name, true))

    then: "it should be present present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset2.getId()) != null
      newWindAsset2 = assetStorageService.find(newWindAsset2.getId())
      assert newWindAsset2.getAttribute(ElectricityProducerWindAsset.POWER).flatMap {
        it.value
      }.orElse(0d) == 0d
      assert newWindAsset2.getAttribute(ElectricityProducerWindAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset2.getId(), ElectricityProducerWindAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset2.getId(), ElectricityProducerWindAsset.POWER_FORECAST.getName())).size() > 0
    }

    when: "an asset updated it's setActualValueWithForecast to true"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newWindAsset2.getId(), ElectricityProducerWindAsset.SET_ACTUAL_WIND_VALUE_WITH_FORECAST.name, true))

    then: "it power should be updated too"
    conditions.eventually {
      newWindAsset2 = assetStorageService.find(newWindAsset2.getId())
      assert newWindAsset2.getAttribute(ElectricityProducerWindAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
    }

    when: "an asset updated it's includeForecastWindService to false"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newWindAsset2.getId(), ElectricityProducerWindAsset.INCLUDE_FORECAST_WIND_SERVICE.name, false))

    then: "it shouldn't be present present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset2.getId()) == null
    }

    when: "an asset is added with includeForecastWindService set to true but no location"
    def newWindAsset3 = new ElectricityProducerWindAsset("WindAsset3")
    newWindAsset3.setParentId(managerTestSetup.electricityOptimisationAssetId)
    newWindAsset3.setRealm(managerTestSetup.realmEnergyName)
    newWindAsset3.setWindSpeedMax(18d)
    newWindAsset3.setWindSpeedMin(2d)
    newWindAsset3.setWindSpeedReference(12d)
    newWindAsset3.setEfficiencyExport(100)
    newWindAsset3.setPowerExportMax(2.5)
    newWindAsset3.setSetActualWindValueWithForecast(true)
    newWindAsset3.setIncludeForecastWindService(true)
    newWindAsset3 = assetStorageService.merge(newWindAsset3)

    then: "the assetId shouldn't be present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset3.getId()) == null
    }

    when: "the asset location is set"
    def location = new GeoJSONPoint(9.195275, 48.787418)
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newWindAsset3.getId(), Asset.LOCATION.name, location))

    then: "the new location is persisted before forecasting is retriggered"
    conditions.eventually {
      assert assetStorageService.find(newWindAsset3.getId()).getLocation().isPresent()
    }

    when: "forecasting is retriggered by setting includeForecastWindService to true again"
    assetProcessingService.sendAttributeEvent(new AttributeEvent(newWindAsset3.getId(), ElectricityProducerWindAsset.INCLUDE_FORECAST_WIND_SERVICE.name, true))

    then: "it should be present present in the calculationFutures"
    conditions.eventually {
      assert forecastWindService.calculationFutures.get(newWindAsset3.getId()) != null
      newWindAsset3 = assetStorageService.find(newWindAsset3.getId())
      assert newWindAsset3.getAttribute(ElectricityProducerWindAsset.POWER).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert newWindAsset3.getAttribute(ElectricityProducerWindAsset.POWER_FORECAST).flatMap {
        it.value
      }.orElse(0d) != 0d
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset3.getId(), ElectricityProducerWindAsset.POWER.getName())).size() > 0
      assert assetPredictedDatapointService.getDatapoints(new AttributeRef(newWindAsset3.getId(), ElectricityProducerWindAsset.POWER_FORECAST.getName())).size() > 0
    }

    cleanup: "stop forecast tasks and close the client with the test filter"
    stopContainer()
  }
}
