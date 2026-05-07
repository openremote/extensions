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
import org.openremote.extension.hawkbit.model.firmware.FirmwareAutoAssignRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueries;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQuery;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueryRequest;
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
    public FirmwareTargetFilterQueries getTargetFilters(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            return firmwareService.targetFiltersResource.getTargetFilters(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filters", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery getTargetFilter(RequestParams requestParams, Long id) {
        try {
            return firmwareService.targetFiltersResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery createTargetFilter(RequestParams requestParams,
                                                         FirmwareTargetFilterQueryRequest filter) {
        try {
            return firmwareService.targetFiltersResource.create(filter);
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
    public FirmwareDistributionSet getAutoAssignDS(RequestParams requestParams, Long id) {
        try {
            return firmwareService.targetFiltersResource.getAutoAssignDS(id);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve auto assign distribution set for filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery setAutoAssignDS(RequestParams requestParams, Long id,
                                                      FirmwareAutoAssignRequest request) {
        try {
            return firmwareService.targetFiltersResource.setAutoAssignDS(id, request);
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
