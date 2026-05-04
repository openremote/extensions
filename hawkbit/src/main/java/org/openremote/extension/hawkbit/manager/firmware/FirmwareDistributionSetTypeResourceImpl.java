package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypeResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypes;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;

public class FirmwareDistributionSetTypeResourceImpl extends ManagerWebResource
        implements FirmwareDistributionSetTypeResource {

    protected final FirmwareService firmwareService;

    public FirmwareDistributionSetTypeResourceImpl(TimerService timerService,
                                                   ManagerIdentityService identityService,
                                                   FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareDistributionSetType createDistributionSetType(RequestParams requestParams,
                                                                 FirmwareDistributionSetType distributionSetType) {
        try {
            FirmwareDistributionSetType[] created = firmwareService.distributionSetTypesResource.create(
                    new FirmwareDistributionSetType[] { distributionSetType });
            return created != null && created.length > 0 ? created[0] : null;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware distribution set type", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSetTypes getDistributionSetTypes(RequestParams requestParams, Integer offset,
                                                                Integer limit) {
        try {
            return firmwareService.distributionSetTypesResource.getDistributionSetTypes(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution set types", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSetType getDistributionSetType(RequestParams requestParams, Long id) {
        try {
            return firmwareService.distributionSetTypesResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution set type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModuleType[] getMandatoryModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                                                Integer limit) {
        try {
            return firmwareService.distributionSetTypesResource.getMandatoryModuleTypes(id, offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve mandatory module types for firmware distribution set type '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModuleType[] getOptionalModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                                               Integer limit) {
        try {
            return firmwareService.distributionSetTypesResource.getOptionalModuleTypes(id, offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve optional module types for firmware distribution set type '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteDistributionSetType(RequestParams requestParams, Long id) {
        try {
            firmwareService.distributionSetTypesResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware distribution set type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
