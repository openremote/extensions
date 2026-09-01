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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseProxy;
import org.openremote.extension.hawkbit.model.resource.TargetFilterResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

public class TargetFilterResourceImpl extends HawkbitWebResource
        implements TargetFilterResource {

    public TargetFilterResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                    HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response getTargetFilters(RequestParams requestParams, String realm, Integer offset, Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware target filters",
                () -> hawkbitFirmwareService.targetFilters().getTargetFilters(offset, limit));
    }

    @Override
    public Response getTargetFilter(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware target filter '" + id + "'",
                () -> hawkbitFirmwareService.targetFilters().get(id));
    }

    @Override
    public Response createTargetFilter(RequestParams requestParams,
                                       String realm,
                                       JsonNode filter) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to create firmware target filter",
                () -> hawkbitFirmwareService.targetFilters().create(filter));
    }

    @Override
    public Response deleteTargetFilter(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to delete firmware target filter '" + id + "'",
                () -> hawkbitFirmwareService.targetFilters().delete(id));
    }

    @Override
    public Response getAutoAssignDS(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to retrieve auto assign distribution set for filter '" + id + "'",
                () -> hawkbitFirmwareService.targetFilters().getAutoAssignDS(id));
    }

    @Override
    public Response setAutoAssignDS(RequestParams requestParams, String realm, Long id,
                                    JsonNode request) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to set auto assign distribution set for filter '" + id + "'",
                () -> hawkbitFirmwareService.targetFilters().setAutoAssignDS(id, request));
    }

    @Override
    public Response deleteAutoAssignDS(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to remove auto assign distribution set from filter '" + id + "'",
                () -> hawkbitFirmwareService.targetFilters().deleteAutoAssignDS(id));
    }
}
