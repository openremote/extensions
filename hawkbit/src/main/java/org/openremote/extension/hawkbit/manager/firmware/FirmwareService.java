/*
 * Copyright 2021, OpenRemote Inc.
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
package org.openremote.extension.hawkbit.manager.firmware;

import static org.openremote.container.web.WebTargetBuilder.CONNECTION_POOL_SIZE;
import static org.openremote.container.web.WebTargetBuilder.CONNECTION_TIMEOUT_MILLISECONDS;
import static org.openremote.container.web.WebTargetBuilder.createClient;
import static org.openremote.model.syslog.SyslogCategory.API;
import static org.openremote.model.util.MapAccess.getString;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.jboss.resteasy.plugins.providers.jackson.ResteasyJackson2Provider;
import org.openremote.container.timer.TimerService;
import org.openremote.container.web.WebClient;
import org.openremote.container.web.WebTargetBuilder;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitArtifactUploadClient;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitBasicAuth;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitDistributionSetsResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitDistributionSetTypesResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitRolloutsResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitSoftwareModulesResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitSoftwareModuleTypesResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitTargetFiltersResource;
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitTargetsResource;
import org.openremote.manager.web.ManagerWebService;
import org.openremote.model.Container;
import org.openremote.model.ContainerService;
import org.openremote.model.asset.Asset;
import org.openremote.model.asset.AssetEvent;
import org.openremote.model.asset.AssetTypeInfo;
import org.openremote.model.attribute.Attribute;
import org.openremote.model.attribute.AttributeEvent;
import org.openremote.model.attribute.MetaMap;
import org.openremote.extension.hawkbit.model.firmware.FirmwareMetaItemType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareMetadataUpdate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTarget;
import org.openremote.model.syslog.SyslogCategory;
import org.openremote.model.util.TextUtil;
import org.openremote.model.util.ValueUtil;
import org.openremote.model.value.AttributeDescriptor;

public class FirmwareService implements ContainerService {


    public static final String HAWKBIT_REALM = "HAWKBIT_REALM";
    public static final String HAWKBIT_REALM_DEFAULT = "master";
    public static final String HAWKBIT_USERNAME = "HAWKBIT_USERNAME";
    public static final String HAWKBIT_USERNAME_DEFAULT = "hawkbit";
    public static final String HAWKBIT_PASSWORD = "HAWKBIT_PASSWORD";
    public static final String HAWKBIT_PASSWORD_DEFAULT = "hawkbit";

    public static final String HAWKBIT_MANAGEMENT_API_URL = "HAWKBIT_MANAGEMENT_API_URL";
    public static final String HAWKBIT_MANAGEMENT_API_URL_DEFAULT = "http://localhost:8083/hawkbit/rest/v1";

    protected static final Logger LOG = SyslogCategory.getLogger(API, FirmwareService.class);

    protected static ResteasyClient client;

    protected String hawkbitRealm;
    protected ClientEventService clientEventService;
    protected ManagerIdentityService identityService;
    protected AssetProcessingService assetProcessingService;
    protected ExecutorService executorService;
    protected TimerService timerService;
    protected HawkbitTargetsResource targetsResource;
    protected HawkbitDistributionSetsResource distributionSetsResource;
    protected HawkbitDistributionSetTypesResource distributionSetTypesResource;
    protected HawkbitSoftwareModulesResource softwareModulesResource;
    protected HawkbitSoftwareModuleTypesResource softwareModuleTypesResource;
    protected HawkbitRolloutsResource rolloutsResource;
    protected HawkbitTargetFiltersResource targetFiltersResource;
    protected HawkbitArtifactUploadClient artifactUploadClient;

    static {
        client = createClient(org.openremote.container.Container.EXECUTOR, CONNECTION_POOL_SIZE,
                CONNECTION_TIMEOUT_MILLISECONDS, resteasyClientBuilder -> {
                    WebClient.registerDefaults((ResteasyClientBuilderImpl) resteasyClientBuilder);
                    ResteasyJackson2Provider provider = new ResteasyJackson2Provider();
                    provider.setMapper(ValueUtil.JSON);
                    resteasyClientBuilder.register(provider);
                    return resteasyClientBuilder;
                });
    }

    @Override
    public void init(Container container) throws Exception {
        clientEventService = container.getService(ClientEventService.class);
        assetProcessingService = container.getService(AssetProcessingService.class);
        executorService = container.getExecutor();
        timerService = container.getService(TimerService.class);
        identityService = container.getService(ManagerIdentityService.class);

        // API resources
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareTargetResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareDistributionSetResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareDistributionSetTypeResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareSoftwareModuleResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareSoftwareModuleTypeResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareRolloutResourceImpl(timerService, identityService, this));
        container.getService(ManagerWebService.class).addApiSingleton(
                new FirmwareTargetFilterResourceImpl(timerService, identityService, this));
    }

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
        
        ResteasyWebTarget webTarget = new WebTargetBuilder(client, uri).build();
        webTarget.register((ClientRequestFilter) requestContext -> requestContext.getHeaders().putSingle(
                HttpHeaders.AUTHORIZATION,
                HawkbitBasicAuth.buildAuthorizationHeader(hawkbitUsername, hawkbitPassword)));

        // Set targets resource
        targetsResource = webTarget.proxy(HawkbitTargetsResource.class);
        distributionSetsResource = webTarget.proxy(HawkbitDistributionSetsResource.class);
        distributionSetTypesResource = webTarget.proxy(HawkbitDistributionSetTypesResource.class);
        softwareModulesResource = webTarget.proxy(HawkbitSoftwareModulesResource.class);
        softwareModuleTypesResource = webTarget.proxy(HawkbitSoftwareModuleTypesResource.class);
        rolloutsResource = webTarget.proxy(HawkbitRolloutsResource.class);
        targetFiltersResource = webTarget.proxy(HawkbitTargetFiltersResource.class);

        // Artifact upload uses raw multipart forwarding because Hawkbit expects
        // multipart/form-data for this endpoint.
        artifactUploadClient = new HawkbitArtifactUploadClient(uri, hawkbitUsername, hawkbitPassword);

        // Subscribe on asset events
        clientEventService.addSubscription(
                AssetEvent.class,
                null,
                this::onAssetChange);

        // Subscribe on attribute events
        clientEventService.addSubscription(
                AttributeEvent.class,
                null,
                this::onAttributeChange);

        LOG.log(Level.INFO,
                "Firmware Service started, connected hawkBit instance: " + uri + ", for realm: " + hawkbitRealm);
    }

    @Override
    public void stop(Container container) throws Exception {

    }

    public Response uploadSoftwareModuleArtifact(Long softwareModuleId, InputStream inputStream,
            String originalFilename, String filename)
            throws IOException, InterruptedException {
        return HawkbitResponse.from(artifactUploadClient.uploadSoftwareModuleArtifact(softwareModuleId, inputStream,
                originalFilename, filename)).asResource();
    }

    private void onAssetChange(AssetEvent assetEvent) {
        if (!Objects.equals(assetEvent.getRealm(), hawkbitRealm))
        {
            return;
        }

        // Submit the assetEvent to the executorService
        executorService.submit(() -> handleAssetChange(assetEvent));
    }

    private void onAttributeChange(AttributeEvent attributeEvent) {
        if (!Objects.equals(attributeEvent.getRealm(), hawkbitRealm))
        {
            return;
        }

        // Submit the attributeEvent to the executorService
        executorService.submit(() -> handleAttributeChange(attributeEvent));
    }

    private void handleAttributeChange(AttributeEvent attributeEvent) {
        if (!hasFirmwareMetadata(attributeEvent.getAssetType(), attributeEvent.getName(), attributeEvent.getMeta())) {
            return;
        }

        if (attributeEvent.isDeleted()) {
            deleteFirmwareTargetMetadata(attributeEvent.getId(), attributeEvent.getName());
            return;
        }

        attributeEvent.getValue().ifPresent(value -> syncFirmwareTargetMetadataValue(
                attributeEvent.getId(),
                attributeEvent.getName(),
                value));
    }

    private void handleAssetChange(AssetEvent assetEvent) {
        Asset<?> asset = assetEvent.getAsset();

        if (getFirmwareTargetInfoDescriptor(asset).isEmpty()) {
            return;
        }

        String controllerId = asset.getId();
        boolean shouldUpdateFirmwareTargetInfo = false;

        LOG.info("Processing hawkbit target sync cause=" + assetEvent.getCause()
                + ", assetId=" + asset.getId() + ", assetType=" + asset.getType()
                + ", controllerId=" + controllerId);

        switch (assetEvent.getCause()) {
            case CREATE:
                LOG.info("Creating hawkbit target for asset id=" + asset.getId());
                shouldUpdateFirmwareTargetInfo = createFirmwareTarget(buildFirmwareTarget(asset));
                break;
            case UPDATE:
                LOG.info("Checking hawkbit target for update asset id=" + asset.getId());
                try {
                    FirmwareTarget existingTarget = getFirmwareTarget(controllerId);
                    if (existingTarget != null) {
                        LOG.info("hawkbit target already exists so nothing to do id=" + controllerId);
                        shouldUpdateFirmwareTargetInfo = true;
                        break;
                    }
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Failed to load hawkbit target id=" + controllerId
                            + ", trying create instead", e);
                }

                if (createFirmwareTarget(buildFirmwareTarget(asset))) {
                    LOG.info("hawkbit target missing for asset id=" + asset.getId() + ", creating it now");
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

    private void updateFirmwareTargetMetadata(Asset<?> asset) {
        String controllerId = asset.getId();
        for (Attribute<?> attribute : asset.getAttributes().values()) {
            if (!hasFirmwareMetadata(asset.getType(), attribute.getName(), attribute.getMeta())) {
                continue;
            }

            attribute.getValue().ifPresent(value ->
                    syncFirmwareTargetMetadataValue(controllerId, attribute.getName(), value));
        }
    }

    private boolean hasFirmwareMetadata(String assetType, String attributeName, MetaMap meta) {
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

    private boolean hasFirmwareMetadata(MetaMap meta) {
        return meta != null
                && meta.get(FirmwareMetaItemType.FIRMWARE_METADATA)
                        .flatMap(metaItem -> metaItem.getValue(Boolean.class))
                        .orElse(false);
    }

    private void syncFirmwareTargetMetadataValue(String controllerId, String key, Object value) {
        if (value == null) {
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

    private void updateFirmwareTargetMetadata(String controllerId, String key, String value) {
        try {
            targetsResource.updateMetadata(controllerId, key, new FirmwareMetadataUpdate(value));
            LOG.fine("Updated hawkbit target metadata targetId=" + controllerId + ", key=" + key);
        } catch (NotFoundException e) {
            LOG.fine("hawkbit target not found for metadata sync targetId="
                    + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to update hawkbit target metadata targetId="
                    + controllerId + ", key=" + key, e);
        }
    }

    private void deleteFirmwareTargetMetadata(String controllerId, String key) {
        try {
            targetsResource.deleteMetadata(controllerId, key);
            LOG.fine("Deleted hawkbit target metadata targetId=" + controllerId + ", key=" + key);
        } catch (NotFoundException e) {
            LOG.fine("hawkbit target metadata not found for delete targetId="
                    + controllerId + ", key=" + key);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkbit target metadata targetId="
                    + controllerId + ", key=" + key, e);
        }
    }

    private Optional<AttributeDescriptor<?>> getFirmwareTargetInfoDescriptor(Asset<?> asset) {
        Optional<AssetTypeInfo> assetTypeInfo = ValueUtil.getAssetInfo(asset.getType());
        if (assetTypeInfo.isEmpty()) {
            LOG.warning("Cannot resolve asset type info for asset type '" + asset.getType() + "'");
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

    private FirmwareTarget buildFirmwareTarget(Asset<?> asset) {
        String controllerId = asset.getId();
        String targetName =  asset.getAssetType() + "-" + controllerId;
        String targetDescription =  "assetId=" + asset.getId() + "; realm=" + asset.getRealm();
        return new FirmwareTarget(controllerId, targetName, targetDescription);
    }

    private boolean createFirmwareTarget(FirmwareTarget target) {
        LOG.info("Creating hawkbit target id=" + target.controllerId());
        try (Response response = targetsResource.create(new FirmwareTarget[] { target })) {
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

    private void deleteFirmwareTarget(String controllerId) {
        try {
            targetsResource.delete(controllerId);
            LOG.info("Deleted hawkbit target id=" + controllerId);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete hawkbit target id=" + controllerId, e);
        }
    }

    private void updateFirmwareTargetInfo(AssetEvent assetEvent) {
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
                    FirmwareTarget firmwareTarget = getFirmwareTarget(controllerId);
                    if (firmwareTarget == null) {
                        return;
                    }
                    Map<String, String> firmwareTargetInfo = new LinkedHashMap<>();
                    firmwareTargetInfo.put("controllerId", firmwareTarget.controllerId());
                    firmwareTargetInfo.put("securityToken", firmwareTarget.securityToken());
                    assetProcessingService.sendAttributeEvent(
                            new AttributeEvent(
                                    asset.getId(),
                                    attributeName,
                                    ValueUtil.asJSON(firmwareTargetInfo).orElse(null)),
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

    private FirmwareTarget getFirmwareTarget(String controllerId) {
        try (Response response = targetsResource.get(controllerId)) {
            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return null;
            }
            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw new WebApplicationException("hawkBit target request failed with status " + response.getStatus(),
                        response.getStatus());
            }
            return response.readEntity(FirmwareTarget.class);
        }
    }

}
