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
package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareAction;
import org.openremote.extension.hawkbit.model.firmware.FirmwareActions;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTarget;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargets;

public class FirmwareTargetResourceImpl extends ManagerWebResource implements FirmwareTargetResource {

    protected final FirmwareService firmwareService;

    public FirmwareTargetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                      FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareTargets getTargets(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            return firmwareService.targetsResource.getTargets(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware targets", e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTarget getTarget(RequestParams requestParams, String id) {
        try {
            return firmwareService.targetsResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSet getAssignedDs(RequestParams requestParams, String id) {
        try {
            return firmwareService.targetsResource.getAssignedDs(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve assigned DS for firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSet getInstalledDs(RequestParams requestParams, String id) {
        try {
            return firmwareService.targetsResource.getInstalledDs(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve installed DS for firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareActions getActions(RequestParams requestParams, String id, Integer offset, Integer limit) {
        try {
            return firmwareService.targetsResource.getActions(id, offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve actions for firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareAction getAction(RequestParams requestParams, String id, Long actionId) {
        try {
            return firmwareService.targetsResource.getAction(id, actionId);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve action '" + actionId + "' for firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void cancelAction(RequestParams requestParams, String id, Long actionId, Boolean force) {
        try {
            firmwareService.targetsResource.cancelAction(id, actionId, force);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to cancel action '" + actionId + "' for firmware target '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
