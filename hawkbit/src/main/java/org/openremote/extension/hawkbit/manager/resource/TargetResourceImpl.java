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
package org.openremote.extension.hawkbit.manager.resource;

import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseProxy;
import org.openremote.extension.hawkbit.model.resource.TargetResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

public class TargetResourceImpl extends HawkbitWebResource implements TargetResource {

    public TargetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                               HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response getTargets(RequestParams requestParams, String realm, String query, Integer offset, Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware targets",
                () -> hawkbitFirmwareService.targets().getTargets(query, offset, limit));
    }

    @Override
    public Response getTarget(RequestParams requestParams, String realm, String id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().get(id));
    }

    @Override
    public Response getMetadata(RequestParams requestParams, String realm, String id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve metadata for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().getMetadata(id));
    }

    @Override
    public Response getAssignedDs(RequestParams requestParams, String realm, String id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve assigned DS for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().getAssignedDs(id));
    }

    @Override
    public Response getInstalledDs(RequestParams requestParams, String realm, String id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve installed DS for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().getInstalledDs(id));
    }

    @Override
    public Response getActions(RequestParams requestParams, String realm, String id, Integer offset, Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve actions for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().getActions(id, offset, limit));
    }

    @Override
    public Response getAction(RequestParams requestParams, String realm, String id, Long actionId) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to retrieve action '" + actionId + "' for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().getAction(id, actionId));
    }

    @Override
    public void cancelAction(RequestParams requestParams, String realm, String id, Long actionId, Boolean force) {
        requireHawkbitRealmAccess(realm);
        HawkbitResponseProxy.proxy(
                "Failed to cancel action '" + actionId + "' for firmware target '" + id + "'",
                () -> hawkbitFirmwareService.targets().cancelAction(id, actionId, force));
    }
}
