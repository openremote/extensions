package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRollout;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutGroup;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutGroups;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRollouts;
import org.openremote.model.http.RequestParams;

public class FirmwareRolloutResourceImpl extends ManagerWebResource
        implements FirmwareRolloutResource {

    protected final FirmwareService firmwareService;

    public FirmwareRolloutResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                        FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareRollouts getRollouts(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            // Request full representation so totalTargetsPerStatus and totalGroups are populated; hawkBit defaults to compact.
            return firmwareService.rolloutsResource.getRollouts(offset, limit, "full");
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware rollouts", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareRollout getRollout(RequestParams requestParams, Long id) {
        try {
            return firmwareService.rolloutsResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareRollout createRollout(RequestParams requestParams, FirmwareRolloutRequest rollout) {
        try {
            return firmwareService.rolloutsResource.create(rollout);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware rollout", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteRollout(RequestParams requestParams, Long id) {
        try {
            firmwareService.rolloutsResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareRollout startRollout(RequestParams requestParams, Long id) {
        try {
            return firmwareService.rolloutsResource.start(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to start firmware rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void pauseRollout(RequestParams requestParams, Long id) {
        try {
            firmwareService.rolloutsResource.pause(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to pause firmware rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareRolloutGroups getRolloutGroups(RequestParams requestParams, Long id,
                                                   Integer offset, Integer limit) {
        try {
            return firmwareService.rolloutsResource.getRolloutGroups(id, offset, limit, "full");
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve groups for rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareRolloutGroup getRolloutGroup(RequestParams requestParams, Long id, Long groupId) {
        try {
            return firmwareService.rolloutsResource.getRolloutGroup(id, groupId);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve group '" + groupId + "' for rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
