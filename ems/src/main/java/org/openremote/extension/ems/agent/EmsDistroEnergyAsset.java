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
package org.openremote.extension.ems.agent;

import jakarta.persistence.Entity;
import java.util.Optional;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

@Entity
public class EmsDistroEnergyAsset extends Asset<EmsDistroEnergyAsset> {

  public static final AttributeDescriptor<String> PORTFOLIO =
      new AttributeDescriptor<>("portfolio", ValueType.TEXT);

  public static final AssetDescriptor<EmsDistroEnergyAsset> DESCRIPTOR =
      new AssetDescriptor<>("transmission-tower", null, EmsDistroEnergyAsset.class);

  protected EmsDistroEnergyAsset() {}

  public EmsDistroEnergyAsset(String name) {
    super(name);
  }

  public Optional<String> getPortfolio() {
    return getAttributes().getValue(PORTFOLIO);
  }

  public void setPortfolio(String portfolio) {
    getAttributes().getOrCreate(PORTFOLIO).setValue(portfolio);
  }
}
