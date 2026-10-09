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

import static org.openremote.model.Constants.*;

import jakarta.persistence.Entity;
import java.util.Optional;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetDescriptor;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.value.*;

@Entity
public class EmsElectricityBatteryAsset extends Asset<EmsElectricityBatteryAsset> {

  public static final AttributeDescriptor<Boolean> ALLOW_CHARGING =
      new AttributeDescriptor<>("allowCharging", ValueType.BOOLEAN);

  public static final AttributeDescriptor<Boolean> ALLOW_DISCHARGING =
      new AttributeDescriptor<>("allowDischarging", ValueType.BOOLEAN);

  public enum EmsElectricityBatteryConnectionStatusValueType {
    connected,
    disconnected
  }

  public static final AttributeDescriptor<Integer> CHARGE_EFFICIENCY =
      new AttributeDescriptor<>("chargeEfficiency", ValueType.POSITIVE_INTEGER)
          .withUnits(UNITS_PERCENTAGE);

  public static final AttributeDescriptor<Double> CHARGE_POWER_MAXIMUM =
      new AttributeDescriptor<>("chargePowerMaximum", ValueType.POSITIVE_NUMBER)
          .withUnits(UNITS_KILO, UNITS_WATT);

  public static final ValueDescriptor<EmsElectricityBatteryConnectionStatusValueType>
      CONNECTION_STATUS_VALUE_TYPE =
          new ValueDescriptor<>(
              "EmsElectricityBatteryConnectionStatusValueType",
              EmsElectricityBatteryConnectionStatusValueType.class);

  public static final AttributeDescriptor<EmsElectricityBatteryConnectionStatusValueType>
      CONNECTION_STATUS =
          new AttributeDescriptor<>(
              "connectionStatus",
              CONNECTION_STATUS_VALUE_TYPE,
              new MetaItem<>(MetaItemType.READ_ONLY),
              new MetaItem<>(MetaItemType.STORE_DATA_POINTS));

  public static final AttributeDescriptor<Integer> DISCHARGE_EFFICIENCY =
      new AttributeDescriptor<>("dischargeEfficiency", ValueType.POSITIVE_INTEGER)
          .withUnits(UNITS_PERCENTAGE);

  public static final AttributeDescriptor<Double> DISCHARGE_POWER_MAXIMUM =
      new AttributeDescriptor<>("dischargePowerMaximum", ValueType.NEGATIVE_NUMBER)
          .withUnits(UNITS_KILO, UNITS_WATT);

  public static final AttributeDescriptor<Double> ENERGY_CAPACITY =
      new AttributeDescriptor<>(
              "energyCapacity", ValueType.POSITIVE_NUMBER, new MetaItem<>(MetaItemType.RULE_STATE))
          .withUnits(UNITS_KILO, UNITS_WATT, UNITS_HOUR);

  public static final AttributeDescriptor<Double> ENERGY_LEVEL =
      new AttributeDescriptor<>(
              "energyLevel",
              ValueType.POSITIVE_NUMBER,
              new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
              new MetaItem<>(MetaItemType.READ_ONLY),
              new MetaItem<>(MetaItemType.RULE_STATE),
              new MetaItem<>(MetaItemType.STORE_DATA_POINTS))
          .withUnits(UNITS_KILO, UNITS_WATT, UNITS_HOUR);

  public static final AttributeDescriptor<Double> ENERGY_LEVEL_PERCENTAGE =
      new AttributeDescriptor<>(
              "energyLevelPercentage",
              ValueType.POSITIVE_NUMBER,
              new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
              new MetaItem<>(MetaItemType.READ_ONLY),
              new MetaItem<>(MetaItemType.RULE_STATE),
              new MetaItem<>(MetaItemType.STORE_DATA_POINTS))
          .withUnits(UNITS_PERCENTAGE);

  public static final AttributeDescriptor<Integer> ENERGY_LEVEL_PERCENTAGE_MAXIMUM =
      new AttributeDescriptor<>("energyLevelPercentageMaximum", ValueType.POSITIVE_INTEGER)
          .withUnits(UNITS_PERCENTAGE);

  public static final AttributeDescriptor<Integer> ENERGY_LEVEL_PERCENTAGE_MINIMUM =
      new AttributeDescriptor<>("energyLevelPercentageMinimum", ValueType.POSITIVE_INTEGER)
          .withUnits(UNITS_PERCENTAGE);

  public static final AttributeDescriptor<Double> POWER =
      new AttributeDescriptor<>(
              "power",
              ValueType.NUMBER,
              new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
              new MetaItem<>(MetaItemType.READ_ONLY),
              new MetaItem<>(MetaItemType.RULE_STATE),
              new MetaItem<>(MetaItemType.STORE_DATA_POINTS))
          .withUnits(UNITS_KILO, UNITS_WATT);

  public static final AttributeDescriptor<Double> POWER_SETPOINT =
      new AttributeDescriptor<>(
              "powerSetpoint",
              ValueType.NUMBER,
              new MetaItem<>(MetaItemType.HAS_PREDICTED_DATA_POINTS),
              new MetaItem<>(MetaItemType.READ_ONLY),
              new MetaItem<>(MetaItemType.RULE_STATE),
              new MetaItem<>(MetaItemType.STORE_DATA_POINTS))
          .withUnits(UNITS_KILO, UNITS_WATT);

  //    // Placeholders
  //    public static final AttributeDescriptor<Boolean> INCLUDE_BATTERY_COST = new
  // AttributeDescriptor<>("includeBatteryCost", ValueType.BOOLEAN
  //    );
  //
  //    public static final AttributeDescriptor<Double> CHARGE_COST = new
  // AttributeDescriptor<>("chargeCost", ValueType.NUMBER,
  //            new MetaItem<>(MetaItemType.LABEL, "Charge cost (€/kWh)")
  //    );
  //
  //    public static final AttributeDescriptor<Double> DISCHARGE_COST = new
  // AttributeDescriptor<>("dischargeCost", ValueType.NUMBER,
  //            new MetaItem<>(MetaItemType.LABEL, "Discharge cost (€/kWh)")
  //    );

  public static final AssetDescriptor<EmsElectricityBatteryAsset> DESCRIPTOR =
      new AssetDescriptor<>("battery-charging", "1B7C89", EmsElectricityBatteryAsset.class);

  protected EmsElectricityBatteryAsset() {}

  public EmsElectricityBatteryAsset(String name) {
    super(name);
  }

  public Optional<Boolean> getAllowCharging() {
    return getAttribute(ALLOW_CHARGING).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Boolean> getAllowDischarging() {
    return getAttribute(ALLOW_DISCHARGING).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<EmsElectricityBatteryConnectionStatusValueType> getConnectionStatus() {
    return getAttribute(CONNECTION_STATUS).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Integer> getChargeEfficiency() {
    return getAttribute(CHARGE_EFFICIENCY).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Double> getChargePowerMaximum() {
    return getAttribute(CHARGE_POWER_MAXIMUM).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Integer> getDischargeEfficiency() {
    return getAttribute(DISCHARGE_EFFICIENCY).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Double> getDischargePowerMaximum() {
    return getAttribute(DISCHARGE_POWER_MAXIMUM).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Double> getEnergyCapacity() {
    return getAttribute(ENERGY_CAPACITY).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Double> getEnergyLevelPercentage() {
    return getAttribute(ENERGY_LEVEL_PERCENTAGE).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Long> getEnergyLevelPercentageTimestamp() {
    return getAttribute(ENERGY_LEVEL_PERCENTAGE).flatMap(Attribute::getTimestamp);
  }

  public Optional<Integer> getEnergyLevelPercentageMaximum() {
    return getAttribute(ENERGY_LEVEL_PERCENTAGE_MAXIMUM).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Integer> getEnergyLevelPercentageMinimum() {
    return getAttribute(ENERGY_LEVEL_PERCENTAGE_MINIMUM).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Double> getPower() {
    return getAttribute(POWER).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Long> getPowerTimestamp() {
    return getAttribute(POWER).flatMap(Attribute::getTimestamp);
  }

  public Optional<Double> getPowerSetpoint() {
    return getAttribute(POWER_SETPOINT).flatMap(AbstractNameValueHolder::getValue);
  }

  public Optional<Long> getPowerSetpointTimestamp() {
    return getAttribute(POWER_SETPOINT).flatMap(Attribute::getTimestamp);
  }

  public EmsElectricityBatteryAsset setAllowCharging(Boolean value) {
    getAttributes().getOrCreate(ALLOW_CHARGING).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setAllowDischarging(Boolean value) {
    getAttributes().getOrCreate(ALLOW_DISCHARGING).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setChargeEfficiency(Integer value) {
    getAttributes().getOrCreate(CHARGE_EFFICIENCY).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setChargePowerMaximum(Double value) {
    getAttributes().getOrCreate(CHARGE_POWER_MAXIMUM).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setDischargeEfficiency(Integer value) {
    getAttributes().getOrCreate(DISCHARGE_EFFICIENCY).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setDischargePowerMaximum(Double value) {
    getAttributes().getOrCreate(DISCHARGE_POWER_MAXIMUM).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setEnergyCapacity(Double value) {
    getAttributes().getOrCreate(ENERGY_CAPACITY).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setEnergyLevelPercentage(Double value) {
    getAttributes().getOrCreate(ENERGY_LEVEL_PERCENTAGE).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setEnergyLevelPercentageMaximum(Integer value) {
    getAttributes().getOrCreate(ENERGY_LEVEL_PERCENTAGE_MAXIMUM).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setEnergyLevelPercentageMinimum(Integer value) {
    getAttributes().getOrCreate(ENERGY_LEVEL_PERCENTAGE_MINIMUM).setValue(value);
    return this;
  }

  public EmsElectricityBatteryAsset setPower(Double value) {
    getAttributes().getOrCreate(POWER).setValue(value);
    return this;
  }
}
