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
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseProxy;
import org.openremote.extension.hawkbit.model.resource.SoftwareModuleResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

import java.io.InputStream;
import java.util.List;

public class SoftwareModuleResourceImpl extends HawkbitWebResource
        implements SoftwareModuleResource {

    public SoftwareModuleResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                       HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response createSoftwareModule(RequestParams requestParams,
                                         String realm,
                                          JsonNode softwareModule) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to create firmware software module",
                () -> hawkbitFirmwareService.softwareModules().create(softwareModule));
    }

    @Override
    public Response getSoftwareModules(RequestParams requestParams, String realm, Integer offset, Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware software modules",
                () -> hawkbitFirmwareService.softwareModules().getSoftwareModules(offset, limit));
    }

    @Override
    public Response getSoftwareModule(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware software module '" + id + "'",
                () -> hawkbitFirmwareService.softwareModules().get(id));
    }

    @Override
    public Response getSoftwareModuleArtifacts(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve artifacts for firmware software module '" + id + "'",
                () -> hawkbitFirmwareService.softwareModules().getArtifacts(id));
    }

    @Override
    public Response uploadSoftwareModuleArtifact(RequestParams requestParams,
                                                 String realm,
                                                 Long id,
                                                 String filename,
                                                 List<EntityPart> parts) {
        requireHawkbitRealmAccess(realm);
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
            return hawkbitFirmwareService.uploadSoftwareModuleArtifact(id, inputStream, submittedFileName, filename);
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to upload artifact for firmware software module '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteSoftwareModule(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        HawkbitResponseProxy.proxy("Failed to delete firmware software module '" + id + "'",
                () -> hawkbitFirmwareService.softwareModules().delete(id));
    }
}
