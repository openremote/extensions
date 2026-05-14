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
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleCreate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleResource;

import java.io.InputStream;
import java.util.List;

public class FirmwareSoftwareModuleResourceImpl extends ManagerWebResource
        implements FirmwareSoftwareModuleResource {

    protected final FirmwareService firmwareService;

    public FirmwareSoftwareModuleResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                              FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response createSoftwareModule(RequestParams requestParams,
                                         FirmwareSoftwareModuleCreate softwareModule) {
        return HawkbitResponse.proxy("Failed to create firmware software module",
                () -> firmwareService.softwareModulesResource.create(
                        new FirmwareSoftwareModuleCreate[] { softwareModule })).asResource();
    }

    @Override
    public Response getSoftwareModules(RequestParams requestParams, Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve firmware software modules",
                () -> firmwareService.softwareModulesResource.getSoftwareModules(offset, limit)).asPage();
    }

    @Override
    public Response getSoftwareModule(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware software module '" + id + "'",
                () -> firmwareService.softwareModulesResource.get(id)).asResource();
    }

    @Override
    public Response getSoftwareModuleArtifacts(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve artifacts for firmware software module '" + id + "'",
                () -> firmwareService.softwareModulesResource.getArtifacts(id)).asPage();
    }

    @Override
    public Response uploadSoftwareModuleArtifact(RequestParams requestParams,
                                                 Long id,
                                                 String filename,
                                                 List<EntityPart> parts) {
        try {
            EntityPart filePart = parts == null ? null : parts.stream()
                    .filter(part -> "file".equals(part.getName()))
                    .findFirst()
                    .orElse(null);
            if (filePart == null) {
                throw new WebApplicationException("Missing multipart field 'file'", Response.Status.BAD_REQUEST);
            }

            String submittedFileName = filePart.getFileName().orElse(null);
            InputStream inputStream = filePart.getContent(InputStream.class);
            return firmwareService.uploadSoftwareModuleArtifact(id, inputStream, submittedFileName, filename);
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to upload artifact for firmware software module '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteSoftwareModule(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to delete firmware software module '" + id + "'",
                () -> firmwareService.softwareModulesResource.delete(id));
    }
}
