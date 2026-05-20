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
package org.openremote.extension.hawkbit.manager;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataWriter;
import org.openremote.container.timer.TimerService;
import org.openremote.container.web.WebClient;
import org.openremote.container.web.WebTargetBuilder;
import org.openremote.extension.hawkbit.manager.hawkbit.*;
import org.openremote.extension.hawkbit.manager.resource.*;
import org.openremote.extension.hawkbit.model.FirmwareMetaItemType;
import org.openremote.extension.hawkbit.model.hawkbit.MetadataUpdateRequest;
import org.openremote.extension.hawkbit.model.hawkbit.Target;
import org.openremote.extension.hawkbit.model.hawkbit.TargetCreateRequest;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebService;
import org.openremote.model.Container;
import org.openremote.model.ContainerService;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetEvent;
import org.openremote.model.asset.AssetTypeInfo;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.MetaMap;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.TextUtil;
import org.openremote.model.util.ValueUtil;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.openremote.container.web.WebTargetBuilder.*;
import static org.openremote.model.syslog.SyslogCategory.API;
import static org.openremote.model.util.MapAccess.getString;

/**
 * Connects the manager to the hawkBit Management API and keeps OpenRemote assets
 * marked for firmware management synchronized with hawkBit targets and metadata.
 */
public class HawkbitFirmwareService implements ContainerService {
    /**
     * hawkBit integration is currently limited to a single realm,
     * this is due to hawkBit's tenancy model being difficult to work with because of
     * the authentication/security mechanisms that are in place.
     */
    public static final String HAWKBIT_REALM = "HAWKBIT_REALM";
    public static final String HAWKBIT_REALM_DEFAULT = "master";

    public static final String HAWKBIT_USERNAME = "HAWKBIT_USERNAME";
    public static final String HAWKBIT_USERNAME_DEFAULT = "hawkbit";
    public static final String HAWKBIT_PASSWORD = "HAWKBIT_PASSWORD";
    public static final String HAWKBIT_PASSWORD_DEFAULT = "hawkbit";

    public static final String HAWKBIT_MANAGEMENT_API_URL = "HAWKBIT_MANAGEMENT_API_URL";
    public static final String HAWKBIT_MANAGEMENT_API_URL_DEFAULT = "http://localhost:8083/hawkbit/rest/v1";

    private static final Logger LOG = SyslogCategory.getLogger(API, HawkbitFirmwareService.class);

    protected ResteasyClient client;

    protected String hawkbitRealm;
    protected ClientEventService clientEventService;
    protected AssetProcessingService assetProcessingService;
    protected ExecutorService executorService;

    protected HawkbitTargetsClient targets;
    protected HawkbitDistributionSetsClient distributionSets;
    protected HawkbitDistributionSetTypesClient distributionSetTypes;
    protected HawkbitSoftwareModulesClient softwareModules;
    protected HawkbitSoftwareModuleTypesClient softwareModuleTypes;
    protected HawkbitRolloutsClient rollouts;
    protected HawkbitTargetFiltersClient targetFilters;

    @Override
    public void init(Container container) throws Exception {
        clientEventService = container.getService(ClientEventService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        executorService = container.getExecutor();
        TimerService timerService = container.getService(TimerService.class);
        ManagerIdentityService identityService = container.getService(ManagerIdentityService.class);

        container.getService(ManagerWebService.class).addApiSingleton(
                new TargetResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new DistributionSetResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new DistributionSetTypeResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new SoftwareModuleResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new SoftwareModuleTypeResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new RolloutResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new TargetFilterResourceImpl(timerService, identityService, this));
    }

    @Override
    public void start(Container container) throws Exception {
        String hawkbitURI = getString(container.getConfig(), HAWKBIT_MANAGEMENT_API_URL,
                HAWKBIT_MANAGEMENT_API_URL_DEFAULT);

        if (TextUtil.isNullOrEmpty(hawkbitURI)) {
            hawkbitURI = HAWKBIT_MANAGEMENT_API_URL_DEFAULT;
        }

        if (HAWKBIT_MANAGEMENT_API_URL_DEFAULT.equals(hawkbitURI)) {
            LOG.fine(HAWKBIT_MANAGEMENT_API_URL + " not configured, using default="
                    + HAWKBIT_MANAGEMENT_API_URL_DEFAULT);
        }

        URI uri;

        try {
            uri = new URI(hawkbitURI);
        } catch (URISyntaxException e) {
            LOG.log(Level.SEVERE, "Invalid " + HAWKBIT_MANAGEMENT_API_URL + " value", e);
            throw e;
        }

        String hawkbitUsername = getString(container.getConfig(), HAWKBIT_USERNAME, HAWKBIT_USERNAME_DEFAULT);
        String hawkbitPassword = getString(container.getConfig(), HAWKBIT_PASSWORD, HAWKBIT_PASSWORD_DEFAULT);

        hawkbitRealm = getString(container.getConfig(), HAWKBIT_REALM, HAWKBIT_REALM_DEFAULT);

        client = createClient(org.openremote.container.Container.EXECUTOR, CONNECTION_POOL_SIZE,
                CONNECTION_TIMEOUT_MILLISECONDS, resteasyClientBuilder -> {
                    WebClient.registerDefaults(resteasyClientBuilder);
                    ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
                    provider.setMapper(ValueUtil.JSON);
                    resteasyClientBuilder.register(provider);
                    resteasyClientBuilder.register(MultipartFormDataWriter.class);
                    return resteasyClientBuilder;
                });

        ResteasyWebTarget webTarget = new WebTargetBuilder(client, uri).build();
        webTarget.register((ClientRequestFilter) requestContext -> requestContext.getHeaders().putSingle(
                HttpHeaders.AUTHORIZATION,
                HawkbitBasicAuth.buildAuthorizationHeader(hawkbitUsername, hawkbitPassword)));

        targets = webTarget.proxy(HawkbitTargetsClient.class);
        distributionSets = webTarget.proxy(HawkbitDistributionSetsClient.class);
        distributionSetTypes = webTarget.proxy(HawkbitDistributionSetTypesClient.class);
        softwareModules = webTarget.proxy(HawkbitSoftwareModulesClient.class);
        softwareModuleTypes = webTarget.proxy(HawkbitSoftwareModuleTypesClient.class);
        rollouts = webTarget.proxy(HawkbitRolloutsClient.class);
        targetFilters = webTarget.proxy(HawkbitTargetFiltersClient.class);

        clientEventService.addSubscription(
                AssetEvent.class,
                null,
                this::onAssetChange);

        clientEventService.addSubscription(
                AttributeEvent.class,
                null,
                this::onAttributeChange);

        LOG.info("Started hawkBit firmware service uri=" + uri + ", realm=" + hawkbitRealm);
    }

    @Override
    public void stop(Container container) throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    protected void onAssetChange(AssetEvent assetEvent) {
        if (!Objects.equals(assetEvent.getRealm(), hawkbitRealm)) {
            return;
        }

        executorService.submit(() -> handleAssetChange(assetEvent));
    }

    protected void onAttributeChange(AttributeEvent attributeEvent) {
        if (!Objects.equals(attributeEvent.getRealm(), hawkbitRealm)) {
            return;
        }

        executorService.submit(() -> handleAttributeChange(attributeEvent));
    }

    protected void handleAttributeChange(AttributeEvent attributeEvent) {
        if (!hasMetadataFlag(attributeEvent.getAssetType(), attributeEvent.getName(), attributeEvent.getMeta())) {
            return;
        }

        if (attributeEvent.isDeleted()) {
            deleteTargetMetadata(attributeEvent.getId(), attributeEvent.getName());
            return;
        }

        syncTargetMetadataValue(
                attributeEvent.getId(),
                attributeEvent.getName(),
                attributeEvent.getValue().orElse(null));
    }


    protected void handleAssetChange(AssetEvent assetEvent) {
        Asset<?> asset = assetEvent.getAsset();
        Optional<String> targetInfoAttributeName = getTargetInfoAttributeName(asset);

        if (targetInfoAttributeName.isEmpty()) {
            return;
        }

        String attributeName = targetInfoAttributeName.get();
        String controllerId = asset.getId();
        Target target = null;

        LOG.fine("Processing hawkBit target sync cause=" + assetEvent.getCause()
                + ", assetId=" + asset.getId());

        switch (assetEvent.getCause()) {
            case CREATE:
            case UPDATE:
                Target existingTarget;
                try {
                    existingTarget = getTarget(controllerId);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "hawkBit target lookup failed id=" + controllerId + ", skipping sync", e);
                    break;
                }

                if (existingTarget != null) {
                    LOG.fine("hawkBit target exists id=" + controllerId);
                    target = existingTarget;
                    break;
                }

                LOG.fine("hawkBit target missing id=" + controllerId + ", creating");
                target = createTarget(asset);
                break;
            case DELETE:
                deleteTarget(controllerId);
                break;
        }

        if (target != null) {
            updateTargetInfoForAttribute(asset, attributeName, target);
            syncTargetMetadata(asset);
        }
    }

    /**
     * Synchronizes all asset attributes marked as firmware metadata to the hawkBit target.
     */
    public void syncTargetMetadata(Asset<?> asset) {
        String controllerId = asset.getId();
        for (Attribute<?> attribute : asset.getAttributes().values()) {
            if (!hasMetadataFlag(asset.getType(), attribute.getName(), attribute.getMeta())) {
                continue;
            }

            syncTargetMetadataValue(controllerId, attribute.getName(), attribute.getValue().orElse(null));
        }
    }

    /**
     * Synchronizes a single metadata value to the hawkBit target.
     * Deletes the metadata entry when the value is empty or {@code null}.
     */
    public void syncTargetMetadataValue(String controllerId, String key, Object value) {
        if (isEmptyAttributeValue(value)) {
            deleteTargetMetadata(controllerId, key);
            return;
        }

        Optional<String> metadataValue = ValueUtil.getStringCoerced(value);
        if (metadataValue.isEmpty()) {
            LOG.warning("Cannot sync hawkBit metadata id=" + controllerId
                    + ", key=" + key + ": value is not string-compatible");
            return;
        }

        updateTargetMetadata(controllerId, key, metadataValue.get());
    }

    /**
     * Updates a single hawkBit target metadata entry.
     */
    public void updateTargetMetadata(String controllerId, String key, String value) {
        try (Response response = targets.updateMetadata(controllerId, key, new MetadataUpdateRequest(value))) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                LOG.fine("hawkBit target not found for metadata sync id="
                        + controllerId + ", key=" + key);
                return;
            }
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warning("Failed to update hawkBit metadata id="
                        + controllerId + ", key=" + key + ", status=" + response.getStatus());
                return;
            }
            LOG.fine("Updated hawkBit metadata id=" + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to update hawkBit metadata id="
                    + controllerId + ", key=" + key, e);
        }
    }

    /**
     * Deletes a single hawkBit target metadata entry.
     */
    public void deleteTargetMetadata(String controllerId, String key) {
        try (Response response = targets.deleteMetadata(controllerId, key)) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                LOG.fine("hawkBit metadata not found for delete id="
                        + controllerId + ", key=" + key);
                return;
            }
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warning("Failed to delete hawkBit metadata id="
                        + controllerId + ", key=" + key + ", status=" + response.getStatus());
                return;
            }
            LOG.fine("Deleted hawkBit metadata id=" + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkBit metadata id="
                    + controllerId + ", key=" + key, e);
        }
    }

    protected TargetCreateRequest buildTargetCreateRequest(Asset<?> asset, String securityToken) {
        String controllerId = asset.getId();
        String targetName = asset.getAssetType() + "-" + controllerId;
        String targetDescription = "assetId=" + asset.getId() + "; realm=" + asset.getRealm();
        return new TargetCreateRequest(controllerId, targetName, targetDescription, securityToken);
    }

    /**
     * Creates a hawkBit target for an asset.
     * The security token is omitted so hawkBit can generate one.
     */
    public Target createTarget(Asset<?> asset) {
        return createTarget(asset, null);
    }

    /**
     * Creates a hawkBit target for an asset using an optional security token.
     * If {@code securityToken} is {@code null}, hawkBit can generate one.
     */
    public Target createTarget(Asset<?> asset, String securityToken) {
        return createTarget(buildTargetCreateRequest(asset, securityToken));
    }

    /**
     * Creates a hawkBit target from a create request.
     * Returns {@code null} if creation fails or hawkBit returns no target.
     */
    public Target createTarget(TargetCreateRequest target) {
        LOG.fine("Creating hawkBit target id=" + target.controllerId());
        try (Response response = targets.create(new TargetCreateRequest[]{target})) {
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warning("Failed to create hawkBit target id=" + target.controllerId()
                        + ", status=" + response.getStatus());
                return null;
            }
            Target[] created = response.readEntity(Target[].class);
            if (created == null || created.length == 0) {
                LOG.warning("hawkBit create returned no targets id=" + target.controllerId());
                return null;
            }
            LOG.info("Created hawkBit target id=" + created[0].controllerId());
            return created[0];
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create hawkBit target id=" + target.controllerId(), e);
            return null;
        }
    }

    /**
     * Deletes a hawkBit target by controllerId.
     */
    public void deleteTarget(String controllerId) {
        try (Response response = targets.delete(controllerId)) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                LOG.fine("hawkBit target not found for delete id=" + controllerId);
                return;
            }
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                LOG.warning("Failed to delete hawkBit target id=" + controllerId
                        + ", status=" + response.getStatus());
                return;
            }
            LOG.info("Deleted hawkBit target id=" + controllerId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkBit target id=" + controllerId, e);
        }
    }

    protected void updateTargetInfoForAttribute(Asset<?> asset, String attributeName, Target target) {
        try {
            Map<String, String> targetInfo = new LinkedHashMap<>();
            targetInfo.put("controllerId", target.controllerId());
            targetInfo.put("securityToken", target.securityToken());
            String newValueJson = ValueUtil.asJSON(targetInfo).orElse(null);

            assetProcessingService.sendAttributeEvent(
                    new AttributeEvent(asset.getId(), attributeName, newValueJson),
                    getClass().getSimpleName());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to update firmware target info assetId=" + asset.getId(), e);
        }
    }

    /**
     * Queries hawkBit for a target by controllerId.
     * Returns {@code null} if the target is not found (404).
     */
    public Target getTarget(String controllerId) {
        try (Response response = targets.get(controllerId)) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return null;
            }
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new WebApplicationException("hawkBit target request failed with status " + response.getStatus(),
                        response.getStatus());
            }
            return response.readEntity(Target.class);
        }
    }

    protected Optional<String> getTargetInfoAttributeName(Asset<?> asset) {
        List<String> matchingAttributeNames = asset.getAttributes().values().stream()
                .filter(attribute -> hasTargetInfoFlag(attribute.getMeta()))
                .map(Attribute::getName)
                .distinct()
                .toList();

        if (matchingAttributeNames.isEmpty()) {
            return Optional.empty();
        }

        if (matchingAttributeNames.size() > 1) {
            LOG.warning("Multiple firmware target attributes assetType=" + asset.getType()
                    + ", meta=" + FirmwareMetaItemType.FIRMWARE_TARGET.getName());
            return Optional.empty();
        }

        return Optional.of(matchingAttributeNames.getFirst());
    }

    protected boolean hasMetadataFlag(String assetType, String attributeName, MetaMap meta) {
        if (hasMetadataMetaFlag(meta)) {
            return true;
        }

        if (assetType == null) {
            return false;
        }

        Optional<AssetTypeInfo> assetTypeInfo = ValueUtil.getAssetInfo(assetType);
        return assetTypeInfo
                .map(typeInfo -> typeInfo.getAttributeDescriptors().values().stream()
                        .filter(attributeDescriptor -> Objects.equals(attributeDescriptor.getName(), attributeName))
                        .anyMatch(attributeDescriptor -> hasMetadataMetaFlag(attributeDescriptor.getMeta())))
                .orElse(false);
    }

    protected boolean hasMetadataMetaFlag(MetaMap meta) {
        return meta != null
                && meta.get(FirmwareMetaItemType.FIRMWARE_METADATA)
                .flatMap(metaItem -> metaItem.getValue(Boolean.class))
                .orElse(false);
    }

    protected boolean hasTargetInfoFlag(MetaMap meta) {
        return meta != null
                && meta.get(FirmwareMetaItemType.FIRMWARE_TARGET)
                .flatMap(metaItem -> metaItem.getValue(Boolean.class))
                .orElse(false);
    }

    protected boolean isEmptyAttributeValue(Object value) {
        return value == null || ValueUtil.getStringCoerced(value)
                .map(String::isEmpty)
                .orElse(false);
    }

    /**
     * Returns the realm this service is bound to. Firmware endpoints are scoped to it.
     */
    public String getRealm() {
        return hawkbitRealm;
    }

    /**
     * Returns the hawkBit targets client.
     */
    public HawkbitTargetsClient targets() {
        return targets;
    }

    /**
     * Returns the hawkBit distribution sets client.
     */
    public HawkbitDistributionSetsClient distributionSets() {
        return distributionSets;
    }

    /**
     * Returns the hawkBit distribution set types client.
     */
    public HawkbitDistributionSetTypesClient distributionSetTypes() {
        return distributionSetTypes;
    }

    /**
     * Returns the hawkBit software modules client.
     */
    public HawkbitSoftwareModulesClient softwareModules() {
        return softwareModules;
    }

    /**
     * Returns the hawkBit software module types client.
     */
    public HawkbitSoftwareModuleTypesClient softwareModuleTypes() {
        return softwareModuleTypes;
    }

    /**
     * Returns the hawkBit rollouts client.
     */
    public HawkbitRolloutsClient rollouts() {
        return rollouts;
    }

    /**
     * Returns the hawkBit target filters client.
     */
    public HawkbitTargetFiltersClient targetFilters() {
        return targetFilters;
    }

}
