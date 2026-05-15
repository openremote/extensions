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

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
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
import org.openremote.model.value.AttributeDescriptor;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.openremote.container.web.WebTargetBuilder.*;
import static org.openremote.model.syslog.SyslogCategory.API;
import static org.openremote.model.util.MapAccess.getString;

public class HawkbitFirmwareService implements ContainerService {


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
    protected ManagerIdentityService identityService;
    protected AssetProcessingService assetProcessingService;
    protected ExecutorService executorService;
    protected TimerService timerService;

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
        timerService = container.getService(TimerService.class);
        identityService = container.getService(ManagerIdentityService.class);

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

    /**
     * Builds the hawkBit REST client and subscribes to asset/attribute events.
     */
    @Override
    public void start(Container container) throws Exception {
        String hawkbitURI = getString(container.getConfig(), HAWKBIT_MANAGEMENT_API_URL,
                HAWKBIT_MANAGEMENT_API_URL_DEFAULT);

        if (TextUtil.isNullOrEmpty(hawkbitURI)) {
            hawkbitURI = HAWKBIT_MANAGEMENT_API_URL_DEFAULT;
        }

        if (HAWKBIT_MANAGEMENT_API_URL_DEFAULT.equals(hawkbitURI)) {
            LOG.info(HAWKBIT_MANAGEMENT_API_URL + " not configured, using default="
                    + HAWKBIT_MANAGEMENT_API_URL_DEFAULT);
        }

        URI uri;

        try {
            uri = new URI(hawkbitURI);
        } catch (URISyntaxException e) {
            LOG.log(Level.SEVERE, HAWKBIT_MANAGEMENT_API_URL + " value is not a valid URI", e);
            throw e;
        }

        String hawkbitUsername = getString(container.getConfig(), HAWKBIT_USERNAME, HAWKBIT_USERNAME_DEFAULT);
        String hawkbitPassword = getString(container.getConfig(), HAWKBIT_PASSWORD, HAWKBIT_PASSWORD_DEFAULT);

        hawkbitRealm = getString(container.getConfig(), HAWKBIT_REALM, HAWKBIT_REALM_DEFAULT);

        LOG.info(HAWKBIT_MANAGEMENT_API_URL + "=" + uri);

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

        LOG.log(Level.INFO,
                "Firmware Service started, connected hawkBit instance: " + uri + ", for realm: " + hawkbitRealm);
    }

    @Override
    public void stop(Container container) throws Exception {
        // Client lifecycle is managed by the container.
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
        if (!hasFirmwareMetadata(attributeEvent.getAssetType(), attributeEvent.getName(), attributeEvent.getMeta())) {
            return;
        }

        if (attributeEvent.isDeleted()) {
            deleteFirmwareTargetMetadata(attributeEvent.getId(), attributeEvent.getName());
            return;
        }

        syncFirmwareTargetMetadataValue(
                attributeEvent.getId(),
                attributeEvent.getName(),
                attributeEvent.getValue().orElse(null));
    }

    /**
     * Synchronises an OpenRemote asset with a hawkBit target.
     * CREATE ensures the target exists; UPDATE recreates it if missing; DELETE removes it.
     */
    protected void handleAssetChange(AssetEvent assetEvent) {
        Asset<?> asset = assetEvent.getAsset();

        if (getFirmwareTargetInfoDescriptor(asset).isEmpty()) {
            return;
        }

        String controllerId = asset.getId();
        boolean shouldUpdateFirmwareTargetInfo = false;

        LOG.fine("Processing hawkbit target sync cause=" + assetEvent.getCause()
                + ", assetId=" + asset.getId() + ", assetType=" + asset.getType()
                + ", controllerId=" + controllerId);

        switch (assetEvent.getCause()) {
            case CREATE:
                LOG.info("Creating hawkbit target for asset id=" + asset.getId());
                shouldUpdateFirmwareTargetInfo = createFirmwareTarget(buildFirmwareTarget(asset));
                break;
            case UPDATE:
                LOG.fine("Checking hawkbit target for update asset id=" + asset.getId());
                Target existingTarget;
                try {
                    existingTarget = getFirmwareTarget(controllerId);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to query hawkbit target id=" + controllerId
                            + ", skipping update to avoid spurious create", e);
                    break;
                }

                if (existingTarget != null) {
                    LOG.fine("hawkbit target already exists id=" + controllerId);
                    shouldUpdateFirmwareTargetInfo = true;
                    break;
                }

                LOG.info("hawkbit target missing for asset id=" + asset.getId() + ", creating it now");
                if (createFirmwareTarget(buildFirmwareTarget(asset))) {
                    shouldUpdateFirmwareTargetInfo = true;
                }
                break;
            case DELETE:
                deleteFirmwareTarget(controllerId);
                break;
            default:
                break;
        }

        if (shouldUpdateFirmwareTargetInfo) {
            updateFirmwareTargetInfo(assetEvent);
            updateFirmwareTargetMetadata(asset);
        }
    }

    protected void updateFirmwareTargetMetadata(Asset<?> asset) {
        String controllerId = asset.getId();
        for (Attribute<?> attribute : asset.getAttributes().values()) {
            if (!hasFirmwareMetadata(asset.getType(), attribute.getName(), attribute.getMeta())) {
                continue;
            }

            syncFirmwareTargetMetadataValue(controllerId, attribute.getName(), attribute.getValue().orElse(null));
        }
    }

    /**
     * Checks whether an attribute (by instance meta or type descriptor meta) is marked
     * as firmware metadata.
     */
    protected boolean hasFirmwareMetadata(String assetType, String attributeName, MetaMap meta) {
        if (hasFirmwareMetadata(meta)) {
            return true;
        }

        if (assetType == null) {
            return false;
        }

        Optional<AssetTypeInfo> assetTypeInfo = ValueUtil.getAssetInfo(assetType);
        return assetTypeInfo
                .map(typeInfo -> typeInfo.getAttributeDescriptors().values().stream()
                        .filter(attributeDescriptor -> Objects.equals(attributeDescriptor.getName(), attributeName))
                        .anyMatch(attributeDescriptor -> hasFirmwareMetadata(attributeDescriptor.getMeta())))
                .orElse(false);
    }

    protected boolean hasFirmwareMetadata(MetaMap meta) {
        return meta != null
                && meta.get(FirmwareMetaItemType.FIRMWARE_METADATA)
                .flatMap(metaItem -> metaItem.getValue(Boolean.class))
                .orElse(false);
    }

    protected void syncFirmwareTargetMetadataValue(String controllerId, String key, Object value) {
        if (isAttributeEmptyOrNull(value)) {
            deleteFirmwareTargetMetadata(controllerId, key);
            return;
        }

        Optional<String> metadataValue = ValueUtil.getStringCoerced(value);
        if (metadataValue.isEmpty()) {
            LOG.warning("Cannot convert firmware metadata value to string for target id="
                    + controllerId + ", key=" + key);
            return;
        }

        updateFirmwareTargetMetadata(controllerId, key, metadataValue.get());
    }

    protected boolean isAttributeEmptyOrNull(Object value) {
        return value == null || ValueUtil.getStringCoerced(value)
                .map(String::isEmpty)
                .orElse(false);
    }

    protected void updateFirmwareTargetMetadata(String controllerId, String key, String value) {
        try (Response response = targets.updateMetadata(controllerId, key, new MetadataUpdateRequest(value))) {
            LOG.fine("Updated hawkbit target metadata targetId=" + controllerId + ", key=" + key);
        } catch (NotFoundException e) {
            LOG.fine("hawkbit target not found for metadata sync targetId="
                    + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to update hawkbit target metadata targetId="
                    + controllerId + ", key=" + key, e);
        }
    }

    protected void deleteFirmwareTargetMetadata(String controllerId, String key) {
        try (Response response = targets.deleteMetadata(controllerId, key)) {
            LOG.fine("Deleted hawkbit target metadata targetId=" + controllerId + ", key=" + key);
        } catch (NotFoundException e) {
            LOG.fine("hawkbit target metadata not found for delete targetId="
                    + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkbit target metadata targetId="
                    + controllerId + ", key=" + key, e);
        }
    }

    /**
     * Finds the single attribute descriptor marked as {@code firmwareTarget} for the given asset type.
     * Returns empty if none or more than one is found.
     */
    protected Optional<AttributeDescriptor<?>> getFirmwareTargetInfoDescriptor(Asset<?> asset) {
        // TODO: Decide whether firmwareTarget should also be honored when defined on
        // an asset attribute instance instead of only on the asset type descriptor.
        Optional<AssetTypeInfo> assetTypeInfo = ValueUtil.getAssetInfo(asset.getType());
        if (assetTypeInfo.isEmpty()) {
            LOG.fine("Cannot resolve asset type info for asset type '" + asset.getType() + "'");
            return Optional.empty();
        }

        List<AttributeDescriptor<?>> matchingDescriptors = assetTypeInfo.get().getAttributeDescriptors().values()
                .stream()
                .filter(attributeDescriptor -> attributeDescriptor.getMeta() != null
                        && attributeDescriptor.getMeta()
                        .get(FirmwareMetaItemType.FIRMWARE_TARGET)
                        .flatMap(metaItem -> metaItem.getValue(Boolean.class))
                        .orElse(false))
                .toList();

        if (matchingDescriptors.isEmpty()) {
            return Optional.empty();
        }

        if (matchingDescriptors.size() > 1) {
            LOG.warning("Asset type '" + asset.getType()
                    + "' has multiple attribute descriptors with meta item '"
                    + FirmwareMetaItemType.FIRMWARE_TARGET.getName() + "'");
            return Optional.empty();
        }

        return Optional.of(matchingDescriptors.getFirst());
    }

    protected TargetCreateRequest buildFirmwareTarget(Asset<?> asset) {
        String controllerId = asset.getId();
        String targetName = asset.getAssetType() + "-" + controllerId;
        String targetDescription = "assetId=" + asset.getId() + "; realm=" + asset.getRealm();
        return new TargetCreateRequest(controllerId, targetName, targetDescription);
    }

    protected boolean createFirmwareTarget(TargetCreateRequest target) {
        LOG.info("Creating hawkbit target id=" + target.controllerId());
        try (Response response = targets.create(new TargetCreateRequest[]{target})) {
            boolean created = response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
            if (!created) {
                LOG.warning("Failed to create hawkbit target id=" + target.controllerId()
                        + ", status=" + response.getStatus());
            }
            return created;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to create hawkbit target id=" + target.controllerId(), e);
            return false;
        }
    }

    protected void deleteFirmwareTarget(String controllerId) {
        try (Response response = targets.delete(controllerId)) {
            LOG.info("Deleted hawkbit target id=" + controllerId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkbit target id=" + controllerId, e);
        }
    }

    /**
     * Builds a JSON value containing the hawkBit target's controllerId and securityToken,
     * then sends it as an attribute event if the value has changed.
     */
    protected void updateFirmwareTargetInfo(AssetEvent assetEvent) {
        Asset<?> asset = assetEvent.getAsset();
        Optional<AttributeDescriptor<?>> firmwareTargetInfoDescriptor = getFirmwareTargetInfoDescriptor(asset);
        if (firmwareTargetInfoDescriptor.isEmpty()) {
            return;
        }

        String attributeName = firmwareTargetInfoDescriptor.get().getName();
        String controllerId = asset.getId();

        switch (assetEvent.getCause()) {
            case CREATE:
            case UPDATE:
                try {
                    Target firmwareTarget = getFirmwareTarget(controllerId);
                    if (firmwareTarget == null) {
                        return;
                    }
                    Map<String, String> firmwareTargetInfo = new LinkedHashMap<>();
                    firmwareTargetInfo.put("controllerId", firmwareTarget.controllerId());
                    firmwareTargetInfo.put("securityToken", firmwareTarget.securityToken());
                    String newValueJson = ValueUtil.asJSON(firmwareTargetInfo).orElse(null);

                    String existingValueJson = asset.getAttribute(attributeName)
                            .flatMap(attr -> attr.getValue(String.class))
                            .orElse(null);
                    if (Objects.equals(existingValueJson, newValueJson)) {
                        LOG.fine("Firmware target info attribute up to date for asset id=" + asset.getId());
                        return;
                    }

                    assetProcessingService.sendAttributeEvent(
                            new AttributeEvent(asset.getId(), attributeName, newValueJson),
                            getClass().getSimpleName());
                    LOG.info("Updated firmware target info attribute for asset id=" + asset.getId()
                            + ", controllerId=" + firmwareTarget.controllerId());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to update firmware target info for asset id=" + asset.getId(), e);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Queries hawkBit for a target by controllerId.
     * Returns {@code null} if the target is not found (404).
     */
    protected Target getFirmwareTarget(String controllerId) {
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

    /**
     * Uploads an artifact file to the given hawkBit software module.
     */
    public Response uploadSoftwareModuleArtifact(Long softwareModuleId, InputStream inputStream,
                                                 String originalFilename, String filename) {
        String effectiveFilename = TextUtil.isNullOrEmpty(filename) ? originalFilename : filename;
        String uploadFilename = TextUtil.isNullOrEmpty(effectiveFilename) ? "artifact.bin" : effectiveFilename;
        MultipartFormDataOutput form = new MultipartFormDataOutput();
        form.addFormData("file", inputStream, MediaType.APPLICATION_OCTET_STREAM_TYPE, uploadFilename);
        return HawkbitResponseHandler.call("Failed to upload artifact for firmware software module '" + softwareModuleId + "'",
                () -> softwareModules.uploadArtifact(softwareModuleId,
                        TextUtil.isNullOrEmpty(effectiveFilename) ? null : effectiveFilename,
                        form));
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
