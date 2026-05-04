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
}
