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

import org.apache.camel.builder.RouteBuilder;
import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset;
import org.openremote.extension.ems.agent.EmsEnergyOptimisationAsset;
import org.openremote.extension.ems.agent.EmsGOPACSAsset;
import org.openremote.extension.ems.manager.gopacs.GOPACSHandler;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.datapoint.AssetDatapointService;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.gateway.GatewayService;
import org.openremote.model.Container;
import org.openremote.model.ContainerService;
import org.openremote.model.PersistenceEvent;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetFilter;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.datapoint.query.AssetDatapointAllQuery;
import org.openremote.model.query.AssetQuery;
import org.openremote.model.syslog.SyslogCategory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.openremote.container.persistence.PersistenceService.PERSISTENCE_TOPIC;
import static org.openremote.container.persistence.PersistenceService.isPersistenceEventForEntityType;
import static org.openremote.manager.gateway.GatewayService.isNotForGateway;
import static org.openremote.model.syslog.SyslogCategory.DATA;

public class EmsOptimisationService extends RouteBuilder implements ContainerService {

    protected static final Logger LOG = SyslogCategory.getLogger(DATA, EmsOptimisationService.class.getName());
    protected Services services;

    protected GOPACSHandler.Factory gopacsHandlerFactory;

    private final Map<String, ScheduledFuture<?>> energyOptimisationAssetsMap = new HashMap<>();
    private final Map<String, Long> energyOptimisationTimersMap = new HashMap<>();
    private final Map<String, GOPACSHandler> gopacsHandlerMap = new HashMap<>();


    @SuppressWarnings("unchecked")
    @Override
    public void configure() throws Exception {
        from(PERSISTENCE_TOPIC)
                .routeId("Persistence-EmsEnergyOptimisation")
                .filter(isPersistenceEventForEntityType(Asset.class))
                .filter(isNotForGateway(services.getGatewayService()))
                .process(exchange -> processAssetChange(exchange.getIn().getBody(PersistenceEvent.class)));
    }

    @Override
    public void init(Container container) throws Exception {
        services = Services.builder()
                .withAssetDatapointService(container.getService(AssetDatapointService.class))
                .withAssetPredictedDatapointService(container.getService(AssetPredictedDatapointService.class))
                .withAssetProcessingService(container.getService(AssetProcessingService.class))
                .withAssetStorageService(container.getService(AssetStorageService.class))
                .withClientEventService(container.getService(ClientEventService.class))
                .withGatewayService(container.getService(GatewayService.class))
                .withMessageBrokerService(container.getService(MessageBrokerService.class))
                .withScheduledExecutorService(container.getScheduledExecutor())
                .withTimerService(container.getService(TimerService.class))
                .build();

        gopacsHandlerFactory = new GOPACSHandler.Factory(container);
    }

    @Override
    public void start(Container container) throws Exception {
        // Add service
        services.getMessageBrokerService().getContext().addRoutes(this);

        // Find all energy optimisation assets
        List<EmsEnergyOptimisationAsset> energyOptimisationAssets = services.getAssetStorageService()
                .findAll(new AssetQuery().types(EmsEnergyOptimisationAsset.class))
                .stream()
                .map(asset -> (EmsEnergyOptimisationAsset) asset)
                .toList();

        // Start optimisation for enabled energy optimisation assets
        if (!energyOptimisationAssets.isEmpty()) {
            List<String> enabledEnergyOptimisationAssetIds = energyOptimisationAssets
                    .stream()
                    .filter(energyOptimisationAsset -> !energyOptimisationAsset.getOptimisationDisabled().orElse(false))
                    .map(Asset::getId)
                    .toList();

            LOG.info(String.format("Number of enabled '%s' assets = %s", EmsEnergyOptimisationAsset.class.getSimpleName(), enabledEnergyOptimisationAssetIds.size()));

            enabledEnergyOptimisationAssetIds.forEach(this::startOptimisation);
        }

        // Start GOPACS handler for all GOPACS assets
        services.getAssetStorageService()
                .findAll(new AssetQuery().types(EmsGOPACSAsset.class).attributeName(EmsGOPACSAsset.CONTRACTED_EAN.getName()))
                .stream()
                .map(asset -> (EmsGOPACSAsset) asset)
                .forEach(gopacsAsset -> startGopacsHandler(gopacsAsset.getContractedEan().orElse(""), gopacsAsset.getRealm(), gopacsAsset.getId()));


        // List of asset types that are part of the core EMS service
        String[] assetTypes = {
                EmsElectricityBatteryAsset.DESCRIPTOR.getName(),
                EmsEnergyOptimisationAsset.DESCRIPTOR.getName(),
                EmsGOPACSAsset.DESCRIPTOR.getName()
        };

        // Listen to attribute events of listed asset types
        services.getClientEventService().addSubscription(
                AttributeEvent.class,
                new AssetFilter<AttributeEvent>().setAssetTypes(assetTypes),
                this::processAttributeEvent);
    }

    @Override
    public void stop(Container container) throws Exception {
        energyOptimisationAssetsMap.forEach((assetId, scheduledFuture) -> stopOptimisation(assetId));
        energyOptimisationTimersMap.clear();
    }

    private void startOptimisation(String assetId) {
        final Runnable command = () -> {
            try {
                runOptimisation(assetId);
            } catch (Exception e) {
                LOG.severe(String.format("assetType='%s', assetId='%s'; Failed to run energy optimisation; Exception: %s", EmsEnergyOptimisationAsset.class.getSimpleName(), assetId, e));
            }
        };

        ScheduledFuture<?> scheduledFuture = services.getScheduledExecutorService().scheduleAtFixedRate(command, 1, 1, TimeUnit.MINUTES);
        energyOptimisationAssetsMap.put(assetId, scheduledFuture);

        energyOptimisationTimersMap.put(assetId, 0L);
    }

    private void runOptimisation(String energyOptimisationAssetId) throws Exception {
        // Get latest asset from database
        EmsEnergyOptimisationAsset energyOptimisationAsset = (EmsEnergyOptimisationAsset) services.getAssetStorageService().find(energyOptimisationAssetId);

        if (energyOptimisationAsset == null) {
            return;
        }

        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        Long triggerTimeMillis = energyOptimisationTimersMap.get(energyOptimisationAssetId);

        // Update forecast attributes every 15 minutes
        if (triggerTimeMillis != null && currentTimeMillis > triggerTimeMillis) {
            // Convert milliseconds to date
            ZoneId zone = ZoneId.systemDefault();
            LocalDate previousDate = Instant.ofEpochMilli(triggerTimeMillis - 60000).atZone(zone).toLocalDate();
            LocalDate currentDate = Instant.ofEpochMilli(currentTimeMillis).atZone(zone).toLocalDate();

            // Update manual forecasts after midnight
            if (!currentDate.equals(previousDate)) {
                updatePowerLimitProfileManualForecasts(energyOptimisationAsset);
            }

            EmsGOPACSAsset gopacsAsset = getGopacsAsset(energyOptimisationAsset);

            // Update total forecasts
            updatePowerLimitProfileTotalForecasts(energyOptimisationAsset, gopacsAsset);

            // Update energy optimisation asset attributes
            String[] energyOptimisationAssetAttributeNames = new String[]{
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL.getName(),
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(),
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL.getName(),
                    EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(),
                    EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(),
                    EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName()
            };

            updatePowerLimitProfileAttributes(energyOptimisationAsset, energyOptimisationAssetAttributeNames);

            if (gopacsAsset != null) {
                // Update GOPACS asset attributes
                String[] gopacsAttributeNames = new String[]{
                        EmsGOPACSAsset.POWER_LIMIT_MAXIMUM_PROFILE_FLEX_ORDER.getName(),
                        EmsGOPACSAsset.POWER_LIMIT_MINIMUM_PROFILE_FLEX_ORDER.getName()
                };

                updatePowerLimitProfileAttributes(gopacsAsset, gopacsAttributeNames);
            }

            // Calculate new trigger time
            triggerTimeMillis = currentTimeMillis - currentTimeMillis % (15 * 60 * 1000) + (15 * 60 * 1000);
            energyOptimisationTimersMap.put(energyOptimisationAssetId, triggerTimeMillis);
        }

        // Run selected optimisation method
        String optimisationMethodName = energyOptimisationAsset.getOptimisationMethod().orElse(EmsEnergyOptimisationAsset.OptimisationMethodValueType.None).toString();
        OptimisationMethodsLoader optimisationMethodsLoader = new OptimisationMethodsLoader();
        optimisationMethodsLoader.runOptimisationMethod(optimisationMethodName, energyOptimisationAssetId, services);
    }

    private void stopOptimisation(String assetId) {
        ScheduledFuture<?> scheduledFuture = energyOptimisationAssetsMap.remove(assetId);
        energyOptimisationTimersMap.remove(assetId);

        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }
    }

    private void startGopacsHandler(String contractedEan, String realm, String assetId) {
        if (contractedEan.isBlank()) {
            LOG.warning("Unable to deploy GOPACS because EAN is blank");
            return;
        }
        LOG.fine("Deploying GOPACS for EAN: " + contractedEan);
        gopacsHandlerMap.put(contractedEan, gopacsHandlerFactory.createHandler(
                contractedEan,
                realm,
                assetId)
        );
        LOG.fine("Deployed GOPACS for EAN: " + contractedEan);
    }

    private void stopGopacsHandler(String contractedEan) {
        GOPACSHandler existing = gopacsHandlerMap.get(contractedEan);
        if (existing != null) {
            existing.undeploy();
            gopacsHandlerMap.remove(contractedEan);
        }
    }

    protected void processAssetChange(PersistenceEvent<?> persistenceEvent) {
        if (persistenceEvent.getEntity() instanceof EmsEnergyOptimisationAsset emsEnergyOptimisationAsset) {
            if (persistenceEvent.getCause() == PersistenceEvent.Cause.DELETE) {
                stopOptimisation(emsEnergyOptimisationAsset.getId());
            }
        } else if (persistenceEvent.getEntity() instanceof EmsGOPACSAsset emsGOPACSAsset) {
            emsGOPACSAsset.getContractedEan().ifPresent(contractedEan -> {
                if (persistenceEvent.getCause() == PersistenceEvent.Cause.DELETE) {
                    stopGopacsHandler(contractedEan);
                }
                if (persistenceEvent.getCause() == PersistenceEvent.Cause.CREATE) {
                    startGopacsHandler(contractedEan, emsGOPACSAsset.getRealm(), emsGOPACSAsset.getId());
                }
                if (persistenceEvent.getCause() == PersistenceEvent.Cause.UPDATE) {
                    stopGopacsHandler(contractedEan);
                    startGopacsHandler(contractedEan, emsGOPACSAsset.getRealm(), emsGOPACSAsset.getId());
                }
            });
        }
    }

    private void processAttributeEvent(AttributeEvent attributeEvent) {
        String assetType = attributeEvent.getAssetType();

        if (assetType.equals(EmsEnergyOptimisationAsset.DESCRIPTOR.getName())) {
            processAttributeEventEmsEnergyOptimisationAsset(attributeEvent);
            return;
        }

        if (assetType.equals(EmsGOPACSAsset.DESCRIPTOR.getName())) {
            processAttributeEventEmsGOPACSAsset(attributeEvent);
            return;
        }
    }

    private void processAttributeEventEmsEnergyOptimisationAsset(AttributeEvent attributeEvent) {
        String assetId = attributeEvent.getId();

        // Get asset from database
        EmsEnergyOptimisationAsset energyOptimisationAsset = (EmsEnergyOptimisationAsset) services.getAssetStorageService().find(assetId);

        // Check if asset exists
        if (energyOptimisationAsset == null) {
            return;
        }

        String logPrefix = String.format("assetType='%s', assetId='%s', assetName='%s', attributeName='%s'", attributeEvent.getAssetType(), attributeEvent.getId(), attributeEvent.getAssetName(), attributeEvent.getName());
        String attributeName = attributeEvent.getName();

        // Disable/enable optimisation
        if (attributeName.equals(EmsEnergyOptimisationAsset.OPTIMISATION_DISABLED.getName())) {
            boolean disabled = (Boolean) attributeEvent.getValue().orElse(false);

            if (!disabled && !energyOptimisationAssetsMap.containsKey(assetId)) {
                startOptimisation(assetId);
                LOG.info(String.format("%s; Enabled energy optimisation", logPrefix));
            } else if (disabled && energyOptimisationAssetsMap.containsKey(assetId)) {
                stopOptimisation(assetId);
                LOG.info(String.format("%s; Disabled energy optimisation", logPrefix));
            }
            return;
        }


        // Generate power maximum profile manual input
        if (attributeName.equals(EmsEnergyOptimisationAsset.GENERATE_POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT.getName())) {
            boolean generatePowerLimitMaximumProfileManualInput = (Boolean) attributeEvent.getValue().orElse(false);

            if (!generatePowerLimitMaximumProfileManualInput) {
                return;
            }

            // Add 1-second delay before resetting checkbox for user-friendliness
            services.getScheduledExecutorService().schedule(() -> services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(assetId, attributeName, false), getClass().getSimpleName()), 1, TimeUnit.SECONDS);

            Double powerLimitMaximumInput = energyOptimisationAsset.getPowerLimitMaximumInput().orElse(null);

            if (powerLimitMaximumInput == null) {
                LOG.warning(String.format("%s; '%s' attribute value is missing. Unable to generate power limit maximum profile", logPrefix, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_INPUT.getName()));
                return;
            }

            String powerLimitMaximumProfileManualInput = energyOptimisationAsset.getPowerLimitMaximumProfileManualInput().orElse("");

            if (!powerLimitMaximumProfileManualInput.isBlank()) {
                LOG.warning(String.format("%s; '%s' attribute is already set. Remove current power limit maximum profile to generate a new power limit profile", logPrefix, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT.getName()));
                return;
            }

            String powerLimitMaximumProfileCsv = generatePowerLimitProfile(powerLimitMaximumInput);
            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT, powerLimitMaximumProfileCsv), getClass().getSimpleName());
        }


        // Generate power limit minimum profile input
        if (attributeName.equals(EmsEnergyOptimisationAsset.GENERATE_POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT.getName())) {
            boolean generatePowerLimitMinimumProfileManualInput = (Boolean) attributeEvent.getValue().orElse(false);

            if (!generatePowerLimitMinimumProfileManualInput) {
                return;
            }

            // Add 1-second delay before resetting checkbox for user-friendliness
            services.getScheduledExecutorService().schedule(() -> services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(assetId, attributeName, false), getClass().getSimpleName()), 1, TimeUnit.SECONDS);

            Double powerLimitMinimumInput = energyOptimisationAsset.getPowerLimitMinimumInput().orElse(null);

            if (powerLimitMinimumInput == null) {
                LOG.warning(String.format("%s; '%s' attribute value is missing. Unable to generate power limit minimum profile", logPrefix, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_INPUT.getName()));
                return;
            }

            String powerLimitMinimumProfileManualInput = energyOptimisationAsset.getPowerLimitMinimumProfileManualInput().orElse("");

            if (!powerLimitMinimumProfileManualInput.isBlank()) {
                LOG.warning(String.format("%s; '%s' attribute is already set. Remove current power limit minimum profile to generate a new power limit profile", logPrefix, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT.getName()));
                return;
            }

            String powerLimitMinimumProfileCsv = generatePowerLimitProfile(powerLimitMinimumInput);
            services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(energyOptimisationAsset.getId(), EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT, powerLimitMinimumProfileCsv), getClass().getSimpleName());
        }


        // Update power limit maximum profile manual
        if (attributeName.equals(EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL_INPUT.getName())) {
            String powerLimitMaximumProfileManualInput = (String) attributeEvent.getValue().orElse("");

            if (powerLimitMaximumProfileManualInput.isBlank()) {
                return;
            }

            // Parse CSV
            List<String[]> parsedCSV = parseCSV(powerLimitMaximumProfileManualInput, ",", logPrefix);

            // Convert parsed CSV to database insertable data-points
            List<ValueDatapoint<?>> powerLimitMaximumProfileManual = csvToValueDatapoints(parsedCSV, logPrefix);
            services.getAssetPredictedDatapointService().updateValues(assetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL.getName(), powerLimitMaximumProfileManual);

            List<List<ValueDatapoint<?>>> powerLimitProfiles = new ArrayList<>();
            powerLimitProfiles.add(powerLimitMaximumProfileManual);

            EmsGOPACSAsset gopacsAsset = getGopacsAsset(energyOptimisationAsset);

            if (gopacsAsset != null) {
                // Forecast data-point range of 1 week
                long startTimeMillis = services.getTimerService().getCurrentTimeMillis();
                long endTimeMillis = startTimeMillis - startTimeMillis % (24 * 60 * 60000) + (8 * 24 * 60 * 60000);
                AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

                // Get power limit profile day-ahead
                List<ValueDatapoint<?>> powerLimitMaximumProfileDayAhead = services.getAssetPredictedDatapointService().queryDatapoints(gopacsAsset.getId(), EmsGOPACSAsset.POWER_LIMIT_MAXIMUM_PROFILE_FLEX_ORDER.getName(), assetDatapointQuery);
                powerLimitProfiles.add(powerLimitMaximumProfileDayAhead);
            }

            // Update power limit maximum profile total
            List<ValueDatapoint<?>> powerLimitMaximumProfileTotal = calculatePowerLimitProfileTotal(powerLimitProfiles, "floor");
            services.getAssetPredictedDatapointService().updateValues(assetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(), powerLimitMaximumProfileTotal);
        }


        // Update power limit minimum profile manual
        if (attributeName.equals(EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL_INPUT.getName())) {
            String powerLimitMinimumProfileManualInput = (String) attributeEvent.getValue().orElse("");

            if (powerLimitMinimumProfileManualInput.isBlank()) {
                return;
            }

            // Parse CSV
            List<String[]> parsedCSV = parseCSV(powerLimitMinimumProfileManualInput, ",", logPrefix);

            // Convert parsed CSV to database insertable data-points
            List<ValueDatapoint<?>> powerLimitMinimumProfileManual = csvToValueDatapoints(parsedCSV, logPrefix);
            services.getAssetPredictedDatapointService().updateValues(assetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL.getName(), powerLimitMinimumProfileManual);

            List<List<ValueDatapoint<?>>> powerLimitProfiles = new ArrayList<>();
            powerLimitProfiles.add(powerLimitMinimumProfileManual);

            EmsGOPACSAsset gopacsAsset = getGopacsAsset(energyOptimisationAsset);

            if (gopacsAsset != null) {
                // Forecast data-point range of 1 week
                long startTimeMillis = services.getTimerService().getCurrentTimeMillis();
                long endTimeMillis = startTimeMillis - startTimeMillis % (24 * 60 * 60000) + (8 * 24 * 60 * 60000);
                AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

                // Get power limit profile day-ahead
                List<ValueDatapoint<?>> powerLimitMinimumProfileDayAhead = services.getAssetPredictedDatapointService().queryDatapoints(gopacsAsset.getId(), EmsGOPACSAsset.POWER_LIMIT_MINIMUM_PROFILE_FLEX_ORDER.getName(), assetDatapointQuery);
                powerLimitProfiles.add(powerLimitMinimumProfileDayAhead);
            }

            // Update power limit minimum profile total
            List<ValueDatapoint<?>> powerLimitMinimumProfileTotal = calculatePowerLimitProfileTotal(powerLimitProfiles, "ceil");
            services.getAssetPredictedDatapointService().updateValues(assetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(), powerLimitMinimumProfileTotal);
        }


        // Run selected optimisation method
        if (attributeName.equals(EmsEnergyOptimisationAsset.OPTIMISATION_METHOD.getName())) {
            if (energyOptimisationAssetsMap.containsKey(assetId)) {
                String optimisationMethodName = attributeEvent.getValue().orElse(EmsEnergyOptimisationAsset.OptimisationMethodValueType.None).toString();
                OptimisationMethodsLoader optimisationMethodsLoader = new OptimisationMethodsLoader();
                optimisationMethodsLoader.runOptimisationMethod(optimisationMethodName, assetId, services);
            }
        }
    }

    private void processAttributeEventEmsGOPACSAsset(AttributeEvent attributeEvent) {
        String assetId = attributeEvent.getId();

        // Get asset from database
        EmsGOPACSAsset gopacsAsset = (EmsGOPACSAsset) services.getAssetStorageService().find(assetId);

        // Check if asset exists
        if (gopacsAsset == null) {
            return;
        }

        String attributeName = attributeEvent.getName();

        if (attributeName.equals(EmsGOPACSAsset.CONTRACTED_EAN.getName())) {
            attributeEvent.getOldValue(String.class).ifPresent(this::stopGopacsHandler);
            attributeEvent.getValue(String.class).ifPresent(contractedEan -> startGopacsHandler(contractedEan, attributeEvent.getRealm(), attributeEvent.getId()));
        }
    }

    private void updatePowerLimitProfileManualForecasts(EmsEnergyOptimisationAsset energyOptimisationAsset) {
        String logPrefix = String.format("assetType='%s', assetId='%s', assetName='%s'", energyOptimisationAsset.getAssetType(), energyOptimisationAsset.getId(), energyOptimisationAsset.getAssetName());
        String[] powerLimitTypes = {"maximum", "minimum"};

        for (String powerLimitType : powerLimitTypes) {
            String powerLimitProfileManualInput;
            String powerLimitProfileManualAttributeName;

            if (powerLimitType.equals("maximum")) {
                powerLimitProfileManualInput = energyOptimisationAsset.getPowerLimitMaximumProfileManualInput().orElse("");
                powerLimitProfileManualAttributeName = EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL.getName();
            } else {
                powerLimitProfileManualInput = energyOptimisationAsset.getPowerLimitMinimumProfileManualInput().orElse("");
                powerLimitProfileManualAttributeName = EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL.getName();
            }

            if (powerLimitProfileManualInput.isBlank()) {
                continue;
            }

            // Parse CSV
            List<String[]> parsedCSV = parseCSV(powerLimitProfileManualInput, ",", logPrefix);

            // Convert parsed CSV to database insertable data-points
            List<ValueDatapoint<?>> powerLimitProfileManual = csvToValueDatapoints(parsedCSV, logPrefix);

            // Update manual forecast
            services.getAssetPredictedDatapointService().updateValues(energyOptimisationAsset.getId(), powerLimitProfileManualAttributeName, powerLimitProfileManual);
        }
    }

    private void updatePowerLimitProfileAttributes(Asset<?> asset, String[] attributeNames) {
        String assetId = asset.getId();

        // Get forecast data-points for current 15 minute interval
        long currentTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long startTimeMillis = currentTimeMillis - currentTimeMillis % (15 * 60000);
        long endTimeMillis = startTimeMillis + (15 * 60000);
        AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

        for (String attributeName : attributeNames) {
            List<ValueDatapoint<?>> forecastDatapoints = services.getAssetPredictedDatapointService().queryDatapoints(assetId, attributeName, assetDatapointQuery);

            // Check if there are forecast data-points in the database
            if (forecastDatapoints.isEmpty()) {
                continue;
            }

            // The last data-point in list is the first data-point in the 15-minute interval
            ValueDatapoint<?> forecastDatapoint = forecastDatapoints.getLast();
            long forecastAttributeMillis = asset.getAttributes().get(attributeName).flatMap(Attribute::getTimestamp).orElse(0L);

            // Update attribute with new forecast value
            if (forecastDatapoint.getTimestamp() > forecastAttributeMillis && forecastDatapoint.getTimestamp() <= currentTimeMillis) {
                Double powerLimit = (Double) forecastDatapoint.getValue();
                long timestampMillis = forecastDatapoint.getTimestamp();
                services.getAssetProcessingService().sendAttributeEvent(new AttributeEvent(assetId, attributeName, powerLimit, timestampMillis));
            }
        }
    }

    private void updatePowerLimitProfileTotalForecasts(EmsEnergyOptimisationAsset energyOptimisationAsset, EmsGOPACSAsset gopacsAsset) {
        String energyOptimisationAssetId = energyOptimisationAsset.getId();

        // Forecast data-point range of 1 week
        long startTimeMillis = services.getTimerService().getCurrentTimeMillis();
        long endTimeMillis = startTimeMillis - startTimeMillis % (24 * 60 * 60000) + (8 * 24 * 60 * 60000);
        AssetDatapointAllQuery assetDatapointQuery = new AssetDatapointAllQuery(startTimeMillis, endTimeMillis);

        // Get power limit profiles
        List<ValueDatapoint<?>> powerLimitMaximumProfileManual = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_MANUAL.getName(), assetDatapointQuery);
        List<ValueDatapoint<?>> powerLimitMinimumProfileManual = services.getAssetPredictedDatapointService().queryDatapoints(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_MANUAL.getName(), assetDatapointQuery);

        List<ValueDatapoint<?>> powerLimitMaximumProfileTotal;
        List<ValueDatapoint<?>> powerLimitMinimumProfileTotal;

        if (gopacsAsset != null) {
            List<ValueDatapoint<?>> powerLimitMaximumProfileDayAhead = services.getAssetPredictedDatapointService().queryDatapoints(gopacsAsset.getId(), EmsGOPACSAsset.POWER_LIMIT_MAXIMUM_PROFILE_FLEX_ORDER.getName(), assetDatapointQuery);
            List<ValueDatapoint<?>> powerLimitMinimumProfileDayAhead = services.getAssetPredictedDatapointService().queryDatapoints(gopacsAsset.getId(), EmsGOPACSAsset.POWER_LIMIT_MINIMUM_PROFILE_FLEX_ORDER.getName(), assetDatapointQuery);

            // Calculate power limit profile totals
            List<List<ValueDatapoint<?>>> powerLimitMaximumProfiles = new ArrayList<>();
            powerLimitMaximumProfiles.add(powerLimitMaximumProfileDayAhead);
            powerLimitMaximumProfiles.add(powerLimitMaximumProfileManual);

            List<List<ValueDatapoint<?>>> powerLimitMinimumProfiles = new ArrayList<>();
            powerLimitMinimumProfiles.add(powerLimitMinimumProfileDayAhead);
            powerLimitMinimumProfiles.add(powerLimitMinimumProfileManual);

            powerLimitMaximumProfileTotal = calculatePowerLimitProfileTotal(powerLimitMaximumProfiles, "floor");
            powerLimitMinimumProfileTotal = calculatePowerLimitProfileTotal(powerLimitMinimumProfiles, "ceil");
        } else {
            powerLimitMaximumProfileTotal = powerLimitMaximumProfileManual;
            powerLimitMinimumProfileTotal = powerLimitMinimumProfileManual;
        }

        // Update power limit profile totals
        services.getAssetPredictedDatapointService().updateValues(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MAXIMUM_PROFILE_TOTAL.getName(), powerLimitMaximumProfileTotal);
        services.getAssetPredictedDatapointService().updateValues(energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_LIMIT_MINIMUM_PROFILE_TOTAL.getName(), powerLimitMinimumProfileTotal);
    }

    private EmsGOPACSAsset getGopacsAsset(EmsEnergyOptimisationAsset energyOptimisationAsset) {
        EmsGOPACSAsset gopacsAsset = null;
        String logPrefix = String.format("assetType='%s', assetId='%s', assetName='%s'", energyOptimisationAsset.getAssetType(), energyOptimisationAsset.getId(), energyOptimisationAsset.getAssetName());

        // Check for GOPACS assets
        List<EmsGOPACSAsset> gopacsAssets = services.getAssetStorageService()
                .findAll(new AssetQuery().parents(energyOptimisationAsset.getId()).types(EmsGOPACSAsset.class))
                .stream()
                .map(asset -> (EmsGOPACSAsset) asset)
                .toList();

        if (gopacsAssets.size() == 1) {
            gopacsAsset = gopacsAssets.getFirst();
        } else if (gopacsAssets.size() > 1) {
            LOG.warning(String.format("%s; Found %s '%s' assets; Only 1 '%s' asset is allowed; Remove additional '%s' assets", logPrefix, gopacsAssets.size(), EmsGOPACSAsset.class.getSimpleName(), EmsGOPACSAsset.class.getSimpleName(), EmsGOPACSAsset.class.getSimpleName()));
        }

        return gopacsAsset;
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

    private List<String[]> parseCSV(String csvFile, String delimiter, String logPrefix) {
        List<String[]> data = new ArrayList<>();

        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new StringReader(csvFile));
            String line;
            while ((line = reader.readLine()) != null) {
                // Split data based on delimiter
                String[] row = line.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

                // Handle escape character "double quotes"
                for (int i = 0; i < row.length; i++) {
                    row[i] = row[i].replaceFirst("\"", "");

                    if (row[i].endsWith("\"")) {
                        row[i] = row[i].substring(0, row[i].length() - 1);
                    }
                    row[i] = row[i].replace("\"\"", "\"");
                }

                data.add(row);
            }
        } catch (IOException e) {
            LOG.warning(String.format("%s; Error while parsing CSV file; Exception: %s", logPrefix, e));
        } finally {
            try {
                assert reader != null;
                reader.close();
            } catch (IOException e) {
                LOG.warning(String.format("%s; Error while closing CSV file reader; Exception: %s", logPrefix, e));
            }
        }

        return data;
    }

    private List<ValueDatapoint<?>> csvToValueDatapoints(List<String[]> parsedCSV, String logPrefix) {
        List<ValueDatapoint<?>> valueDatapointList = new ArrayList<>();

        // Get dates for the coming 7 days starting from today
        Map<String, String> dayToDateMap = weekDates();

        List<String> incorrectlyFormattedRows = new ArrayList<>();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        long currentMillis = System.currentTimeMillis();

        for (String[] line : parsedCSV) {
            int rowLength = line.length;

            if (rowLength != 2) {
                incorrectlyFormattedRows.add(Arrays.toString(line));
                continue;
            }

            String dayTimeStr = line[0];
            String valueStr = line[1];

            String dayAbbreviation = dayTimeStr.split(" ")[0];
            String dateStr = dayToDateMap.get(dayAbbreviation);
            LocalDate today = LocalDate.now();

            if (dateStr != null) {
                // Replace the day in the input string with the date
                String dateTimeStr = dayTimeStr.replaceFirst(dayAbbreviation, dateStr);

                try {
                    LocalDate timestampLocalDate = LocalDate.parse(dateTimeStr, dateTimeFormatter);
                    long timestampMillis = simpleDateFormat.parse(dateTimeStr).getTime();
                    double value = Double.parseDouble(valueStr);

                    // Add this day next week
                    if (today.equals(timestampLocalDate)) {
                        long timestampMillisPlusOneWeek = timestampMillis + 604800000L;
                        valueDatapointList.add((new ValueDatapoint<>(timestampMillisPlusOneWeek, value)));
                    }

                    if (timestampMillis > currentMillis) {
                        valueDatapointList.add((new ValueDatapoint<>(timestampMillis, value)));
                    }
                } catch (Exception e) {
                    incorrectlyFormattedRows.add(Arrays.toString(line));
                }

            } else {
                incorrectlyFormattedRows.add(Arrays.toString(line));
            }
        }

        if (incorrectlyFormattedRows.size() > 0) {
            LOG.warning(String.format("%s; Input is incorrectly formatted at row %s;", logPrefix, incorrectlyFormattedRows));
        }

        return valueDatapointList;
    }

    private Map<String, String> weekDates() {
        String[] dayAbbreviations = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

        // Map to match day abbreviations with DayOfWeek enum
        Map<String, DayOfWeek> dayMap = new HashMap<>();
        dayMap.put("Mon", DayOfWeek.MONDAY);
        dayMap.put("Tue", DayOfWeek.TUESDAY);
        dayMap.put("Wed", DayOfWeek.WEDNESDAY);
        dayMap.put("Thu", DayOfWeek.THURSDAY);
        dayMap.put("Fri", DayOfWeek.FRIDAY);
        dayMap.put("Sat", DayOfWeek.SATURDAY);
        dayMap.put("Sun", DayOfWeek.SUNDAY);

        LocalDate today = LocalDate.now();
        Map<String, String> dayToDateMap = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (String dayAbbreviation : dayAbbreviations) {
            // Get day of week for the day abbreviation
            DayOfWeek dayOfWeek = dayMap.get(dayAbbreviation);

            // Get date of day of week for this week
            LocalDate date = today.with(dayOfWeek);

            // If the day is in the past for this week, add 7 days to move to next week
            if (date.isBefore(today)) {
                date = date.plusWeeks(1);
            }

            dayToDateMap.put(dayAbbreviation, date.format(formatter));
        }

        return dayToDateMap;
    }

    private List<ValueDatapoint<?>> calculatePowerLimitProfileTotal(List<List<ValueDatapoint<?>>> powerLimitProfiles, String floorOrCeil) {
        List<ValueDatapoint<?>> powerLimitProfileTotal = new ArrayList<>();
        Map<Long, List<Double>> mergedMap = new HashMap<>();

        for (List<ValueDatapoint<?>> powerLimitProfile : powerLimitProfiles) {
            for (ValueDatapoint<?> valueDatapoint : powerLimitProfile) {
                long timestamp = valueDatapoint.getTimestamp();
                Double value = (Double) valueDatapoint.getValue();
                mergedMap.computeIfAbsent(timestamp, k -> new ArrayList<>()).add(value);
            }
        }

        if (floorOrCeil.equals("floor")) {
            mergedMap.forEach((timestamp, values) -> {
                ValueDatapoint<?> valueDatapoint = new ValueDatapoint<>(timestamp, Collections.min(values));
                powerLimitProfileTotal.add(valueDatapoint);
            });
        } else if (floorOrCeil.equals("ceil")) {
            mergedMap.forEach((timestamp, values) -> {
                ValueDatapoint<?> valueDatapoint = new ValueDatapoint<>(timestamp, Collections.max(values));
                powerLimitProfileTotal.add(valueDatapoint);
            });
        }

        return powerLimitProfileTotal;
    }
}