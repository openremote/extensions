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

import org.openremote.extension.energy.ManagerTestSetup
import org.openremote.extension.energy.model.EnergyOptimisationAsset
import org.openremote.manager.asset.AssetStorageService
import org.openremote.manager.setup.SetupService
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class EnergyServiceStartupTest extends Specification implements ManagerContainerTrait {

  def setupSpec() {
    def config = defaultConfig()
    // Keep the provider services loaded while ensuring that neither makes a network request.
    config.remove(ForecastSolarService.OR_FORECAST_SOLAR_API_KEY)
    config.remove(ForecastWindService.OR_OPEN_WEATHER_API_APP_ID)
    startContainer(config, defaultServices())
  }

  def cleanupSpec() {
    stopContainer()
  }

  def "manager startup runs the saved energy optimizer and persists its savings"() {
    given:
    def setup = container.getService(SetupService).getTaskOfType(ManagerTestSetup)
    def storage = container.getService(AssetStorageService)
    def conditions = new PollingConditions(timeout: 10, delay: 0.2)

    expect: "the startup run writes savings through the real attribute event route"
    conditions.eventually {
      def asset = storage.find(setup.electricityOptimisationAssetId) as EnergyOptimisationAsset
      assert asset.financialSaving.isPresent()
      assert asset.carbonSaving.isPresent()
    }
  }
}
