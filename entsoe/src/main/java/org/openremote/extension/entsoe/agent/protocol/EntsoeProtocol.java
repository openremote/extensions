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
package org.openremote.extension.entsoe.agent.protocol;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.openremote.agent.protocol.AbstractProtocol;
import org.openremote.model.Container;
import org.openremote.model.asset.agent.ConnectionStatus;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.AttributeRef;
import org.openremote.model.datapoint.ValueDatapoint;
import org.openremote.model.syslog.SyslogCategory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.model.syslog.SyslogCategory.PROTOCOL;

public class EntsoeProtocol extends AbstractProtocol<EntsoeAgent, EntsoeAgentLink> {

    private static final Logger LOG = SyslogCategory.getLogger(PROTOCOL, EntsoeProtocol.class);
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter ENTSOE_DATETIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .appendOffsetId()
            .toFormatter();
    public static final String PROTOCOL_DISPLAY_NAME = "ENTSO-E";
    private static final AtomicReference<ResteasyClient> client = new AtomicReference<>();
    private static final JAXBContext PUBLICATION_MARKET_DOCUMENT_CONTEXT = createPublicationMarketDocumentContext();
    private static final ThreadLocal<Unmarshaller> PUBLICATION_UNMARSHALLER = ThreadLocal.withInitial(EntsoeProtocol::createPublicationUnmarshaller);
    private static final ThreadLocal<XMLInputFactory> XML_INPUT_FACTORY = ThreadLocal.withInitial(EntsoeProtocol::createSecureXmlInputFactory);

    // Initial delay to allow system to populate agent links
    private static final int INITIAL_POLLING_DELAY_MILLIS = 3000; // 3 seconds
    private static final int DEFAULT_POLLING_MILLIS = 3600000; // 1 hour

    protected ScheduledFuture<?> pollingFuture;

    public EntsoeProtocol(EntsoeAgent agent) {
        super(agent);
        initClient();
    }

    @Override
    protected void doStart(Container container) throws Exception {
        setConnectionStatus(ConnectionStatus.CONNECTING);

        if (agent.getSecurityToken().isEmpty()) {
            setConnectionStatus(ConnectionStatus.ERROR);
            LOG.warning("Security token is not set");
            return;
        }

        if (!healthCheck()) {
            setConnectionStatus(ConnectionStatus.ERROR);
            LOG.warning("Could not reach ENTSO-E API, either API is unavailable or security token is invalid");
            return;
        }

        restartPollingWithInitialDelay();

        setConnectionStatus(ConnectionStatus.CONNECTED);
    }

    @Override
    protected void doStop(Container container) throws Exception {
        // Cancel the polling task
        if (pollingFuture != null) {
            pollingFuture.cancel(true);
        }
    }

    @Override
    protected void doLinkAttribute(String assetId, Attribute<?> attribute, EntsoeAgentLink agentLink) throws RuntimeException {
        if (!attribute.getType().getType().isAssignableFrom(BigDecimal.class) && !attribute.getType().getType().isAssignableFrom(Double.class)) {
            LOG.warning("Linked attribute " + attribute.getName() + " of asset " + assetId + " not of supported type. Predicted data points will still be generated but inconsistent behaviour could occur.");
        }
        restartPollingWithInitialDelay();
    }

    @Override
    protected void doUnlinkAttribute(String assetId, Attribute<?> attribute, EntsoeAgentLink agentLink) {
        // Do nothing, attributes that have been unlinked will not be processed anymore when we next trigger
    }

    protected synchronized void restartPollingWithInitialDelay() {
        if (scheduledExecutorService == null) {
            return;
        }

        if (pollingFuture != null) {
            pollingFuture.cancel(false);
        }

        int pollingMillis = agent.getPollingMillis().orElse(DEFAULT_POLLING_MILLIS);
        pollingFuture = scheduledExecutorService.scheduleAtFixedRate(
                this::runScheduledUpdate,
                INITIAL_POLLING_DELAY_MILLIS,
                pollingMillis,
                TimeUnit.MILLISECONDS
        );
    }

    protected void runScheduledUpdate() {
        try {
            updateAllLinkedAttributes();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, e, () -> "Scheduled ENTSO-E polling failed; keeping schedule active");
        }
    }

    @Override
    protected void doLinkedAttributeWrite(EntsoeAgentLink agentLink, AttributeEvent event, Object processedValue) {
        // If some external source wants to write the current value of the attribute, we're OK with that
        // and relay the event with AgentService as source so it goes through.
        assetService.sendAttributeEvent(event);
    }

    @Override
    public String getProtocolName() {
        return PROTOCOL_DISPLAY_NAME;
    }

    @Override
    public String getProtocolInstanceUri() {
        return "https://transparency.entsoe.eu/";
    }

    protected void updateAllLinkedAttributes() {
        LOG.fine("Updating all linked attributes with pricing information from ENTSO-E");

        if (getLinkedAttributes().isEmpty()) {
            LOG.fine("No linked attributes found, skipping pricing data update");
            return;
        }

        Map<String, List<Map.Entry<AttributeRef, Attribute>>> attributesByZone = collectLinkedAttributesByZone();

        attributesByZone.forEach((zone, linkedAttributesForZone) -> {
            PublicationMarketDocument document = fetchPricingInformation(buildApiUrl(zone));
            applyPricingInformation(zone, document, linkedAttributesForZone);
        });
    }

    protected Map<String, List<Map.Entry<AttributeRef, Attribute>>> collectLinkedAttributesByZone() {
        Map<String, List<Map.Entry<AttributeRef, Attribute>>> attributesByZone = new LinkedHashMap<>();

        getLinkedAttributes().forEach((attributeRef, attribute) -> {
            EntsoeAgentLink agentLink = agent.getAgentLink(attribute);
            attributesByZone.computeIfAbsent(agentLink.getZone(), ignored -> new ArrayList<>())
                    .add(Map.entry(attributeRef, attribute));
        });

        return attributesByZone;
    }

    protected void applyPricingInformation(String zone, PublicationMarketDocument document, List<Map.Entry<AttributeRef, Attribute>> linkedAttributesForZone) {
        if (document == null) {
            linkedAttributesForZone.forEach(entry ->
                    LOG.warning(() -> "No ENTSO-E publication document returned for attribute: " + entry.getKey() + " in zone: " + zone));
            return;
        }

        List<ValueDatapoint<?>> predictedDatapoints = buildPredictedDatapoints(document);
        if (predictedDatapoints.isEmpty()) {
            linkedAttributesForZone.forEach(entry ->
                    LOG.warning(() -> "No datapoints built from ENTSO-E publication document for attribute: " + entry.getKey() + " in zone: " + zone));
            return;
        }

        linkedAttributesForZone.forEach(entry -> {
            AttributeRef attributeRef = entry.getKey();
            Attribute attribute = entry.getValue();
            LOG.fine("Updating pricing information data for attribute " + attribute.getName());
            predictedDatapointService.updateValues(attributeRef.getId(), attributeRef.getName(), predictedDatapoints);
        });
    }

    protected String buildApiUrl(String zone) {
        String securityToken = agent.getSecurityToken().orElseThrow(() -> new IllegalStateException("Security token is not set"));
        String baseUrl = agent.getBaseURL().orElse("https://web-api.tp.entsoe.eu/api");
        Instant start = timerService.getNow();
        Instant end = start.plus(1, ChronoUnit.DAYS);

        return UriBuilder.fromUri(baseUrl)
                .queryParam("documentType", "A44")
                .queryParam("contract_MarketAgreement.type", "A01")
                .queryParam("periodStart", PERIOD_FORMATTER.format(start))
                .queryParam("periodEnd", PERIOD_FORMATTER.format(end))
                .queryParam("in_Domain", zone)
                .queryParam("out_Domain", zone)
                .queryParam("securityToken", securityToken)
                .build()
                .toString();
    }

    /**
     * Perform a health check by sending a request to the ENTSO-E API
     *
     * @return true if the health check is successful, false otherwise
     */
    protected boolean healthCheck() {
        String apiUrl = buildApiUrl("10YBE----------2");
        try (Response response = client.get().target(apiUrl).request(jakarta.ws.rs.core.MediaType.APPLICATION_XML).get()) {
            if (response.getStatus() != 200) {
                LOG.warning("Health check failed with status: " + response.getStatus());
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "Failed to perform health check");
            return false;
        }
    }

    /**
     * Fetch the pricing data from the ENTSO-E API for the given API URL
     *
     * @param apiUrl the API URL
     * @return the PublicationMarketDocument from the API
     */
    protected PublicationMarketDocument fetchPricingInformation(String apiUrl) {
        try (Response response = client.get().target(apiUrl).request(javax.ws.rs.core.MediaType.APPLICATION_XML).get()) {
            if (response.getStatus() == 200) {
                String responseXml = response.readEntity(String.class);
                EntsoeXmlMeta xmlMeta = parseEntsoeXmlMeta(responseXml);

                if ("Publication_MarketDocument".equals(xmlMeta.rootElement)) {
                    return unmarshalPublicationMarketDocument(responseXml);
                }

                if ("Acknowledgement_MarketDocument".equals(xmlMeta.rootElement)) {
                    String reason = xmlMeta.reasonText != null ? xmlMeta.reasonText : "no reason provided";
                    LOG.info("No ENTSO-E pricing data available: " + reason);
                    return null;
                }

                LOG.warning("Unsupported ENTSO-E response XML root element: " + xmlMeta.rootElement);
                return null;
            } else if (response.getStatus() == 401) {
                LOG.warning("API request was unauthorized, either the security token is invalid or does not provide access to the API");
                return null;
            } else {
                LOG.warning("API request failed with status: " + response.getStatus());
                return null;
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, e, () -> "Failed to fetch pricing data");
            return null;
        }
    }

    protected static XMLInputFactory createSecureXmlInputFactory() {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> null);
            return factory;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise secure XMLInputFactory", e);
        }
    }

    protected static JAXBContext createPublicationMarketDocumentContext() {
        try {
            return JAXBContext.newInstance(PublicationMarketDocument.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise PublicationMarketDocument JAXB context", e);
        }
    }

    protected static Unmarshaller createPublicationUnmarshaller() {
        try {
            return PUBLICATION_MARKET_DOCUMENT_CONTEXT.createUnmarshaller();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialise PublicationMarketDocument unmarshaller", e);
        }
    }

    protected PublicationMarketDocument unmarshalPublicationMarketDocument(String xml) throws Exception {
        XMLStreamReader reader = XML_INPUT_FACTORY.get().createXMLStreamReader(new StringReader(xml));
        try {
            return (PublicationMarketDocument) PUBLICATION_UNMARSHALLER.get().unmarshal(reader);
        } finally {
            reader.close();
        }
    }

    protected EntsoeXmlMeta parseEntsoeXmlMeta(String xml) throws Exception {
        XMLStreamReader reader = XML_INPUT_FACTORY.get().createXMLStreamReader(new StringReader(xml));
        String rootElement = null;
        StringBuilder reasonTextBuilder = new StringBuilder();
        boolean inReason = false;
        boolean inReasonText = false;

        try {
            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String localName = reader.getLocalName();
                    if (rootElement == null) {
                        rootElement = localName;
                    }
                    if ("Reason".equals(localName)) {
                        inReason = true;
                    } else if (inReason && "text".equals(localName)) {
                        inReasonText = true;
                    }
                } else if (event == XMLStreamConstants.CHARACTERS && inReasonText) {
                    String text = reader.getText();
                    if (text != null) {
                        reasonTextBuilder.append(text);
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String localName = reader.getLocalName();
                    if ("text".equals(localName)) {
                        inReasonText = false;
                    } else if ("Reason".equals(localName)) {
                        inReason = false;
                    }
                }
            }
        } finally {
            reader.close();
        }

        String reasonText = reasonTextBuilder.toString().replaceAll("\\s+", " ").trim();
        if (reasonText.isEmpty()) {
            reasonText = null;
        }

        return new EntsoeXmlMeta(rootElement, reasonText);
    }

    protected static class EntsoeXmlMeta {
        protected final String rootElement;
        protected final String reasonText;

        protected EntsoeXmlMeta(String rootElement, String reasonText) {
            this.rootElement = rootElement;
            this.reasonText = reasonText;
        }
    }

    protected List<ValueDatapoint<?>> buildPredictedDatapoints(PublicationMarketDocument document) {
        List<ValueDatapoint<?>> values = new ArrayList<>();
        long nowMillis = timerService.getCurrentTimeMillis();
        if (document.getTimeSeries() == null || document.getTimeSeries().isEmpty()) {
            return values;
        }

        for (PublicationMarketDocument.TimeSeries timeSeries : document.getTimeSeries()) {
            if (timeSeries.getPeriods() == null || timeSeries.getPeriods().isEmpty()) {
                continue;
            }

            for (PublicationMarketDocument.Period period : timeSeries.getPeriods()) {
                if (period == null || period.getPoints() == null || period.getPoints().isEmpty()) {
                    continue;
                }

                PublicationMarketDocument.PeriodTimeInterval timeInterval = period.getTimeInterval() != null
                        ? period.getTimeInterval()
                        : document.getPeriodTimeInterval();
                if (timeInterval == null || timeInterval.getStart() == null || period.getResolution() == null) {
                    continue;
                }

                final Instant start;
                final Duration resolution;
                try {
                    start = parseEntsoeInstant(timeInterval.getStart());
                    resolution = Duration.parse(period.getResolution());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, e, () -> "Could not parse ENTSO-E timeseries time data");
                    continue;
                }

                period.getPoints().stream()
                        .filter(point -> point.getPosition() != null && point.getPosition() > 0 && point.getPriceAmount() != null)
                        .sorted(Comparator.comparingInt(PublicationMarketDocument.Point::getPosition))
                        .forEach(point -> {
                            long timestamp = start
                                    .plus(resolution.multipliedBy(point.getPosition() - 1L))
                                    .toEpochMilli();
                            if (timestamp >= nowMillis) {
                                values.add(new ValueDatapoint<>(timestamp, point.getPriceAmount()));
                            }
                        });
            }
        }

        values.sort(Comparator.comparingLong(ValueDatapoint::getTimestamp));
        return values;
    }

    protected Instant parseEntsoeInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return OffsetDateTime.parse(value, ENTSOE_DATETIME_FORMATTER).toInstant();
        }
    }

    protected static void initClient() {
        synchronized (client) {
            if (client.get() == null) {
                client.set(createClient(org.openremote.container.Container.SCHEDULED_EXECUTOR));
            }
        }
    }

}
