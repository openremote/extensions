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
package org.openremote.extension.hawkbit.model.firmware;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Firmware Targets", description = "Management of firmware targets")
@Path("firmware/target")
public interface FirmwareTargetResource {

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTargets(@BeanParam RequestParams requestParams,
                        @QueryParam("q") String query,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTarget(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/metadata")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getMetadata(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/assignedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getAssignedDs(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/installedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getInstalledDs(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/actions")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getActions(@BeanParam RequestParams requestParams,
                        @PathParam("id") String id,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/actions/{actionId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getAction(@BeanParam RequestParams requestParams,
                       @PathParam("id") String id,
                       @PathParam("actionId") Long actionId);

    @DELETE
    @Path("{id}/actions/{actionId}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void cancelAction(@BeanParam RequestParams requestParams,
                      @PathParam("id") String id,
                      @PathParam("actionId") Long actionId,
                      @QueryParam("force") Boolean force);
}
