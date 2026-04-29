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
import org.openremote.model.value.AbstractNameValueHolder;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

import java.util.Optional;

import static org.openremote.model.Constants.*;

@Entity
public class EmsDayAheadAsset extends Asset<EmsDayAheadAsset> {

    public static final AttributeDescriptor<String> COLLECT_TIME_FORECASTS = new AttributeDescriptor<>("collectTimeForecasts", ValueType.TEXT);

    public static final AttributeDescriptor<String> LAST_UPDATE_FORECASTS = new AttributeDescriptor<>("lastUpdateForecasts", ValueType.TEXT,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    );

    public static final AttributeDescriptor<Double> TARIFF_EXPORT_DAY_AHEAD = new AttributeDescriptor<>("tariffExportDayAheadForecast", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits("EUR", UNITS_PER, UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Double> TARIFF_IMPORT_DAY_AHEAD = new AttributeDescriptor<>("tariffImportDayAheadForecast", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits("EUR", UNITS_PER, UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Boolean> USE_TARIFF_DAY_AHEAD_FORECASTS = new AttributeDescriptor<>("useTariffDayAheadForecasts", ValueType.BOOLEAN);


    public static final AssetDescriptor<EmsDayAheadAsset> DESCRIPTOR = new AssetDescriptor<>("calendar-clock", "000000", EmsDayAheadAsset.class);

    protected EmsDayAheadAsset() {
    }

    public EmsDayAheadAsset(String name) {
        super(name);
    }


    public Optional<String> getCollectTimeForecasts() {
        return getAttribute(COLLECT_TIME_FORECASTS).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<String> getLastUpdateForecasts() {
        return getAttribute(LAST_UPDATE_FORECASTS).flatMap(AbstractNameValueHolder::getValue);
    }

    public Optional<Long> getLastUpdateForecastsTimestamp() {
        return getAttribute(LAST_UPDATE_FORECASTS).flatMap(Attribute::getTimestamp);
    }

    public Optional<Boolean> getUseTariffDayAheadForecasts() {
        return getAttribute(USE_TARIFF_DAY_AHEAD_FORECASTS).flatMap(AbstractNameValueHolder::getValue);
    }


    public EmsDayAheadAsset setCollectTimeForecasts(String value) {
        getAttributes().getOrCreate(COLLECT_TIME_FORECASTS).setValue(value);
        return this;
    }

    public EmsDayAheadAsset setUseTariffDayAheadForecasts(Boolean value) {
        getAttributes().getOrCreate(USE_TARIFF_DAY_AHEAD_FORECASTS).setValue(value);
        return this;
    }

}
