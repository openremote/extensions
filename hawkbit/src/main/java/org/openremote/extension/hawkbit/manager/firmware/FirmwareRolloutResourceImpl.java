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

import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutCreate;
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
        // Request full representation so totalTargetsPerStatus and totalGroups are populated; hawkBit defaults to compact.
        return HawkbitResponse.proxy("Failed to retrieve firmware rollouts",
                () -> firmwareService.rolloutsResource.getRollouts(offset, limit, "full")).asPage();
    }

    @Override
    public Response getRollout(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.get(id)).asResource();
    }

    @Override
    public Response createRollout(RequestParams requestParams, FirmwareRolloutCreate rollout) {
        return HawkbitResponse.proxy("Failed to create firmware rollout",
                () -> firmwareService.rolloutsResource.create(rollout)).asResource();
    }

    @Override
    public void deleteRollout(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to delete firmware rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.delete(id));
    }

    @Override
    public Response startRollout(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to start firmware rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.start(id)).asResource();
    }

    @Override
    public void pauseRollout(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to pause firmware rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.pause(id));
    }

    @Override
    public Response getRolloutGroups(RequestParams requestParams, Long id,
                                     Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve groups for rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.getRolloutGroups(id, offset, limit, "full")).asPage();
    }

    @Override
    public Response getRolloutGroup(RequestParams requestParams, Long id, Long groupId) {
        return HawkbitResponse.proxy(
                "Failed to retrieve group '" + groupId + "' for rollout '" + id + "'",
                () -> firmwareService.rolloutsResource.getRolloutGroup(id, groupId)).asResource();
    }
}
