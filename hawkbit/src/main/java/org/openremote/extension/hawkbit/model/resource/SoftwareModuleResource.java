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
package org.openremote.extension.hawkbit.model.resource;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Response;
import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Tag(name = "Firmware Software Modules", description = "Management of firmware software modules")
@Path("firmware/softwaremodule")
public interface SoftwareModuleResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createSoftwareModule(@BeanParam RequestParams requestParams,
                                  JsonNode softwareModule);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getSoftwareModules(@BeanParam RequestParams requestParams,
                                @QueryParam("offset") Integer offset,
                                @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getSoftwareModule(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @GET
    @Path("{id}/artifacts")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getSoftwareModuleArtifacts(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @POST
    @Path("{id}/artifacts")
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response uploadSoftwareModuleArtifact(@BeanParam RequestParams requestParams,
                                          @PathParam("id") Long id,
                                          @QueryParam("filename") String filename,
                                          List<EntityPart> parts);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteSoftwareModule(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

}
