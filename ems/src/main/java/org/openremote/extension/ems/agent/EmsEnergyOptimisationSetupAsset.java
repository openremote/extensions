/*
 * Copyright 2025, OpenRemote Inc.
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
package org.openremote.extension.ems.agent;

import jakarta.persistence.Entity;
import java.util.Optional;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

@Entity
public class EmsEnergyOptimisationSetupAsset extends Asset<EmsEnergyOptimisationSetupAsset> {
  public static final AttributeDescriptor<Boolean> CREATE_ENERGY_MANAGEMENT_SYSTEM =
      new AttributeDescriptor<>("createEnergyManagementSystem", ValueType.BOOLEAN);

  public static final AttributeDescriptor<String> ENERGY_MANAGEMENT_SYSTEM_NAME =
      new AttributeDescriptor<>("energyManagementSystemName", ValueType.TEXT);

  public static final AttributeDescriptor<Boolean> INCLUDE_DAY_AHEAD_FORECASTS =
      new AttributeDescriptor<>("includeDayAheadForecasts", ValueType.BOOLEAN);

  public static final AttributeDescriptor<Boolean> INCLUDE_GOPACS =
      new AttributeDescriptor<>("includeGOPACS", ValueType.BOOLEAN);

  public static final AttributeDescriptor<String> INFO_FIELD =
      new AttributeDescriptor<>(
          "infoField",
          ValueType.TEXT,
          new MetaItem<>(MetaItemType.MULTILINE),
          new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AssetDescriptor<EmsEnergyOptimisationSetupAsset> DESCRIPTOR =
      new AssetDescriptor<>(
          "application-cog-outline", "000000", EmsEnergyOptimisationSetupAsset.class);

  protected EmsEnergyOptimisationSetupAsset() {}

  public EmsEnergyOptimisationSetupAsset(String name) {
    super(name);
  }

  public Optional<Boolean> getIncludeDayAheadForecasts() {
    return getAttributes().getValue(INCLUDE_DAY_AHEAD_FORECASTS);
  }

  public Optional<Boolean> getIncludeGopacs() {
    return getAttributes().getValue(INCLUDE_GOPACS);
  }

  public Optional<String> getEnergyManagementSystemName() {
    return getAttributes().getValue(ENERGY_MANAGEMENT_SYSTEM_NAME);
  }
}
