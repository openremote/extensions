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
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.datapoint.query.AssetDatapointAllQuery;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.openremote.model.syslog.SyslogCategory.DATA;

public class EmsOptimisation implements OptimisationMethod {
    protected static final Logger LOG = SyslogCategory.getLogger(DATA, EmsOptimisation.class.getName());
    private final String optimisationMethodName = EmsOptimisation.class.getSimpleName();

    // Maximum interval between data-points send by the device to be considered connected
    private final int ACTIVE_PERIOD_MINUTES = 5;

    // EMS power limits settings
    private final int POWER_LIMIT_MAXIMUM_SAFETY_MARGIN_PERCENTAGE = 5;
    private final int POWER_LIMIT_MINIMUM_SAFETY_MARGIN_PERCENTAGE = 5;
    private final int POWER_LIMIT_MAXIMUM_BATTERIES_MARGIN_PERCENTAGE = 10;
    private final int POWER_LIMIT_MINIMUM_BATTERIES_MARGIN_PERCENTAGE = 10;

    // Battery settings
    private final int BATTERY_ENERGY_LEVEL_PERCENTAGE_DEFAULT = 50;
    private final int BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL = 3;
    private final int BATTERY_EFFICIENCY_BUFFER_PERCENTAGE = 3;


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

        String logPrefixEnergyOptimisation = String.format("assetType='%s', assetId='%s', assetName='%s'", energyOptimisationAsset.getAssetType(), energyOptimisationAssetId, energyOptimisationAsset.getAssetName());

        // Get all battery assets
        List<EmsElectricityBatteryAsset> electricityBatteryAssets = services.getAssetStorageService()
                .findAll(new AssetQuery().parents(energyOptimisationAssetId).types(EmsElectricityBatteryAsset.class))
                .stream()
                .map(asset -> (EmsElectricityBatteryAsset) asset)
                .toList();

        // TODO: Add order to battery assets from longest to smallest discharge duration

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

        // Check connection status of battery assets
        batteryCheckConnection(electricityBatteryAssets, services);

        // Check if all required attributes are connected/set and create log messages
        batteryCheckSetup(electricityBatteryAssets);

        // Calculate new battery power set-points
        Map<String, Double> powerSetpointsNew = batteryCalculatePowerSetpoints(energyOptimisationAsset, electricityBatteryAssets, services, logPrefixEnergyOptimisation);

        // Update battery power set-points
        batteryUpdatePowerSetpoints(electricityBatteryAssets, powerSetpointsNew, services);
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
            energyLevelPercentageDefault = (int) Math.round((double) (energyLevelPercentageMaximumBattery - energyLevelPercentageMinimumBattery) / 2 + energyLevelPercentageMinimumBattery);
        }

        return energyLevelPercentageDefault;
    }

    private Map<String, Integer> batteryCalculateForecasts(List<EmsElectricityBatteryAsset> electricityBatteryAssets, EmsEnergyOptimisationAsset energyOptimisationAsset, Services services) {
        Map<String, Integer> batteryEnergyLevelPercentageTargets = new HashMap<>();

        // Get power consumption forecast for 1 week
        long intervalMillis = 15 * 60000;
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long startTimeMillis = currentTimeMillis - currentTimeMillis % intervalMillis;
        long endTimeMillis = startTimeMillis + 7 * 24 * 60 * 60000;

        String energyOptimisationAssetId = energyOptimisationAsset.getId();
        AssetDatapointAllQuery assetDatapointQueryConsumptionPredicted = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);
        List<ValueDatapoint<?>> energyOptimisationPowerConsumptionPredicted = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_CONSUMPTION.getName(), assetDatapointQueryConsumptionPredicted);

//        System.out.println("energyOptimisationPowerConsumptionPredicted = " + energyOptimisationPowerConsumptionPredicted);

        if (energyOptimisationPowerConsumptionPredicted.isEmpty()) {
            return batteryEnergyLevelPercentageTargets;
        }

        // Calculate power average for each 15-minute interval
        List<ValueDatapoint<?>> totalPowerConsumptionAveraged = intervalAverage(energyOptimisationPowerConsumptionPredicted, intervalMillis);
        totalPowerConsumptionAveraged.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));

//        System.out.println("totalPowerConsumptionAveraged = " + totalPowerConsumptionAveraged);

        long startTimestampMillis = totalPowerConsumptionAveraged.getFirst().getTimestamp();
        long endTimestampMillis = totalPowerConsumptionAveraged.getLast().getTimestamp();

        // Interpolate power average values for each 15-minute interval
        List<ValueDatapoint<?>> totalPowerConsumptionInterpolated = intervalInterpolate(totalPowerConsumptionAveraged, startTimestampMillis, endTimestampMillis, intervalMillis);

        List<Long> timestampsMillisList = new ArrayList<>();
        List<Double> totalPowerConsumptionList = new ArrayList<>();

        for (ValueDatapoint<?> datapoint : totalPowerConsumptionInterpolated) {
            long timestampMillis = datapoint.getTimestamp();
            Double value = (Double) datapoint.getValue();

            timestampsMillisList.add(timestampMillis);
            totalPowerConsumptionList.add(value);
        }

//        System.out.println("timestampsMillisList = " + timestampsMillisList);
//        System.out.println("totalPowerConsumptionList = " + totalPowerConsumptionList);

        if (totalPowerConsumptionList.isEmpty()) {
            return batteryEnergyLevelPercentageTargets;
        }

        AssetDatapointAllQuery assetDatapointQueryPeriodPredicted = new AssetDatapointAllQuery(startTimestampMillis, endTimestampMillis);
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

        // List to store the sum of total power consumption, production and flexible power
        List<Double> totalPowerConsumptionProductionFlexList = new ArrayList<>();

        // Sum total power consumption and production
        for (int i = 0; i < timestampsMillisList.size(); i++) {
            Double powerConsumption = totalPowerConsumptionList.get(i);
            Double powerProduction = totalPowerProductionMap.get(timestampsMillisList.get(i));

            if (powerProduction != null) {
                double sum = powerConsumption + powerProduction;
                totalPowerConsumptionProductionFlexList.add(sum);
            } else {
                totalPowerConsumptionProductionFlexList.add(powerConsumption);
            }
        }

//        System.out.println("totalPowerConsumptionProductionFlexList = " + totalPowerConsumptionProductionFlexList);
//        System.out.println();

        // Get power limits forecasts
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

        // Add current available charge and discharge power
        chargePowerAvailableTotalList.add(0.0);
        dischargePowerAvailableTotalList.add(0.0);

        double intervalHour = (double) intervalMillis / (60 * 60000);

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
            if ((energyLevelPercentageMaximumBattery - energyLevelPercentageMinimumBattery) < BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL) {
                continue;
            }

            // Calculate total available charge and discharge power
            for (int i = 0; i < timestampsMillisList.size(); i++) {
                long timestampMillis = timestampsMillisList.get(i);
                double totalPower = totalPowerConsumptionProductionFlexList.get(i);

                Double powerLimitMaximum = powerLimitMaximumMap.getOrDefault(timestampMillis, null);
                Double powerLimitMinimum = powerLimitMinimumMap.getOrDefault(timestampMillis, null);

                if (powerLimitMaximum != null) {
                    double powerLimitMaximumVirtual = powerLimitMaximum * (1 - POWER_LIMIT_MAXIMUM_SAFETY_MARGIN_PERCENTAGE * 0.01);
                    double chargePowerTotalAvailable = powerLimitMaximumVirtual - totalPower;
                    chargePowerAvailableTotalList.add(chargePowerTotalAvailable);
                } else {
                    chargePowerAvailableTotalList.add(Double.POSITIVE_INFINITY);
                }

                if (powerLimitMinimum != null) {
                    double powerLimitMinimumVirtual = powerLimitMinimum * (1 - POWER_LIMIT_MINIMUM_SAFETY_MARGIN_PERCENTAGE * 0.01);
                    double dischargePowerTotalAvailable = powerLimitMinimumVirtual - totalPower;
                    dischargePowerAvailableTotalList.add(dischargePowerTotalAvailable);
                } else {
                    dischargePowerAvailableTotalList.add(Double.NEGATIVE_INFINITY);
                }
            }

            int numberOfDataPoints = chargePowerAvailableTotalList.size();

//            System.out.println("numberOfDataPoints = " + numberOfDataPoints);
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

            chargeEfficiencyBattery = chargeEfficiencyBattery - BATTERY_EFFICIENCY_BUFFER_PERCENTAGE;
            dischargeEfficiencyBattery = dischargeEfficiencyBattery - BATTERY_EFFICIENCY_BUFFER_PERCENTAGE;

            for (int i = 0; i < numberOfDataPoints; i++) {
                double c = intervalHour * chargeNeededTotalList.get(i) * chargeEfficiencyBattery / energyCapacityBattery;
                double d = 10000 * intervalHour * dischargeNeededTotalList.get(i) / (energyCapacityBattery * dischargeEfficiencyBattery);
                chargePercentageNeededTotalList.add(c);
                dischargePercentageNeededTotalList.add(d);
            }

//            System.out.println("chargePercentageNeededTotalList = " + chargePercentageNeededTotalList);
//            System.out.println("dischargePercentageNeededTotalList = " + dischargePercentageNeededTotalList);

            List<Double> chargePercentageAvailableBatteryList = new ArrayList<>();
            List<Double> dischargePercentageAvailableBatteryList = new ArrayList<>();

            for (int i = 0; i < numberOfDataPoints; i++) {
                double c = intervalHour * chargePowerAvailableBatteryList.get(i) * chargeEfficiencyBattery / energyCapacityBattery;
                double d = 10000 * intervalHour * dischargePowerAvailableBatteryList.get(i) / (energyCapacityBattery * dischargeEfficiencyBattery);
                chargePercentageAvailableBatteryList.add(c);
                dischargePercentageAvailableBatteryList.add(d);
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
                    dischargePercentageAvailableBatteryList.set(i, dischargePercentageAvailableBatteryList.get(i) - dischargePercentageAvailable);
                    getToLimitPercentageList.add(getToLimitPercentage);
                } else if (getToLimitPercentage < energyLevelPercentageMinimumBattery) {
                    double chargePercentageNeeded = energyLevelPercentageMinimumBattery - getToLimitPercentage;
                    double chargePercentageAvailable = Math.min(chargePercentageAvailableBatteryList.get(i), chargePercentageNeeded);
                    getToLimitPercentage = getToLimitPercentage + chargePercentageAvailable;
                    chargePercentageAvailableBatteryList.set(i, chargePercentageAvailableBatteryList.get(i) - chargePercentageAvailable);
                    getToLimitPercentageList.add(getToLimitPercentage);
                } else {
                    break;
                }
            }

//            System.out.println("getToLimitIndex = " + getToLimitIndex);
//            System.out.println("getToLimitPercentage = " + getToLimitPercentage);
//            System.out.println("getToLimitPercentageList = " + getToLimitPercentageList);
//            System.out.println("chargePercentageAvailableBatteryList = " + chargePercentageAvailableBatteryList);
//            System.out.println("dischargePercentageAvailableBatteryList = " + dischargePercentageAvailableBatteryList);

            List<Double> energyLevelPredictionList = new ArrayList<>();

            if (getToLimitPercentage > energyLevelPercentageMaximumBattery || getToLimitPercentage < energyLevelPercentageMinimumBattery) {
                // Energy level percentage forecast when whole forecast is outside of battery percentage limits
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
                    chargeAndDischargePercentageWantBatteryList.add(chargePercentageWantBatteryList.get(i) + dischargePercentageWantBatteryList.get(i));
                }

//                System.out.println("chargeAndDischargePercentageWantBatteryList = " + chargeAndDischargePercentageWantBatteryList);
//                System.out.println();

                // Calculate forecast without battery percentage limits
                int startRunningSumIndex = getToLimitIndex > 0 ? getToLimitIndex - 1 : 0;
                List<Double> runningSumList = new ArrayList<>();
                double sum = getToLimitPercentage;

                for (int i = startRunningSumIndex; i < numberOfDataPoints; i++) {
                    sum = sum + chargeAndDischargePercentageWantBatteryList.get(i);
                    runningSumList.add(sum);
                }

//                System.out.println("getToLimitPercentageList = " + getToLimitPercentageList);
//                System.out.println("runningSumList = " + runningSumList);
//                System.out.println();

                // Combine outside limits and running sum energy level percentages
                if (getToLimitPercentageList.size() > 1) {
                    energyLevelPredictionList.addAll(getToLimitPercentageList.subList(0, getToLimitPercentageList.size() - 1));
                }
                energyLevelPredictionList.addAll(runningSumList);

//                System.out.println("energyLevelPredictionList = " + energyLevelPredictionList);
//                System.out.println();

                // Calculate forecast starting within battery percentage limits
                double energyLevelPredictionMaximum = Collections.max(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionList.size()));
                double energyLevelPredictionMinimum = Collections.min(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionList.size()));

                long dtStart = services.getTimerService().getCurrentTimeMillis();
                long dt = 0;
                long timeoutMillis = 10000;

                int intervalEndIndex = startRunningSumIndex;
                int predictionListIndex = startRunningSumIndex;

                while (energyLevelPredictionMaximum > energyLevelPercentageMaximumBattery || energyLevelPredictionMinimum < energyLevelPercentageMinimumBattery) {
                    String chargeOrDischarge = "";
                    int intervalStartIndex = startRunningSumIndex;

                    for (int i = intervalEndIndex; i < energyLevelPredictionList.size(); i++) {
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
                        double chargeAvailableInterval = energyLevelPercentageMaximumBattery - Collections.max(energyLevelPredictionList.subList(intervalStartIndex, intervalEndIndex));

                        if (chargeAvailableInterval > 0) {
                            for (int i = intervalEndIndex - 1; i >= intervalStartIndex; i--) {
                                if (i - 1 < 0) {
                                    break;
                                }

                                double chargeAvailableLeft = 0;

                                if (dischargePercentageWantBatteryList.get(i) >= 0) {
                                    double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                    chargeAvailableLeft = chargePercentageAvailableBatteryList.get(i) - chargeInUse;
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

                        for (int i = changeIndex; i < energyLevelPredictionList.size(); i++) {
                            energyLevelPredictionList.set(i, energyLevelPredictionList.get(i) + changeValue);
                        }
                    } else if (chargeOrDischarge.equals("discharge")) {
                        double dischargeAvailable = 0;
                        double dischargeAvailableInterval = energyLevelPercentageMinimumBattery - Collections.min(energyLevelPredictionList.subList(intervalStartIndex, intervalEndIndex));

                        if (dischargeAvailableInterval > 0) {
                            for (int i = intervalEndIndex - 1; i >= intervalStartIndex; i--) {
                                if (i - 1 < 0) {
                                    break;
                                }

                                double dischargeAvailableLeft = 0;

                                if (chargePercentageWantBatteryList.get(i) <= 0) {
                                    double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                    dischargeAvailableLeft = dischargePercentageAvailableBatteryList.get(i) - dischargeInUse;
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

                        for (int i = changeIndex; i < energyLevelPredictionList.size(); i++) {
                            energyLevelPredictionList.set(i, energyLevelPredictionList.get(i) + changeValue);
                        }
                    }

                    if (energyLevelPredictionValueBefore == energyLevelPredictionList.get(intervalEndIndex)) {
                        LOG.warning(String.format("Battery energy level percentage calculation error at index = %s", predictionListIndex));
//                        System.out.println("ERROR: calculation error at index = " + predictionListIndex);
                        break;
                    } else if (dt > timeoutMillis) {
                        LOG.warning(String.format("Battery energy level percentage calculation timed out at index = %s", predictionListIndex));
//                        System.out.println("ERROR: calculation timeout at index = " + predictionListIndex);
                        break;
                    }

                    energyLevelPredictionMaximum = Collections.max(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionList.size()));
                    energyLevelPredictionMinimum = Collections.min(energyLevelPredictionList.subList(startRunningSumIndex, energyLevelPredictionList.size()));
                    dt = services.getTimerService().getCurrentTimeMillis() - dtStart;
                }

//                System.out.println("After limits: energyLevelPredictionList = " + energyLevelPredictionList + "\n");

                // Get day ahead asset
                EmsDayAheadAsset dayAheadAsset = getDayAheadAsset(energyOptimisationAsset, services);
                boolean useDayAheadTariffs = false;

                if (dayAheadAsset != null) {
                    useDayAheadTariffs = dayAheadAsset.getUseTariffDayAheadForecasts().orElse(false);
                }

                // Get the tariff forecasts from energy optimisation asset for 1 week
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

                // Calculate battery energy level percentage default
                double energyLevelPercentageDefault = batteryCalculateEnergyLevelPercentageDefault(energyLevelPercentageMaximumBattery, energyLevelPercentageMinimumBattery, energyOptimisationAsset);

                // TODO: make window dynamic based on time needed to reach energyLevelPercentageDefault
                // Calculate optimal charge and discharge zone for each day based on tariffs
                Map<Long, Integer> chargeAndDischargeZonesMap = calculateTariffChargeAndDischargeZones(tariffImportDatapoints, tariffExportDatapoints, 4);

                // Charge zone = 1, discharge zone = -1
                List<Integer> chargeAndDischargeZonesList = new ArrayList<>(Collections.nCopies(energyLevelPredictionList.size(), 0));

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

                // Default energy level percentage for each interval
                List<Double> energyLevelPercentageDefaultList = new ArrayList<>(Collections.nCopies(chargeAndDischargeZonesList.size(), energyLevelPercentageDefault));

                for (int i = 0; i < chargeAndDischargeZonesList.size(); i++) {
                    if (chargeAndDischargeZonesList.get(i) == 1) {
                        energyLevelPercentageDefaultList.set(i, Double.valueOf(energyLevelPercentageMaximumBattery));
                    } else if (chargeAndDischargeZonesList.get(i) == -1) {
                        energyLevelPercentageDefaultList.set(i, Double.valueOf(energyLevelPercentageMinimumBattery));
                    }
                }

//                System.out.println("energyLevelPercentageDefaultList = " + energyLevelPercentageDefaultList);

                // TODO: handle debounce interval in forecast
                // Optimise forecast based on optimal tariffs
                for (int i = 1; i < chargeAndDischargeZonesList.size() && i < energyLevelPredictionList.size(); i++) {
                    // Only adjust forecast if predicted energy level percentage is outside battery debounce interval
                    if (Math.abs(energyLevelPredictionList.get(i) - energyLevelPercentageDefaultList.get(i)) <= BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL) {
                        continue;
                    }

                    if (chargeAndDischargeZonesList.get(i) == 1 && energyLevelPredictionList.get(i) < energyLevelPercentageDefaultList.get(i)) {
                        Double energyLevelIntervalMaximum = Collections.max(energyLevelPredictionList.subList(i, energyLevelPredictionList.size()));

                        double chargeSpaceLeft = energyLevelPercentageMaximumBattery - energyLevelIntervalMaximum;

                        if (chargeSpaceLeft > 0) {
                            double chargeNeeded = energyLevelPercentageDefaultList.get(i) - energyLevelPredictionList.get(i);
                            double chargeAvailableLeft = 0;

                            if (dischargePercentageWantBatteryList.get(i) >= 0) {
                                double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                chargeAvailableLeft = chargePercentageAvailableBatteryList.get(i) - chargeInUse;
                            }

                            if (chargeAvailableLeft > 0) {
                                chargeAvailableLeft = Math.min(Math.min(chargeSpaceLeft, chargeAvailableLeft), chargeNeeded);

                                for (int j = i; j < energyLevelPredictionList.size(); j++) {
                                    energyLevelPredictionList.set(j, energyLevelPredictionList.get(j) + chargeAvailableLeft);
                                }
                            }
                        } else {
                            double chargeZoneEnergyLevel = energyLevelPredictionList.get(i);

                            int energyLevelIntervalMaximumIndex = energyLevelPredictionList.size();

                            if (energyLevelIntervalMaximum >= energyLevelPercentageMaximumBattery) {
                                energyLevelIntervalMaximumIndex = energyLevelPredictionList.indexOf(energyLevelIntervalMaximum);
                            }

                            for (int k = i; k <= energyLevelIntervalMaximumIndex; k++) {
                                double chargeNeeded = energyLevelPredictionList.get(k) - chargeZoneEnergyLevel;

                                if (chargeNeeded > 0 && chargePercentageWantBatteryList.get(k) == 0) {
                                    double chargeAvailableLeft = 0;

                                    if (dischargePercentageWantBatteryList.get(i) >= 0) {
                                        double chargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                        chargeAvailableLeft = chargePercentageAvailableBatteryList.get(i) - chargeInUse;
                                    }

                                    if (chargeAvailableLeft > 0) {
                                        double energyLevelIntervalMaximum2 = Collections.max(energyLevelPredictionList.subList(i, k - 1));
                                        double chargeSpaceInterval = energyLevelPercentageMaximumBattery - energyLevelIntervalMaximum2;

                                        if (chargeSpaceInterval > 0) {
                                            chargeAvailableLeft = Math.min(Math.min(chargeAvailableLeft, chargeNeeded), chargeSpaceInterval);

                                            for (int j = i; j < k; j++) {
                                                energyLevelPredictionList.set(j, energyLevelPredictionList.get(j) + chargeAvailableLeft);
                                            }
                                        }
                                    }

                                    break;
                                }
                            }
                        }
                    } else if (chargeAndDischargeZonesList.get(i) == -1 && energyLevelPredictionList.get(i) > energyLevelPercentageDefaultList.get(i)) {
                        Double energyLevelIntervalMinimum = Collections.min(energyLevelPredictionList.subList(i, energyLevelPredictionList.size()));

                        double dischargeSpaceLeft = energyLevelPercentageMinimumBattery - energyLevelIntervalMinimum;

                        if (dischargeSpaceLeft < 0) {
                            double dischargeNeeded = energyLevelPercentageDefaultList.get(i) - energyLevelPredictionList.get(i);
                            double dischargeAvailableLeft = 0;

                            if (chargePercentageWantBatteryList.get(i) <= 0) {
                                double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                dischargeAvailableLeft = dischargePercentageAvailableBatteryList.get(i) - dischargeInUse;
                            }

                            if (dischargeAvailableLeft < 0) {
                                dischargeAvailableLeft = Math.max(Math.max(dischargeSpaceLeft, dischargeAvailableLeft), dischargeNeeded);

                                for (int j = i; j < energyLevelPredictionList.size(); j++) {
                                    energyLevelPredictionList.set(j, energyLevelPredictionList.get(j) + dischargeAvailableLeft);
                                }
                            }
                        } else {
                            double dischargeZoneEnergyLevel = energyLevelPredictionList.get(i);

                            int energyLevelIntervalMinimumIndex = energyLevelPredictionList.size();

                            if (energyLevelIntervalMinimum <= energyLevelPercentageMinimumBattery) {
                                energyLevelIntervalMinimumIndex = energyLevelPredictionList.indexOf(energyLevelIntervalMinimum);
                            }

                            for (int k = i; k <= energyLevelIntervalMinimumIndex; k++) {
                                double dischargeNeeded = energyLevelPredictionList.get(k) - dischargeZoneEnergyLevel;

                                if (dischargeNeeded < 0 && dischargePercentageWantBatteryList.get(k) == 0) {
                                    double dischargeAvailableLeft = 0;

                                    if (chargePercentageWantBatteryList.get(i) <= 0) {
                                        double dischargeInUse = energyLevelPredictionList.get(i) - energyLevelPredictionList.get(i - 1);
                                        dischargeAvailableLeft = dischargePercentageAvailableBatteryList.get(i) - dischargeInUse;
                                    }

                                    if (dischargeAvailableLeft < 0) {
                                        double energyLevelIntervalMinimum2 = Collections.min(energyLevelPredictionList.subList(i, k - 1));
                                        double dischargeSpaceInterval = energyLevelPercentageMinimumBattery - energyLevelIntervalMinimum2;

                                        if (dischargeSpaceInterval < 0) {
                                            dischargeAvailableLeft = Math.max(Math.max(dischargeAvailableLeft, dischargeNeeded), dischargeSpaceInterval);

                                            for (int j = i; j < k; j++) {
                                                energyLevelPredictionList.set(j, energyLevelPredictionList.get(j) + dischargeAvailableLeft);
                                            }
                                        }
                                    }

                                    break;
                                }
                            }
                        }
                    }
                }
            }

//            System.out.println("After Tariffs: energyLevelPredictionList = " + energyLevelPredictionList + "\n");

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

            for (int i = 0; i < powerChangeTotalList.size() - 1; i++) {
                totalPowerConsumptionProductionFlexList.set(i, totalPowerConsumptionProductionFlexList.get(i) - powerChangeTotalList.get(i));
            }

//            System.out.println("totalPowerConsumptionProductionFlexList = " + totalPowerConsumptionProductionFlexList);

            List<ValueDatapoint<?>> energyLevelPercentageForecast = new ArrayList<>();
            List<ValueDatapoint<?>> powerSetpointForecast = new ArrayList<>();

            // Update energy level percentage forecast starting after current time
            for (int i = 1; i < timestampsMillisList.size(); i++) {
                energyLevelPercentageForecast.add(new ValueDatapoint<>(timestampsMillisList.get(i), (int) Math.round(energyLevelPredictionList.get(i))));
            }

            // Update power set-point starting from current power limit
            for (int i = 0; i < timestampsMillisList.size(); i++) {
                powerSetpointForecast.add(new ValueDatapoint<>(timestampsMillisList.get(i), powerChangeTotalList.get(i)));
            }

            services.getAssetPredictedDatapointService().updateValues(batteryAssetId, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE.getName(), energyLevelPercentageForecast);
            services.getAssetPredictedDatapointService().updateValues(batteryAssetId, EmsElectricityBatteryAsset.POWER_SETPOINT.getName(), powerSetpointForecast);

//            System.out.println("UPDATED forecasts");
        }

        return batteryEnergyLevelPercentageTargets;
    }

    private Map<String, double[]> batteryCalculatePowerFlexibleAvailable(List<EmsElectricityBatteryAsset> electricityBatteryAssets) {
        HashMap<String, double[]> powerFlexibleAvailable = new HashMap<>();

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

            powerFlexibleAvailable.put(electricityBatteryAsset.getId(), new double[]{chargePowerMaximum, dischargePowerMaximum});
        }

        return powerFlexibleAvailable;
    }

    private Map<String, Double> batteryCalculatePowerSetpoints(EmsEnergyOptimisationAsset energyOptimisationAsset, List<EmsElectricityBatteryAsset> electricityBatteryAssets, Services services, String logPrefix) {
        Map<String, double[]> powerFlexibleAvailable = batteryCalculatePowerFlexibleAvailable(electricityBatteryAssets);
        Map<String, Double> powerSetpointsNew;

        // Get latest power set-point across all batteries
        long powerSetpointTimestampMillisLatest = 0L;

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            long powerSetpointTimestampMillis = electricityBatteryAsset.getPowerSetpointTimestamp().orElse(0L);

            if (powerSetpointTimestampMillis > powerSetpointTimestampMillisLatest) {
                powerSetpointTimestampMillisLatest = powerSetpointTimestampMillis;
            }
        }

        // Check if power net is updated since last battery power set-points update
        Double powerNet = energyOptimisationAsset.getPowerNet().orElse(null);
        long powerNetTimestampMillis = energyOptimisationAsset.getPowerNetTimestamp().orElse(0L);

        // Turn off batteries that do not have flexible power available during main power meter disconnect
        if (powerSetpointTimestampMillisLatest > powerNetTimestampMillis || powerNet == null) {
            powerSetpointsNew = batteryCheckPowerSetpointsCurrent(electricityBatteryAssets, powerFlexibleAvailable);
            return powerSetpointsNew;
        }

        // Check if power limits are present and calculate additional power limits
        Double powerLimitMaximumProfileTotal = energyOptimisationAsset.getPowerLimitMaximumProfileTotal().orElse(null);
        Double powerLimitMinimumProfileTotal = energyOptimisationAsset.getPowerLimitMinimumProfileTotal().orElse(null);

        Double powerLimitMaximumBatteries = null;

        if (powerLimitMaximumProfileTotal != null) {
            powerLimitMaximumBatteries = round(powerLimitMaximumProfileTotal * (1 - POWER_LIMIT_MAXIMUM_BATTERIES_MARGIN_PERCENTAGE * 0.01), 3);
        }

        Double powerLimitMinimumBatteries = null;

        if (powerLimitMinimumProfileTotal != null) {
            powerLimitMinimumBatteries = round(powerLimitMinimumProfileTotal * (1 - POWER_LIMIT_MINIMUM_BATTERIES_MARGIN_PERCENTAGE * 0.01), 3);
        }

        // TODO: add battery limits cross-over warning
        // TODO: add advanced settings

        int currentMinute = LocalDateTime.now().getMinute();
        Map<String, Integer> batteryEnergyLevelPercentageTargets = batteryGetEnergyLevelPercentageTargetsCurrent(electricityBatteryAssets, services);

        // Calculate battery energy level forecast
        // TODO: add 15 minute skip protection with forecast update timestamp for robust forecast updating
        if ((currentMinute % 15) == 0) {
            batteryEnergyLevelPercentageTargets = batteryCalculateForecasts(electricityBatteryAssets, energyOptimisationAsset, services);
        }

        // Calculate virtual power consumption
        Map<String, Double> powerSetpointsCurrent = new HashMap<>();

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            double powerSetpoint = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);
            powerSetpointsCurrent.put(electricityBatteryAssetId, powerSetpoint);
        }

        double powerSetpointsCurrentSum = powerSetpointsCurrent.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerConsumptionVirtual = powerNet - powerSetpointsCurrentSum;

        // Calculate new power set-points
        if (powerLimitMaximumProfileTotal != null && powerConsumptionVirtual > powerLimitMaximumProfileTotal) {
            Double powerLimitMaximumVirtual = round(powerLimitMaximumProfileTotal * (1 - POWER_LIMIT_MAXIMUM_SAFETY_MARGIN_PERCENTAGE * 0.01), 3);
            powerSetpointsNew = batteryCalculatePowerSetpointsOnLimitBreach(powerFlexibleAvailable, powerConsumptionVirtual, powerLimitMaximumVirtual, "max");
        } else if (powerLimitMinimumProfileTotal != null && powerConsumptionVirtual < powerLimitMinimumProfileTotal) {
            Double powerLimitMinimumVirtual = round(powerLimitMinimumProfileTotal * (1 - POWER_LIMIT_MINIMUM_SAFETY_MARGIN_PERCENTAGE * 0.01), 3);
            powerSetpointsNew = batteryCalculatePowerSetpointsOnLimitBreach(powerFlexibleAvailable, powerConsumptionVirtual, powerLimitMinimumVirtual, "min");
        } else {
            powerSetpointsNew = batteryRestoreEnergyLevels(energyOptimisationAsset, electricityBatteryAssets, powerFlexibleAvailable, powerNet, powerSetpointsCurrentSum, powerLimitMinimumBatteries, powerLimitMaximumBatteries, batteryEnergyLevelPercentageTargets);
        }

        // Create log messages on power limit breach
        double powerSetpointsNewSum = powerSetpointsNew.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerNetNewVirtual = powerConsumptionVirtual + powerSetpointsNewSum;

        if (powerLimitMaximumProfileTotal != null && powerNet > powerLimitMaximumProfileTotal) {
            double powerExceededAmount = round((powerNet - powerLimitMaximumProfileTotal), 3);
            LOG.warning(String.format("%s; Power net is %s kW above power limit maximum", logPrefix, powerExceededAmount));

            if (powerNetNewVirtual > powerLimitMaximumProfileTotal) {
                double powerReductionShortage = round((powerNetNewVirtual - powerLimitMaximumProfileTotal), 3);
                LOG.warning(String.format("%s; Not enough flexible power to get below power limit maximum; Shortage of %s kW", logPrefix, powerReductionShortage));
            }
        } else if (powerLimitMinimumProfileTotal != null && powerNet < powerLimitMinimumProfileTotal) {
            double powerExceededAmount = round((powerNet - powerLimitMinimumProfileTotal), 3);
            LOG.warning(String.format("%s; Power net is %s kW below power limit minimum", logPrefix, powerExceededAmount));

            if (powerNetNewVirtual < powerLimitMinimumProfileTotal) {
                double powerReductionShortage = round((powerNetNewVirtual - powerLimitMinimumProfileTotal), 3);
                LOG.warning(String.format("%s; Not enough flexible power to get above power limit minimum; Shortage of %s kW", logPrefix, powerReductionShortage));
            }
        }

        return powerSetpointsNew;
    }

    private Map<String, Double> batteryCalculatePowerSetpointsOnLimitBreach(Map<String, double[]> powerFlexibleAvailable, Double powerConsumptionVirtual, Double powerLimitVirtual, String maxOrMin) {
        Map<String, Double> powerSetpointsNew = new HashMap<>();
        boolean isMin = "min".equals(maxOrMin);

        // Calculate the total power adjustment needed
        double powerReduction = powerConsumptionVirtual - powerLimitVirtual;

        if (isMin) {
            powerReduction = -powerReduction;
        }

        // Iterate over each battery asset
        for (Map.Entry<String, double[]> entry : powerFlexibleAvailable.entrySet()) {
            String electricityBatteryAssetId = entry.getKey();
            double[] powerBatteryAvailableChargeDischarge = entry.getValue();

            // Choose available power based on breach type
            double powerBatteryAvailable = isMin ? -powerBatteryAvailableChargeDischarge[0] : powerBatteryAvailableChargeDischarge[1];

            if (powerReduction > 0) {
                // Limit the power set-point to available battery power or remaining reduction needed
                double setpoint = round(Math.max(-powerReduction, powerBatteryAvailable), 3);

                if (isMin) {
                    setpoint = -setpoint;
                }

                powerSetpointsNew.put(electricityBatteryAssetId, setpoint);
                powerReduction += setpoint;
            } else {
                powerSetpointsNew.put(electricityBatteryAssetId, 0.0);
            }
        }

        return powerSetpointsNew;
    }

    private void batteryCheckConnection(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Services services) {
        // This method checks if a battery is connected based on if the power and energyLevelPercentage attributes update within the active time interval
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
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);
            Double power = electricityBatteryAsset.getPower().orElse(null);
            StringBuilder logMessageBatteryConnect = new StringBuilder();

            if (energyLevelPercentage == null) {
                logMessageBatteryConnect.append(String.format(" '%s',", EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE.getName()));
            }

            if (power == null) {
                logMessageBatteryConnect.append(String.format(" '%s',", EmsElectricityBatteryAsset.POWER.getName()));
            }

            if (!logMessageBatteryConnect.isEmpty()) {
                logMessageBatteryConnect.setLength(logMessageBatteryConnect.length() - 1);
                logMessageBatteryConnect.insert(0, String.format("%s; Can't use battery for flexible power. The following attributes are not connected:", logPrefixBattery));
                LOG.warning(logMessageBatteryConnect.toString());
            }

            // Check if the following attributes are set
            Double chargePowerMaximum = electricityBatteryAsset.getChargePowerMaximum().orElse(null);
            Double dischargePowerMaximum = electricityBatteryAsset.getDischargePowerMaximum().orElse(null);
            Integer energyLevelPercentageMaximum = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
            Integer energyLevelPercentageMinimum = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);
            StringBuilder logMessageBatterySet = new StringBuilder();

            if (chargePowerMaximum == null) {
                logMessageBatterySet.append(String.format(" '%s',", EmsElectricityBatteryAsset.CHARGE_POWER_MAXIMUM.getName()));
            }

            if (dischargePowerMaximum == null) {
                logMessageBatterySet.append(String.format(" '%s',", EmsElectricityBatteryAsset.DISCHARGE_POWER_MAXIMUM.getName()));
            }

            if (energyLevelPercentageMaximum == null) {
                logMessageBatterySet.append(String.format(" '%s',", EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MAXIMUM.getName()));
            }

            if (energyLevelPercentageMinimum == null) {
                logMessageBatterySet.append(String.format(" '%s',", EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MINIMUM.getName()));
            }

            if (!logMessageBatterySet.isEmpty()) {
                logMessageBatterySet.setLength(logMessageBatterySet.length() - 1);
                logMessageBatterySet.insert(0, String.format("%s; Can't use battery for flexible power. The following attributes are not set:", logPrefixBattery));
                LOG.warning(logMessageBatterySet.toString());
            }

            if (energyLevelPercentageMaximum != null && energyLevelPercentageMinimum != null && (energyLevelPercentageMaximum - energyLevelPercentageMinimum) < BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL) {
                int diff = energyLevelPercentageMaximum - energyLevelPercentageMinimum;
                LOG.warning(String.format("%s; Can't use battery for flexible power. %s - %s = %s - %s = %s < %s%%)", logPrefixBattery, EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MAXIMUM.getName(), EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MINIMUM.getName(),
                        energyLevelPercentageMaximum, energyLevelPercentageMinimum, diff, BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL));
            }
        }
    }

    private Map<String, Double> batteryCheckPowerSetpointsCurrent(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, double[]> powerFlexibleAvailable) {
        Map<String, Double> powerSetpointsNew = new HashMap<>();

        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);

            if (energyLevelPercentage == null) {
                powerSetpointsNew.put(electricityBatteryAssetId, 0.0);
                continue;
            }

            double powerSetpointCurrent = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);
            double chargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId)[0];
            double dischargePowerAvailable = powerFlexibleAvailable.get(electricityBatteryAssetId)[1];

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

    private Map<String, Integer> batteryGetEnergyLevelPercentageTargetsCurrent(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Services services) {
        Map<String, Integer> batteryEnergyLevelPercentageTargetsCurrent = new HashMap<>();

        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long endTimeMillis = currentTimeMillis - currentTimeMillis % (15 * 60000) + (15 * 60000);

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

    private Map<String, Double> batteryRestoreEnergyLevels(EmsEnergyOptimisationAsset energyOptimisationAsset, List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, double[]> powerFlexibleAvailable, Double powerNet,
                                                           Double powerSetpointsCurrentSum, Double powerLimitMinimumBatteries, Double powerLimitMaximumBatteries, Map<String, Integer> batteryEnergyLevelPercentageTargets) {
        Map<String, Double> powerSetpointsNew = new HashMap<>();

        // Find battery power set-points for a system without power limits
        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double energyLevelPercentage = electricityBatteryAsset.getEnergyLevelPercentage().orElse(null);

            // Set initial power set-point of 0
            powerSetpointsNew.put(electricityBatteryAssetId, 0.0);

            if (energyLevelPercentage == null) {
                continue;
            }

            double powerSetpointCurrent = electricityBatteryAsset.getPowerSetpoint().orElse(0.0);

            // Get battery energy level target
            Integer energyLevelPercentageTarget = batteryEnergyLevelPercentageTargets.get(electricityBatteryAssetId);

            if (energyLevelPercentageTarget == null) {
                Integer energyLevelPercentageMaximum = electricityBatteryAsset.getEnergyLevelPercentageMaximum().orElse(null);
                Integer energyLevelPercentageMinimum = electricityBatteryAsset.getEnergyLevelPercentageMinimum().orElse(null);
                energyLevelPercentageTarget = batteryCalculateEnergyLevelPercentageDefault(energyLevelPercentageMaximum, energyLevelPercentageMinimum, energyOptimisationAsset);
            }

            if (energyLevelPercentage < (energyLevelPercentageTarget - BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL)) {
                // Start charging
                double powerSetpointNew = powerFlexibleAvailable.get(electricityBatteryAssetId)[0];
                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            } else if (energyLevelPercentage > (energyLevelPercentageTarget + BATTERY_ENERGY_LEVEL_PERCENTAGE_DEBOUNCE_INTERVAL)) {
                // Start discharging
                double powerSetpointNew = powerFlexibleAvailable.get(electricityBatteryAssetId)[1];
                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            } else if (powerSetpointCurrent > 0.0 && energyLevelPercentage < energyLevelPercentageTarget) {
                // Continue charging
                double powerSetpointNew = powerFlexibleAvailable.get(electricityBatteryAssetId)[0];
                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            } else if (powerSetpointCurrent < 0.0 && energyLevelPercentage > energyLevelPercentageTarget) {
                // Continue discharging
                double powerSetpointNew = powerFlexibleAvailable.get(electricityBatteryAssetId)[1];
                powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointNew);
            }
        }

        // Adjust battery power set-points in a system with power limits
        double powerSetpointsNewSum = powerSetpointsNew.values().stream().mapToDouble(Double::doubleValue).sum();
        double powerConsumptionVirtual = powerNet - powerSetpointsCurrentSum;

        Double chargingSpace = null;
        Double dischargingSpace = null;

        if (powerLimitMaximumBatteries != null) {
            chargingSpace = powerLimitMaximumBatteries - powerConsumptionVirtual;
        }

        if (powerLimitMinimumBatteries != null) {
            dischargingSpace = powerLimitMinimumBatteries - powerConsumptionVirtual;
        }

        if (chargingSpace != null && powerSetpointsNewSum > chargingSpace) {
            // Adjust battery power set-points in case of charging space shortage
            double powerReduction = powerSetpointsNewSum - chargingSpace;

            for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
                String electricityBatteryAssetId = electricityBatteryAsset.getId();
                double powerSetpointNew = powerSetpointsNew.getOrDefault(electricityBatteryAssetId, 0.0);

                // Only adjust battery power set-point for charging batteries
                if (powerSetpointNew > 0.0) {
                    powerReduction = powerReduction - powerSetpointNew;

                    if (powerReduction >= 0.0) {
                        powerSetpointsNew.put(electricityBatteryAssetId, 0.0);
                    } else {
                        double powerSetpointAdjusted = round(Math.min(-powerReduction, powerSetpointNew), 3);
                        powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointAdjusted);
                        break;
                    }
                }
            }
        } else if (dischargingSpace != null && powerSetpointsNewSum < dischargingSpace) {
            // Adjust battery power set-points in case of discharging space shortage
            double powerReduction = powerSetpointsNewSum - dischargingSpace;

            for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
                String electricityBatteryAssetId = electricityBatteryAsset.getId();
                double powerSetpointNew = powerSetpointsNew.getOrDefault(electricityBatteryAssetId, 0.0);

                // Only adjust battery power set-point for discharging batteries
                if (powerSetpointNew < 0.0) {
                    powerReduction = powerReduction - powerSetpointNew;

                    if (powerReduction <= 0.0) {
                        powerSetpointsNew.put(electricityBatteryAssetId, 0.0);
                    } else {
                        double powerSetpointAdjusted = Math.max(-powerReduction, powerSetpointNew);
                        powerSetpointsNew.put(electricityBatteryAssetId, powerSetpointAdjusted);
                        break;
                    }
                }
            }
        }

        return powerSetpointsNew;
    }

    private void batteryUpdatePowerSetpoints(List<EmsElectricityBatteryAsset> electricityBatteryAssets, Map<String, Double> powerSetpointsNew, Services services) {
        for (EmsElectricityBatteryAsset electricityBatteryAsset : electricityBatteryAssets) {
            String electricityBatteryAssetId = electricityBatteryAsset.getId();
            Double powerSetpointNew = powerSetpointsNew.get(electricityBatteryAssetId);

            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(electricityBatteryAssetId, EmsElectricityBatteryAsset.POWER_SETPOINT, powerSetpointNew), getClass().getSimpleName());
        }
    }

    private Map<Long, Integer> calculateTariffChargeAndDischargeZones(List<ValueDatapoint<?>> tariffImportDatapoints, List<ValueDatapoint<?>> tariffExportDatapoints, int window) {
        Map<Long, Integer> chargeAndDischargeZonesMap = new HashMap<>();

        // Return empty map when there is no tariff forecast present
        if (tariffImportDatapoints.size() < window || tariffExportDatapoints.size() < window) {
            return chargeAndDischargeZonesMap;
        }

        // Find the best import price for each day
        ZoneId zoneId = ZoneId.systemDefault();

        List<ValueDatapoint<?>> tariffImportMovingAverage = movingAverage(tariffImportDatapoints, window);
        List<ValueDatapoint<?>> tariffExportMovingAverage = movingAverage(tariffExportDatapoints, window);

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

        // Combine historic and predicted data-points into one list
        List<ValueDatapoint<?>> tariffCombined = new ArrayList<>(tariffHistoric);
        tariffCombined.addAll(tariffPredicted);

        return tariffCombined;
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

    public static List<ValueDatapoint<?>> movingAverage(List<ValueDatapoint<?>> dataPoints, int window) {
        List<ValueDatapoint<?>> result = new ArrayList<>();
        if (window <= 0 || dataPoints.size() < window) {
            return result;
        }

        double sum = 0.0;

        for (int i = 0; i < dataPoints.size(); i++) {
            // Extract numeric value
            Number value = (Number) dataPoints.get(i).getValue();
            sum += value.doubleValue();

            // Remove value exiting the sliding window
            if (i >= window) {
                Number oldValue = (Number) dataPoints.get(i - window).getValue();
                sum -= oldValue.doubleValue();
            }

            // When the window is "full", generate output datapoint
            if (i >= window - 1) {
                double avg = sum / window;

                // Use the timestamp of the window end (index i)
                ValueDatapoint<?> original = dataPoints.get(i);
                ValueDatapoint<Double> averaged =
                        new ValueDatapoint<>(original.getTimestamp(), avg);

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

        // Collect the day ahead tariff forecasts at the desired collect time
        if (collectTime != null) {
            // Calculate the 15-minute interval for collecting the day ahead tariff forecasts
            LocalDate currentDate = LocalDate.now();
            LocalDateTime collectDateTime = LocalDateTime.of(currentDate, collectTime);
            long collectTimeMillisStart = collectDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long collectTimeMillisEnd = collectTimeMillisStart + 15 * 60000;
            long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();

            if (currentTimeMillis >= collectTimeMillisStart && currentTimeMillis < collectTimeMillisEnd) {
                // Create asset datapoint query
                LocalDate tomorrowDate = currentDate.plusDays(1);
                LocalDateTime startOfNextDay = tomorrowDate.atStartOfDay();
                long startTimeMillis = startOfNextDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTimeMillis = startTimeMillis + 24 * 60 * 60000 - 60000;
                AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

                // Get the tariffs number of data-points from day ahead asset
                int tariffExportDayAheadSize = services.getAssetDatapointService().queryDatapoints(dayAheadAssetId, EmsDayAheadAsset.TARIFF_EXPORT_DAY_AHEAD.getName(), assetDatapointQuery).size();
                int tariffImportDayAheadSize = services.getAssetDatapointService().queryDatapoints(dayAheadAssetId, EmsDayAheadAsset.TARIFF_IMPORT_DAY_AHEAD.getName(), assetDatapointQuery).size();

                // Only update the historic datapoint table if there are no day ahead tariffs present
                if (tariffExportDayAheadSize == 0) {
                    List<ValueDatapoint<?>> tariffExport = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(), assetDatapointQuery);
                    services.getAssetDatapointService().upsertValues(dayAheadAssetId, EmsDayAheadAsset.TARIFF_EXPORT_DAY_AHEAD.getName(), tariffExport);
                }

                if (tariffImportDayAheadSize == 0) {
                    List<ValueDatapoint<?>> tariffImport = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName(), assetDatapointQuery);
                    services.getAssetDatapointService().upsertValues(dayAheadAssetId, EmsDayAheadAsset.TARIFF_IMPORT_DAY_AHEAD.getName(), tariffImport);
                }

                // Update the last update forecasts datetime field
                String lastUpdateForecasts = dayAheadAsset.getLastUpdateForecasts().orElse("");
                String lastUpdateForecastsNew = collectDateTime.toString();

                LocalDateTime lastUpdateDateTime = collectDateTime.plusDays(-1);

                if (!lastUpdateForecasts.isEmpty()) {
                    lastUpdateDateTime = LocalDateTime.parse(lastUpdateForecasts);
                }

                LocalDate nextUpdateDate = lastUpdateDateTime.plusDays(1).toLocalDate();

                if (nextUpdateDate.isEqual(currentDate)) {
                    services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(dayAheadAssetId, EmsDayAheadAsset.LAST_UPDATE_FORECASTS.getName(), lastUpdateForecastsNew, collectTimeMillisStart));
                }
            }
        }
    }

    record IndexedDatapoint(long timestamp, double value, int index) {
    }
}
