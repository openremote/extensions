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
package org.openremote.extension.ems.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.openremote.container.persistence.PersistenceService;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.ems.agent.*;
import org.openremote.manager.app.ConfigurationService;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.dashboard.DashboardStorageService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.model.Constants;
import org.openremote.model.Container;
import org.openremote.model.ContainerService;
import org.openremote.model.asset.AssetFilter;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.MetaItem;
import org.openremote.model.dashboard.Dashboard;
import org.openremote.model.manager.ManagerAppConfig;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.rules.RealmRuleset;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.UniqueIdentifierGenerator;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.MetaItemType;
import org.openremote.model.value.ValueType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.openremote.model.rules.Ruleset.Lang.GROOVY;
import static org.openremote.model.syslog.SyslogCategory.DATA;

public class EmsOptimisationSetupService implements ContainerService {
    protected static final Logger LOG = SyslogCategory.getLogger(DATA, EmsOptimisationService.class.getName());

    protected AssetProcessingService assetProcessingService;
    protected AssetStorageService assetStorageService;
    protected ClientEventService clientEventService;
    private ConfigurationService configurationService;
    protected DashboardStorageService dashboardStorageService;
    protected PersistenceService persistenceService;
    protected ScheduledExecutorService scheduledExecutorService;
    protected TimerService timerService;

    @Override
    public void init(Container container) throws Exception {
        assetProcessingService = container.getService(AssetProcessingService.class);
        assetStorageService = container.getService(AssetStorageService.class);
        clientEventService = container.getService(ClientEventService.class);
        configurationService = container.getService(ConfigurationService.class);
        dashboardStorageService = container.getService(DashboardStorageService.class);
        persistenceService = container.getService(PersistenceService.class);
        scheduledExecutorService = container.getScheduledExecutor();
        timerService = container.getService(TimerService.class);
    }

    @Override
    public void start(Container container) throws Exception {
        // List of asset types that are part of this service
        String[] assetTypes = {
                EmsEnergyOptimisationSetupAsset.DESCRIPTOR.getName()
        };

        // Listen to attribute events of listed asset types
        clientEventService.addSubscription(
                AttributeEvent.class,
                new AssetFilter<AttributeEvent>().setAssetTypes(assetTypes),
                this::processAttributeEvent);
    }

    @Override
    public void stop(Container container) throws Exception {
    }

    private void processAttributeEvent(AttributeEvent attributeEvent) {
        String assetType = attributeEvent.getAssetType();

        if (assetType.equals(EmsEnergyOptimisationSetupAsset.DESCRIPTOR.getName())) {
            processAttributeEventEnergyOptimisationSetupAsset(attributeEvent);
        }
    }

    private void processAttributeEventEnergyOptimisationSetupAsset(AttributeEvent attributeEvent) {
        String attributeName = attributeEvent.getName();
        String assetId = attributeEvent.getId();

        if (attributeName.equals(EmsEnergyOptimisationSetupAsset.CREATE_ENERGY_MANAGEMENT_SYSTEM.getName())) {
            boolean checkboxValue = (Boolean) attributeEvent.getValue().orElse(false);

            // Get asset from database
            EmsEnergyOptimisationSetupAsset setupAsset = (EmsEnergyOptimisationSetupAsset) assetStorageService.find(assetId);

            if (setupAsset == null || !checkboxValue) {
                return;
            }

            // Add a 1-second delay before resetting checkbox for user-friendliness
            scheduledExecutorService.schedule(() -> assetProcessingService.sendAttributeEvent(new AttributeEvent(setupAsset.getId(), EmsEnergyOptimisationSetupAsset.CREATE_ENERGY_MANAGEMENT_SYSTEM, false), getClass().getSimpleName()), 1, TimeUnit.SECONDS);

            try {
                updateManagerConfig();
                String infoFieldMessage = createEnergyManagementSystem(setupAsset);

                // Add a 1-second delay to ensure the info field is updated after all assets and rules are merged
                scheduledExecutorService.schedule(() -> assetProcessingService.sendAttributeEvent(new AttributeEvent(setupAsset.getId(), EmsEnergyOptimisationSetupAsset.INFO_FIELD, infoFieldMessage), getClass().getSimpleName()), 1, TimeUnit.SECONDS);
            } catch (Exception e) {
                LOG.warning(String.format("assetName='%s', assetId='%s'; An exception occurred during energy management system creation; Exception: %s", setupAsset.getName(), setupAsset.getId(), e));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void updateManagerConfig() {
        ManagerAppConfig managerConfig = configurationService.getManagerConfig();

        Map<String, Object> pages = managerConfig.getPages();
        if (pages == null) {
            pages = new HashMap<>();
            managerConfig.setPages(pages);
        }

        Map<String, Object> assets = (Map<String, Object>) pages.computeIfAbsent("assets", k -> new HashMap<>());
        Map<String, Object> viewer = (Map<String, Object>) assets.computeIfAbsent("viewer", k -> new HashMap<>());
        Map<String, Object> assetTypes = (Map<String, Object>) viewer.computeIfAbsent("assetTypes", k -> new HashMap<>());

        try {
            assetTypes.putAll(getEmsAssetTypesConfig());
            configurationService.saveManagerConfig(managerConfig);
        } catch (Exception e) {
            LOG.warning(String.format("Failed to update EMS asset types config; Exception: %s", e));
        }
    }

    private Map<String, Object> getEmsAssetTypesConfig() {
        String path = "/ems/config/asset-types.json";
        try (InputStream is = EmsOptimisationSetupService.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }

            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            ObjectMapper mapper = new ObjectMapper();
            return mapper.convertValue(mapper.readTree(json), new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }

    private String createEnergyManagementSystem(EmsEnergyOptimisationSetupAsset setupAsset) {
        String energyManagementSystemName = setupAsset.getEnergyManagementSystemName().orElse("");

        // Initiate info field string
        String infoFieldMessage;

        // Check if energy management system name is provided
        if (energyManagementSystemName.isBlank()) {
            infoFieldMessage = "Energy management system not created:\n" +
                    "  - Set energy management system name";
            return infoFieldMessage;
        }

        String energyOptimisationAssetName = energyManagementSystemName + " Energy Optimisation";

        // Check if energy management system already exists
        List<EmsEnergyOptimisationAsset> energyOptimisationAssets = assetStorageService
                .findAll(new AssetQuery().types(EmsEnergyOptimisationAsset.class).names(energyOptimisationAssetName))
                .stream()
                .map(asset -> (EmsEnergyOptimisationAsset) asset)
                .toList();

        if (!energyOptimisationAssets.isEmpty()) {
            infoFieldMessage = "Energy management system not created:\n" +
                    "  - \"" + energyOptimisationAssetName + "\" asset already exists";
            return infoFieldMessage;
        }


        // Create Energy Optimisation Asset
        EmsEnergyOptimisationAsset energyOptimisationAsset = new EmsEnergyOptimisationAsset(energyOptimisationAssetName);
        energyOptimisationAsset.setId(UniqueIdentifierGenerator.generateId()).setParent(setupAsset);

        double powerLimitMaximum = 500.0;
        double powerLimitMinimum = -500.0;
        double powerFluctuation = 40.0;
        Double powerConsumption = 400.0;

        String powerLimitMaximumProfileCsv = generatePowerLimitProfile(powerLimitMaximum);
        String powerLimitMinimumProfileCsv = generatePowerLimitProfile(powerLimitMinimum);

        energyOptimisationAsset
                .setOptimisationMethod(EmsEnergyOptimisationAsset.OptimisationMethodValueType.EmsOptimisation)
                .setPowerLimitMaximumInput(powerLimitMaximum)
                .setPowerLimitMaximumProfileManual(powerLimitMaximum)
                .setPowerLimitMaximumProfileManualInput(powerLimitMaximumProfileCsv)
                .setPowerLimitMaximumProfileTotal(powerLimitMaximum)
                .setPowerLimitMinimumInput(powerLimitMinimum)
                .setPowerLimitMinimumProfileManual(powerLimitMinimum)
                .setPowerLimitMinimumProfileManualInput(powerLimitMinimumProfileCsv)
                .setPowerLimitMinimumProfileTotal(powerLimitMinimum)
//                .setPowerNet(powerConsumption)
        ;

        energyOptimisationAsset.addOrReplaceAttributes(
                new Attribute<>("powerLimitMaximumFluctuationMargin", ValueType.POSITIVE_NUMBER, powerFluctuation)
                        .addOrReplaceMeta(
                                new MetaItem<>(MetaItemType.READ_ONLY),
                                new MetaItem<>(MetaItemType.UNITS, Constants.units(Constants.UNITS_KILO, Constants.UNITS_WATT))
                        ),
                new Attribute<>("powerLimitMinimumFluctuationMargin", ValueType.POSITIVE_NUMBER, powerFluctuation)
                        .addOrReplaceMeta(
                                new MetaItem<>(MetaItemType.READ_ONLY),
                                new MetaItem<>(MetaItemType.UNITS, Constants.units(Constants.UNITS_KILO, Constants.UNITS_WATT))
                        )
        );

        assetStorageService.merge(energyOptimisationAsset);

        // Create Electricity Battery Asset
        EmsElectricityBatteryAsset electricityBatteryAsset = new EmsElectricityBatteryAsset(energyManagementSystemName + " Battery");
        electricityBatteryAsset.setId(UniqueIdentifierGenerator.generateId()).setParent(energyOptimisationAsset);

        electricityBatteryAsset
                .setAllowCharging(true)
                .setAllowDischarging(true)
                .setChargeEfficiency(100)
                .setChargePowerMaximum(200.0)
                .setDischargeEfficiency(100)
                .setDischargePowerMaximum(-200.0)
                .setEnergyCapacity(425.0)
                .setEnergyLevelPercentage(50.0)
                .setEnergyLevelPercentageMaximum(95)
                .setEnergyLevelPercentageMinimum(20)
                .setPower(0.0);

        assetStorageService.merge(electricityBatteryAsset);

        // Create Day Ahead Asset
        if (setupAsset.getIncludeDayAheadForecasts().orElse(false)) {
            EmsDayAheadAsset dayAheadAsset = new EmsDayAheadAsset(energyManagementSystemName + " Day ahead forecasts");
            dayAheadAsset.setId(UniqueIdentifierGenerator.generateId()).setParent(energyOptimisationAsset);

            dayAheadAsset
                    .setCollectTimeForecasts("10:00")
                    .setUseTariffDayAheadForecasts(true);

            assetStorageService.merge(dayAheadAsset);
        }

        // Create GOPACS Asset
        if (setupAsset.getIncludeGopacs().orElse(false)) {
            EmsGOPACSAsset gopacsAsset = new EmsGOPACSAsset(energyManagementSystemName + " GOPACS");
            gopacsAsset.setId(UniqueIdentifierGenerator.generateId()).setParent(energyOptimisationAsset);

            assetStorageService.merge(gopacsAsset);
        }


        // Setup rules
        String realmName = setupAsset.getRealm();
        String energyOptimisationAssetId = energyOptimisationAsset.getId();
        String electricityBatteryAssetId = electricityBatteryAsset.getId();

        try (InputStream inputStream = EmsOptimisationService.class.getResourceAsStream("/ems/rules/EmsDemoSimulationRules.groovy")) {
            if (inputStream != null) {
                String rulesName = energyManagementSystemName + ": Demo simulation rules";

                String rules = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                // General variables rules
                rules = rules.replaceFirst("setId1", energyOptimisationAssetId);
                rules = rules.replaceFirst("setValue1", String.valueOf(powerConsumption));
                rules = rules.replaceFirst("setValue2", "1");
                rules = rules.replaceFirst("setValue3", String.valueOf(powerFluctuation));

                // Link forecasts rule
                rules = rules.replaceFirst("linkInputAssetId1", electricityBatteryAssetId);
                rules = rules.replaceFirst("linkInputAttributeName1", EmsElectricityBatteryAsset.POWER_SETPOINT.getName());
                rules = rules.replaceFirst("linkOutputAssetId1", energyOptimisationAssetId);
                rules = rules.replaceFirst("linkOutputAttributeName1", EmsEnergyOptimisationAsset.POWER_FLEXIBLE.getName());

                // Sum forecasts rule
                rules = rules.replaceFirst("sumInputAssetId1", energyOptimisationAssetId);
                rules = rules.replaceFirst("sumInputAttributeName1", EmsEnergyOptimisationAsset.POWER_CONSUMPTION.getName());
                rules = rules.replaceFirst("sumInputAssetId2", energyOptimisationAssetId);
                rules = rules.replaceFirst("sumInputAttributeName2", EmsEnergyOptimisationAsset.POWER_PRODUCTION.getName());
                rules = rules.replaceFirst("sumInputAssetId3", energyOptimisationAssetId);
                rules = rules.replaceFirst("sumInputAttributeName3", EmsEnergyOptimisationAsset.POWER_FLEXIBLE.getName());
                rules = rules.replaceFirst("sumOutputAssetId", energyOptimisationAssetId);
                rules = rules.replaceFirst("sumOutputAttributeName", EmsEnergyOptimisationAsset.POWER_NET.getName());

                RealmRuleset districtRuleSet = new RealmRuleset(realmName, rulesName, GROOVY, rules);

                // Merge rules into database
                persistenceService.doReturningTransaction(entityManager -> entityManager.merge(districtRuleSet));
            }
        } catch (Exception e) {
            LOG.warning(String.format("assetName='%s', assetId='%s'; Rules were not created for energy management system '%s'; Exception: %s", setupAsset.getName(), setupAsset.getId(), energyOptimisationAssetName, e));
        }

        Instant currentDate = timerService.getNow();

        // Create main dashboard
        try (InputStream inputStream = EmsOptimisationService.class.getResourceAsStream("/ems/dashboards/EmsOverviewDashboard.json")) {
            String dashboardName = energyManagementSystemName + ": Overview";
            if (inputStream != null) {

                String dashboardStr = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                dashboardStr = dashboardStr
                        .replace("setAssetId1", energyOptimisationAssetId)
                        .replace("setTemplateId1", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId1", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId2", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId3", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId4", UniqueIdentifierGenerator.generateId())
                ;

                Dashboard dashboard = ValueUtil.JSON.readValue(dashboardStr, Dashboard.class);

                dashboard.setCreatedOn(currentDate);
                dashboard.setRealm(realmName);
                dashboard.setDisplayName(dashboardName);

                dashboardStorageService.createNew(dashboard);
            }
        } catch (Exception e) {
            LOG.warning(String.format("assetName='%s', assetId='%s'; Dashboard was not created for energy management system '%s'; Exception: %s", setupAsset.getName(), setupAsset.getId(), energyOptimisationAssetName, e));
        }

        // Create battery dashboard
        try (InputStream inputStream = EmsOptimisationService.class.getResourceAsStream("/ems/dashboards/EmsBatteryDashboard.json")) {
            String dashboardName = energyManagementSystemName + ": Battery";
            if (inputStream != null) {

                String dashboardStr = IOUtils.toString(inputStream, StandardCharsets.UTF_8);

                dashboardStr = dashboardStr
                        .replace("setAssetId1", electricityBatteryAssetId)
                        .replace("setTemplateId1", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId1", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId2", UniqueIdentifierGenerator.generateId())
                        .replace("setWidgetId3", UniqueIdentifierGenerator.generateId())
                ;

                Dashboard dashboard = ValueUtil.JSON.readValue(dashboardStr, Dashboard.class);

                dashboard.setCreatedOn(currentDate);
                dashboard.setRealm(realmName);
                dashboard.setDisplayName(dashboardName);

                dashboardStorageService.createNew(dashboard);
            }
        } catch (Exception e) {
            LOG.warning(String.format("assetName='%s', assetId='%s'; Dashboard was not created for energy management system '%s'; Exception: %s", setupAsset.getName(), setupAsset.getId(), energyOptimisationAssetName, e));
        }


        // Create info field message
        infoFieldMessage = "Created energy management system \"" + energyOptimisationAssetName + "\":\n" +
                "\n" +
                "1) Drag the energy management system to your preferred location\n";

        if (setupAsset.getIncludeGopacs().orElse(false)) {
            infoFieldMessage = infoFieldMessage + "2) Set GOPACS variables\n";
        }

        infoFieldMessage = infoFieldMessage + "\nYou can delete this setup asset after you have created your energy management system.";

        LOG.info(String.format("assetName='%s', assetId='%s'; Created energy management system '%s'", setupAsset.getName(), setupAsset.getId(), energyOptimisationAssetName));

        return infoFieldMessage;
    }

    private String generatePowerLimitProfile(double powerLimit) {
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<String> dailyIntervals = new ArrayList<>();

        // Generate 15-minute intervals for a day
        for (LocalDateTime dateTime = startOfDay; dateTime.isBefore(endOfDay); dateTime = dateTime.plusMinutes(15)) {
            String time = dateTime.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
            dailyIntervals.add(time);
        }

        String[] days = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        StringBuilder powerLimitProfileCsv = new StringBuilder();

        // Generate power limit profile for a week
        for (String day : days) {
            for (String time : dailyIntervals) {
                String powerLimitProfileEntry = String.format("%s %s,%s\n", day, time, powerLimit);
                powerLimitProfileCsv.append(powerLimitProfileEntry);
            }
        }

        return powerLimitProfileCsv.toString();
    }
}
