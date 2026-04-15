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
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

import org.openremote.model.value.ValueType.ObjectMap;

import java.util.Optional;

import static org.openremote.model.Constants.*;

@Entity
public class EmsGOPACSAsset extends Asset<EmsGOPACSAsset> {

    // --- UFTP / Day-Ahead attributes ---

    public static final AttributeDescriptor<String> CONTRACTED_EAN = new AttributeDescriptor<>("contractedEAN", ValueType.TEXT
    );

    public static final AttributeDescriptor<Double> CURRENT_POWER_FLEX_REQUEST = new AttributeDescriptor<>("currentPowerFlexRequest", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MAXIMUM_PROFILE_FLEX_ORDER = new AttributeDescriptor<>("powerLimitMaximumProfileFlexOrder", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_LIMIT_MINIMUM_PROFILE_FLEX_ORDER = new AttributeDescriptor<>("powerLimitMinimumProfileFlexOrder", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_MAXIMUM_FLEX_REQUEST = new AttributeDescriptor<>("powerMaximumFlexRequest", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> POWER_MINIMUM_FLEX_REQUEST = new AttributeDescriptor<>("powerMinimumFlexRequest", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    // --- Redispatch configuration attributes ---

    public static final AttributeDescriptor<Boolean> REDISPATCH_ENABLED = new AttributeDescriptor<>("redispatchEnabled", ValueType.BOOLEAN
    );

    // --- Redispatch status attributes (read-only, system-updated) ---

    public static final AttributeDescriptor<String> REDISPATCH_ANNOUNCEMENT_ID = new AttributeDescriptor<>("redispatchAnnouncementId", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<String> REDISPATCH_COMPLIANCE_TYPE = new AttributeDescriptor<>("redispatchComplianceType", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<String> REDISPATCH_ANNOUNCEMENT_MESSAGE = new AttributeDescriptor<>("redispatchAnnouncementMessage", ValueType.TEXT,
            new MetaItem<>(MetaItemType.MULTILINE),
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Long> REDISPATCH_START_TIME = new AttributeDescriptor<>("redispatchStartTime", ValueType.TIMESTAMP,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Long> REDISPATCH_END_TIME = new AttributeDescriptor<>("redispatchEndTime", ValueType.TIMESTAMP,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Long> REDISPATCH_BID_VALIDITY_END = new AttributeDescriptor<>("redispatchBidValidityEnd", ValueType.TIMESTAMP,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Double> REDISPATCH_REQUESTED_POWER = new AttributeDescriptor<>("redispatchRequestedPower", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<String> REDISPATCH_EAN_EFFECTIVITY = new AttributeDescriptor<>("redispatchEanEffectivity", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<String> REDISPATCH_REQUEST_AREA_BUY = new AttributeDescriptor<>("redispatchRequestAreaBuy", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<String> REDISPATCH_REQUEST_AREA_SELL = new AttributeDescriptor<>("redispatchRequestAreaSell", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    public static final AttributeDescriptor<Long> REDISPATCH_LAST_POLL = new AttributeDescriptor<>("redispatchLastPoll", ValueType.TIMESTAMP,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    // --- Redispatch bid attributes (auto-calculated, operator-overridable) ---

    public static final AttributeDescriptor<Double> REDISPATCH_SUGGESTED_POWER = new AttributeDescriptor<>("redispatchSuggestedPower", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 7),
            new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    ).withUnits(UNITS_KILO, UNITS_WATT);

    public static final AttributeDescriptor<Double> REDISPATCH_SUGGESTED_VOLUME = new AttributeDescriptor<>("redispatchSuggestedVolume", ValueType.NUMBER,
            new MetaItem<>(MetaItemType.READ_ONLY)
    ).withUnits(UNITS_KILO, UNITS_WATT, UNITS_HOUR);

    public static final AttributeDescriptor<Double> REDISPATCH_BID_PRICE = new AttributeDescriptor<>("redispatchBidPrice", ValueType.NUMBER
    ).withUnits("EUR", UNITS_PER, UNITS_MEGA, UNITS_WATT, UNITS_HOUR);

    // --- Redispatch confirmation workflow ---

    public static final AttributeDescriptor<Boolean> REDISPATCH_CONFIRM_BID = new AttributeDescriptor<>("redispatchConfirmBid", ValueType.BOOLEAN
    );

    public static final AttributeDescriptor<String> REDISPATCH_BID_STATUS = new AttributeDescriptor<>("redispatchBidStatus", ValueType.TEXT,
            new MetaItem<>(MetaItemType.READ_ONLY)
    );

    // --- Redispatch history (stored as data points for time-series history) ---

    public static final AttributeDescriptor<ObjectMap> REDISPATCH_ANNOUNCEMENT_HISTORY = new AttributeDescriptor<>("redispatchAnnouncementHistory", ValueType.JSON_OBJECT,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 90),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    );

    public static final AttributeDescriptor<ObjectMap> REDISPATCH_BID_HISTORY = new AttributeDescriptor<>("redispatchBidHistory", ValueType.JSON_OBJECT,
            new MetaItem<>(MetaItemType.DATA_POINTS_MAX_AGE_DAYS, 90),
            new MetaItem<>(MetaItemType.READ_ONLY),
            new MetaItem<>(MetaItemType.STORE_DATA_POINTS)
    );

    public static final AssetDescriptor<EmsGOPACSAsset> DESCRIPTOR = new AssetDescriptor<>("transmission-tower", null, EmsGOPACSAsset.class);

    protected EmsGOPACSAsset() {
    }

    public EmsGOPACSAsset(String name) {
        super(name);
    }

    // --- UFTP getters ---

    public Optional<String> getContractedEan() {
        return getAttributes().getValue(CONTRACTED_EAN);
    }

    // --- Redispatch getters ---

    public Optional<Boolean> getRedispatchEnabled() {
        return getAttributes().getValue(REDISPATCH_ENABLED);
    }

    public Optional<String> getRedispatchAnnouncementId() {
        return getAttributes().getValue(REDISPATCH_ANNOUNCEMENT_ID);
    }

    public Optional<String> getRedispatchBidStatus() {
        return getAttributes().getValue(REDISPATCH_BID_STATUS);
    }

    public Optional<Double> getRedispatchBidPrice() {
        return getAttributes().getValue(REDISPATCH_BID_PRICE);
    }

    public Optional<Boolean> getRedispatchConfirmBid() {
        return getAttributes().getValue(REDISPATCH_CONFIRM_BID);
    }

    // --- Redispatch setters ---

    public EmsGOPACSAsset setRedispatchEnabled(Boolean enabled) {
        getAttributes().getOrCreate(REDISPATCH_ENABLED).setValue(enabled);
        return this;
    }

    public EmsGOPACSAsset setRedispatchBidPrice(Double bidPrice) {
        getAttributes().getOrCreate(REDISPATCH_BID_PRICE).setValue(bidPrice);
        return this;
    }
}
