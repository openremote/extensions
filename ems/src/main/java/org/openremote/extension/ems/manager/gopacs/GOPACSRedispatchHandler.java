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
package org.openremote.extension.ems.manager.gopacs;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.ems.agent.EmsGOPACSAsset;
import org.openremote.extension.ems.manager.gopacs.dto.AnnouncementDto;
import org.openremote.extension.ems.manager.gopacs.dto.EanSolvingEffectivityDto;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.model.Container;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.syslog.SyslogCategory;

import jakarta.ws.rs.core.Response;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.model.syslog.SyslogCategory.API;

/**
 * Handles GOPACS Redispatch (intraday congestion management) by polling for
 * congestion announcements, checking EAN solving effectivity, calculating
 * suggested bid parameters, and managing the operator confirmation workflow.
 */
public class GOPACSRedispatchHandler {

    private static final Logger LOG = SyslogCategory.getLogger(API, GOPACSRedispatchHandler.class);

    public static final String GOPACS_REDISPATCH_API_KEY = "GOPACS_REDISPATCH_API_KEY";
    public static final String GOPACS_REDISPATCH_URL = "GOPACS_REDISPATCH_URL";
    public static final String DEFAULT_GOPACS_REDISPATCH_URL = "https://idcons.gopacs-services.eu";
    public static final String GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES = "GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES";
    public static final String DEFAULT_GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES = "5";

    protected final String contractedEAN;
    protected final String assetId;
    protected final String realm;

    protected final AssetProcessingService assetProcessingService;
    protected final AssetStorageService assetStorageService;
    protected final AssetPredictedDatapointService assetPredictedDatapointService;
    protected final ScheduledExecutorService scheduledExecutorService;
    protected final TimerService timerService;

    protected final ResteasyClient client;
    protected final GOPACSAnnouncementResource announcementResource;
    protected final GOPACSEanEffectivityResource eanEffectivityResource;

    protected final ObjectMapper objectMapper;
    protected final String apiKey;
    protected final int pollIntervalMinutes;

    private ScheduledFuture<?> pollingFuture;
    private String lastProcessedAnnouncementId;

    public static class Factory {
        protected Container container;

        public Factory(Container container) {
            this.container = container;
        }

        public GOPACSRedispatchHandler createHandler(String contractedEan, String realm, String assetId) {
            return new GOPACSRedispatchHandler(contractedEan, realm, assetId, container);
        }
    }

    protected GOPACSRedispatchHandler(String contractedEAN, String realm, String assetId, Container container) {
        this.contractedEAN = contractedEAN;
        this.realm = realm;
        this.assetId = assetId;

        this.assetProcessingService = container.getService(AssetProcessingService.class);
        this.assetStorageService = container.getService(AssetStorageService.class);
        this.assetPredictedDatapointService = container.getService(AssetPredictedDatapointService.class);
        this.scheduledExecutorService = container.getScheduledExecutor();
        this.timerService = container.getService(TimerService.class);

        this.apiKey = container.getConfig().get(GOPACS_REDISPATCH_API_KEY);
        this.pollIntervalMinutes = Math.max(5, Integer.parseInt(
                container.getConfig().getOrDefault(GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES, DEFAULT_GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES)));

        String redispatchUrl = container.getConfig().getOrDefault(GOPACS_REDISPATCH_URL, DEFAULT_GOPACS_REDISPATCH_URL);

        this.client = createClient(org.openremote.container.Container.EXECUTOR);
        this.announcementResource = client.target(redispatchUrl).proxy(GOPACSAnnouncementResource.class);
        this.eanEffectivityResource = client.target(redispatchUrl).proxy(GOPACSEanEffectivityResource.class);

        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        if (apiKey == null || apiKey.isBlank()) {
            LOG.warning("GOPACS_REDISPATCH_API_KEY not configured; EAN effectivity checks will be skipped for EAN: " + contractedEAN);
        }

        LOG.info("Initialized GOPACSRedispatchHandler for EAN: " + contractedEAN + " (poll interval: " + pollIntervalMinutes + " min)");
    }

    public void startPolling() {
        if (pollingFuture != null && !pollingFuture.isCancelled()) {
            LOG.warning("Polling already active for EAN: " + contractedEAN);
            return;
        }

        pollingFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                pollAndProcess();
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error during redispatch poll for EAN: " + contractedEAN, e);
            }
        }, 0, pollIntervalMinutes, TimeUnit.MINUTES);

        LOG.info("Started redispatch polling for EAN: " + contractedEAN);
    }

    public void stopPolling() {
        if (pollingFuture != null) {
            pollingFuture.cancel(false);
            pollingFuture = null;
        }
        client.close();
        LOG.info("Stopped redispatch polling for EAN: " + contractedEAN);
    }

    protected void pollAndProcess() {
        LOG.fine("Redispatch poll started for EAN: " + contractedEAN);

        // Get postal code from asset
        EmsGOPACSAsset gopacsAsset = (EmsGOPACSAsset) assetStorageService.find(assetId);
        if (gopacsAsset == null) {
            LOG.warning("GOPACS asset not found: " + assetId);
            return;
        }

        String postalCode = gopacsAsset.getPostalCode().orElse(null);
        LOG.fine("Fetching announcements for EAN: " + contractedEAN + " with postalCode: " + postalCode);

        // Fetch announcements
        List<AnnouncementDto> announcements = fetchAnnouncements(postalCode);
        if (announcements == null || announcements.isEmpty()) {
            LOG.fine("No announcements found for EAN: " + contractedEAN);
            updateLastPoll();
            clearAnnouncementAttributes();
            return;
        }

        LOG.fine("Fetched " + announcements.size() + " announcements for EAN: " + contractedEAN);

        // Filter for CONGESTIONMANAGEMENT and OPEN
        List<AnnouncementDto> relevant = announcements.stream()
                .filter(a -> "CONGESTIONMANAGEMENT".equals(a.getType()))
                .filter(a -> "ANNOUNCEMENT_OPEN".equals(a.getAnnouncementState()))
                .toList();

        if (relevant.isEmpty()) {
            LOG.fine("No open CONGESTIONMANAGEMENT announcements for EAN: " + contractedEAN);
            updateLastPoll();
            clearAnnouncementAttributes();
            return;
        }

        LOG.fine("Found " + relevant.size() + " relevant open announcements for EAN: " + contractedEAN);

        // If no postal code filter, check EAN effectivity to find relevant ones
        AnnouncementDto selected = null;
        String effectivityCategory = null;

        if (postalCode == null || postalCode.isBlank()) {
            // No postal code → check effectivity for all, find ones where our EAN is listed
            LOG.fine("No postalCode set, checking EAN effectivity for " + relevant.size() + " announcements");
            for (AnnouncementDto announcement : relevant) {
                String category = checkEanEffectivity(announcement.getId());
                if (category != null) {
                    LOG.fine("EAN " + contractedEAN + " found in category '" + category + "' for announcement " + announcement.getId());
                    // Prefer MANDATORY over VOLUNTARY
                    if (selected == null || "MANDATORY".equals(announcement.getComplianceType())) {
                        selected = announcement;
                        effectivityCategory = category;
                    }
                }
            }
        } else {
            // With postal code → select best announcement, then check effectivity
            // Prefer MANDATORY over VOLUNTARY
            selected = relevant.stream()
                    .filter(a -> "MANDATORY".equals(a.getComplianceType()))
                    .findFirst()
                    .orElse(relevant.getFirst());

            LOG.fine("Selected announcement " + selected.getId() + " (" + selected.getComplianceType() + ") from " + selected.getOrganisationName());
            effectivityCategory = checkEanEffectivity(selected.getId());
        }

        if (selected == null) {
            LOG.fine("No relevant announcement found for EAN: " + contractedEAN + " after effectivity check");
            updateLastPoll();
            clearAnnouncementAttributes();
            return;
        }

        // Check if this is a new announcement
        boolean isNew = !selected.getId().equals(lastProcessedAnnouncementId);
        lastProcessedAnnouncementId = selected.getId();

        if (isNew) {
            LOG.info("New redispatch announcement for EAN " + contractedEAN + ": id=" + selected.getId()
                    + ", compliance=" + selected.getComplianceType()
                    + ", org=" + selected.getOrganisationName()
                    + ", effectivity=" + effectivityCategory);
        } else {
            LOG.fine("Announcement " + selected.getId() + " unchanged for EAN: " + contractedEAN);
        }

        // Update asset attributes with announcement info
        updateAnnouncementAttributes(selected, effectivityCategory);

        // Record history for new announcements
        if (isNew) {
            recordAnnouncementHistory(selected, effectivityCategory);

            // Store remaining problem profile as predicted data points (MW → kW)
            storeRequestedPowerProfile(selected);

            // Set status to PENDING_CONFIRMATION for new announcements
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), "PENDING_CONFIRMATION");

            // Reset confirmation flag
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_CONFIRM_BID.getName(), false);
        }

        updateLastPoll();
        LOG.fine("Redispatch poll completed for EAN: " + contractedEAN);
    }

    protected List<AnnouncementDto> fetchAnnouncements(String postalCode) {
        try (Response response = announcementResource.fetchAnnouncements(
                postalCode,
                null,
                null,
                "CONGESTIONMANAGEMENT",
                "ANNOUNCEMENT_OPEN"
        )) {
            if (response.getStatus() == 200) {
                String body = response.readEntity(String.class);
                LOG.fine("GOPACS announcements found: " + body);
                return objectMapper.readValue(body, new TypeReference<>() {});
            } else {
                LOG.warning("Failed to fetch announcements: HTTP " + response.getStatus());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error fetching announcements for EAN: " + contractedEAN, e);
            return Collections.emptyList();
        }
    }

    protected String checkEanEffectivity(String announcementId) {
        if (apiKey == null || apiKey.isBlank()) {
            LOG.fine("Skipping EAN effectivity check (no API key) for announcement " + announcementId);
            return null;
        }

        LOG.fine("Checking EAN effectivity for announcement " + announcementId + " and EAN " + contractedEAN);
        try (Response response = eanEffectivityResource.fetchEanSolvingEffectivity(announcementId, apiKey)) {
            if (response.getStatus() == 200) {
                String body = response.readEntity(String.class);
                LOG.fine("Fetched EAN solving effectivity: " + body);
                List<EanSolvingEffectivityDto> effectivities = objectMapper.readValue(body, new TypeReference<>() {});

                for (EanSolvingEffectivityDto effectivity : effectivities) {
                    if (effectivity.getEansByCategory() != null) {
                        LOG.fine("Effectivity categories: " + effectivity.getEansByCategory().keySet());
                        for (Map.Entry<String, Set<String>> entry : effectivity.getEansByCategory().entrySet()) {
                            if (entry.getValue() != null && entry.getValue().contains(contractedEAN)) {
                                LOG.fine("EAN " + contractedEAN + " found in category '" + entry.getKey() + "'");
                                return entry.getKey();
                            }
                        }
                    }
                }
                LOG.fine("EAN " + contractedEAN + " not found in any effectivity category");
            } else {
                LOG.warning("Failed to fetch EAN effectivity for announcement " + announcementId + ": HTTP " + response.getStatus());
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error checking EAN effectivity for announcement " + announcementId, e);
        }
        return null;
    }

    protected void updateAnnouncementAttributes(AnnouncementDto announcement, String effectivityCategory) {
        LOG.fine("Updating announcement attributes for announcement " + announcement.getId() + ", effectivity=" + effectivityCategory);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID.getName(), announcement.getId());
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_COMPLIANCE_TYPE.getName(), announcement.getComplianceType());
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_MESSAGE.getName(), announcement.getMessage());
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_BUY.getName(), announcement.getRequestAreaDescriptionBuyOrders());
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_SELL.getName(), announcement.getRequestAreaDescriptionSellOrders());

        if (announcement.getProblemPeriod() != null) {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_START_TIME.getName(), announcement.getProblemPeriod().getStartTime());
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_END_TIME.getName(), announcement.getProblemPeriod().getEndTime());
        }

        if (announcement.getBidValidityPeriod() != null) {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END.getName(), announcement.getBidValidityPeriod().getEndTime());
        }

        if (effectivityCategory != null) {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_EAN_EFFECTIVITY.getName(), effectivityCategory);
        }
    }

    protected void clearAnnouncementAttributes() {
        // Only clear if there was a previous announcement
        if (lastProcessedAnnouncementId != null) {
            LOG.fine("Clearing announcement attributes (previous: " + lastProcessedAnnouncementId + ") for EAN: " + contractedEAN);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_COMPLIANCE_TYPE.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_MESSAGE.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_START_TIME.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_END_TIME.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_EAN_EFFECTIVITY.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_BUY.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_SELL.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), "NONE");
            lastProcessedAnnouncementId = null;
        }
    }

    protected void storeRequestedPowerProfile(AnnouncementDto announcement) {
        if (announcement.getRemainingProblemProfileInMW() == null || announcement.getProblemPeriod() == null) {
            LOG.fine("No power profile or problem period in announcement " + announcement.getId());
            return;
        }

        Long startTime = announcement.getProblemPeriod().getStartTime();
        if (startTime == null) {
            return;
        }

        List<ValueDatapoint<?>> datapoints = new ArrayList<>();
        long quarterMillis = 15 * 60 * 1000L;

        for (int i = 0; i < announcement.getRemainingProblemProfileInMW().size(); i++) {
            double powerMW = announcement.getRemainingProblemProfileInMW().get(i);
            double powerKW = powerMW * 1000.0; // Convert MW to kW
            long timestamp = startTime + (long) i * quarterMillis;
            datapoints.add(new ValueDatapoint<>(timestamp, powerKW));
        }

        LOG.fine("Storing " + datapoints.size() + " power profile data points for announcement " + announcement.getId()
                + " (profile MW: " + announcement.getRemainingProblemProfileInMW() + ")");
        assetPredictedDatapointService.updateValues(assetId, EmsGOPACSAsset.REDISPATCH_REQUESTED_POWER.getName(), datapoints);
    }

    protected void recordAnnouncementHistory(AnnouncementDto announcement, String effectivityCategory) {
        try {
            ObjectNode historyEntry = objectMapper.createObjectNode();
            historyEntry.put("announcementId", announcement.getId());
            historyEntry.put("type", announcement.getType());
            historyEntry.put("complianceType", announcement.getComplianceType());
            historyEntry.put("organisationName", announcement.getOrganisationName());
            if (announcement.getProblemPeriod() != null) {
                historyEntry.put("startTime", announcement.getProblemPeriod().getStartTime());
                historyEntry.put("endTime", announcement.getProblemPeriod().getEndTime());
            }
            if (announcement.getRemainingProblemProfileInMW() != null) {
                historyEntry.put("requestedPowerMW", announcement.getRemainingProblemProfileInMW().stream()
                        .mapToDouble(Double::doubleValue).max().orElse(0));
            }
            if (effectivityCategory != null) {
                historyEntry.put("eanEffectivity", effectivityCategory);
            }
            historyEntry.put("receivedAt", timerService.getCurrentTimeMillis());

            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_HISTORY.getName(), historyEntry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record announcement history", e);
        }
    }

    public void handleConfirmation() {
        LOG.fine("Processing bid confirmation for EAN: " + contractedEAN);
        EmsGOPACSAsset gopacsAsset = (EmsGOPACSAsset) assetStorageService.find(assetId);
        if (gopacsAsset == null) {
            LOG.warning("Cannot confirm bid: GOPACS asset not found: " + assetId);
            return;
        }

        String announcementId = gopacsAsset.getRedispatchAnnouncementId().orElse(null);
        Double bidPrice = gopacsAsset.getRedispatchBidPrice().orElse(null);

        if (announcementId == null) {
            LOG.warning("Cannot confirm bid: no active announcement for EAN " + contractedEAN);
            resetConfirmFlag();
            return;
        }

        if (bidPrice == null || bidPrice <= 0) {
            LOG.warning("Cannot confirm bid: bid price not set or invalid for EAN " + contractedEAN);
            resetConfirmFlag();
            return;
        }

        LOG.info("Bid confirmed for EAN " + contractedEAN + ": announcement=" + announcementId + ", price=" + bidPrice + " EUR/MWh");

        // Update status
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), "CONFIRMED");

        // Record bid history
        updateBidHistory("CONFIRMED", announcementId, bidPrice);

        // Reset confirm flag
        resetConfirmFlag();

        // Placeholder for trading platform integration
        placeBidOnPlatform(announcementId, bidPrice);
    }

    protected void placeBidOnPlatform(String announcementId, Double bidPrice) {
        // TODO: Integrate with trading platform (ETPA, EPEX SPOT, or NordPool) when decided
        LOG.info("Bid placement placeholder for EAN " + contractedEAN + ": announcement=" + announcementId +
                ", price=" + bidPrice + " EUR/MWh. Trading platform integration pending.");
    }

    protected void updateBidHistory(String status, String announcementId, Double bidPrice) {
        try {
            ObjectNode historyEntry = objectMapper.createObjectNode();
            historyEntry.put("announcementId", announcementId);
            historyEntry.put("bidPrice", bidPrice);
            historyEntry.put("status", status);
            historyEntry.put("timestamp", timerService.getCurrentTimeMillis());

            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_HISTORY.getName(), historyEntry);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to record bid history", e);
        }
    }

    private void resetConfirmFlag() {
        scheduledExecutorService.schedule(() ->
                sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_CONFIRM_BID.getName(), false), 1, TimeUnit.SECONDS);
    }

    private void updateLastPoll() {
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_LAST_POLL.getName(), timerService.getCurrentTimeMillis());
    }

    private void sendAttributeEvent(String attributeName, Object value) {
        assetProcessingService.sendAttributeEvent(new AttributeEvent(assetId, attributeName, value), getClass().getSimpleName());
    }
}
