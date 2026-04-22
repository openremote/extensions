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
package org.openremote.extension.ems.manager.optimisationMethods;

import org.openremote.extension.ems.agent.EmsDayAheadAsset;
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset;
import org.openremote.extension.ems.agent.EmsEnergyOptimisationAsset;
import org.openremote.extension.ems.agent.EmsGOPACSAsset;
import org.openremote.extension.ems.manager.Services;
import org.openremote.model.asset.Asset;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.datapoint.query.AssetDatapointAllQuery;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.value.ValueType;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.openremote.model.syslog.SyslogCategory.DATA;

public class EmsOptimisationBeta implements OptimisationMethod {
    protected static final Logger LOG = SyslogCategory.getLogger(DATA, EmsOptimisationBeta.class.getName());
    private final String optimisationMethodName = EmsOptimisationBeta.class.getSimpleName();

    // Maximum interval between data-points send by the device to be considered connected
    private final int ACTIVE_PERIOD_MINUTES = 5;

    // Default EMS power limit settings
    private final int POWER_LIMIT_FLUCTUATION_MARGIN_PERCENTAGE_DEFAULT = 10;

    // Default battery settings
    private final int BATTERY_ENERGY_LEVEL_PERCENTAGE_DEFAULT = 50;
    private final double BATTERY_POWER_SETPOINT_RESPONSIVENESS_DEFAULT = 0.5;
    private final int BATTERY_TARIFF_OPTIMISATION_WINDOW_DEFAULT = 8;

    // Advanced settings attribute names
    private final String POWER_LIMIT_MAXIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME = "powerLimitMaximumFluctuationMargin";
    private final String POWER_LIMIT_MINIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME = "powerLimitMinimumFluctuationMargin";

    private final String[][] advancedSettingsAttributesInfo = {
            {POWER_LIMIT_MAXIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME, ValueType.POSITIVE_NUMBER.getName()},
            {POWER_LIMIT_MINIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME, ValueType.POSITIVE_NUMBER.getName()}
    };

    @Override
    public void execute(String energyOptimisationAssetId, Services services) {
        runOptimisationMethodEms(energyOptimisationAssetId, services);
    }

    private void runOptimisationMethodEms(String energyOptimisationAssetId, Services services) {
        // Get energy optimisation asset
        EmsEnergyOptimisationAsset energyOptimisationAsset = (EmsEnergyOptimisationAsset) services.getAssetStorageService().find(energyOptimisationAssetId);

        if (energyOptimisationAsset == null) {
            return;
        }

        // Update Advanced settings attributes info
        String advancedSettingsAttributes = energyOptimisationAsset.getAdvancedSettingsAttributes().orElse("");

        if (advancedSettingsAttributes.isBlank()) {
            StringBuilder advancedSettingsAttributesBody = new StringBuilder();

            String firstRow = String.format("Optimisation method '%s':\n", optimisationMethodName);
            advancedSettingsAttributesBody.append(firstRow);

            for (String[] row : advancedSettingsAttributesInfo) {
                advancedSettingsAttributesBody.append(String.join(",", row)).append("\n");
            }

            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(energyOptimisationAssetId, EmsEnergyOptimisationAsset.ADVANCED_SETTINGS_ATTRIBUTES, advancedSettingsAttributesBody.toString()), getClass().getSimpleName());
        }

        String logPrefixEnergyOptimisation = String.format("assetType='%s', assetId='%s', assetName='%s'", energyOptimisationAsset.getAssetType(), energyOptimisationAssetId, energyOptimisationAsset.getAssetName());

        // Get all battery assets
        List<EmsElectricityBatteryAsset> electricityBatteryAssets = services.getAssetStorageService()
                .findAll(new AssetQuery().parents(energyOptimisationAssetId).types(EmsElectricityBatteryAsset.class))
                .stream()
                .map(asset -> (EmsElectricityBatteryAsset) asset)
                .toList();

        if (energyOptimisationAsset.getEnableDetailedLogging().orElse(false)) {
            int allowChargingSize = electricityBatteryAssets
                    .stream()
                    .filter(electricityBatteryAsset -> electricityBatteryAsset.getAllowCharging().orElse(false))
                    .toList()
                    .size();

            int allowDischargingSize = electricityBatteryAssets
                    .stream()
                    .filter(electricityBatteryAsset -> electricityBatteryAsset.getAllowDischarging().orElse(false))
                    .toList()
                    .size();

            LOG.info(String.format("%s; Energy optimisation asset has %s battery asset(s). Number of batteries with '%s'= %s and '%s'= %s",
                    logPrefixEnergyOptimisation, electricityBatteryAssets.size(), EmsElectricityBatteryAsset.ALLOW_CHARGING.getName(), allowChargingSize, EmsElectricityBatteryAsset.ALLOW_DISCHARGING.getName(), allowDischargingSize));
        }

        // Check if power net is connected
        Double powerNet = energyOptimisationAsset.getPowerNet().orElse(null);

        if (powerNet == null) {
            LOG.warning(String.format("%s; Failed to perform '%s' energy optimisation method. '%s' attribute is not connected", logPrefixEnergyOptimisation, optimisationMethodName, EmsEnergyOptimisationAsset.POWER_NET.getName()));
        }

        // Validate power limits
        Double powerLimitMaximumProfileTotal = energyOptimisationAsset.getPowerLimitMaximumProfileTotal().orElse(null);
        Double powerLimitMinimumProfileTotal = energyOptimisationAsset.getPowerLimitMinimumProfileTotal().orElse(null);
        Long powerLimitMaximumProfileTimestampMillis = energyOptimisationAsset.getPowerLimitMaximumProfileTotalTimestamp().orElse(null);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

        String powerLimitMaximumProfileDateTime = "";

        if (powerLimitMaximumProfileTimestampMillis != null) {
            powerLimitMaximumProfileDateTime = formatter.format(Instant.ofEpochMilli(powerLimitMaximumProfileTimestampMillis));
        }

        if (powerLimitMaximumProfileTotal != null && powerLimitMinimumProfileTotal != null && powerLimitMaximumProfileTotal <= powerLimitMinimumProfileTotal) {
            LOG.warning(String.format("%s; Failed to perform '%s' energy optimisation method. The '%s'= %s kW is lower than or equal to '%s'= %s kW for timestamp='%s'",
                    logPrefixEnergyOptimisation, optimisationMethodName, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(), powerLimitMaximumProfileTotal,
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(), powerLimitMinimumProfileTotal, powerLimitMaximumProfileDateTime));
        }

        // Get day ahead asset
        EmsDayAheadAsset dayAheadAsset = getDayAheadAsset(energyOptimisationAsset, services);

        // Update day ahead asset at specific time of day
        if (dayAheadAsset != null) {
            updateDayAheadAsset(energyOptimisationAsset, dayAheadAsset, services);
        }

        // Check for battery assets
        if (electricityBatteryAssets.isEmpty()) {
            return;
        }

        // Order battery assets
        electricityBatteryAssets = batteryOrder(electricityBatteryAssets);

        // Check connection status of battery assets
        batteryCheckConnection(electricityBatteryAssets, services);

        // Check if all required attributes are connected/set and create log messages
        batteryCheckSetup(electricityBatteryAssets);

        // Find latest power set-point update across all batteries
        long powerSetpointTimestampLatestMillis = batteriesLatestPowerSetpointUpdate(electricityBatteryAssets);

        // Calculate battery energy level percentage targets
        int intervalPeriodMinutes = 15;
        int currentMinute = LocalDateTime.now().getMinute();
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long forecastUpdateTimeMillis = currentTimeMillis - currentTimeMillis % (intervalPeriodMinutes * 60 * 1000);

        Map<String, Integer> batteryEnergyLevelPercentageTargets;

        if ((currentMinute % intervalPeriodMinutes) == 0 || powerSetpointTimestampLatestMillis < forecastUpdateTimeMillis) {
            batteryEnergyLevelPercentageTargets = batteryCalculateForecasts(electricityBatteryAssets, energyOptimisationAsset, services);
        } else {
            batteryEnergyLevelPercentageTargets = batteryGetEnergyLevelPercentageTargetsCurrent(electricityBatteryAssets, services);
        }

        // Calculate battery power set-points
        Map<String, Double> powerSetpointsNew = batteryCalculatePowerSetpoints(energyOptimisationAsset, electricityBatteryAssets, powerSetpointTimestampLatestMillis, batteryEnergyLevelPercentageTargets, services, logPrefixEnergyOptimisation);

        // Update battery power set-points
        batteryUpdatePowerSetpoints(electricityBatteryAssets, powerSetpointsNew, services);
    }

    private long batteriesLatestPowerSetpointUpdate(List<EmsElectricityBatteryAsset> electricityBatteryAssets) {
        // Find latest power set-point update across all batteries
        long powerSetpointTimestampLatestMillis = 0L;

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            long powerSetpointTimestampMillis = electricityBatteryAsset.getPowerSetpointTimestamp().orElse(0L);

            if (powerSetpointTimestampMillis > powerSetpointTimestampLatestMillis) {
                powerSetpointTimestampLatestMillis = powerSetpointTimestampMillis;
            }
        }

        return powerSetpointTimestampLatestMillis;
    }

    private int batteryCalculateEnergyLevelPercentageDefault(Integer energyLevelPercentageMaximumBattery, Integer energyLevelPercentageMinimumBattery, EmsEnergyOptimisationAsset energyOptimisationAsset) {
        if (energyLevelPercentageMaximumBattery == null || energyLevelPercentageMinimumBattery == null) {
            return BATTERY_ENERGY_LEVEL_PERCENTAGE_DEFAULT;
        }

        Double powerLimitMaximumProfileTotal = energyOptimisationAsset.getPowerLimitMaximumInput().orElse(null);
        Double powerLimitMinimumProfileTotal = energyOptimisationAsset.getPowerLimitMinimumInput().orElse(null);

        int energyLevelPercentageDefault;

        if (powerLimitMaximumProfileTotal == null && powerLimitMinimumProfileTotal != null) {
            energyLevelPercentageDefault = energyLevelPercentageMinimumBattery;
        } else if (powerLimitMaximumProfileTotal != null && powerLimitMinimumProfileTotal == null) {
            energyLevelPercentageDefault = energyLevelPercentageMaximumBattery;
        } else {
            energyLevelPercentageDefault = (int) Math.round((double) (energyLevelPercentageMaximumBattery + energyLevelPercentageMinimumBattery) / 2);
        }

        return energyLevelPercentageDefault;
    }

    private Map<String, Integer> batteryCalculateForecasts(List<EmsElectricityBatteryAsset> electricityBatteryAssets, EmsEnergyOptimisationAsset energyOptimisationAsset, Services services) {
        Map<String, Integer> batteryEnergyLevelPercentageTargets = new HashMap<>();

        StringBuilder infoStr = new StringBuilder();
        infoStr.append("EMS forecast:\n");

        // Get power consumption forecast for 1 week
        long intervalMillis = 15 * 60000;
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long startTimeForecastPeriodMillis = currentTimeMillis - currentTimeMillis % intervalMillis;
        long endTimeForecastPeriodMillis = startTimeForecastPeriodMillis - startTimeForecastPeriodMillis % (24 * 60 * 60000) + (8 * 24 * 60 * 60000);

        String energyOptimisationAssetId = energyOptimisationAsset.getId();
        AssetDatapointAllQuery assetDatapointQueryConsumptionPredicted = new AssetDatapointAllQuery(startTimeForecastPeriodMillis, endTimeForecastPeriodMillis);
        List<ValueDatapoint<?>> energyOptimisationPowerConsumptionPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_CONSUMPTION.getName(), assetDatapointQueryConsumptionPredicted);

//        System.out.println("energyOptimisationPowerConsumptionPredicted = " + energyOptimisationPowerConsumptionPredicted);

        if (energyOptimisationPowerConsumptionPredicted.isEmpty()) {
            return batteryEnergyLevelPercentageTargets;
        }

        // Calculate power average for each 15-minute interval
        List<ValueDatapoint<?>> totalPowerConsumptionAveraged = intervalAverage(energyOptimisationPowerConsumptionPredicted, intervalMillis);
        totalPowerConsumptionAveraged.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));

//        System.out.println("totalPowerConsumptionAveraged = " + totalPowerConsumptionAveraged);

        long startTimeForecastDataMillis = totalPowerConsumptionAveraged.getFirst().getTimestamp();
        long endTimeForecastDataMillis = totalPowerConsumptionAveraged.getLast().getTimestamp();

        // Interpolate power average values for each 15-minute interval
        List<ValueDatapoint<?>> totalPowerConsumptionInterpolated = intervalInterpolate(totalPowerConsumptionAveraged, startTimeForecastDataMillis, endTimeForecastDataMillis, intervalMillis);

        List<Long> timestampsMillisList = new ArrayList<>();
        List<Double> totalPowerConsumptionList = new ArrayList<>();

        for (ValueDatapoint<?> datapoint : totalPowerConsumptionInterpolated) {
            long timestampMillis = datapoint.getTimestamp();
            Double value = (Double) datapoint.getValue();

            timestampsMillisList.add(timestampMillis);
            totalPowerConsumptionList.add(value);
        }

        int numberOfTimestamps = timestampsMillisList.size();

        infoStr.append(String.format("numberOfTimestamps = %s \n", numberOfTimestamps));
        infoStr.append(String.format("timestampsMillisList = %s \n", timestampsMillisList));
        infoStr.append(String.format("totalPowerConsumptionList = %s \n", totalPowerConsumptionList));

        if (totalPowerConsumptionList.isEmpty()) {
            return batteryEnergyLevelPercentageTargets;
        }

        AssetDatapointAllQuery assetDatapointQueryPeriodPredicted = new AssetDatapointAllQuery(startTimeForecastDataMillis, endTimeForecastDataMillis);
        List<ValueDatapoint<?>> energyOptimisationPowerProductionPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_PRODUCTION.getName(), assetDatapointQueryPeriodPredicted);

//        System.out.println("energyOptimisationPowerProductionPredicted = " + energyOptimisationPowerProductionPredicted);

        Map<Long, Double> totalPowerProductionMap = new HashMap<>();

        if (!energyOptimisationPowerProductionPredicted.isEmpty()) {
            // Calculate power average for each 15-minute interval
            List<ValueDatapoint<?>> totalPowerProductionAveraged = intervalAverage(energyOptimisationPowerProductionPredicted, intervalMillis);
            totalPowerProductionAveraged.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));

            // Interpolate power average values for each 15-minute interval
            List<ValueDatapoint<?>> totalPowerProductionInterpolated = intervalInterpolate(totalPowerProductionAveraged, totalPowerProductionAveraged.getFirst().getTimestamp(), totalPowerProductionAveraged.getLast().getTimestamp(), intervalMillis);

            totalPowerProductionMap = totalPowerProductionInterpolated.stream().collect(Collectors.toMap(ValueDatapoint::getTimestamp, dp -> ((Double) dp.getValue())));
        }

        // List to store the sum of total power consumption, production and flexible
        List<Double> totalPowerConsumptionProductionFlexibleList = new ArrayList<>();

        // Sum total power consumption and production
        for (int i = 0; i < numberOfTimestamps; i++) {
            Double powerConsumption = totalPowerConsumptionList.get(i);
            Double powerProduction = totalPowerProductionMap.get(timestampsMillisList.get(i));

            if (powerProduction != null) {
                double sum = powerConsumption + powerProduction;
                totalPowerConsumptionProductionFlexibleList.add(sum);
            } else {
                totalPowerConsumptionProductionFlexibleList.add(powerConsumption);
            }
        }

        infoStr.append(String.format("totalPowerConsumptionProductionFlexibleList = %s \n", totalPowerConsumptionProductionFlexibleList));

        // Get power limit forecasts
        List<ValueDatapoint<?>> energyOptimisationPowerLimitMaximumPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(), assetDatapointQueryPeriodPredicted);
        List<ValueDatapoint<?>> energyOptimisationPowerLimitMinimumPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(), assetDatapointQueryPeriodPredicted);

        Map<Long, Double> powerLimitMaximumMap = new HashMap<>();
        Map<Long, Double> powerLimitMinimumMap = new HashMap<>();

        for (ValueDatapoint<?> dp : energyOptimisationPowerLimitMaximumPredicted) {
            powerLimitMaximumMap.put(dp.getTimestamp(), (Double) dp.getValue());
        }

        for (ValueDatapoint<?> dp : energyOptimisationPowerLimitMinimumPredicted) {
            powerLimitMinimumMap.put(dp.getTimestamp(), (Double) dp.getValue());
        }

        List<Double> chargePowerAvailableTotalList = new ArrayList<>();
        List<Double> dischargePowerAvailableTotalList = new ArrayList<>();

        // Add current available charge and discharge power which is 0
        chargePowerAvailableTotalList.add(0.0);
        dischargePowerAvailableTotalList.add(0.0);

        double intervalHour = round((double) intervalMillis / (60 * 60000), 5);
        int batteryPercentageRoundingPrecision = 5;

        // Calculate forecast for each battery
        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String batteryAssetId = electricityBatteryAsset.getId();
            Integer chargeEfficiencyBattery = electricityBatteryAsset.getChargeEfficiency().orElse(null);
            Double chargePowerMaximumBattery = electricityBatteryAsset.getChargePowerMaximum().orElse(null);
            Integer dischargeEfficiencyBattery = electricityBatteryAsset.getDischargeEfficiency().orElse(null);
            Double dischargePowerMaximumBattery = electricityBatteryAsset.getDischargePowerMaximum().orElse(null);
            Double energyCapacityBattery = electricityBatteryAsset.getEnergyCapacity().orElse(null);
            Double energyLevelPercentageBattery = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);
            Integer energyLevelPercentageMaximumBattery = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
            Integer energyLevelPercentageMinimumBattery = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);

            if (chargeEfficiencyBattery == null || chargePowerMaximumBattery == null || dischargeEfficiencyBattery == null || dischargePowerMaximumBattery == null || energyCapacityBattery == null ||
                    energyLevelPercentageBattery == null || energyLevelPercentageMaximumBattery == null || energyLevelPercentageMinimumBattery == null) {
                continue;
            }

            // Check if the energy level percentage maximum and minimum are valid
            if (energyLevelPercentageMaximumBattery <= energyLevelPercentageMinimumBattery) {
                continue;
            }

            // Check if the charge and discharge efficiency are valid
            if (chargeEfficiencyBattery <= 0 || dischargeEfficiencyBattery <= 0) {
                continue;
            }

            List<Double> powerLimitMaximumVirtualList = new ArrayList<>();
            List<Double> powerLimitMinimumVirtualList = new ArrayList<>();

            // Calculate total available charge and discharge power
            for (int i = 0; i < numberOfTimestamps; i++) {
                long timestampMillis = timestampsMillisList.get(i);
                double totalPower = totalPowerConsumptionProductionFlexibleList.get(i);

                Double powerLimitMaximum = powerLimitMaximumMap.getOrDefault(timestampMillis, null);
                Double powerLimitMinimum = powerLimitMinimumMap.getOrDefault(timestampMillis, null);

                Double powerLimitMaximumVirtual = calculatePowerLimitVirtual(energyOptimisationAsset, powerLimitMaximum, "max");
                Double powerLimitMinimumVirtual = calculatePowerLimitVirtual(energyOptimisationAsset, powerLimitMinimum, "min");

                powerLimitMaximumVirtualList.add(powerLimitMaximumVirtual);
                powerLimitMinimumVirtualList.add(powerLimitMinimumVirtual);

                if (powerLimitMaximumVirtual != null) {
                    double chargePowerTotalAvailable = powerLimitMaximumVirtual - totalPower;
                    chargePowerAvailableTotalList.add(chargePowerTotalAvailable);
                } else {
                    chargePowerAvailableTotalList.add(Double.POSITIVE_INFINITY);
                }

                if (powerLimitMinimumVirtual != null) {
                    double dischargePowerTotalAvailable = powerLimitMinimumVirtual - totalPower;
                    dischargePowerAvailableTotalList.add(dischargePowerTotalAvailable);
                } else {
                    dischargePowerAvailableTotalList.add(Double.NEGATIVE_INFINITY);
                }
            }

            int numberOfDataPoints = chargePowerAvailableTotalList.size();

            infoStr.append(String.format("powerLimitMaximumVirtualList = %s \n", powerLimitMaximumVirtualList));
            infoStr.append(String.format("powerLimitMinimumVirtualList = %s \n", powerLimitMinimumVirtualList));
            infoStr.append("\n");

//            System.out.println("chargePowerAvailableTotalList = " + chargePowerAvailableTotalList);
//            System.out.println("dischargePowerAvailableTotalList = " + dischargePowerAvailableTotalList);

            List<Double> chargeNeededTotalList = dischargePowerAvailableTotalList.stream().map(x -> x > 0 ? x : 0).toList();
            List<Double> dischargeNeededTotalList = chargePowerAvailableTotalList.stream().map(x -> x < 0 ? x : 0).toList();

//            System.out.println("chargeNeededTotalList = " + chargeNeededTotalList);
//            System.out.println("dischargeNeededTotalList = " + dischargeNeededTotalList);

            List<Double> chargePowerAvailableBatteryList = chargePowerAvailableTotalList.stream().map(x -> x > 0 ? Math.min(x, chargePowerMaximumBattery) : 0).toList();
            List<Double> dischargePowerAvailableBatteryList = dischargePowerAvailableTotalList.stream().map(x -> x < 0 ? Math.max(x, dischargePowerMaximumBattery) : 0).toList();

//            System.out.println("chargeAvailableBatteryList = " + chargePowerAvailableBatteryList);
//            System.out.println("dischargeAvailableBatteryList = " + dischargePowerAvailableBatteryList);
//            System.out.println();

            // Convert power to energy level percentage
            List<Double> chargePercentageNeededTotalList = new ArrayList<>();
            List<Double> dischargePercentageNeededTotalList = new ArrayList<>();

            for (int i = 0; i < numberOfDataPoints; i++) {
                double c = intervalHour * chargeNeededTotalList.get(i) * chargeEfficiencyBattery / energyCapacityBattery;
                double d = 10000 * intervalHour * dischargeNeededTotalList.get(i) / (energyCapacityBattery * dischargeEfficiencyBattery);
                chargePercentageNeededTotalList.add(round(c, batteryPercentageRoundingPrecision));
                dischargePercentageNeededTotalList.add(round(d, batteryPercentageRoundingPrecision));
            }

//            System.out.println("chargePercentageNeededTotalList = " + chargePercentageNeededTotalList);
//            System.out.println("dischargePercentageNeededTotalList = " + dischargePercentageNeededTotalList);

            List<Double> chargePercentageAvailableBatteryList = new ArrayList<>();
            List<Double> dischargePercentageAvailableBatteryList = new ArrayList<>();

            for (int i = 0; i < numberOfDataPoints; i++) {
                double c = intervalHour * chargePowerAvailableBatteryList.get(i) * chargeEfficiencyBattery / energyCapacityBattery;
                double d = 10000 * intervalHour * dischargePowerAvailableBatteryList.get(i) / (energyCapacityBattery * dischargeEfficiencyBattery);
                chargePercentageAvailableBatteryList.add(round(c, batteryPercentageRoundingPrecision));
                dischargePercentageAvailableBatteryList.add(round(d, batteryPercentageRoundingPrecision));
            }

//            System.out.println("chargePercentageAvailableBatteryList = " + chargePercentageAvailableBatteryList);
//            System.out.println("dischargePercentageAvailableBatteryList = " + dischargePercentageAvailableBatteryList);
//            System.out.println();

            // Calculate forecast when current energy level percentage is outside limits
            int getToLimitIndex = 0;
            double getToLimitPercentage = energyLevelPercentageBattery;
            List<Double> getToLimitPercentageList = new ArrayList<>();

            for (int i = 0; i < numberOfDataPoints; i++) {
                getToLimitIndex = i;

                if (getToLimitPercentage > energyLevelPercentageMaximumBattery) {
                    double dischargePercentageNeeded = energyLevelPercentageMaximumBattery - getToLimitPercentage;
                    double dischargePercentageAvailable = Math.max(dischargePercentageAvailableBatteryList.get(i), dischargePercentageNeeded);
                    getToLimitPercentage = getToLimitPercentage + dischargePercentageAvailable;
                    dischargePercentageAvailableBatteryList.set(i, round(dischargePercentageAvailableBatteryList.get(i) - dischargePercentageAvailable, batteryPercentageRoundingPrecision));
                    getToLimitPercentageList.add(round(getToLimitPercentage, batteryPercentageRoundingPrecision));
                } else if (getToLimitPercentage < energyLevelPercentageMinimumBattery) {
                    double chargePercentageNeeded = energyLevelPercentageMinimumBattery - getToLimitPercentage;
                    double chargePercentageAvailable = Math.min(chargePercentageAvailableBatteryList.get(i), chargePercentageNeeded);
                    getToLimitPercentage = getToLimitPercentage + chargePercentageAvailable;
                    chargePercentageAvailableBatteryList.set(i, round(chargePercentageAvailableBatteryList.get(i) - chargePercentageAvailable, batteryPercentageRoundingPrecision));
                    getToLimitPercentageList.add(round(getToLimitPercentage, batteryPercentageRoundingPrecision));
                } else {
                    break;
                }
            }

//            System.out.println("getToLimitIndex = " + getToLimitIndex);
//            System.out.println("getToLimitPercentage = " + getToLimitPercentage);
//            System.out.println("getToLimitPercentageList = " + getToLimitPercentageList);
//            System.out.println("chargePercentageAvailableBatteryList2 = " + chargePercentageAvailableBatteryList);
//            System.out.println("dischargePercentageAvailableBatteryList2 = " + dischargePercentageAvailableBatteryList);

            List<Double> energyLevelPredictionList = new ArrayList<>();

            if (getToLimitPercentage > energyLevelPercentageMaximumBattery || getToLimitPercentage < energyLevelPercentageMinimumBattery) {
                // Energy level percentage forecast when entire forecast is outside of battery percentage limits
                energyLevelPredictionList = getToLimitPercentageList;
            } else {
                // Calculate energy level percentage forecast starting from value within battery percentage limits
                List<Double> chargePercentageWantBatteryList = new ArrayList<>();
                List<Double> dischargePercentageWantBatteryList = new ArrayList<>();

                for (int i = 0; i < numberOfDataPoints; i++) {
                    chargePercentageWantBatteryList.add(Math.min(chargePercentageNeededTotalList.get(i), chargePercentageAvailableBatteryList.get(i)));
                    dischargePercentageWantBatteryList.add(Math.max(dischargePercentageNeededTotalList.get(i), dischargePercentageAvailableBatteryList.get(i)));
                }

//                System.out.println("chargePercentageWantBatteryList = " + chargePercentageWantBatteryList);
//                System.out.println("dischargePercentageWantBatteryList = " + dischargePercentageWantBatteryList);

                List<Double> chargeAndDischargePercentageWantBatteryList = new ArrayList<>();

                for (int i = 0; i < numberOfDataPoints; i++) {
                    chargeAndDischargePercentageWantBatteryList.add(round(chargePercentageWantBatteryList.get(i) + dischargePercentageWantBatteryList.get(i), batteryPercentageRoundingPrecision));
                }

//                System.out.println("chargeAndDischargePercentageWantBatteryList = " + chargeAndDischargePercentageWantBatteryList);
//                System.out.println();

                // Calculate forecast without battery percentage limits
                int startRunningSumIndex = getToLimitIndex > 0 ? getToLimitIndex - 1 : 0;
                List<Double> runningSumList = new ArrayList<>();
                double sum = getToLimitPercentage;

                for (int i = startRunningSumIndex; i < numberOfDataPoints; i++) {
                    sum = sum + chargeAndDischargePercentageWantBatteryList.get(i);
                    runningSumList.add(round(sum, batteryPercentageRoundingPrecision));
                }

//                System.out.println("getToLimitPercentageList = " + getToLimitPercentageList);
//                System.out.println("runningSumList = " + runningSumList);
//                System.out.println();

                // Combine outside limits and running sum energy level percentages
                if (getToLimitPercentageList.size() > 1) {
                    energyLevelPredictionList.addAll(getToLimitPercentageList.subList(0, getToLimitPercentageList.size() - 1));
                }
                energyLevelPredictionList.addAll(runningSumList);
                int energyLevelPredictionListSize = energyLevelPredictionList.size();

                infoStr.append(String.format("energyLevelPredictionListOriginal = %s \n", energyLevelPredictionList));

                // Calculate forecast starting within battery percentage limits
                double energyLevelPredictionMaximum = Collections.max(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionListSize));
                double energyLevelPredictionMinimum = Collections.min(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionListSize));

                long dtStart = services.getTimerService().getCurrentTimeMillis();
                long dt = 0;
                long timeoutMillis = 10000;

                int intervalEndIndex = startRunningSumIndex;
                int predictionListIndex = startRunningSumIndex;

                while (energyLevelPredictionMaximum > energyLevelPercentageMaximumBattery || energyLevelPredictionMinimum < energyLevelPercentageMinimumBattery) {
                    String chargeOrDischarge = "";
                    int intervalStartIndex = startRunningSumIndex;

                    for (int i = intervalEndIndex; i < energyLevelPredictionListSize; i++) {
                        predictionListIndex = i;

                        if (energyLevelPredictionList.get(i) < energyLevelPercentageMinimumBattery) {
                            intervalEndIndex = i;

                            for (int j = intervalEndIndex; j >= startRunningSumIndex; j--) {
                                if (energyLevelPredictionList.get(j) >= energyLevelPercentageMaximumBattery) {
                                    intervalStartIndex = j;
                                    break;
                                }
                            }

                            chargeOrDischarge = "charge";

//                            System.out.println("CHARGE");
//                            System.out.println("intervalStartIndex = " + intervalStartIndex);
//                            System.out.println("intervalEndIndex = " + intervalEndIndex);

                            break;
                        } else if (energyLevelPredictionList.get(i) > energyLevelPercentageMaximumBattery) {
                            intervalEndIndex = i;

                            for (int j = intervalEndIndex; j >= startRunningSumIndex; j--) {
                                if (energyLevelPredictionList.get(j) <= energyLevelPercentageMinimumBattery) {
                                    intervalStartIndex = j;
                                    break;
                                }
                            }

                            chargeOrDischarge = "discharge";

//                            System.out.println("DISCHARGE");
//                            System.out.println("intervalStartIndex = " + intervalStartIndex);
//                            System.out.println("intervalEndIndex = " + intervalEndIndex);

                            break;
                        }
                    }

                    int changeIndex = -1;
                    double energyLevelPredictionValueBefore = energyLevelPredictionList.get(intervalEndIndex);

                    if (chargeOrDischarge.equals("charge")) {
                        double chargeAvailable = 0;
                        double chargeAvailableInterval = round(energyLevelPercentageMaximumBattery - Collections.max(energyLevelPredictionList.subList(intervalStartIndex, intervalEndIndex)), batteryPercentageRoundingPrecision);

                        if (chargeAvailableInterval > 0) {
                            for (int i = intervalEndIndex - 1; i >= intervalStartIndex; i--) {
                                if (i - 1 < 0) {
                                    break;
                                }

                                double chargeAvailableLeft = 0;

                                if (dischargePercentageWantBatteryList.get(i) >= 0) {
                                    double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                    chargeAvailableLeft = round(chargePercentageAvailableBatteryList.get(i) - chargeInUse, batteryPercentageRoundingPrecision);
                                }

                                if (chargeAvailableLeft > 0) {
                                    changeIndex = i;
                                    chargeAvailable = Math.min(chargeAvailableInterval, chargeAvailableLeft);
                                    break;
                                }
                            }
                        }

                        double chargeNeeded = energyLevelPercentageMinimumBattery - energyLevelPredictionList.get(intervalEndIndex);
                        double changeValue = Math.min(chargeAvailable, chargeNeeded);

                        if (changeIndex == -1) {
                            changeIndex = intervalEndIndex;
                            changeValue = chargeNeeded;
                        }

                        for (int i = changeIndex; i < energyLevelPredictionListSize; i++) {
                            energyLevelPredictionList.set(i, round(energyLevelPredictionList.get(i) + changeValue, batteryPercentageRoundingPrecision));
                        }
                    } else if (chargeOrDischarge.equals("discharge")) {
                        double dischargeAvailable = 0;
                        double dischargeAvailableInterval = round(energyLevelPercentageMinimumBattery - Collections.min(energyLevelPredictionList.subList(intervalStartIndex, intervalEndIndex)), batteryPercentageRoundingPrecision);

                        if (dischargeAvailableInterval > 0) {
                            for (int i = intervalEndIndex - 1; i >= intervalStartIndex; i--) {
                                if (i - 1 < 0) {
                                    break;
                                }

                                double dischargeAvailableLeft = 0;

                                if (chargePercentageWantBatteryList.get(i) <= 0) {
                                    double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                    dischargeAvailableLeft = round(dischargePercentageAvailableBatteryList.get(i) - dischargeInUse, batteryPercentageRoundingPrecision);
                                }
                                if (dischargeAvailableLeft < 0) {
                                    changeIndex = i;
                                    dischargeAvailable = Math.max(dischargeAvailableInterval, dischargeAvailableLeft);
                                    break;
                                }
                            }
                        }

                        double dischargeNeeded = energyLevelPercentageMaximumBattery - energyLevelPredictionList.get(intervalEndIndex);
                        double changeValue = Math.max(dischargeAvailable, dischargeNeeded);

                        if (changeIndex == -1) {
                            changeIndex = intervalEndIndex;
                            changeValue = dischargeNeeded;
                        }

                        for (int i = changeIndex; i < energyLevelPredictionListSize; i++) {
                            energyLevelPredictionList.set(i, round(energyLevelPredictionList.get(i) + changeValue, batteryPercentageRoundingPrecision));
                        }
                    }

                    if (energyLevelPredictionValueBefore == energyLevelPredictionList.get(intervalEndIndex)) {
                        infoStr.insert(0, String.format("Battery energy level percentage calculation error at timestamp = %s", timestampsMillisList.get(predictionListIndex)));
                        infoStr.append(String.format("energyLevelPredictionListOptimised = %s \n", energyLevelPredictionList));
                        LOG.warning(infoStr.toString());
                        break;
                    } else if (dt > timeoutMillis) {
                        infoStr.insert(0, String.format("Battery energy level percentage calculation timed out during power limit optimisation at timestamp = %s", timestampsMillisList.get(predictionListIndex)));
                        infoStr.append(String.format("energyLevelPredictionListOptimised = %s \n", energyLevelPredictionList));
                        LOG.warning(infoStr.toString());
                        break;
                    }

                    energyLevelPredictionMaximum = Collections.max(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionListSize));
                    energyLevelPredictionMinimum = Collections.min(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionListSize));
                    dt = services.getTimerService().getCurrentTimeMillis() - dtStart;
                }

//                System.out.println("energyLevelPredictionListOptimisedLimits = " + energyLevelPredictionList + "\n");

                // Get day ahead asset
                EmsDayAheadAsset dayAheadAsset = getDayAheadAsset(energyOptimisationAsset, services);
                boolean useDayAheadTariffs = false;

                if (dayAheadAsset != null) {
                    useDayAheadTariffs = dayAheadAsset.getUseTariffDayAheadForecasts().orElse(false);
                }

                // Get the tariff forecasts from energy optimisation asset for 1 week, timestamps are ordered from newest to oldest (descending order)
                List<ValueDatapoint<?>> tariffExportDatapoints = getTariffDatapoints(energyOptimisationAsset, EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(), services);
                List<ValueDatapoint<?>> tariffImportDatapoints = getTariffDatapoints(energyOptimisationAsset, EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName(), services);

                // Overwrite the current tariff forecasts with the day ahead tariff forecasts
                if (useDayAheadTariffs) {
                    List<ValueDatapoint<?>> tariffExportDayAheadAssetDatapoints = getTariffDayAheadDatapoints(dayAheadAsset, EmsDayAheadAsset.TARIFF_EXPORT_DAY_AHEAD.getName(), services);
                    List<ValueDatapoint<?>> tariffImportDayAheadAssetDatapoints = getTariffDayAheadDatapoints(dayAheadAsset, EmsDayAheadAsset.TARIFF_IMPORT_DAY_AHEAD.getName(), services);

                    // Combine tariff export forecasts
                    if (!tariffExportDayAheadAssetDatapoints.isEmpty()) {
                        Map<Long, ValueDatapoint<?>> mergedMap = new HashMap<>();

                        for (ValueDatapoint<?> dp : tariffExportDatapoints) {
                            mergedMap.put(dp.getTimestamp(), dp);
                        }

                        for (ValueDatapoint<?> dp : tariffExportDayAheadAssetDatapoints) {
                            mergedMap.put(dp.getTimestamp(), dp);
                        }

                        tariffExportDatapoints = new ArrayList<>(mergedMap.values());
                        tariffExportDatapoints.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));
                    }

                    // Combine tariff import forecasts
                    if (!tariffImportDayAheadAssetDatapoints.isEmpty()) {
                        Map<Long, ValueDatapoint<?>> mergedMap = new HashMap<>();

                        for (ValueDatapoint<?> dp : tariffImportDatapoints) {
                            mergedMap.put(dp.getTimestamp(), dp);
                        }

                        for (ValueDatapoint<?> dp : tariffImportDayAheadAssetDatapoints) {
                            mergedMap.put(dp.getTimestamp(), dp);
                        }

                        tariffImportDatapoints = new ArrayList<>(mergedMap.values());
                        tariffImportDatapoints.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));
                    }
                }

                // Calculate optimal charge and discharge zone for each day based on tariffs
                Map<Long, Integer> chargeAndDischargeZonesMap = calculateTariffChargeAndDischargeZones(tariffImportDatapoints, tariffExportDatapoints, BATTERY_TARIFF_OPTIMISATION_WINDOW_DEFAULT);

                // Charge zone = 1, discharge zone = -1
                List<Integer> chargeAndDischargeZonesList = new ArrayList<>(Collections.nCopies(energyLevelPredictionListSize, 0));

                for (Map.Entry<Long, Integer> entry : chargeAndDischargeZonesMap.entrySet()) {
                    long timestampMillis = entry.getKey();
                    int value = entry.getValue();

                    int timestampIndex = timestampsMillisList.indexOf(timestampMillis);

                    if (timestampIndex != -1) {
                        // Add 1 to align list indices
                        chargeAndDischargeZonesList.set(timestampIndex + 1, value);
                    }
                }

//                System.out.println("chargeAndDischargeZonesList = " + chargeAndDischargeZonesList);

                // Calculate battery energy level percentage default list
                double energyLevelPercentageDefault = batteryCalculateEnergyLevelPercentageDefault(energyLevelPercentageMaximumBattery, energyLevelPercentageMinimumBattery, energyOptimisationAsset);
                List<Double> energyLevelPercentageDefaultList = new ArrayList<>(Collections.nCopies(energyLevelPredictionListSize, energyLevelPercentageDefault));

                for (int i = 0; i < energyLevelPredictionListSize; i++) {
                    if (chargeAndDischargeZonesList.get(i) == 1) {
                        energyLevelPercentageDefaultList.set(i, Double.valueOf(energyLevelPercentageMaximumBattery));
                    } else if (chargeAndDischargeZonesList.get(i) == -1) {
                        energyLevelPercentageDefaultList.set(i, Double.valueOf(energyLevelPercentageMinimumBattery));
                    }
                }

//                System.out.println("energyLevelPercentageDefaultList = " + energyLevelPercentageDefaultList);

                // Optimise forecast based on tariffs
                for (int i = 1; i < chargeAndDischargeZonesList.size() && i < energyLevelPredictionListSize; i++) {
                    if (chargeAndDischargeZonesList.get(i) == 1 && energyLevelPredictionList.get(i) < energyLevelPercentageDefaultList.get(i)) {
                        // Interval from charge zone index till end of forecast
                        Double energyLevelIntervalMaximum = Collections.max(energyLevelPredictionList.subList(i, energyLevelPredictionListSize));
                        double chargeSpaceOverall = round(energyLevelPercentageMaximumBattery - energyLevelIntervalMaximum, batteryPercentageRoundingPrecision);

                        if (chargeSpaceOverall > 0) {
                            double chargeNeeded = round(energyLevelPercentageDefaultList.get(i) - energyLevelPredictionList.get(i), batteryPercentageRoundingPrecision);
                            double chargeAvailableLeft = 0;

                            if (dischargePercentageWantBatteryList.get(i) >= 0) {
                                double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                chargeAvailableLeft = round(chargePercentageAvailableBatteryList.get(i) - chargeInUse, batteryPercentageRoundingPrecision);
                            }

                            if (chargeAvailableLeft > 0) {
                                chargeAvailableLeft = Math.min(Math.min(chargeSpaceOverall, chargeAvailableLeft), chargeNeeded);
                                chargeSpaceOverall = round(chargeSpaceOverall - chargeAvailableLeft, batteryPercentageRoundingPrecision);

                                for (int j = i; j < energyLevelPredictionListSize; j++) {
                                    energyLevelPredictionList.set(j, round(energyLevelPredictionList.get(j) + chargeAvailableLeft, batteryPercentageRoundingPrecision));
                                }
                            }
                        }

                        // Get the index of the first maximum
                        energyLevelIntervalMaximum = Collections.max(energyLevelPredictionList.subList(i, energyLevelPredictionListSize));
                        int energyLevelIntervalMaximumIndex = energyLevelPredictionList.indexOf(energyLevelIntervalMaximum);

                        if (chargeSpaceOverall <= 0 && i < energyLevelIntervalMaximumIndex) {
                            double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                            double chargeAvailableLeft = round(chargePercentageAvailableBatteryList.get(i) - chargeInUse, batteryPercentageRoundingPrecision);

                            int energyLevelIntervalIndex = 0;
                            dt = 0;
                            dtStart = services.getTimerService().getCurrentTimeMillis();

                            while (chargeAvailableLeft > 0 && energyLevelIntervalIndex < energyLevelIntervalMaximumIndex) {

                                for (int k = i + 1; k <= energyLevelIntervalMaximumIndex; k++) {
                                    energyLevelIntervalIndex = k;
                                    double chargeNeeded = round(energyLevelPredictionList.get(k) - energyLevelPredictionList.get(i), batteryPercentageRoundingPrecision);

                                    // Only allow moving charging moment into charge zone if no charge is wanted at index k
                                    if (chargeNeeded > 0 && chargePercentageWantBatteryList.get(k) == 0) {
                                        double energyLevelIntervalMaximum2 = Collections.max(energyLevelPredictionList.subList(i, k));
                                        double chargeSpaceInterval = round(energyLevelPercentageMaximumBattery - energyLevelIntervalMaximum2, batteryPercentageRoundingPrecision);

                                        if (chargeSpaceInterval > 0) {
                                            double chargeChange = Math.min(Math.min(chargeAvailableLeft, chargeNeeded), chargeSpaceInterval);
                                            chargeAvailableLeft = round(chargeAvailableLeft - chargeChange, batteryPercentageRoundingPrecision);

                                            for (int j = i; j < k; j++) {
                                                energyLevelPredictionList.set(j, round(energyLevelPredictionList.get(j) + chargeChange, batteryPercentageRoundingPrecision));
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (dt > timeoutMillis) {
                                    infoStr.insert(0, String.format("Battery energy level percentage calculation timed out during tariff charge optimisation at timestamp = %s", timestampsMillisList.get(i)));
                                    infoStr.append(String.format("energyLevelPredictionListOptimised = %s \n", energyLevelPredictionList));
                                    LOG.warning(infoStr.toString());
                                    break;
                                }

                                dt = services.getTimerService().getCurrentTimeMillis() - dtStart;
                            }
                        }
                    } else if (chargeAndDischargeZonesList.get(i) == -1 && energyLevelPredictionList.get(i) > energyLevelPercentageDefaultList.get(i)) {
                        // Interval from discharge zone index till end of forecast
                        Double energyLevelIntervalMinimum = Collections.min(energyLevelPredictionList.subList(i, energyLevelPredictionListSize));
                        double dischargeSpaceOverall = round(energyLevelPercentageMinimumBattery - energyLevelIntervalMinimum, batteryPercentageRoundingPrecision);

                        if (dischargeSpaceOverall < 0) {
                            double dischargeNeeded = round(energyLevelPercentageDefaultList.get(i) - energyLevelPredictionList.get(i), batteryPercentageRoundingPrecision);
                            double dischargeAvailableLeft = 0;

                            if (chargePercentageWantBatteryList.get(i) <= 0) {
                                double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                dischargeAvailableLeft = round(dischargePercentageAvailableBatteryList.get(i) - dischargeInUse, batteryPercentageRoundingPrecision);
                            }

                            if (dischargeAvailableLeft < 0) {
                                dischargeAvailableLeft = Math.max(Math.max(dischargeSpaceOverall, dischargeAvailableLeft), dischargeNeeded);
                                dischargeSpaceOverall = round(dischargeSpaceOverall - dischargeAvailableLeft, batteryPercentageRoundingPrecision);

                                for (int j = i; j < energyLevelPredictionListSize; j++) {
                                    energyLevelPredictionList.set(j, round(energyLevelPredictionList.get(j) + dischargeAvailableLeft, batteryPercentageRoundingPrecision));
                                }
                            }
                        }

                        // Get the index of the first minimum
                        energyLevelIntervalMinimum = Collections.min(energyLevelPredictionList.subList(i, energyLevelPredictionListSize));
                        int energyLevelIntervalMinimumIndex = energyLevelPredictionList.indexOf(energyLevelIntervalMinimum);

                        if (dischargeSpaceOverall >= 0 && i < energyLevelIntervalMinimumIndex) {
                            double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                            double dischargeAvailableLeft = round(dischargePercentageAvailableBatteryList.get(i) - dischargeInUse, batteryPercentageRoundingPrecision);

                            int energyLevelIntervalIndex = 0;
                            dt = 0;
                            dtStart = services.getTimerService().getCurrentTimeMillis();

                            while (dischargeAvailableLeft < 0 && energyLevelIntervalIndex < energyLevelIntervalMinimumIndex) {

                                for (int k = i + 1; k <= energyLevelIntervalMinimumIndex; k++) {
                                    energyLevelIntervalIndex = k;
                                    double dischargeNeeded = round(energyLevelPredictionList.get(k) - energyLevelPredictionList.get(i), batteryPercentageRoundingPrecision);

                                    // Only allow moving discharging moment into discharge zone if no discharge is wanted at index k
                                    if (dischargeNeeded < 0 && dischargePercentageWantBatteryList.get(k) == 0) {
                                        double energyLevelIntervalMinimum2 = Collections.min(energyLevelPredictionList.subList(i, k));
                                        double dischargeSpaceInterval = round(energyLevelPercentageMinimumBattery - energyLevelIntervalMinimum2, batteryPercentageRoundingPrecision);

                                        if (dischargeSpaceInterval < 0) {
                                            double dischargeChange = Math.max(Math.max(dischargeAvailableLeft, dischargeNeeded), dischargeSpaceInterval);
                                            dischargeAvailableLeft = round(dischargeAvailableLeft - dischargeChange, batteryPercentageRoundingPrecision);

                                            for (int j = i; j < k; j++) {
                                                energyLevelPredictionList.set(j, round(energyLevelPredictionList.get(j) + dischargeChange, batteryPercentageRoundingPrecision));
                                            }
                                            break;
                                        }
                                    }
                                }

                                if (dt > timeoutMillis) {
                                    infoStr.insert(0, String.format("Battery energy level percentage calculation timed out during tariff discharge optimisation at timestamp = %s", timestampsMillisList.get(i)));
                                    infoStr.append(String.format("energyLevelPredictionListOptimised = %s \n", energyLevelPredictionList));
                                    LOG.warning(infoStr.toString());
                                    break;
                                }

                                dt = services.getTimerService().getCurrentTimeMillis() - dtStart;
                            }
                        }
                    }
                }
            }

//            System.out.println("energyLevelPredictionListOptimisedLimitsAndTariffs = " + energyLevelPredictionList + "\n");

            // Get the current energy level percentage target
            int batteryEnergyLevelPercentageTarget = (int) Math.round(energyLevelPredictionList.get(1));
            batteryEnergyLevelPercentageTargets.put(batteryAssetId, batteryEnergyLevelPercentageTarget);

            // Calculate energy level percentage change per interval
            List<Double> percentageChangeBatteryList = new ArrayList<>();

            for (int i = 0; i < energyLevelPredictionList.size() - 1; i++) {
                double percentageChange = energyLevelPredictionList.get(i + 1) - energyLevelPredictionList.get(i);
                percentageChangeBatteryList.add(percentageChange);
            }

//            System.out.println("percentageChangeBatteryList = " + percentageChangeBatteryList);

            // Calculate total power change for the EMS
            List<Double> powerChangeTotalList = new ArrayList<>();

            for (double percentageChange : percentageChangeBatteryList) {
                double power = 0;

                if (percentageChange > 0) {
                    power = percentageChange * energyCapacityBattery / (intervalHour * chargeEfficiencyBattery);
                } else if (percentageChange < 0) {
                    power = percentageChange * energyCapacityBattery * dischargeEfficiencyBattery / (10000 * intervalHour);
                }

                powerChangeTotalList.add(round(power, 3));
            }

//            System.out.println("powerChangeTotalList = " + powerChangeTotalList);

            // Update list with power flexible changes
            for (int i = 0; i < powerChangeTotalList.size() - 1; i++) {
                totalPowerConsumptionProductionFlexibleList.set(i, totalPowerConsumptionProductionFlexibleList.get(i) - powerChangeTotalList.get(i));
            }

//            System.out.println("totalPowerConsumptionProductionFlexibleList = " + totalPowerConsumptionProductionFlexibleList);

            List<ValueDatapoint<?>> energyLevelPercentageForecast = new ArrayList<>();
            List<ValueDatapoint<?>> powerSetpointForecast = new ArrayList<>();

            // Update energy level percentage forecast starting after current time
            for (int i = 1; i < numberOfTimestamps; i++) {
                energyLevelPercentageForecast.add(new ValueDatapoint<>(timestampsMillisList.get(i), (int) Math.round(energyLevelPredictionList.get(i))));
            }

            // Update power set-point starting from current power limit
            for (int i = 0; i < numberOfTimestamps; i++) {
                powerSetpointForecast.add(new ValueDatapoint<>(timestampsMillisList.get(i), powerChangeTotalList.get(i)));
            }

            services.getAssetPredictedDatapointService().updateValues(batteryAssetId, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE.getName(), energyLevelPercentageForecast);
            services.getAssetPredictedDatapointService().updateValues(batteryAssetId, EmsElectricityBatteryAsset.POWER_SETPOINT.getName(), powerSetpointForecast);
        }

        return batteryEnergyLevelPercentageTargets;
    }

    private Map<String, ChargeDischarge> batteryCalculatePowerFlexibleAvailable(List<EmsElectricityBatteryAsset> electricityBatteryAssets) {
        Map<String, ChargeDischarge> powerFlexibleAvailable = new HashMap<>();

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            boolean allowCharging = electricityBatteryAsset.getAllowCharging().orElse(false);
            boolean allowDischarging = electricityBatteryAsset.getAllowDischarging().orElse(false);
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);
            Integer energyLevelPercentageMaximum = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
            Integer energyLevelPercentageMinimum = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);

            double chargePowerMaximum = electricityBatteryAsset.getChargePowerMaximum().orElse(0.0);
            double dischargePowerMaximum = electricityBatteryAsset.getDischargePowerMaximum().orElse(0.0);

            if (!allowCharging || energyLevelPercentageMaximum == null || energyLevelPercentage == null) {
                chargePowerMaximum = 0.0;
            } else if (energyLevelPercentage >= energyLevelPercentageMaximum) {
                chargePowerMaximum = 0.0;
            }

            if (!allowDischarging || energyLevelPercentageMinimum == null || energyLevelPercentage == null) {
                dischargePowerMaximum = 0.0;
            } else if (energyLevelPercentage <= energyLevelPercentageMinimum) {
                dischargePowerMaximum = 0.0;
            }

            powerFlexibleAvailable.put(electricityBatteryAsset.getId(), new ChargeDischarge(chargePowerMaximum, dischargePowerMaximum));
        }

        return powerFlexibleAvailable;
    }

    private Map<String, Double> batteryCalculatePowerSetpoints(EmsEnergyOptimisationAsset energyOptimisationAsset, List<EmsElectricityBatteryAsset> electricityBatteryAssets, long powerSetpointTimestampMillisLatest, Map<String, Integer> batteryEnergyLevelPercentageTargets, Services services, String logPrefixEnergyOptimisation) {
        Map<String, ChargeDischarge> powerFlexibleAvailable = batteryCalculatePowerFlexibleAvailable(electricityBatteryAssets);
        Map<String, Double> powerSetpointsNew;

        // Check if power net updated since last batteries power set-point update
        Double powerNet = energyOptimisationAsset.getPowerNet().orElse(null);
        long powerNetTimestampMillis = energyOptimisationAsset.getPowerNetTimestamp().orElse(0L);

        // Turn off batteries that do not have flexible power available during main power meter disconnect
        if (powerSetpointTimestampMillisLatest > powerNetTimestampMillis || powerNet == null) {
            powerSetpointsNew = batteryCheckPowerSetpointsCurrent(electricityBatteryAssets, powerFlexibleAvailable);
            return powerSetpointsNew;
        }

        // Check if power limits are present
        Double powerLimitMaximumProfileTotal = energyOptimisationAsset.getPowerLimitMaximumProfileTotal().orElse(null);
        Double powerLimitMinimumProfileTotal = energyOptimisationAsset.getPowerLimitMinimumProfileTotal().orElse(null);

        // Calculate debounce power limits
        Double powerLimitMaximumVirtual = calculatePowerLimitVirtual(energyOptimisationAsset, powerLimitMaximumProfileTotal, "max");
        Double powerLimitMinimumVirtual = calculatePowerLimitVirtual(energyOptimisationAsset, powerLimitMinimumProfileTotal, "min");

        // Send warning LOG message when too large fluctuation margins are set
        if (powerLimitMaximumProfileTotal != null && powerLimitMinimumProfileTotal != null && powerLimitMaximumVirtual != null && powerLimitMinimumVirtual != null && powerLimitMaximumVirtual < powerLimitMinimumVirtual) {
            double diffPowerLimit = powerLimitMaximumProfileTotal - powerLimitMinimumProfileTotal;
            double fluctuationMarginSum = Math.abs(powerLimitMaximumProfileTotal - powerLimitMaximumVirtual) + Math.abs(powerLimitMinimumProfileTotal - powerLimitMinimumVirtual);

            Long powerLimitMaximumProfileTimestampMillis = energyOptimisationAsset.getPowerLimitMaximumProfileTotalTimestamp().orElse(null);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
            String powerLimitMaximumProfileDateTime = "";

            if (powerLimitMaximumProfileTimestampMillis != null) {
                powerLimitMaximumProfileDateTime = formatter.format(Instant.ofEpochMilli(powerLimitMaximumProfileTimestampMillis));
            }

            LOG.warning(String.format("%s; Failed to perform '%s' energy optimisation method. The difference between '%s' - %s = %s kW is smaller than the fluctuation margin sum = %s kW for timestamp='%s'",
                    logPrefixEnergyOptimisation, optimisationMethodName, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(),
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(), diffPowerLimit, fluctuationMarginSum, powerLimitMaximumProfileDateTime));
        }

        // Calculate virtual power consumption
        Map<String, Double> powerSetpointsCurrent = new HashMap<>();

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            double powerSetpoint = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);
            powerSetpointsCurrent.put(electricityBatteryAsset.getId(), powerSetpoint);
        }

        double powerSetpointsCurrentSum = powerSetpointsCurrent.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerConsumptionVirtual = powerNet - powerSetpointsCurrentSum;

        // Calculate new power set-points without limits
        Map<String, Double> powerSetpointsNewWithoutLimits = batteryCalculatePowerSetpointsWithoutLimits(electricityBatteryAssets, batteryEnergyLevelPercentageTargets, energyOptimisationAsset, powerFlexibleAvailable, services);

        // Apply power limits and adjust new power set-points
        double powerSetpointsNewWithoutLimitsSum = powerSetpointsNewWithoutLimits.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerNetVirtualNew = powerConsumptionVirtual + powerSetpointsNewWithoutLimitsSum;

        double powerLimitMaximumFluctuation = calculatePowerFluctuationMargin(energyOptimisationAsset, powerLimitMaximumProfileTotal, "max");
        double powerLimitMinimumFluctuation = calculatePowerFluctuationMargin(energyOptimisationAsset, powerLimitMinimumProfileTotal, "min");

        if (powerLimitMaximumVirtual != null && (powerNetVirtualNew + powerLimitMaximumFluctuation) > powerLimitMaximumVirtual) {
            powerSetpointsNew = batteryCalculatePowerSetpointsOnLimitBreach(powerNetVirtualNew, powerLimitMaximumVirtual, electricityBatteryAssets, powerFlexibleAvailable, powerSetpointsNewWithoutLimits);
        } else if (powerLimitMinimumVirtual != null && (powerNetVirtualNew - powerLimitMinimumFluctuation) < powerLimitMinimumVirtual) {
            powerSetpointsNew = batteryCalculatePowerSetpointsOnLimitBreach(powerNetVirtualNew, powerLimitMinimumVirtual, electricityBatteryAssets, powerFlexibleAvailable, powerSetpointsNewWithoutLimits);
        } else {
            powerSetpointsNew = powerSetpointsNewWithoutLimits;
        }

        // Send warning LOG message on power limit breach
        double powerSetpointsNewSum = powerSetpointsNew.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerNetNewVirtual = powerConsumptionVirtual + powerSetpointsNewSum;

        if (powerLimitMaximumProfileTotal != null && powerNetNewVirtual > powerLimitMaximumProfileTotal) {
            double powerReductionShortage = round((powerNetNewVirtual - powerLimitMaximumProfileTotal), 3);
            LOG.warning(String.format("%s; Not enough flexible power to get below power limit maximum; Shortage of %s kW", logPrefixEnergyOptimisation, powerReductionShortage));
        } else if (powerLimitMinimumProfileTotal != null && powerNetNewVirtual < powerLimitMinimumProfileTotal) {
            double powerReductionShortage = round((powerNetNewVirtual - powerLimitMinimumProfileTotal), 3);
            LOG.warning(String.format("%s; Not enough flexible power to get above power limit minimum; Shortage of %s kW", logPrefixEnergyOptimisation, powerReductionShortage));
        }

        return powerSetpointsNew;
    }

    private Map<String, Double> batteryCalculatePowerSetpointsOnLimitBreach(double power, double powerLimitVirtual, List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, ChargeDischarge> powerFlexibleAvailable, Map<String, Double> powerSetpointsNewWithoutLimits) {
        Map<String, Double> powerSetpointsNew = new HashMap<>();
        double powerChangeNeeded = round(power - powerLimitVirtual, 3);

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();

            double chargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).charge;
            double dischargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).discharge;
            double powerSetpointNewWithoutLimits = powerSetpointsNewWithoutLimits.getOrDefault(electricityBatteryAssetId, 0.0);
            double chargePowerAvailableTotal = chargePowerAvailable - powerSetpointNewWithoutLimits;
            double dischargePowerAvailableTotal = dischargePowerAvailable - powerSetpointNewWithoutLimits;

            double powerSetpointChange = 0.0;

            if (powerChangeNeeded > 0) {
                powerSetpointChange = Math.max(-powerChangeNeeded, dischargePowerAvailableTotal);
            } else if (powerChangeNeeded < 0) {
                powerSetpointChange = Math.min(-powerChangeNeeded, chargePowerAvailableTotal);
            }

            powerChangeNeeded = round(powerChangeNeeded + powerSetpointChange, 3);

            double powerSetpointNew = round(powerSetpointNewWithoutLimits + powerSetpointChange, 3);
            powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
        }

        return powerSetpointsNew;
    }

    private Map<String, Double> batteryCalculatePowerSetpointsWithoutLimits(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, Integer> batteryEnergyLevelPercentageTargets, EmsEnergyOptimisationAsset energyOptimisationAsset, Map<String, ChargeDischarge> powerFlexibleAvailable, Services services) {
        // Find battery power set-points for a system without power limits
        Map<String, Double> powerSetpointsNew = new HashMap<>();

        long intervalMillis = 15 * 60000;
        long endTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long startTimeMillis = endTimeMillis - endTimeMillis % intervalMillis;

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);

            // Set initial power set-point of 0
            powerSetpointsNew.put(electricityBatteryAssetId, 0.0);

            if (energyLevelPercentage == null) {
                continue;
            }

            double energyCapacity = electricityBatteryAsset.getEnergyCapacity().orElse(0.0);
            double powerSetpointCurrent = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);

            // Get battery energy level percentage target
            Integer energyLevelPercentageTarget = batteryEnergyLevelPercentageTargets.get(electricityBatteryAssetId);

            if (energyLevelPercentageTarget == null) {
                Integer energyLevelPercentageMaximum = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
                Integer energyLevelPercentageMinimum = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);
                energyLevelPercentageTarget = batteryCalculateEnergyLevelPercentageDefault(energyLevelPercentageMaximum, energyLevelPercentageMinimum, energyOptimisationAsset);
            }

            // Get battery power set-point target
            AssetDatapointAllQuery assetDatapointQueryPredicted = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);
            List<ValueDatapoint<?>> powerSetpointForecastList = services.getAssetPredictedDatapointService().queryDatapoints(electricityBatteryAssetId, EmsElectricityBatteryAsset.POWER_SETPOINT.getName(), assetDatapointQueryPredicted);

            // Calculate battery power set-point target
            double energyLevelPercentageRounded = round(energyLevelPercentage, 1);
            double powerNeeded = round((energyLevelPercentageTarget - energyLevelPercentageRounded) * 0.01 * energyCapacity, 3);
            double powerSetpointNeeded = round(powerNeeded * 60 * BATTERY_POWER_SETPOINT_RESPONSIVENESS_DEFAULT, 3);

            if (energyLevelPercentageRounded < energyLevelPercentageTarget || (powerSetpointCurrent > 0.0 && energyLevelPercentageRounded < energyLevelPercentageTarget)) {
                // Start charging or continue charging
                int chargeEfficiencyPercentage = electricityBatteryAsset.getChargeEfficiency().orElse(100);

                double chargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).charge;
                double powerSetpointEfficiency = round(powerSetpointNeeded / (chargeEfficiencyPercentage * 0.01), 3);
                double powerSetpointNew = Math.min(powerSetpointEfficiency, chargePowerAvailable);

                if (!powerSetpointForecastList.isEmpty()) {
                    Double powerSetpointForecast = (Double) powerSetpointForecastList.getLast().getValue();

                    if (powerSetpointForecast != null && powerSetpointForecast > 0) {
                        powerSetpointNew = Math.min(powerSetpointNew, powerSetpointForecast);
                    }
                }

                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            } else if (energyLevelPercentageRounded > energyLevelPercentageTarget || (powerSetpointCurrent < 0.0 && energyLevelPercentageRounded > energyLevelPercentageTarget)) {
                // Start discharging or continue discharging
                int dischargeEfficiencyPercentage = electricityBatteryAsset.getDischargeEfficiency().orElse(100);

                double dischargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).discharge;
                double powerSetpointEfficiency = round(powerSetpointNeeded / (dischargeEfficiencyPercentage * 0.01), 3);
                double powerSetpointNew = Math.max(powerSetpointEfficiency, dischargePowerAvailable);

                if (!powerSetpointForecastList.isEmpty()) {
                    Double powerSetpointForecast = (Double) powerSetpointForecastList.getLast().getValue();

                    if (powerSetpointForecast != null && powerSetpointForecast < 0) {
                        powerSetpointNew = Math.max(powerSetpointNew, powerSetpointForecast);
                    }
                }

                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            }
        }

        return powerSetpointsNew;
    }

    private void batteryCheckConnection(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Services services) {
        // This method checks if a battery is connected based on if the 'power' and 'energyLevelPercentage' attributes update within the active time interval
        String connected = EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.connected.toString();
        String disconnected = EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.disconnected.toString();

        long currentTimestampMillis = services.getTimerService().getCurrentTimeMillis();
        long activePeriodMillis = ACTIVE_PERIOD_MINUTES * 60000L;

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();

            Optional<EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType> connectionStatusValue = electricityBatteryAsset.getConnectionStatus();
            String connectionStatusPrevious = "";

            if (connectionStatusValue.isPresent()) {
                connectionStatusPrevious = connectionStatusValue.get().toString();
            }

            Double powerBattery = electricityBatteryAsset.getPower().orElse(null);
            long powerTimestampMillisBattery = electricityBatteryAsset.getPowerTimestamp().orElse(0L);

            Double energyLevelPercentageBattery = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);
            long energyLevelPercentageTimestampMillisBattery = electricityBatteryAsset.getEnergyLevelPercentageTimestamp().orElse(0L);

            String connectionStatusCurrent = disconnected;

            if ((currentTimestampMillis - powerTimestampMillisBattery) < activePeriodMillis && powerBattery != null && (currentTimestampMillis - energyLevelPercentageTimestampMillisBattery) < activePeriodMillis && energyLevelPercentageBattery != null) {
                connectionStatusCurrent = connected;
            }

            if (connectionStatusCurrent.equals(connected) && connectionStatusPrevious.equals(disconnected)) {
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.CONNECTION_STATUS, EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.connected), getClass().getSimpleName());
            } else if (connectionStatusCurrent.equals(disconnected) && connectionStatusPrevious.equals(connected)) {
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.CONNECTION_STATUS, EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.disconnected), getClass().getSimpleName());
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE, null), getClass().getSimpleName());
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.POWER, null), getClass().getSimpleName());
            } else if (connectionStatusCurrent.equals(connected) && connectionStatusPrevious.isEmpty()) {
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.CONNECTION_STATUS, EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.connected), getClass().getSimpleName());
            } else if (connectionStatusCurrent.equals(disconnected) && connectionStatusPrevious.isEmpty()) {
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.CONNECTION_STATUS, EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType.disconnected), getClass().getSimpleName());
            }
        }
    }

    private Map<String, Double> batteryCheckPowerSetpointsCurrent(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, ChargeDischarge> powerFlexibleAvailable) {
        Map<String, Double> powerSetpointsNew = new HashMap<>();

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);

            if (energyLevelPercentage == null) {
                powerSetpointsNew.put(electricityBatteryAssetId, 0.0);
                continue;
            }

            double powerSetpointCurrent = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);
            double chargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).charge;
            double dischargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId).discharge;

            // Set initial power set-point new
            double powerSetpointNew = 0.0;

            // Check if power set-point current is within available power limits
            if (powerSetpointCurrent > 0.0) {
                powerSetpointNew = Math.min(powerSetpointCurrent, chargePowerAvailable);
            } else if (powerSetpointCurrent < 0.0) {
                powerSetpointNew = Math.max(powerSetpointCurrent, dischargePowerAvailable);
            }

            powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
        }

        return powerSetpointsNew;
    }

    private void batteryCheckSetup(List<EmsElectricityBatteryAsset> electricityBatteryAssets) {
        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            // Check if the battery is enabled
            boolean allowCharging = electricityBatteryAsset.getAllowDischarging().orElse(false);
            boolean allowDischarging = electricityBatteryAsset.getAllowCharging().orElse(false);

            if (!allowCharging && !allowDischarging) {
                continue;
            }

            String logPrefixBattery = String.format("assetType='%s', assetId='%s', assetName='%s'", electricityBatteryAsset.getAssetType(), electricityBatteryAsset.getId(), electricityBatteryAsset.getAssetName());

            // Check if the following attributes are connected
            Map<String, Object> requiredFieldsConnection = new HashMap<>();

            requiredFieldsConnection.put(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE.getName(), electricityBatteryAsset.getEnergyLevelPercentage().orElse(null));
            requiredFieldsConnection.put(EmsElectricityBatteryAsset.POWER.getName(), electricityBatteryAsset.getPower().orElse(null));

            List<String> missingFieldsConnection = requiredFieldsConnection.entrySet().stream()
                    .filter(entry -> entry.getValue() == null)
                    .map(Map.Entry::getKey)
                    .toList();

            if (!missingFieldsConnection.isEmpty()) {
                LOG.warning(String.format("%s; Can't use battery for flexible power. The following attributes are not connected: %s",
                        logPrefixBattery,
                        String.join(", ", missingFieldsConnection.stream().map(attr -> "'" + attr + "'").toList())
                ));
            }

            // Check if the following attributes are set
            Map<String, Object> requiredFieldsSetup = new HashMap<>();

            Integer energyLevelPercentageMaximum = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
            Integer energyLevelPercentageMinimum = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);
            Integer chargeEfficiency = electricityBatteryAsset.getChargeEfficiency().orElse(null);
            Integer dischargeEfficiency = electricityBatteryAsset.getDischargeEfficiency().orElse(null);

            requiredFieldsSetup.put(EmsElectricityBatteryAsset.CHARGE_EFFICIENCY.getName(), chargeEfficiency);
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.CHARGE_POWER_MAXIMUM.getName(), electricityBatteryAsset.getChargePowerMaximum().orElse(null));
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.DISCHARGE_EFFICIENCY.getName(), dischargeEfficiency);
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.DISCHARGE_POWER_MAXIMUM.getName(), electricityBatteryAsset.getDischargePowerMaximum().orElse(null));
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.ENERGY_CAPACITY.getName(), electricityBatteryAsset.getEnergyCapacity().orElse(null));
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MAXIMUM.getName(), energyLevelPercentageMaximum);
            requiredFieldsSetup.put(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MINIMUM.getName(), energyLevelPercentageMinimum);

            List<String> missingFieldsSetup = requiredFieldsSetup.entrySet().stream()
                    .filter(entry -> entry.getValue() == null)
                    .map(Map.Entry::getKey)
                    .toList();

            if (!missingFieldsSetup.isEmpty()) {
                LOG.warning(String.format("%s; Can't use battery for flexible power. The following attributes are not set: %s",
                        logPrefixBattery,
                        String.join(", ", missingFieldsSetup.stream().map(attr -> "'" + attr + "'").toList())
                ));
            }

            // Check if the following attributes are set correctly
            if (energyLevelPercentageMaximum != null && energyLevelPercentageMinimum != null && energyLevelPercentageMaximum <= energyLevelPercentageMinimum) {
                LOG.warning(String.format("%s; Can't use battery for flexible power. '%s' = %s%% is smaller than or equal to '%s'= %s%%)", logPrefixBattery,
                        EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MAXIMUM.getName(), energyLevelPercentageMaximum, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MINIMUM.getName(), energyLevelPercentageMinimum));
            }

            if (chargeEfficiency != null && chargeEfficiency <= 0) {
                LOG.warning(String.format("The charge efficiency = %s%% is smaller than or equal to 0", chargeEfficiency));
            }

            if (dischargeEfficiency != null && dischargeEfficiency <= 0) {
                LOG.warning(String.format("The discharge efficiency = %s%% is smaller than or equal to 0", dischargeEfficiency));
            }
        }
    }

    private Map<String, Integer> batteryGetEnergyLevelPercentageTargetsCurrent(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Services services) {
        Map<String, Integer> batteryEnergyLevelPercentageTargetsCurrent = new HashMap<>();

        long intervalMillis = 15 * 60000;
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long endTimeMillis = currentTimeMillis - currentTimeMillis % intervalMillis + intervalMillis;

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String batteryAssetId = electricityBatteryAsset.getId();
            AssetDatapointAllQuery assetDatapointQueryPredicted = new AssetDatapointAllQuery(currentTimeMillis, endTimeMillis);
            List<ValueDatapoint<?>> energyLevelPercentagePredictedCurrent = services.getAssetPredictedDatapointService().queryDatapoints(batteryAssetId, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE.getName(), assetDatapointQueryPredicted);

            Integer energyLevelPercentageTarget = null;

            if (!energyLevelPercentagePredictedCurrent.isEmpty()) {
                Double value = ((Double) energyLevelPercentagePredictedCurrent.getLast().getValue());
                energyLevelPercentageTarget = (value != null) ? value.intValue() : null;
            }

            batteryEnergyLevelPercentageTargetsCurrent.put(batteryAssetId, energyLevelPercentageTarget);
        }

        return batteryEnergyLevelPercentageTargetsCurrent;
    }

    private List<EmsElectricityBatteryAsset> batteryOrder(List<EmsElectricityBatteryAsset> electricityBatteryAssets) {
        List<EmsElectricityBatteryAsset> sorted = new ArrayList<>(electricityBatteryAssets);

        sorted.sort(Comparator
                .comparing((EmsElectricityBatteryAsset b) -> b.getEnergyCapacity().isPresent() && b.getDischargePowerMaximum().orElse(0.0) != 0.0)
                .reversed()
                .thenComparing(b -> b.getDischargePowerMaximum().orElse(0.0) == 0
                                ? null
                                : round(b.getEnergyCapacity().orElse(0.0) / b.getDischargePowerMaximum().orElse(0.0), 2),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(EmsElectricityBatteryAsset::getId)
        );

        return sorted;
    }

    private void batteryUpdatePowerSetpoints(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, Double> powerSetpointsNew, Services services) {
        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double powerSetpointNew = powerSetpointsNew.get(electricityBatteryAssetId);

            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.POWER_SETPOINT, powerSetpointNew), getClass().getSimpleName());
        }
    }

    private double calculatePowerFluctuationMargin(EmsEnergyOptimisationAsset energyOptimisationAsset, Double powerLimitProfileTotal, String maxOrMin) {
        if (powerLimitProfileTotal == null) {
            return 0.0;
        }

        boolean isMin = "min".equals(maxOrMin);
        String fluctuationMarginAttributeName = POWER_LIMIT_MAXIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME;

        if (isMin) {
            fluctuationMarginAttributeName = POWER_LIMIT_MINIMUM_FLUCTUATION_MARGIN_ATTRIBUTE_NAME;
        }

        Double powerLimitFluctuationMargin = (Double) energyOptimisationAsset.getAttribute(fluctuationMarginAttributeName)
                .flatMap(Attribute::getValue)
                .orElse(null);

        if (powerLimitFluctuationMargin == null) {
            powerLimitFluctuationMargin = powerLimitProfileTotal * POWER_LIMIT_FLUCTUATION_MARGIN_PERCENTAGE_DEFAULT * 0.01;
        }

        return round(Math.abs(powerLimitFluctuationMargin), 3);
    }

    private Double calculatePowerLimitVirtual(EmsEnergyOptimisationAsset energyOptimisationAsset, Double powerLimitProfileTotal, String maxOrMin) {
        if (powerLimitProfileTotal == null) {
            return null;
        }

        double powerLimitFluctuationMargin = calculatePowerFluctuationMargin(energyOptimisationAsset, powerLimitProfileTotal, maxOrMin);
        boolean isMin = "min".equals(maxOrMin);

        double powerLimitVirtual;

        if (isMin) {
            powerLimitVirtual = powerLimitProfileTotal + powerLimitFluctuationMargin;
        } else {
            powerLimitVirtual = powerLimitProfileTotal - powerLimitFluctuationMargin;
        }

        return round(powerLimitVirtual, 3);
    }

    private Map<Long, Integer> calculateTariffChargeAndDischargeZones(List<ValueDatapoint<?>> tariffImportDatapoints, List<ValueDatapoint<?>> tariffExportDatapoints, int window) {
        Map<Long, Integer> chargeAndDischargeZonesMap = new HashMap<>();

        // Return empty map when there is no tariff forecast present
        if (tariffImportDatapoints.size() < window || tariffExportDatapoints.size() < window) {
            return chargeAndDischargeZonesMap;
        }

        // Find the best import/export tariff window for each day
        List<ValueDatapoint<?>> tariffImportMovingAverage = movingAverage(tariffImportDatapoints, window);
        List<ValueDatapoint<?>> tariffExportMovingAverage = movingAverage(tariffExportDatapoints, window);

        ZoneId zoneId = ZoneId.systemDefault();

        Map<LocalDate, IndexedDatapoint> tariffImportDailyMinimumMap =
                IntStream.range(0, tariffImportMovingAverage.size())
                        .mapToObj(i -> {
                            ValueDatapoint<?> dp = tariffImportMovingAverage.get(i);
                            return new IndexedDatapoint(dp.getTimestamp(), (Double) dp.getValue(), i);
                        })
                        .collect(Collectors.toMap(
                                dp -> Instant.ofEpochMilli(dp.timestamp()).atZone(zoneId).toLocalDate(),
                                Function.identity(),
                                BinaryOperator.minBy(Comparator.comparing(IndexedDatapoint::value))
                        ));

        Map<LocalDate, IndexedDatapoint> tariffExportDailyMinimumMap =
                IntStream.range(0, tariffExportMovingAverage.size())
                        .mapToObj(i -> {
                            ValueDatapoint<?> dp = tariffExportMovingAverage.get(i);
                            return new IndexedDatapoint(dp.getTimestamp(), (Double) dp.getValue(), i);
                        })
                        .collect(Collectors.toMap(
                                dp -> Instant.ofEpochMilli(dp.timestamp()).atZone(zoneId).toLocalDate(),
                                Function.identity(),
                                BinaryOperator.minBy(Comparator.comparing(IndexedDatapoint::value))
                        ));

        Map<Long, Integer> chargeZonesMap = new HashMap<>();
        Map<Long, Integer> dischargeZonesMap = new HashMap<>();

        for (Map.Entry<LocalDate, IndexedDatapoint> entry : tariffImportDailyMinimumMap.entrySet()) {
            int startIndex = entry.getValue().index;
            int endIndex = startIndex + window;

            for (int i = startIndex; i < endIndex; i++) {
                long timeMillis = tariffImportDatapoints.get(i).getTimestamp();
                chargeZonesMap.put(timeMillis, 1);
            }
        }

        for (Map.Entry<LocalDate, IndexedDatapoint> entry : tariffExportDailyMinimumMap.entrySet()) {
            int startIndex = entry.getValue().index;
            int endIndex = startIndex + window;

            for (int i = startIndex; i < endIndex; i++) {
                long timeMillis = tariffExportDatapoints.get(i).getTimestamp();
                dischargeZonesMap.put(timeMillis, -1);
            }
        }

        for (Map.Entry<Long, Integer> entry : chargeZonesMap.entrySet()) {
            if (!dischargeZonesMap.containsKey(entry.getKey())) {
                chargeAndDischargeZonesMap.put(entry.getKey(), entry.getValue());
            }
        }

        for (Map.Entry<Long, Integer> entry : dischargeZonesMap.entrySet()) {
            if (!chargeZonesMap.containsKey(entry.getKey())) {
                chargeAndDischargeZonesMap.put(entry.getKey(), entry.getValue());
            }
        }

        return chargeAndDischargeZonesMap;
    }

    private EmsDayAheadAsset getDayAheadAsset(EmsEnergyOptimisationAsset energyOptimisationAsset, Services services) {
        // Find assets in database
        List<EmsDayAheadAsset> assets = services.getAssetStorageService()
                .findAll(new AssetQuery().parents(energyOptimisationAsset.getId()).types(EmsDayAheadAsset.class))
                .stream()
                .map(asset -> (EmsDayAheadAsset) asset)
                .toList();

        EmsDayAheadAsset asset = null;

        if (assets.size() == 1) {
            asset = assets.getFirst();
        } else if (assets.size() > 1) {
            String logPrefixEnergyOptimisation = String.format("assetType='%s', assetId='%s', assetName='%s'", energyOptimisationAsset.getAssetType(), energyOptimisationAsset.getId(), energyOptimisationAsset.getAssetName());
            LOG.warning(String.format("%s; Found %s '%s' assets; Only 1 '%s' asset is allowed; Remove additional '%s' assets", logPrefixEnergyOptimisation, assets.size(), EmsGOPACSAsset.class.getSimpleName(), EmsGOPACSAsset.class.getSimpleName(), EmsGOPACSAsset.class.getSimpleName()));
        }

        return asset;
    }

    private List<ValueDatapoint<?>> getTariffDatapoints(EmsEnergyOptimisationAsset energyOptimisationAsset, String attributeName, Services services) {
        // Get the start of the day (00:00) in milliseconds
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId).toLocalDate();
        long startOfCurrentDayMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli();

        // Get tariff data-points of yesterday and 1 week into the future
        long startTimeMillis = startOfCurrentDayMillis - 24 * 60 * 60000;
        long endTimeMillis = startOfCurrentDayMillis + 8 * 24 * 60 * 60000;
        AssetDatapointAllQuery assetDatapointQueryHistoric = new AssetDatapointAllQuery(startTimeMillis, currentTimeMillis);
        AssetDatapointAllQuery assetDatapointQueryPredicted = new AssetDatapointAllQuery(currentTimeMillis, endTimeMillis);
        List<ValueDatapoint<?>> tariffHistoric = services.getAssetDatapointService().queryDatapoints(energyOptimisationAsset.getId(), attributeName, assetDatapointQueryHistoric);
        List<ValueDatapoint<?>> tariffPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAsset.getId(), attributeName, assetDatapointQueryPredicted);

        // Combine historic and predicted data-points, timestamps are ordered from newest to oldest (descending order)
        List<ValueDatapoint<?>> tariffCombined = new ArrayList<>(tariffPredicted);
        tariffCombined.addAll(tariffHistoric);

        return tariffCombined;
    }

    private List<ValueDatapoint<?>> getTariffDayAheadDatapoints(Asset<?> asset, String attributeName, Services services) {
        // Get the start of the day (00:00) in milliseconds
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId).toLocalDate();
        long startOfCurrentDayMillis = date.atStartOfDay(zoneId).toInstant().toEpochMilli();

        // Get tariff data-points of yesterday, today and tomorrow
        long startTimeMillis = startOfCurrentDayMillis - 24 * 60 * 60000;
        long endTimeMillis = startOfCurrentDayMillis + 2 * 24 * 60 * 60000;
        AssetDatapointAllQuery assetDatapointQueryHistoric = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);
        List<ValueDatapoint<?>> tariffHistoric = services.getAssetDatapointService().queryDatapoints(asset.getId(), attributeName, assetDatapointQueryHistoric);

        return tariffHistoric;
    }

    public static List<ValueDatapoint<?>> intervalAverage(List<ValueDatapoint<?>> dataPoints, long intervalMillis) {
        // Map<interval start, list of values in that interval>
        Map<Long, List<Double>> valuesPerIntervalMap = new HashMap<>();

        for (ValueDatapoint<?> datapoint : dataPoints) {
            long timestampMillis = datapoint.getTimestamp();
            Double value = (Double) datapoint.getValue();

            if (value != null) {
                // Calculate start of 15-minute interval
                long intervalStartMillis = timestampMillis - timestampMillis % intervalMillis;

                // Add value to corresponding interval
                valuesPerIntervalMap.computeIfAbsent(intervalStartMillis, key -> new ArrayList<>()).add(value);
            }
        }

        // List with average values per interval
        List<ValueDatapoint<?>> averageList = new ArrayList<>();

        for (Map.Entry<Long, List<Double>> entry : valuesPerIntervalMap.entrySet()) {
            Long intervalStartMillis = entry.getKey();
            List<Double> values = entry.getValue();

            // Compute the average
            double sum = 0;
            for (double value : values) {
                sum += value;
            }
            double average = sum / values.size();

            averageList.add(new ValueDatapoint<>(intervalStartMillis, average));
        }

        return averageList;
    }

    private List<ValueDatapoint<?>> intervalInterpolate(List<ValueDatapoint<?>> dataPoints, long startTimeMillis, long endTimeMillis, long intervalMillis) {
        List<ValueDatapoint<?>> interpolatedList = new ArrayList<>();

        if (dataPoints == null || dataPoints.size() < 2) {
            return interpolatedList;
        }

        int idx1 = 0;

        for (long intervalTimeMillis = startTimeMillis; intervalTimeMillis <= endTimeMillis; intervalTimeMillis += intervalMillis) {
            // Find data-point before and after interval
            while (idx1 < (dataPoints.size() - 1) && dataPoints.get(idx1 + 1).getTimestamp() < intervalTimeMillis) {
                idx1++;
            }

            long timeBeforeMillis = dataPoints.get(idx1).getTimestamp();
            long timeAfterMillis = dataPoints.get(idx1 + 1).getTimestamp();

            // Interpolate value
            if (intervalTimeMillis >= timeBeforeMillis && intervalTimeMillis <= timeAfterMillis) {
                double valueBefore = (double) dataPoints.get(idx1).getValue();
                double valueAfter = (double) dataPoints.get(idx1 + 1).getValue();

                double factor = (double) (intervalTimeMillis - timeBeforeMillis) / (timeAfterMillis - timeBeforeMillis);
                double interpolatedValue = valueBefore + factor * (valueAfter - valueBefore);

                interpolatedList.add(new ValueDatapoint<>(intervalTimeMillis, interpolatedValue));
            }
        }

        return interpolatedList;
    }

    public List<ValueDatapoint<?>> movingAverage(List<ValueDatapoint<?>> dataPoints, int window) {
        List<ValueDatapoint<?>> result = new ArrayList<>();
        if (window <= 0 || dataPoints.size() < window) {
            return result;
        }

        double sum = 0.0;

        for (int i = 0; i < dataPoints.size(); i++) {
            Double value = (Double) dataPoints.get(i).getValue();
            sum += value;

            if (i >= window) {
                Double valueOld = (Double) dataPoints.get(i - window).getValue();
                sum -= valueOld;
            }

            if (i >= window - 1) {
                double avg = sum / window;

                ValueDatapoint<?> original = dataPoints.get(i);

                ValueDatapoint<Double> averaged = new ValueDatapoint<>(original.getTimestamp(), round(avg, 7));
                result.add(averaged);
            }
        }

        return result;
    }

    private double round(double value, int precision) {
        double scale = Math.pow(10, precision);
        return Math.round(value * scale) / scale;
    }

    private void updateDayAheadAsset(EmsEnergyOptimisationAsset energyOptimisationAsset, EmsDayAheadAsset dayAheadAsset, Services services) {
        String dayAheadAssetId = dayAheadAsset.getId();
        String logPrefixDayAhead = String.format("assetType='%s', assetId='%s', assetName='%s'", dayAheadAsset.getAssetType(), dayAheadAssetId, dayAheadAsset.getAssetName());

        String collectTimeForecasts = dayAheadAsset.getCollectTimeForecasts().orElse("");

        if (collectTimeForecasts.isBlank()) {
            LOG.warning(String.format("%s, attributeName='%s'; Set time to collect day ahead forecasts", logPrefixDayAhead, EmsDayAheadAsset.COLLECT_TIME_FORECASTS.getName()));
        }

        // Parse the time string
        LocalTime collectTime = null;

        if (!collectTimeForecasts.isBlank()) {
            try {
                collectTime = LocalTime.parse(collectTimeForecasts, DateTimeFormatter.ofPattern("HH:mm"));
            } catch (Exception e) {
                LOG.warning(String.format("%s, attributeName='%s'; Error while parsing collect time; Exception: %s", logPrefixDayAhead, EmsDayAheadAsset.COLLECT_TIME_FORECASTS.getName(), e));
            }
        }

        Long lastUpdateForecastsTimestamp = dayAheadAsset.getLastUpdateForecastsTimestamp().orElse(null);

        if (collectTime == null || lastUpdateForecastsTimestamp == null) {
            return;
        }

        // Calculate the 15-minute interval for collecting the day ahead tariff forecasts
        LocalDate currentDate = LocalDate.now();
        LocalDateTime currentCollectDateTime = LocalDateTime.of(currentDate, collectTime);
        long collectTimeStartMillis = currentCollectDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long collectTimeEndMillis = collectTimeStartMillis + 15 * 60000;
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();

        String lastUpdateForecasts = dayAheadAsset.getLastUpdateForecasts().orElse("");
        LocalDateTime lastUpdateDateTime = currentCollectDateTime.plusDays(-1);

        if (!lastUpdateForecasts.isEmpty()) {
            lastUpdateDateTime = LocalDateTime.parse(lastUpdateForecasts);
        }

        LocalDate nextUpdateDate = lastUpdateDateTime.plusDays(1).toLocalDate();

        // Collect the day ahead tariff forecasts at the desired collect time
        if (currentTimeMillis >= collectTimeStartMillis && currentTimeMillis < collectTimeEndMillis && collectTimeStartMillis > lastUpdateForecastsTimestamp && currentDate.isEqual(nextUpdateDate)) {
            // Create asset datapoint query
            LocalDateTime startOfNextDay = currentDate.plusDays(1).atStartOfDay();
            long startTimeMillis = startOfNextDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTimeMillis = startTimeMillis + 24 * 60 * 60000 - 60000;
            AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

            // Get the tariffs number of data-points from day ahead asset
            int tariffExportDayAheadSize = services.getAssetDatapointService().queryDatapoints(dayAheadAssetId, EmsDayAheadAsset.TARIFF_EXPORT_DAY_AHEAD.getName(), assetDatapointQuery).size();
            int tariffImportDayAheadSize = services.getAssetDatapointService().queryDatapoints(dayAheadAssetId, EmsDayAheadAsset.TARIFF_IMPORT_DAY_AHEAD.getName(), assetDatapointQuery).size();

            // Only update the historic data-point table if there are no day ahead tariffs present in the historic data-point table for current interval
            if (tariffExportDayAheadSize == 0) {
                List<ValueDatapoint<?>> tariffExport = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(), assetDatapointQuery);
                services.getAssetDatapointService().upsertValues(dayAheadAssetId, EmsDayAheadAsset.TARIFF_EXPORT_DAY_AHEAD.getName(), tariffExport);
            }

            if (tariffImportDayAheadSize == 0) {
                List<ValueDatapoint<?>> tariffImport = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName(), assetDatapointQuery);
                services.getAssetDatapointService().upsertValues(dayAheadAssetId, EmsDayAheadAsset.TARIFF_IMPORT_DAY_AHEAD.getName(), tariffImport);
            }

            // Update the 'last update forecasts' datetime field with current update datetime
            String lastUpdateForecastsNew = currentCollectDateTime.toString();
            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(dayAheadAssetId, EmsDayAheadAsset.LAST_UPDATE_FORECASTS.getName(), lastUpdateForecastsNew, collectTimeStartMillis));
        }
    }

    private record ChargeDischarge(double charge, double discharge) {
    }

    private record IndexedDatapoint(long timestamp, double value, int index) {
    }
}
