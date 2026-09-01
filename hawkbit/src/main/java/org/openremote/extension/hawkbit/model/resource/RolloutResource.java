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
import jakarta.ws.rs.core.Response;
import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Proxies the hawkBit Management API rollout endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitRolloutsClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Rollouts", description = "Management of firmware rollouts")
@Path("firmware/rollout")
public interface RolloutResource {

    /**
     * Retrieve all rollouts, paged.
     * <p>
     * Forces {@code representation=full} so {@code totalTargetsPerStatus} and
     * {@code totalGroups} are populated. hawkBit's default is {@code compact}
     * which omits both.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRollouts(@BeanParam RequestParams requestParams,
                         @QueryParam("realm") String realm,
                         @QueryParam("offset") Integer offset,
                         @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single rollout by id.
     */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRollout(@BeanParam RequestParams requestParams,
                        @QueryParam("realm") String realm,
                        @PathParam("id") Long id);

    /**
     * Create a rollout. Body matches hawkBit's Rollout create payload.
     */
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createRollout(@BeanParam RequestParams requestParams,
                           @QueryParam("realm") String realm,
                           JsonNode rollout);

    /**
     * Delete a rollout.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteRollout(@BeanParam RequestParams requestParams,
                           @QueryParam("realm") String realm,
                           @PathParam("id") Long id);

    /**
     * Start a rollout.
     */
    @POST
    @Path("{id}/start")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response startRollout(@BeanParam RequestParams requestParams,
                          @QueryParam("realm") String realm,
                          @PathParam("id") Long id);

    /**
     * Pause a running rollout.
     */
    @POST
    @Path("{id}/pause")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response pauseRollout(@BeanParam RequestParams requestParams,
                          @QueryParam("realm") String realm,
                          @PathParam("id") Long id);

    /**
     * Retrieve deployment groups for a rollout, paged.
     * <p>
     * Forces {@code representation=full} so per-group status counters are populated.
     */
    @GET
    @Path("{id}/deploygroups")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRolloutGroups(@BeanParam RequestParams requestParams,
                              @QueryParam("realm") String realm,
                              @PathParam("id") Long id,
                              @QueryParam("offset") Integer offset,
                              @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single deployment group within a rollout.
     */
    @GET
    @Path("{id}/deploygroups/{groupId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getRolloutGroup(@BeanParam RequestParams requestParams,
                             @QueryParam("realm") String realm,
                             @PathParam("id") Long id,
                             @PathParam("groupId") Long groupId);
}
