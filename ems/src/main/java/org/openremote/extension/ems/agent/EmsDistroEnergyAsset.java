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

import static org.openremote.model.Constants.*;

import jakarta.persistence.Entity;
import java.util.Optional;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;
import org.openremote.model.value.ValueType.ObjectMap;

@Entity
public class EmsDistroEnergyAsset extends Asset<EmsDistroEnergyAsset> {

  public static final AttributeDescriptor<String> PORTFOLIO =
      new AttributeDescriptor<>("portfolio", ValueType.TEXT);

  /** When the last run that submitted at least one market day finished. */
  public static final AttributeDescriptor<Long> LAST_SUBMISSION =
      new AttributeDescriptor<>(
          "lastSubmission", ValueType.TIMESTAMP, new MetaItem<>(MetaItemType.READ_ONLY));

  /** Number of market days the last run submitted, i.e. the forecast horizon in days. */
  public static final AttributeDescriptor<Integer> DAYS_SUBMITTED =
      new AttributeDescriptor<>(
          "daysSubmitted", ValueType.POSITIVE_INTEGER, new MetaItem<>(MetaItemType.READ_ONLY));

  // --- GOPACS orders: configuration ---

  public static final AttributeDescriptor<Boolean> GOPACS_ENABLED =
      new AttributeDescriptor<>("gopacsEnabled", ValueType.BOOLEAN);

  // --- GOPACS orders: announcement inputs, written through attribute links from EmsGOPACSAsset ---

  public static final AttributeDescriptor<String> GOPACS_EAN =
      new AttributeDescriptor<>(
          "gopacsEan", ValueType.TEXT, new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AttributeDescriptor<String> GOPACS_ANNOUNCEMENT_ID =
      new AttributeDescriptor<>(
          "gopacsAnnouncementId", ValueType.TEXT, new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AttributeDescriptor<Long> GOPACS_START_TIME =
      new AttributeDescriptor<>(
          "gopacsStartTime", ValueType.TIMESTAMP, new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AttributeDescriptor<Long> GOPACS_END_TIME =
      new AttributeDescriptor<>(
          "gopacsEndTime", ValueType.TIMESTAMP, new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AttributeDescriptor<Long> GOPACS_BID_VALIDITY_END =
      new AttributeDescriptor<>(
          "gopacsBidValidityEnd", ValueType.TIMESTAMP, new MetaItem<>(MetaItemType.READ_ONLY));

  public static final AttributeDescriptor<String> GOPACS_EAN_EFFECTIVITY =
      new AttributeDescriptor<>(
          "gopacsEanEffectivity", ValueType.TEXT, new MetaItem<>(MetaItemType.READ_ONLY));

  // --- GOPACS orders: next order, pre-filled by the handler and operator-overridable ---

  /** BUY or SELL. */
  public static final AttributeDescriptor<String> GOPACS_ORDER_TYPE =
      new AttributeDescriptor<>("gopacsOrderType", ValueType.TEXT);

  public static final AttributeDescriptor<Double> GOPACS_ORDER_QUANTITY =
      new AttributeDescriptor<>("gopacsOrderQuantity", ValueType.NUMBER)
          .withUnits(UNITS_KILO, UNITS_WATT);

  public static final AttributeDescriptor<Double> GOPACS_ORDER_PRICE =
      new AttributeDescriptor<>("gopacsOrderPrice", ValueType.NUMBER)
          .withUnits("EUR", UNITS_PER, UNITS_MEGA, UNITS_WATT, UNITS_HOUR);

  // --- GOPACS orders: operator actions, reset by the handler ---

  public static final AttributeDescriptor<Boolean> GOPACS_CONFIRM_ORDER =
      new AttributeDescriptor<>("gopacsConfirmOrder", ValueType.BOOLEAN);

  public static final AttributeDescriptor<Boolean> GOPACS_CANCEL_ORDERS =
      new AttributeDescriptor<>("gopacsCancelOrders", ValueType.BOOLEAN);

  // --- GOPACS orders: status ---

  /** Orders still being tracked, keyed by reference. Terminal orders drop out. */
  public static final AttributeDescriptor<ObjectMap> GOPACS_ORDERS =
      new AttributeDescriptor<>(
          "gopacsOrders", ValueType.JSON_OBJECT, new MetaItem<>(MetaItemType.READ_ONLY));

  /** One data point per order action and status change. */
  public static final AttributeDescriptor<ObjectMap> GOPACS_ORDER_HISTORY =
      new AttributeDescriptor<>(
          "gopacsOrderHistory",
          ValueType.JSON_OBJECT,
          new MetaItem<>(MetaItemType.READ_ONLY),
          new MetaItem<>(MetaItemType.STORE_DATA_POINTS));

  /** One data point per settled trade. */
  public static final AttributeDescriptor<ObjectMap> GOPACS_SETTLEMENT_HISTORY =
      new AttributeDescriptor<>(
          "gopacsSettlementHistory",
          ValueType.JSON_OBJECT,
          new MetaItem<>(MetaItemType.READ_ONLY),
          new MetaItem<>(MetaItemType.STORE_DATA_POINTS));

  /** When the tracked orders were last synced with Distro. */
  public static final AttributeDescriptor<Long> GOPACS_LAST_SYNC =
      new AttributeDescriptor<>(
          "gopacsLastSync", ValueType.TIMESTAMP, new MetaItem<>(MetaItemType.READ_ONLY));

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

  // --- GOPACS getters ---

  public Optional<Boolean> getGopacsEnabled() {
    return getAttributes().getValue(GOPACS_ENABLED);
  }

  public Optional<String> getGopacsEan() {
    return getAttributes().getValue(GOPACS_EAN);
  }

  public Optional<String> getGopacsAnnouncementId() {
    return getAttributes().getValue(GOPACS_ANNOUNCEMENT_ID);
  }

  public Optional<Long> getGopacsStartTime() {
    return getAttributes().getValue(GOPACS_START_TIME);
  }

  public Optional<Long> getGopacsEndTime() {
    return getAttributes().getValue(GOPACS_END_TIME);
  }

  public Optional<Long> getGopacsBidValidityEnd() {
    return getAttributes().getValue(GOPACS_BID_VALIDITY_END);
  }

  public Optional<String> getGopacsEanEffectivity() {
    return getAttributes().getValue(GOPACS_EAN_EFFECTIVITY);
  }

  public Optional<String> getGopacsOrderType() {
    return getAttributes().getValue(GOPACS_ORDER_TYPE);
  }

  public Optional<Double> getGopacsOrderQuantity() {
    return getAttributes().getValue(GOPACS_ORDER_QUANTITY);
  }

  public Optional<Double> getGopacsOrderPrice() {
    return getAttributes().getValue(GOPACS_ORDER_PRICE);
  }

  public Optional<ObjectMap> getGopacsOrders() {
    return getAttributes().getValue(GOPACS_ORDERS);
  }

  // --- GOPACS setters ---

  public EmsDistroEnergyAsset setGopacsEnabled(Boolean enabled) {
    getAttributes().getOrCreate(GOPACS_ENABLED).setValue(enabled);
    return this;
  }

  public EmsDistroEnergyAsset setGopacsOrderPrice(Double price) {
    getAttributes().getOrCreate(GOPACS_ORDER_PRICE).setValue(price);
    return this;
  }
}
