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
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetResource;

public class FirmwareTargetResourceImpl extends ManagerWebResource implements FirmwareTargetResource {

    protected final FirmwareService firmwareService;

    public FirmwareTargetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                      FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response getTargets(RequestParams requestParams, String query, Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve firmware targets",
                () -> firmwareService.targetsResource.getTargets(query, offset, limit)).asPage();
    }

    @Override
    public Response getTarget(RequestParams requestParams, String id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware target '" + id + "'",
                () -> firmwareService.targetsResource.get(id)).asResource();
    }

    @Override
    public Response getMetadata(RequestParams requestParams, String id) {
        return HawkbitResponse.proxy("Failed to retrieve metadata for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.getMetadata(id)).asPage();
    }

    @Override
    public Response getAssignedDs(RequestParams requestParams, String id) {
        return HawkbitResponse.proxy("Failed to retrieve assigned DS for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.getAssignedDs(id)).asResource();
    }

    @Override
    public Response getInstalledDs(RequestParams requestParams, String id) {
        return HawkbitResponse.proxy("Failed to retrieve installed DS for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.getInstalledDs(id)).asResource();
    }

    @Override
    public Response getActions(RequestParams requestParams, String id, Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve actions for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.getActions(id, offset, limit)).asPage();
    }

    @Override
    public Response getAction(RequestParams requestParams, String id, Long actionId) {
        return HawkbitResponse.proxy(
                "Failed to retrieve action '" + actionId + "' for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.getAction(id, actionId)).asResource();
    }

    @Override
    public void cancelAction(RequestParams requestParams, String id, Long actionId, Boolean force) {
        HawkbitResponse.proxy(
                "Failed to cancel action '" + actionId + "' for firmware target '" + id + "'",
                () -> firmwareService.targetsResource.cancelAction(id, actionId, force));
    }
}
