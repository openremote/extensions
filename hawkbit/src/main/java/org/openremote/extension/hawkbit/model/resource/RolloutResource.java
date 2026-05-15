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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.openremote.extension.hawkbit.model.hawkbit.RolloutCreateRequest;
import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Firmware Rollouts", description = "Management of firmware rollouts")
@Path("firmware/rollout")
public interface RolloutResource {

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRollouts(@BeanParam RequestParams requestParams,
                         @QueryParam("offset") Integer offset,
                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRollout(@BeanParam RequestParams requestParams,
                        @PathParam("id") Long id);

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createRollout(@BeanParam RequestParams requestParams,
                           RolloutCreateRequest rollout);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteRollout(@BeanParam RequestParams requestParams,
                       @PathParam("id") Long id);

    @POST
    @Path("{id}/start")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response startRollout(@BeanParam RequestParams requestParams,
                          @PathParam("id") Long id);

    @POST
    @Path("{id}/pause")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void pauseRollout(@BeanParam RequestParams requestParams,
                      @PathParam("id") Long id);

    @GET
    @Path("{id}/deploygroups")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRolloutGroups(@BeanParam RequestParams requestParams,
                              @PathParam("id") Long id,
                              @QueryParam("offset") Integer offset,
                              @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/deploygroups/{groupId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRolloutGroup(@BeanParam RequestParams requestParams,
                             @PathParam("id") Long id,
                             @PathParam("groupId") Long groupId);
}
