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
package org.openremote.extension.hawkbit.model;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.openremote.model.AssetModelProvider;
import org.openremote.model.asset.Asset;
import org.openremote.model.value.MetaItemDescriptor;

public class FirmwareModelProvider implements AssetModelProvider {

  @Override
  public boolean useAutoScan() {
    return false;
  }

  @Override
  public Map<String, Collection<MetaItemDescriptor<?>>> getMetaItemDescriptors() {
    Collection<MetaItemDescriptor<?>> descriptors =
        List.of(FirmwareMetaItemType.FIRMWARE_TARGET, FirmwareMetaItemType.FIRMWARE_METADATA);
    return Map.of(Asset.class.getSimpleName(), descriptors);
  }
}
