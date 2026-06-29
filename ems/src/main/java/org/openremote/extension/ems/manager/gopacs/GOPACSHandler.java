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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.lfenergy.shapeshifter.api.*;
import org.lfenergy.shapeshifter.api.model.UftpParticipantInformation;
import org.lfenergy.shapeshifter.core.common.exception.UftpConnectorException;
import org.lfenergy.shapeshifter.core.common.xml.XmlSerializer;
import org.lfenergy.shapeshifter.core.common.xsd.XsdFactory;
import org.lfenergy.shapeshifter.core.common.xsd.XsdSchemaFactoryPool;
import org.lfenergy.shapeshifter.core.common.xsd.XsdSchemaProvider;
import org.lfenergy.shapeshifter.core.common.xsd.XsdValidator;
import org.lfenergy.shapeshifter.core.model.*;
import org.lfenergy.shapeshifter.core.service.ParticipantAuthorizationProvider;
import org.lfenergy.shapeshifter.core.service.UftpParticipantService;
import org.lfenergy.shapeshifter.core.service.crypto.LazySodiumBase64Pool;
import org.lfenergy.shapeshifter.core.service.crypto.LazySodiumFactory;
import org.lfenergy.shapeshifter.core.service.crypto.UftpCryptoService;
import org.lfenergy.shapeshifter.core.service.handler.UftpPayloadHandler;
import org.lfenergy.shapeshifter.core.service.participant.ParticipantResolutionService;
import org.lfenergy.shapeshifter.core.service.receiving.UftpReceivedMessageService;
import org.lfenergy.shapeshifter.core.service.sending.UftpSendMessageService;
import org.lfenergy.shapeshifter.core.service.serialization.UftpSerializer;
import org.lfenergy.shapeshifter.core.service.validation.UftpValidationService;
import org.openremote.container.timer.TimerService;
import org.openremote.container.web.CORSConfig;
import org.openremote.container.web.WebApplication;
import org.openremote.container.web.WebService;
import org.openremote.extension.ems.agent.EmsGOPACSAsset;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.model.Container;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.TextUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.openremote.container.web.WebService.getStandardProviders;
import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.model.syslog.SyslogCategory.API;

/**
 * This class handles the communication with the GoPacs server.
 */
public class GOPACSHandler implements UftpPayloadHandler, UftpParticipantService, ParticipantAuthorizationProvider {

    private static final Logger LOG = SyslogCategory.getLogger(API, GOPACSHandler.class);
    public static final String GOPACS_PRIVATE_KEY_FILE = "GOPACS_PRIVATE_KEY_FILE";
    public static final String GOPACS_BROKER_URL = "GOPACS_BROKER_URL";
    public static final String DEFAULT_GOPACS_BROKER_URL = "https://clc-message-broker.gopacs-services.eu";
    public static final String GOPACS_PARTICIPANT_URL = "GOPACS_PARTICIPANT_URL";
    public static final String DEFAULT_GOPACS_PARTICIPANT_URL = "https://api.gopacs-services.eu";
    public static final String GOPACS_OAUTH2_URL = "GOPACS_OAUTH2_URL";
    public static final String DEFAULT_GOPACS_OAUTH2_URL = "https://auth.gopacs-services.eu/realms/gopacs/protocol/openid-connect/token";
    public static final String GOPACS_CLIENT_ID = "GOPACS_CLIENT_ID";
    public static final String GOPACS_CLIENT_SECRET = "GOPACS_CLIENT_SECRET";
    public static final String GOPACS_RESPONSE_DELAY_SECONDS = "GOPACS_RESPONSE_DELAY_SECONDS";
    public static final String DEFAULT_GOPACS_RESPONSE_DELAY_SECONDS = "10";
    public static final String GOPACS_FLEX_OFFER_DELAY_SECONDS = "GOPACS_FLEX_OFFER_DELAY_SECONDS";
    public static final String DEFAULT_GOPACS_FLEX_OFFER_DELAY_SECONDS = "30";
    public static final String DEPLOYMENT_PATH = "/gopacs";
    /** Scheme prefix GOPACS uses on congestion-point identifiers, e.g. "ean.265987182507322951". */
    public static final String EAN_PREFIX = "ean.";


    protected static final UftpSerializer serializer = new UftpSerializer(new XmlSerializer(), new XsdValidator(new XsdSchemaProvider(new XsdFactory(new XsdSchemaFactoryPool()))));

    protected final boolean devMode;
    protected final String contractedEAN;
    protected final String electricitySupplierAssetId;
    protected final String realm;
    protected final String gopacsBrokerUrl;
    protected final Map<String, UftpParticipantInformation> participants;

    protected final AssetProcessingService assetProcessingService;
    protected final AssetPredictedDatapointService assetPredictedDatapointService;
    protected final ScheduledExecutorService scheduledExecutorService;
    protected final TimerService timerService;
    protected final WebService webService;

    protected final ResteasyClient client;
    protected final GOPACSAddressBookResource gopacsAddressBookResource;
    protected final GOPACSAuthResource gopacsAuthResource;
    protected final GOPACSServerResource gopacsServerResource;

    protected final ParticipantResolutionService participantResolutionService;
    protected final UftpValidationService uftpValidationService;
    protected final UftpReceivedMessageService uftpReceivedMessageService;
    protected final UftpSendMessageService uftpSendMessageService;
    protected final UftpCryptoService cryptoService;
    protected final String privateKey;
    protected final String clientId;
    protected final String clientSecret;

    protected final int responseDelaySeconds;
    protected final int flexOfferDelaySeconds;
    protected ObjectMapper objectMapper;

    List<ScheduledFuture<?>> scheduledFutureList = new ArrayList<>();

    public static class Factory {
        protected Container container;

        public Factory(Container container) {
            this.container = container;
        }

        public GOPACSHandler createHandler(String contractedEan, String realm, String electricitySupplierAssetId) {
            return new GOPACSHandler(contractedEan, realm, electricitySupplierAssetId, container);
        }
    }

    protected GOPACSHandler(String contractedEAN, String realm, String electricitySupplierAssetId, Container container) {
        this.devMode = container.isDevMode();
        this.contractedEAN = contractedEAN;
        this.realm = realm;
        this.electricitySupplierAssetId = electricitySupplierAssetId;
        this.participants = new HashMap<>();

        this.assetProcessingService = container.getService(AssetProcessingService.class);
        this.assetPredictedDatapointService = container.getService(AssetPredictedDatapointService.class);
        this.scheduledExecutorService = container.getScheduledExecutor();
        this.timerService = container.getService(TimerService.class);
        this.webService = container.getService(WebService.class);

        // Strip trailing slashes so the synthesised broker endpoint never contains a double slash
        this.gopacsBrokerUrl = container.getConfig().getOrDefault(GOPACS_BROKER_URL, DEFAULT_GOPACS_BROKER_URL).replaceAll("/+$", "");
        this.responseDelaySeconds = Integer.parseInt(container.getConfig().getOrDefault(GOPACS_RESPONSE_DELAY_SECONDS, DEFAULT_GOPACS_RESPONSE_DELAY_SECONDS));
        this.flexOfferDelaySeconds = Integer.parseInt(container.getConfig().getOrDefault(GOPACS_FLEX_OFFER_DELAY_SECONDS, DEFAULT_GOPACS_FLEX_OFFER_DELAY_SECONDS));

        // Initialize OAuth2 configuration first
        this.clientId = container.getConfig().get(GOPACS_CLIENT_ID);
        this.clientSecret = container.getConfig().get(GOPACS_CLIENT_SECRET);

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new RuntimeException(GOPACS_CLIENT_ID + " or " + GOPACS_CLIENT_SECRET + " not defined, cannot use GOPACS.");
        }

        String goPacsPrivateKeyFile = container.getConfig().get(GOPACS_PRIVATE_KEY_FILE);
        if (TextUtil.isNullOrEmpty(goPacsPrivateKeyFile)) {
            throw new RuntimeException(GOPACS_PRIVATE_KEY_FILE + " not defined, can not send use GOPACS.");
        }
        if (!Files.isReadable(Paths.get(goPacsPrivateKeyFile))) {
            throw new RuntimeException(GOPACS_PRIVATE_KEY_FILE + " invalid path or file not readable: " + goPacsPrivateKeyFile);
        }
        // Read the private key from file
        try {
            this.privateKey = Files.readString(Paths.get(goPacsPrivateKeyFile));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to initialize GOPACSHandler for ean " + contractedEAN + ".", ex);
        }

        this.client = createClient(org.openremote.container.Container.EXECUTOR);

        String addressBookUrl = container.getConfig().getOrDefault(GOPACS_PARTICIPANT_URL, DEFAULT_GOPACS_PARTICIPANT_URL);
        String oAuth2Url = container.getConfig().getOrDefault(GOPACS_OAUTH2_URL, DEFAULT_GOPACS_OAUTH2_URL);

        this.gopacsAddressBookResource = client.target(addressBookUrl).proxy(GOPACSAddressBookResource.class);
        this.gopacsAuthResource = client.target(oAuth2Url).proxy(GOPACSAuthResource.class);
        this.gopacsServerResource = new GOPACSServerResourceImpl(this::processRawMessage);

        this.participantResolutionService = new ParticipantResolutionService(this);
        this.cryptoService = new UftpCryptoService(participantResolutionService, new LazySodiumFactory(), new LazySodiumBase64Pool());
        this.uftpValidationService = new UftpValidationService(new ArrayList<>());
        this.uftpReceivedMessageService = new UftpReceivedMessageService(uftpValidationService, this);
        this.uftpSendMessageService = new UftpSendMessageService(serializer, cryptoService, participantResolutionService, this, uftpValidationService);

        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

        deploy(container);
    }

    /**
     * Test-support constructor. Wires the message-processing collaborators directly and skips
     * remote configuration (OAuth client, private-key file) and JAX-RS deployment, so the
     * day-ahead UFTP message flow can be exercised in isolation. Not used in production wiring.
     */
    protected GOPACSHandler(String contractedEAN,
                            String realm,
                            String electricitySupplierAssetId,
                            AssetProcessingService assetProcessingService,
                            AssetPredictedDatapointService assetPredictedDatapointService,
                            TimerService timerService,
                            ScheduledExecutorService scheduledExecutorService,
                            String privateKey) {
        this.devMode = false;
        this.contractedEAN = contractedEAN;
        this.realm = realm;
        this.electricitySupplierAssetId = electricitySupplierAssetId;
        this.participants = new HashMap<>();

        this.assetProcessingService = assetProcessingService;
        this.assetPredictedDatapointService = assetPredictedDatapointService;
        this.timerService = timerService;
        this.scheduledExecutorService = scheduledExecutorService;
        this.webService = null;

        this.gopacsBrokerUrl = "";
        this.responseDelaySeconds = 0;
        this.flexOfferDelaySeconds = 0;
        this.clientId = null;
        this.clientSecret = null;
        this.privateKey = privateKey;

        this.client = null;
        this.gopacsAddressBookResource = null;
        this.gopacsAuthResource = null;
        this.gopacsServerResource = null;

        this.participantResolutionService = new ParticipantResolutionService(this);
        this.cryptoService = new UftpCryptoService(participantResolutionService, new LazySodiumFactory(), new LazySodiumBase64Pool());
        this.uftpValidationService = new UftpValidationService(new ArrayList<>());
        this.uftpReceivedMessageService = new UftpReceivedMessageService(uftpValidationService, this);
        this.uftpSendMessageService = new UftpSendMessageService(serializer, cryptoService, participantResolutionService, this, uftpValidationService);

        this.objectMapper = new ObjectMapper();
    }

    protected static String getDeploymentName(String contractedEAN) {
        return "GOPACS: " + contractedEAN;
    }

    protected void deploy(Container container) {
        LOG.info("Deploying JAX-RS deployment for instance : " + this);

        List<Object> singletons = Stream.of(getStandardProviders(devMode), Collections.<Object>singletonList(gopacsServerResource))
                .flatMap(Collection::stream)
                .toList();
        Application application = new WebApplication(
                container,
                null,
                singletons);

        webService.deployJaxRsApplication(application, DEPLOYMENT_PATH, getDeploymentName(contractedEAN), 0, true, new CORSConfig()
                .setCorsAllowedOrigins(Collections.singleton(CORSConfig.DEFAULT_CORS_ALLOW_ALL))
                .setCorsAllowedMethods(CORSConfig.DEFAULT_CORS_ALLOW_ALL)
                .setCorsAllowedHeaders(CORSConfig.DEFAULT_CORS_ALLOW_ALL));
    }

    public void undeploy() {
        for (ScheduledFuture<?> scheduledFuture : scheduledFutureList) {
            scheduledFuture.cancel(true);
        }
        scheduledFutureList.clear();
        webService.undeploy(getDeploymentName(contractedEAN));
    }

    @Override
    public void notifyNewIncomingMessage(IncomingUftpMessage<? extends PayloadMessageType> message) {
        LOG.info("Received message with conversation ID: " + message.payloadMessage().getConversationID() + " type " + message.payloadMessage().getClass().getSimpleName());
        var messageType = message.payloadMessage().getClass();

        if (FlexRequest.class.isAssignableFrom(messageType)) {
            var flexRequest = (FlexRequest) message.payloadMessage();
            LOG.fine("Processing FlexRequest: " + flexRequest.getConversationID());
            handleFlexRequestMessage(message.sender(), flexRequest);
            LOG.fine("Finished processing FlexRequest: " + flexRequest.getConversationID());
        } else if (FlexOfferResponse.class.isAssignableFrom(messageType)) {
            var flexOfferResponse = (FlexOfferResponse) message.payloadMessage();
            LOG.fine("Processing FlexOfferResponse: " + flexOfferResponse.getConversationID());
            handleFlexOfferResponseMessage(message.sender(), flexOfferResponse);
            LOG.fine("Finished processing FlexOfferResponse: " + flexOfferResponse.getConversationID());
        } else if (FlexOrder.class.isAssignableFrom(messageType)) {
            var flexOrder = (FlexOrder) message.payloadMessage();
            LOG.fine("Processing FlexOrder: " + flexOrder.getConversationID());
            handleFlexOrderMessage(message.sender(), flexOrder);
            LOG.fine("Finished processing FlexOrder: " + flexOrder.getConversationID());
        } else {
            LOG.warning("Received unknown message type: " + messageType.getSimpleName());
        }
        LOG.info("Finished processing message with conversation ID: " + message.payloadMessage().getConversationID() + " type " + message.payloadMessage().getClass().getSimpleName());
    }

    @Override
    public void notifyNewOutgoingMessage(OutgoingUftpMessage<? extends PayloadMessageType> message) {
        LOG.info("Sending message: " + message.payloadMessage().getConversationID() + " type " + message.payloadMessage().getClass().getSimpleName());
        try {
            if (message.payloadMessage() instanceof FlexRequestResponse ||
                    message.payloadMessage() instanceof FlexOffer ||
                    message.payloadMessage() instanceof FlexOrderResponse) {

                if (LOG.isLoggable(Level.FINE)) {
                    LOG.fine("Content to send: " + serializer.toXml(message.payloadMessage()));
                }

                String recipientDomain = message.payloadMessage().getRecipientDomain();
                uftpSendMessageService.attemptToSendMessage(
                        message.payloadMessage(),
                        new SigningDetails(
                                message.sender(),
                                this.privateKey,
                                new UftpParticipant(recipientDomain, UftpRoleInformation.getRecipientRoleBySenderRole(message.sender().role()))
                        )
                );
            }
            LOG.fine("Finished sending message: " + message.payloadMessage().getConversationID());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to send message", e);
        }
    }

    @Override
    public String getAuthorizationHeader(UftpParticipant uftpParticipant) {
        LOG.fine("Getting authorization header for: " + uftpParticipant);
        String authorization = fetchBearerToken();
        if (authorization.isBlank()) {
            LOG.warning("No OAuth2 bearer token available for authorization header of " + uftpParticipant);
        }
        return authorization;
    }

    protected String fetchBearerToken() {
        try {
            try (Response response = gopacsAuthResource.getAccessToken(
                    "client_credentials",
                    this.clientId,
                    this.clientSecret
            )) {
                if (response.getStatus() == 200) {
                    String responseBody = response.readEntity(String.class);
                    OAuth2TokenResponse tokenResponse = objectMapper.readValue(responseBody, OAuth2TokenResponse.class);
                    return "Bearer " + tokenResponse.getAccessToken();
                }
                LOG.warning("OAuth2 token request failed with status: " + response.getStatus());
                return "";
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to obtain OAuth2 access token", e);
            return "";
        }
    }

    protected void handleFlexRequestMessage(UftpParticipant participant, FlexRequest flexRequest) {
        LOG.fine("Received Flex Request: " + flexRequest.getConversationID());
        int year = flexRequest.getPeriod().getYear();
        int month = flexRequest.getPeriod().getMonthValue();
        int day = flexRequest.getPeriod().getDayOfMonth();
        List<ValueDatapoint<?>> powerImportMaxGopacsDatapoints = new ArrayList<>();
        List<ValueDatapoint<?>> powerExportMaxGopacsDatapoints = new ArrayList<>();

        for (int i = 0; i < flexRequest.getISPS().size(); i++) {
            FlexRequestISPType flexRequestISPType = flexRequest.getISPS().get(i);
            LocalTime start = FlexRequestISPTypeHelper.getISPStart(flexRequestISPType.getStart(), year, month, day, flexRequest.getTimeZone());
            double importMax = flexRequestISPType.getMaxPower() == 0L ? 0 : (double) flexRequestISPType.getMaxPower() / 1000.0F;
            double exportMax = flexRequestISPType.getMinPower() == 0L ? 0 : (double) Math.abs(flexRequestISPType.getMinPower()) / 1000.0F;
            LOG.fine("importMax:" + importMax + " exportMax:" + exportMax);
            this.schedulePowerUpdate(start, EmsGOPACSAsset.POWER_MAXIMUM_FLEX_REQUEST.getName(), exportMax);
            this.schedulePowerUpdate(start, EmsGOPACSAsset.POWER_MINIMUM_FLEX_REQUEST.getName(), exportMax);

            // Correct usage of ZoneId.of instead of ZoneOffset.of
            ZoneId zoneId = ZoneId.of(flexRequest.getTimeZone());
            long startEpochMilli = flexRequest.getPeriod().atTime(start).atZone(zoneId).toInstant().toEpochMilli();

            powerImportMaxGopacsDatapoints.add(new ValueDatapoint<>(startEpochMilli, importMax));
            powerExportMaxGopacsDatapoints.add(new ValueDatapoint<>(startEpochMilli, exportMax));
        }

        this.setPredictedDataPoints(EmsGOPACSAsset.POWER_MAXIMUM_FLEX_REQUEST.getName(), powerImportMaxGopacsDatapoints);
        this.setPredictedDataPoints(EmsGOPACSAsset.POWER_MINIMUM_FLEX_REQUEST.getName(), powerExportMaxGopacsDatapoints);
        LOG.fine("Finished processing Flex Request: " + flexRequest);
    }

    protected void handleFlexOfferResponseMessage(UftpParticipant participant, FlexOfferResponse flexOfferResponse) {
        // Handle the response to our FlexOffer
        if (flexOfferResponse.getResult() == AcceptedRejectedType.ACCEPTED) {
            LOG.info("FlexOffer accepted: " + flexOfferResponse.getConversationID());
        } else {
            LOG.warning("FlexOffer rejected: " + flexOfferResponse.getConversationID());
        }
    }

    protected void handleFlexOrderMessage(UftpParticipant participant, FlexOrder flexOrder) {
        LOG.fine("Received FlexOrder for FlexOffer: " + flexOrder.getConversationID());

        int year = flexOrder.getPeriod().getYear();
        int month = flexOrder.getPeriod().getMonthValue();
        int day = flexOrder.getPeriod().getDayOfMonth();
        List<ValueDatapoint<?>> powerDatapoints = new ArrayList<>();
        List<ValueDatapoint<?>> powerDatapointsFeedin = new ArrayList<>();
        List<ValueDatapoint<?>> powerDatapointsOfftake = new ArrayList<>();

        for (int i = 0; i < flexOrder.getISPS().size(); i++) {
            FlexOrderISPType flexOrderISPType = flexOrder.getISPS().get(i);
            LocalTime start = FlexRequestISPTypeHelper.getISPStart(flexOrderISPType.getStart(), year, month, day, flexOrder.getTimeZone());
            double power = flexOrderISPType.getPower() / 1000.0F;
            LOG.fine("power:" + power);
            this.schedulePowerUpdate(start, EmsGOPACSAsset.CURRENT_POWER_FLEX_REQUEST.getName(), power);

            // Correct usage of ZoneId.of instead of ZoneOffset.of
            ZoneId zoneId = ZoneId.of(flexOrder.getTimeZone());
            long startEpochMilli = flexOrder.getPeriod().atTime(start).atZone(zoneId).toInstant().toEpochMilli();

            powerDatapoints.add(new ValueDatapoint<>(startEpochMilli, power));

            // NOTE: this logic assumes offtake MaxPower is always higher than 0 Watt. If this is not the case this logic needs to be changed
            if (power > 0.0) {
                powerDatapointsOfftake.add(new ValueDatapoint<>(startEpochMilli, power));
            } else {
                powerDatapointsFeedin.add(new ValueDatapoint<>(startEpochMilli, power));
            }
        }

        if (!powerDatapointsOfftake.isEmpty()) {
            this.setPredictedDataPoints(EmsGOPACSAsset.POWER_LIMIT_MAXIMUM_PROFILE_FLEX_ORDER.getName(), powerDatapointsOfftake);
        } else if (!powerDatapointsFeedin.isEmpty()) {
            this.setPredictedDataPoints(EmsGOPACSAsset.POWER_LIMIT_MINIMUM_PROFILE_FLEX_ORDER.getName(), powerDatapointsFeedin);
        }

        this.setPredictedDataPoints(EmsGOPACSAsset.CURRENT_POWER_FLEX_REQUEST.getName(), powerDatapoints);
    }

    protected void sendFlexOffer(UftpParticipant recipient, FlexRequest flexRequest) {
        try {
            FlexOffer flexOffer = new FlexOffer();
            flexOffer.setVersion("3.0.0");
            flexOffer.setSenderDomain(flexRequest.getRecipientDomain());
            flexOffer.setRecipientDomain(flexRequest.getSenderDomain());
            flexOffer.setTimeStamp(OffsetDateTime.now(ZoneId.systemDefault()));
            flexOffer.setMessageID(UUID.randomUUID().toString());
            flexOffer.setConversationID(flexRequest.getConversationID());
            flexOffer.setISPDuration(flexRequest.getISPDuration());
            flexOffer.setTimeZone(flexRequest.getTimeZone());
            flexOffer.setPeriod(flexRequest.getPeriod());
            flexOffer.setCongestionPoint(flexRequest.getCongestionPoint());
            flexOffer.setExpirationDateTime(flexRequest.getExpirationDateTime());
            flexOffer.setFlexRequestMessageID(flexRequest.getMessageID());
            flexOffer.setContractID(flexRequest.getContractID());
            flexOffer.setBaselineReference("");
            flexOffer.setCurrency("EUR");

            // Set ISPs based on the FlexRequest ISPs
            List<FlexOfferOptionISPType> currentOptionIsps = new ArrayList<>();
            long previousPower = 0L, currentPower = 0L;
            boolean firstIsp = true;

            for (FlexRequestISPType requestIsp : flexRequest.getISPS()) {
                FlexOfferOptionISPType offerIsp = new FlexOfferOptionISPType();
                offerIsp.setStart(requestIsp.getStart());
                offerIsp.setDuration(requestIsp.getDuration());
                // Set power values based on your available flexibility
                currentPower = calculatePower(requestIsp);
                offerIsp.setPower(currentPower);

                // Add the ISP to the current option
                currentOptionIsps.add(offerIsp);

                // Check if we need to create a new FlexOfferOptionType
                // when power changes from negative to positive or vice versa
                // Skip the check for the first ISP
                if (!firstIsp && ((previousPower < 0 && currentPower >= 0) || (previousPower >= 0 && currentPower < 0))) {
                    // Create a new FlexOfferOptionType for the previous set of ISPs with the same sign
                    FlexOfferOptionType flexOfferOptionType = new FlexOfferOptionType();
                    flexOfferOptionType.setOptionReference(UUID.randomUUID().toString());
                    flexOfferOptionType.setPrice(new BigDecimal("0.00"));

                    // Remove the current ISP from the list as it belongs to the next option
                    List<FlexOfferOptionISPType> previousOptionIsps = new ArrayList<>(currentOptionIsps);
                    previousOptionIsps.removeLast();

                    flexOfferOptionType.getISPS().addAll(previousOptionIsps);
                    flexOffer.getOfferOptions().add(flexOfferOptionType);

                    // Start a new list with just the current ISP
                    currentOptionIsps = new ArrayList<>();
                    currentOptionIsps.add(offerIsp);
                }

                previousPower = currentPower;
                firstIsp = false;
            }

            // Create the final FlexOfferOptionType with any remaining ISPs
            if (!currentOptionIsps.isEmpty()) {
                FlexOfferOptionType finalOptionType = new FlexOfferOptionType();
                finalOptionType.setOptionReference(UUID.randomUUID().toString());
                finalOptionType.setPrice(new BigDecimal("0.00"));
                finalOptionType.getISPS().addAll(currentOptionIsps);
                flexOffer.getOfferOptions().add(finalOptionType);
            }

            // Send the FlexOffer
            UftpParticipant sender = new UftpParticipant(flexRequest.getRecipientDomain(), USEFRoleType.AGR);
            notifyNewOutgoingMessage(OutgoingUftpMessage.create(sender, flexOffer));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to send FlexOffer", e);
        }
    }

    protected void schedulePowerUpdate(LocalTime start, String attributeName, double power) {
        long currentTimeMillis = timerService.getCurrentTimeMillis();
        long ispStartMillis = start.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long delay = ispStartMillis - currentTimeMillis;
        scheduledExecutorService.schedule(() -> updatePowerValues(attributeName, power), delay, TimeUnit.MILLISECONDS);
    }

    protected void updatePowerValues(String attributeName, double power) {
        assetProcessingService.sendAttributeEvent(new AttributeEvent(electricitySupplierAssetId, attributeName, power), getClass().getSimpleName());
    }

    protected void setPredictedDataPoints(String attributeName, List<ValueDatapoint<?>> valuesAndTimestamps) {
        assetPredictedDatapointService.updateValues(electricitySupplierAssetId, attributeName, valuesAndTimestamps);
    }

    protected long calculatePower(FlexRequestISPType requestIsp) {
        //TODO: Implement power calculation logic here
        return requestIsp.getMaxPower() == 0 ? requestIsp.getMinPower() : requestIsp.getMaxPower();
    }

    /**
     * Only act on flex messages whose congestion point matches this handler's contracted EAN. Returns
     * false (and logs a warning) for out-of-scope messages so they are dropped before any asset mutation
     * or outbound response. See issue #28 for full per-contract/role scoping via the V3 contracts endpoint.
     */
    protected boolean isWithinContractedScope(String messageType, String conversationId, String congestionPoint) {
        if (Objects.equals(toCongestionPoint(contractedEAN), toCongestionPoint(congestionPoint))) {
            return true;
        }
        LOG.warning("Rejecting " + messageType + " " + conversationId + " for out-of-scope congestion point "
                + congestionPoint + " (contracted EAN " + contractedEAN + ")");
        return false;
    }

    /**
     * Converts an EAN / congestion-point identifier to the canonical GOPACS congestion-point format
     * "ean.&lt;code&gt;" (for example "ean.265987182507322951"). GOPACS flex messages always carry the
     * congestion point with the "ean." prefix, whereas the contracted EAN may be configured with or
     * without it; canonicalising both sides keeps the scope check correct either way.
     */
    protected static String toCongestionPoint(String ean) {
        if (ean == null) {
            return null;
        }
        String trimmed = ean.trim();
        if (trimmed.regionMatches(true, 0, EAN_PREFIX, 0, EAN_PREFIX.length())) {
            // Already prefixed (any case) — normalise the prefix to its canonical lower-case form.
            return EAN_PREFIX + trimmed.substring(EAN_PREFIX.length());
        }
        return EAN_PREFIX + trimmed;
    }

    protected void processRawMessage(String transportXml) {
        try {
            SignedMessage signedMessage = serializer.fromSignedXml(transportXml);
            String payloadXml = cryptoService.verifySignedMessage(signedMessage);
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine("Received message:" + payloadXml);
            }
            PayloadMessageType payloadMessage = serializer.fromPayloadXml(payloadXml);

            // Re-assert EAN scoping that the V2 participant lookup enforced implicitly. The V3 lookup
            // resolves any participant domain, so a validly signed flex message from a participant outside
            // this handler's contracted EAN would otherwise be applied to the asset. Full per-contract/role
            // scoping via the V3 contracts endpoint is tracked in issue #28.
            // Out-of-scope messages are intentionally dropped here: the transport call still returns 200
            // (signed envelope accepted) but no FlexRequestResponse/FlexOffer is sent in reply.
            if (payloadMessage instanceof FlexMessageType flexMessage
                    && !isWithinContractedScope(payloadMessage.getClass().getSimpleName(),
                            payloadMessage.getConversationID(), flexMessage.getCongestionPoint())) {
                return;
            }

            var incomingUftpMessage = IncomingUftpMessage.create(new UftpParticipant(signedMessage), payloadMessage, transportXml, payloadXml);
            notifyNewIncomingMessage(incomingUftpMessage);

            // Send response delayed to ensure HTTP response is sent first
            scheduledExecutorService.schedule(() -> {
                uftpReceivedMessageService.process(incomingUftpMessage);
            }, this.responseDelaySeconds, TimeUnit.SECONDS); // 10s delay to ensure HTTP response is sent

            // Check if the message is a FlexRequest and schedule sendFlexOffer with delay
            if (payloadMessage instanceof FlexRequest flexRequest) {
                UftpParticipant participant = new UftpParticipant(signedMessage);

                // Schedule FlexOffer to be sent after a short delay to ensure HTTP response is sent first
                scheduledExecutorService.schedule(() -> {
                    try {
                        sendFlexOffer(participant, flexRequest);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "Error sending delayed FlexOffer", e);
                    }
                }, this.flexOfferDelaySeconds, TimeUnit.SECONDS); // 30s delay to ensure FlexRequestResponse is sent and processed by the other party
            }
        } catch (UftpConnectorException e) {
            LOG.log(Level.SEVERE, "Error processing raw message", e);
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            String errorMessage = rootCause.getMessage();
            if (errorMessage == null) {
                errorMessage = "Invalid message format";
            }
            throw new WebApplicationException(errorMessage, Response.Status.BAD_REQUEST);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error processing raw message", e);
            // Do not expose internal exception details to the client
            String errorMessage = "Internal server error occurred";
            throw new WebApplicationException(errorMessage, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public Optional<UftpParticipantInformation> getParticipantInformation(USEFRoleType role, String domain) {
        if (participants.containsKey(domain)) {
            return Optional.of(participants.get(domain));
        }

        String authorization = fetchBearerToken();
        if (authorization.isBlank()) {
            LOG.warning("Skipping participant lookup for " + domain + ": no OAuth2 bearer token available");
            return Optional.empty();
        }
        try (Response response = gopacsAddressBookResource.fetchParticipantByDomain(authorization, domain)) {
            int status = response != null ? response.getStatus() : -1;
            if (status == 200) {
                ParticipantView view = response.readEntity(ParticipantView.class);
                UftpParticipantInformation info = new UftpParticipantInformation(view.domain(), view.publicKey(), this.gopacsBrokerUrl + "/shapeshifter/api/v3/message", true);
                participants.put(view.domain(), info);
                return Optional.of(info);
            }
            if (status == 404) {
                LOG.fine("Participant not found in GOPACS address book: " + domain);
            } else {
                LOG.severe("Unexpected status " + status + " when requesting participant information for " + domain);
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() instanceof IOException ? e.getCause() : e;
            LOG.log(Level.SEVERE, "Exception when requesting participant information for " + domain, cause);
        }
        return Optional.empty();
    }
}
