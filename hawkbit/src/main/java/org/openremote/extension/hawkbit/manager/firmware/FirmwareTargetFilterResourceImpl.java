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
import org.openremote.extension.hawkbit.model.firmware.FirmwareAutoAssignDS;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterCreate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterResource;
import org.openremote.model.http.RequestParams;

public class FirmwareTargetFilterResourceImpl extends ManagerWebResource
        implements FirmwareTargetFilterResource {

    protected final FirmwareService firmwareService;

    public FirmwareTargetFilterResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                             FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response getTargetFilters(RequestParams requestParams, Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve firmware target filters",
                () -> firmwareService.targetFiltersResource.getTargetFilters(offset, limit)).asPage();
    }

    @Override
    public Response getTargetFilter(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware target filter '" + id + "'",
                () -> firmwareService.targetFiltersResource.get(id)).asResource();
    }

    @Override
    public Response createTargetFilter(RequestParams requestParams,
                                       FirmwareTargetFilterCreate filter) {
        return HawkbitResponse.proxy("Failed to create firmware target filter",
                () -> firmwareService.targetFiltersResource.create(filter)).asResource();
    }

    @Override
    public void deleteTargetFilter(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to delete firmware target filter '" + id + "'",
                () -> firmwareService.targetFiltersResource.delete(id));
    }

    @Override
    public Response getAutoAssignDS(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy(
                "Failed to retrieve auto assign distribution set for filter '" + id + "'",
                () -> firmwareService.targetFiltersResource.getAutoAssignDS(id)).asResource();
    }

    @Override
    public Response setAutoAssignDS(RequestParams requestParams, Long id,
                                    FirmwareAutoAssignDS request) {
        return HawkbitResponse.proxy(
                "Failed to set auto assign distribution set for filter '" + id + "'",
                () -> firmwareService.targetFiltersResource.setAutoAssignDS(id, request)).asResource();
    }

    @Override
    public void deleteAutoAssignDS(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy(
                "Failed to remove auto assign distribution set from filter '" + id + "'",
                () -> firmwareService.targetFiltersResource.deleteAutoAssignDS(id));
    }
}
