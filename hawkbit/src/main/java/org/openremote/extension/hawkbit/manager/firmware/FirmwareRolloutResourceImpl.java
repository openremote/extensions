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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutResource;
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
    public Response getRollouts(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            // Request full representation so totalTargetsPerStatus and totalGroups are populated; hawkBit defaults to compact.
            return HawkbitResponse.from(firmwareService.rolloutsResource.getRollouts(offset, limit, "full")).asPage();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware rollouts", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getRollout(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.rolloutsResource.get(id)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response createRollout(RequestParams requestParams, JsonNode rollout) {
        try {
            return HawkbitResponse.from(firmwareService.rolloutsResource.create(rollout)).asResource();
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
    public Response startRollout(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.rolloutsResource.start(id)).asResource();
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
    public Response getRolloutGroups(RequestParams requestParams, Long id,
                                     Integer offset, Integer limit) {
        try {
            return HawkbitResponse.from(firmwareService.rolloutsResource.getRolloutGroups(id, offset, limit, "full"))
                    .asPage();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve groups for rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getRolloutGroup(RequestParams requestParams, Long id, Long groupId) {
        try {
            return HawkbitResponse.from(firmwareService.rolloutsResource.getRolloutGroup(id, groupId)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve group '" + groupId + "' for rollout '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
