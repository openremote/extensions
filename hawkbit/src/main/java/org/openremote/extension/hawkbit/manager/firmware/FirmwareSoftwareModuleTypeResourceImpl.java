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
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleTypeResource;

public class FirmwareSoftwareModuleTypeResourceImpl extends ManagerWebResource
        implements FirmwareSoftwareModuleTypeResource {

    protected final FirmwareService firmwareService;

    public FirmwareSoftwareModuleTypeResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                                  FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response createSoftwareModuleType(RequestParams requestParams,
                                             JsonNode softwareModuleType) {
        try {
            return HawkbitResponse.from(firmwareService.softwareModuleTypesResource.create(softwareModuleType))
                    .asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware software module type", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getSoftwareModuleTypes(RequestParams requestParams, Integer offset,
                                           Integer limit) {
        try {
            return HawkbitResponse.from(firmwareService.softwareModuleTypesResource.getSoftwareModuleTypes(offset, limit))
                    .asPage();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software module types", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public Response getSoftwareModuleType(RequestParams requestParams, Long id) {
        try {
            return HawkbitResponse.from(firmwareService.softwareModuleTypesResource.get(id)).asResource();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software module type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteSoftwareModuleType(RequestParams requestParams, Long id) {
        try {
            firmwareService.softwareModuleTypesResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware software module type '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
