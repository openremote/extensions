/*
 * Copyright 2025, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.extension.hawkbit.model.firmware;

import org.openremote.model.util.TsIgnore;
import org.openremote.model.value.MetaItemDescriptor;
import org.openremote.model.value.ValueType;

@TsIgnore
public final class FirmwareMetaItemType {

    private FirmwareMetaItemType() {
    }

    /**
     * Can be used on a {@link ValueType#TEXT} attribute to indicate that the parent asset should be synced as a
     * firmware target to hawkBit, enabling firmware updates and DDI interactions. The attribute value will be
     * updated with target details from hawkBit.
     */
    public static final MetaItemDescriptor<Boolean> FIRMWARE_TARGET = new MetaItemDescriptor<>(
            "firmwareTarget", ValueType.BOOLEAN);

    /**
     * Can be used on any attribute to indicate that this attribute should be synced as metadata to the corresponding
     * target in hawkBit, enabling hawkBit target filters on specific metadata values.
     * Requires the parent asset to be synced to hawkBit via the firmwareTarget metaItem.
     */
    public static final MetaItemDescriptor<Boolean> FIRMWARE_METADATA = new MetaItemDescriptor<>(
            "firmwareMetadata", ValueType.BOOLEAN);

}
