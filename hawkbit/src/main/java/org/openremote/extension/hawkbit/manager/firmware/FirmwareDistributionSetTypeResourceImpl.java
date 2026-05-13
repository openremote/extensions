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
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypeResource;

public class FirmwareDistributionSetTypeResourceImpl extends ManagerWebResource
        implements FirmwareDistributionSetTypeResource {

    protected final FirmwareService firmwareService;

    public FirmwareDistributionSetTypeResourceImpl(TimerService timerService,
                                                   ManagerIdentityService identityService,
                                                   FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response createDistributionSetType(RequestParams requestParams,
                                              JsonNode distributionSetType) {
        try {
            return HawkbitResponse.from(firmwareService.distributionSetTypesResource.create(distributionSetType))
                    .asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware distribution set type", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getDistributionSetTypes(RequestParams requestParams, Integer offset,
                                            Integer limit) {
        try {
            return HawkbitResponse.from(firmwareService.distributionSetTypesResource.getDistributionSetTypes(offset, limit))
                    .asPage();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution set types", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getDistributionSetType(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.distributionSetTypesResource.get(id)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution set type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getMandatoryModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                            Integer limit) {
        try {
            return HawkbitResponse.from(firmwareService.distributionSetTypesResource.getMandatoryModuleTypes(id, offset, limit))
                    .asPage();
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve mandatory module types for firmware distribution set type '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getOptionalModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                           Integer limit) {
        try {
            return HawkbitResponse.from(firmwareService.distributionSetTypesResource.getOptionalModuleTypes(id, offset, limit))
                    .asPage();
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve optional module types for firmware distribution set type '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteDistributionSetType(RequestParams requestParams, Long id) {
        try {
            firmwareService.distributionSetTypesResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware distribution set type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
