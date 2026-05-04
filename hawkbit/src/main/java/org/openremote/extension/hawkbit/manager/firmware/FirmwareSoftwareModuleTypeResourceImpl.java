package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleTypeResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleTypes;

public class FirmwareSoftwareModuleTypeResourceImpl extends ManagerWebResource
        implements FirmwareSoftwareModuleTypeResource {

    protected final FirmwareService firmwareService;

    public FirmwareSoftwareModuleTypeResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                                  FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareSoftwareModuleType createSoftwareModuleType(RequestParams requestParams,
                                                               FirmwareSoftwareModuleType softwareModuleType) {
        try {
            FirmwareSoftwareModuleType[] created = firmwareService.softwareModuleTypesResource.create(
                    new FirmwareSoftwareModuleType[] { softwareModuleType });
            return created != null && created.length > 0 ? created[0] : null;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware software module type", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModuleTypes getSoftwareModuleTypes(RequestParams requestParams, Integer offset,
                                                              Integer limit) {
        try {
            return firmwareService.softwareModuleTypesResource.getSoftwareModuleTypes(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software module types", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModuleType getSoftwareModuleType(RequestParams requestParams, Long id) {
        try {
            return firmwareService.softwareModuleTypesResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software module type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteSoftwareModuleType(RequestParams requestParams, Long id) {
        try {
            firmwareService.softwareModuleTypesResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware software module type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
