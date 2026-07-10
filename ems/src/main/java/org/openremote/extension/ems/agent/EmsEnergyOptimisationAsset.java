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
package org.openremote.extension.ems.agent;

import jakarta.persistence.Entity;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.*;

import java.util.Optional;

import static org.openremote.model.Constants.*;

@Entity
public class EmsEnergyOptimisationAsset extends Asset<EmsEnergyOptimisationAsset> {

    public static final AttributeDescriptor<String> ADVANCED_SETTINGS_ATTRIBUTES = new AttributeDescriptor<>("advancedSettingsAttributes", ValueType.TEXT,
            new MetaItem<>(MetaItemType.MULTILINE),
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Boolean> ENABLE_DETAILED_LOGGING = new AttributeDescriptor<>("enableDetailedLogging", ValueType.BOOLEAN);

    public static final AttributeDescriptor<Double> ENERGY_EXPORT_TOTAL = new AttributeDescriptor<>("energyExportTotal", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Double> ENERGY_IMPORT_TOTAL = new AttributeDescriptor<>("energyImportTotal", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Boolean> GENERATE_POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT = new AttributeDescriptor<>("generatePowerLimitMaximumProfileManualInput", ValueType.BOOLEAN);

    public static final AttributeDescriptor<Boolean> GENERATE_POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT = new AttributeDescriptor<>("generatePowerLimitMinimumProfileManualInput", ValueType.BOOLEAN);

    public static final AttributeDescriptor<Boolean> DISABLE_OPTIMISATION_SERVICE = new AttributeDescriptor<>("disableOptimisationService", ValueType.BOOLEAN);

    public enum OptimisationMethodValueType {
        None,
        EmsOptimisation,
        EmsOptimisationBeta,
    }

    public static final ValueDescriptor<OptimisationMethodValueType> OPTIMISATION_METHOD_VALUE_TYPE = new ValueDescriptor<>("optimisationMethodValueType", OptimisationMethodValueType.class);

    public static final AttributeDescriptor<OptimisationMethodValueType> OPTIMISATION_METHOD = new AttributeDescriptor<>("optimisationMethod", OPTIMISATION_METHOD_VALUE_TYPE
    );

    public static final AttributeDescriptor<Double> POWER_CONSUMPTION = new AttributeDescriptor<>("powerConsumption", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_FLEXIBLE = new AttributeDescriptor<>("powerFlexible", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_NET = new AttributeDescriptor<>("powerNet", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_PRODUCTION = new AttributeDescriptor<>("powerProduction", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MAXIMUM_INPUT = new AttributeDescriptor<>("powerLimitMaximumInput", ValueType.NUMBER
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MAXIMUM_PROFILE_MANUAL = new AttributeDescriptor<>("powerLimitMaximumProfileManual", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<String> POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT = new AttributeDescriptor<>("powerLimitMaximumProfileManualInput", ValueType.TEXT,
            new MetaItem<>(MetaItemType.MULTILINE)
    );

    public static final AttributeDescriptor<Double> POWER_LIMIT_MAXIMUM_PROFILE_TOTAL = new AttributeDescriptor<>("powerLimitMaximumProfileTotal", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MINIMUM_INPUT = new AttributeDescriptor<>("powerLimitMinimumInput", ValueType.NUMBER
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MINIMUM_PROFILE_MANUAL = new AttributeDescriptor<>("powerLimitMinimumProfileManual", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<String> POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT = new AttributeDescriptor<>("powerLimitMinimumProfileManualInput", ValueType.TEXT,
            new MetaItem<>(MetaItemType.MULTILINE)
    );

    public static final AttributeDescriptor<Double> POWER_LIMIT_MINIMUM_PROFILE_TOTAL = new AttributeDescriptor<>("powerLimitMinimumProfileTotal", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> TARIFF_EXPORT = new AttributeDescriptor<>("tariffExport", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits("EUR", UNITS_PER, UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Double> TARIFF_IMPORT = new AttributeDescriptor<>("tariffImport", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.RULE_STATE),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits("EUR", UNITS_PER, UNITS_KILO, UNITS_WATT, UNITS_HOUR);


    public static final AssetDescriptor<EmsEnergyOptimisationAsset> DESCRIPTOR = new AssetDescriptor<>("flash", "C4DB0D", EmsEnergyOptimisationAsset.class);

    protected EmsEnergyOptimisationAsset() {
    }

    public EmsEnergyOptimisationAsset(String name) {
        super(name);
    }

    public Optional<String> getAdvancedSettingsAttributes() {
        return getAttribute(ADVANCED_SETTINGS_ATTRIBUTES).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Boolean> getEnableDetailedLogging() {
        return getAttribute(ENABLE_DETAILED_LOGGING).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Double> getEnergyExportTotal() {
        return getAttribute(ENERGY_EXPORT_TOTAL).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Double> getEnergyImportTotal() {
        return getAttribute(ENERGY_IMPORT_TOTAL).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Boolean> getOptimisationDisabled() {
        return getAttribute(DISABLE_OPTIMISATION_SERVICE).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<OptimisationMethodValueType> getOptimisationMethod() {
        return getAttribute(OPTIMISATION_METHOD).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Double> getPowerNet() {
        return getAttribute(POWER_NET).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Long> getPowerNetTimestamp() {
        return getAttribute(POWER_NET).flatMap(Attribute::getTimestamp);
    }

    public Optional<Double> getPowerLimitMaximumInput() {
        return getAttribute(POWER_LIMIT_MAXIMUM_INPUT).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<String> getPowerLimitMaximumProfileManualInput() {
        return getAttribute(POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Double> getPowerLimitMaximumProfileTotal() {
        return getAttribute(POWER_LIMIT_MAXIMUM_PROFILE_TOTAL).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Long> getPowerLimitMaximumProfileTotalTimestamp() {
        return getAttribute(POWER_LIMIT_MAXIMUM_PROFILE_TOTAL).flatMap(Attribute::getTimestamp);
    }

    public Optional<Double> getPowerLimitMinimumInput() {
        return getAttribute(POWER_LIMIT_MINIMUM_INPUT).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<String> getPowerLimitMinimumProfileManualInput() {
        return getAttribute(POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Double> getPowerLimitMinimumProfileTotal() {
        return getAttribute(POWER_LIMIT_MINIMUM_PROFILE_TOTAL).flatMap(AbstractNameValueHolder::getValue);
    }


    public EmsEnergyOptimisationAsset setOptimisationDisabled(Boolean value) {
        getAttributes().getOrCreate(DISABLE_OPTIMISATION_SERVICE).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setOptimisationMethod(OptimisationMethodValueType value) {
        getAttributes().getOrCreate(OPTIMISATION_METHOD).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMaximumInput(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MAXIMUM_INPUT).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMaximumProfileManual(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MAXIMUM_PROFILE_MANUAL).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMaximumProfileManualInput(String value) {
        getAttributes().getOrCreate(POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMaximumProfileTotal(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MAXIMUM_PROFILE_TOTAL).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMinimumInput(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MINIMUM_INPUT).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMinimumProfileManual(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MINIMUM_PROFILE_MANUAL).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMinimumProfileManualInput(String value) {
        getAttributes().getOrCreate(POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerLimitMinimumProfileTotal(Double value) {
        getAttributes().getOrCreate(POWER_LIMIT_MINIMUM_PROFILE_TOTAL).setValue(value);
        return this;
    }

    public EmsEnergyOptimisationAsset setPowerNet(Double value) {
        getAttributes().getOrCreate(POWER_NET).setValue(value);
        return this;
    }
}
