/*
 * Copyright 2026, OpenRemote Inc.
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
import jakarta.ws.rs.core.Response;
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

    public static final String ANNOUNCEMENT_TYPE_CONGESTIONMANAGEMENT = "CONGESTIONMANAGEMENT";
    public static final String ANNOUNCEMENT_STATE_OPEN = "ANNOUNCEMENT_OPEN";
    public static final String COMPLIANCE_TYPE_MANDATORY = "MANDATORY";
    public static final String BID_STATUS_NONE = "NONE";
    public static final String BID_STATUS_PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    public static final String BID_STATUS_CONFIRMED = "CONFIRMED";

    protected record EanEffectivityResult(
            String matchedCategory,
            List<EanSolvingEffectivityDto> effectivities
    ) {}

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

    private static final int MAX_RECORDED_ANNOUNCEMENT_IDS = 10_000;

    private ScheduledFuture<?> pollingFuture;
    private String lastProcessedAnnouncementId;
    // Bounded LRU set so a long-running handler does not accumulate every announcement ID it has ever seen.
    private final Set<String> recordedAnnouncementIds = Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_RECORDED_ANNOUNCEMENT_IDS;
                }
            });

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
        // GOPACS recommends to poll 5 or more minutes apart
        String pollIntervalConfig = container.getConfig().getOrDefault(
                GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES, DEFAULT_GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES);
        int parsedPollInterval;
        try {
            parsedPollInterval = Integer.parseInt(pollIntervalConfig);
        } catch (NumberFormatException e) {
            parsedPollInterval = Integer.parseInt(DEFAULT_GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES);
            LOG.warning("Invalid " + GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES + " value '" + pollIntervalConfig
                    + "', falling back to default " + parsedPollInterval + " minutes");
        }
        this.pollIntervalMinutes = Math.max(5, parsedPollInterval);

        String redispatchUrl = container.getConfig().getOrDefault(GOPACS_REDISPATCH_URL, DEFAULT_GOPACS_REDISPATCH_URL);

        this.client = createClient(org.openremote.container.Container.EXECUTOR);
        this.announcementResource = client.target(redispatchUrl).proxy(GOPACSAnnouncementResource.class);
        this.eanEffectivityResource = client.target(redispatchUrl).proxy(GOPACSEanEffectivityResource.class);

        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        LOG.info("Initialized GOPACSRedispatchHandler for EAN: " + contractedEAN + " (poll interval: " + pollIntervalMinutes + " min)");
    }

    public void startPolling() {
        if (apiKey == null || apiKey.isBlank()) {
            LOG.severe("GOPACS_REDISPATCH_API_KEY not configured; redispatch polling will not start for EAN: "
                    + contractedEAN + " (the API key is required to resolve EAN effectivity per announcement)");
            return;
        }
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
            // Interrupt to abort any in-flight HTTP call before closing the client
            pollingFuture.cancel(true);
            pollingFuture = null;
        }
        client.close();
        LOG.info("Stopped redispatch polling for EAN: " + contractedEAN);
    }

    protected void pollAndProcess() {
        LOG.fine("Redispatch poll started for EAN: " + contractedEAN);

        EmsGOPACSAsset gopacsAsset = (EmsGOPACSAsset) assetStorageService.find(assetId);
        if (gopacsAsset == null) {
            LOG.warning("GOPACS asset not found: " + assetId);
            return;
        }

        LOG.fine("Fetching announcements for EAN: " + contractedEAN);

        // Fetch announcements
        List<AnnouncementDto> announcements = fetchAnnouncements();
        if (announcements == null) {
            // Fetch failed (HTTP error / exception). Skip this poll and keep existing
            // announcement state so a transient connectivity issue does not flap the UI.
            LOG.fine("Skipping announcement processing due to fetch failure for EAN: " + contractedEAN);
            return;
        }
        if (announcements.isEmpty()) {
            LOG.fine("No announcements found for EAN: " + contractedEAN);
            updateLastPoll();
            clearAnnouncementAttributes();
            return;
        }

        LOG.fine("Fetched " + announcements.size() + " announcements for EAN: " + contractedEAN);

        // Record every newly-seen announcement so we keep an audit trail of what GOPACS returned.
        for (AnnouncementDto announcement : announcements) {
            if (recordedAnnouncementIds.add(announcement.getId())) {
                recordAnnouncementHistory(announcement, null, null);
            }
        }

        // The API call already filters by type/state, but re-check defensively in case
        // GOPACS ever returns extras so downstream logic can rely on the invariant.
        List<AnnouncementDto> relevant = announcements.stream()
                .filter(a -> ANNOUNCEMENT_TYPE_CONGESTIONMANAGEMENT.equals(a.getType()))
                .filter(a -> ANNOUNCEMENT_STATE_OPEN.equals(a.getAnnouncementState()))
                .toList();

        if (relevant.isEmpty()) {
            LOG.fine("No open CONGESTIONMANAGEMENT announcements for EAN: " + contractedEAN);
            updateLastPoll();
            clearAnnouncementAttributes();
            return;
        }

        LOG.fine("Found " + relevant.size() + " relevant open announcements for EAN: " + contractedEAN);

        // Check EAN effectivity to find relevant ones
        AnnouncementDto selected = null;
        String effectivityCategory = null;
        EanEffectivityResult selectedEffectivity = null;

        // Check effectivity for all, find ones where our EAN is listed
        LOG.fine("Checking EAN effectivity for " + relevant.size() + " announcements");
        for (AnnouncementDto announcement : relevant) {
            EanEffectivityResult result = checkEanEffectivity(announcement.getId());
            if (result != null) {
                LOG.fine("EAN " + contractedEAN + " found in category '" + result.matchedCategory() + "' for announcement " + announcement.getId());
                // Prefer MANDATORY over VOLUNTARY
                if (selected == null || COMPLIANCE_TYPE_MANDATORY.equals(announcement.getComplianceType())) {
                    selected = announcement;
                    effectivityCategory = result.matchedCategory();
                    selectedEffectivity = result;
                }
            }
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
            recordAnnouncementHistory(selected, effectivityCategory,
                    selectedEffectivity != null ? selectedEffectivity.effectivities() : null);

            // Store remaining problem profile as predicted data points (MW → kW)
            storeRequestedPowerProfile(selected);

            // TODO: populate redispatchSuggestedPower / redispatchSuggestedVolume from
            // the linked EnergyOptimisation flex profile + Pierre's bid pricing strategy.
            // Tracked separately; attributes intentionally left empty in this PR.

            // Set status to PENDING_CONFIRMATION for new announcements
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), BID_STATUS_PENDING_CONFIRMATION);

            // Reset confirmation flag
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_CONFIRM_BID.getName(), false);
        }

        updateLastPoll();
        LOG.fine("Redispatch poll completed for EAN: " + contractedEAN);
    }

    /**
     * Returns the fetched announcements, an empty list if the API confirms there are
     * none, or {@code null} if the fetch itself failed (HTTP error / exception). The
     * caller uses the null sentinel to skip processing without wiping current state.
     */
    protected List<AnnouncementDto> fetchAnnouncements() {
        try (Response response = announcementResource.fetchAnnouncements(
                null,
                null,
                null,
                ANNOUNCEMENT_TYPE_CONGESTIONMANAGEMENT,
                ANNOUNCEMENT_STATE_OPEN
        )) {
            if (response.getStatus() == 200) {
                String body = response.readEntity(String.class);
                LOG.fine("GOPACS announcements found: " + body);
                return objectMapper.readValue(body, new TypeReference<>() {
                });
            }
            LOG.warning("Failed to fetch announcements: HTTP " + response.getStatus());
            return null;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error fetching announcements for EAN: " + contractedEAN, e);
            return null;
        }
    }

    protected EanEffectivityResult checkEanEffectivity(String announcementId) {
        LOG.fine("Checking EAN effectivity for announcement " + announcementId + " and EAN " + contractedEAN);
        try (Response response = eanEffectivityResource.fetchEanSolvingEffectivity(announcementId, apiKey)) {
            if (response.getStatus() == 200) {
                String body = response.readEntity(String.class);
                LOG.fine("Fetched EAN solving effectivity: " + body);
                List<EanSolvingEffectivityDto> effectivities = objectMapper.readValue(body, new TypeReference<>() {
                });

                for (EanSolvingEffectivityDto effectivity : effectivities) {
                    if (effectivity.getEansByCategory() != null) {
                        LOG.fine("Effectivity categories: " + effectivity.getEansByCategory().keySet());
                        for (Map.Entry<String, Set<String>> entry : effectivity.getEansByCategory().entrySet()) {
                            if (entry.getValue() != null && entry.getValue().contains(contractedEAN)) {
                                LOG.fine("EAN " + contractedEAN + " found in category '" + entry.getKey() + "'");
                                return new EanEffectivityResult(entry.getKey(), effectivities);
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
        } else {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_START_TIME.getName(), null);
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_END_TIME.getName(), null);
        }

        if (announcement.getBidValidityPeriod() != null) {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END.getName(), announcement.getBidValidityPeriod().getEndTime());
        } else {
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END.getName(), null);
        }

        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_EAN_EFFECTIVITY.getName(), effectivityCategory);
    }

    protected void clearAnnouncementAttributes() {
        // Check asset state to handle stale attributes after service restart (lastProcessedAnnouncementId is in-memory only)
        if (lastProcessedAnnouncementId != null) {
            LOG.fine("Clearing announcement attributes (previous: " + lastProcessedAnnouncementId + ") for EAN: " + contractedEAN);
        } else {
            EmsGOPACSAsset gopacsAsset = (EmsGOPACSAsset) assetStorageService.find(assetId);
            if (gopacsAsset == null || gopacsAsset.getRedispatchAnnouncementId().isEmpty()) {
                return;
            }
            LOG.fine("Clearing stale announcement attributes for EAN: " + contractedEAN);
        }

        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_COMPLIANCE_TYPE.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_MESSAGE.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_START_TIME.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_END_TIME.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_EAN_EFFECTIVITY.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_BUY.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_REQUEST_AREA_SELL.getName(), null);
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), BID_STATUS_NONE);
        lastProcessedAnnouncementId = null;
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

    protected void recordAnnouncementHistory(AnnouncementDto announcement, String effectivityCategory,
                                               List<EanSolvingEffectivityDto> effectivities) {
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
                historyEntry.put("maxRequestedPowerMW", announcement.getRemainingProblemProfileInMW().stream()
                        .mapToDouble(Double::doubleValue).max().orElse(0));
            }
            if (effectivityCategory != null) {
                historyEntry.put("eanEffectivity", effectivityCategory);
            }
            if (effectivities != null && !effectivities.isEmpty()) {
                historyEntry.set("eanEffectivityDetails", objectMapper.valueToTree(effectivities));
            }
            historyEntry.put("receivedAt", timerService.getCurrentTimeMillis());

            Map<String, Object> historyMap = objectMapper.convertValue(historyEntry, new TypeReference<>() {
            });
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_HISTORY.getName(), historyMap);
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
        sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_STATUS.getName(), BID_STATUS_CONFIRMED);

        // Record bid history
        updateBidHistory(BID_STATUS_CONFIRMED, announcementId, bidPrice);

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

            Map<String, Object> historyMap = objectMapper.convertValue(historyEntry, new TypeReference<>() {
            });
            sendAttributeEvent(EmsGOPACSAsset.REDISPATCH_BID_HISTORY.getName(), historyMap);
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
