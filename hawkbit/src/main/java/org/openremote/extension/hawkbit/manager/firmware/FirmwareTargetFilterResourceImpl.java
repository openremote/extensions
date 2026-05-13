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
        try {
            return HawkbitResponse.from(firmwareService.targetFiltersResource.getTargetFilters(offset, limit)).asPage();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filters", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getTargetFilter(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.targetFiltersResource.get(id)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response createTargetFilter(RequestParams requestParams,
                                       JsonNode filter) {
        try {
            return HawkbitResponse.from(firmwareService.targetFiltersResource.create(filter)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware target filter", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteTargetFilter(RequestParams requestParams, Long id) {
        try {
            firmwareService.targetFiltersResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware target filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getAutoAssignDS(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.targetFiltersResource.getAutoAssignDS(id)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve auto assign distribution set for filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response setAutoAssignDS(RequestParams requestParams, Long id,
                                    JsonNode request) {
        try {
            return HawkbitResponse.from(firmwareService.targetFiltersResource.setAutoAssignDS(id, request)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to set auto assign distribution set for filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteAutoAssignDS(RequestParams requestParams, Long id) {
        try {
            firmwareService.targetFiltersResource.deleteAutoAssignDS(id);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to remove auto assign distribution set from filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
