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
import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * Proxies the hawkBit Management API target endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitTargetsClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Targets", description = "Management of firmware targets")
@Path("firmware/target")
public interface TargetResource {

    /**
     * Retrieve firmware targets, paged.
     * <p>
     * {@code q} is a hawkBit RSQL filter (e.g. {@code name==foo}), not free-text search.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTargets(@BeanParam RequestParams requestParams,
                        @QueryParam("realm") String realm,
                        @QueryParam("q") String query,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single firmware target by controllerId.
     */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTarget(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") String id);

    /**
     * Delete a firmware target by controllerId.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteTarget(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") String id);

    /**
     * Retrieve all metadata key/value pairs for a firmware target.
     */
    @GET
    @Path("{id}/metadata")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getMetadata(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") String id);

    /**
     * Retrieve the distribution set currently assigned to a firmware target.
     * <p>
     * "Assigned" is the DS the server has scheduled. See {@link #getInstalledDs}
     * for what the target has confirmed installed.
     */
    @GET
    @Path("{id}/assignedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getAssignedDs(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") String id);

    /**
     * Retrieve the distribution set currently reported as installed on a firmware target.
     * <p>
     * "Installed" reflects the target's last confirmation. See {@link #getAssignedDs}
     * for what the server has scheduled.
     */
    @GET
    @Path("{id}/installedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getInstalledDs(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") String id);

    /**
     * Retrieve the action history for a firmware target, paged.
     */
    @GET
    @Path("{id}/actions")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getActions(@BeanParam RequestParams requestParams,
                        @QueryParam("realm") String realm,
                        @PathParam("id") String id,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single action for a firmware target.
     */
    @GET
    @Path("{id}/actions/{actionId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getAction(@BeanParam RequestParams requestParams,
                       @QueryParam("realm") String realm,
                       @PathParam("id") String id,
                       @PathParam("actionId") Long actionId);

    /**
     * Retrieve the status history for a single action, paged.
     * <p>
     * Each entry carries the {@code messages} the controller reported over the
     * DDI feedback channel (e.g. OTA progress or failure detail), plus the
     * reported status code.
     */
    @GET
    @Path("{id}/actions/{actionId}/status")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getActionStatus(@BeanParam RequestParams requestParams,
                             @QueryParam("realm") String realm,
                             @PathParam("id") String id,
                             @PathParam("actionId") Long actionId,
                             @QueryParam("offset") Integer offset,
                             @QueryParam("limit") Integer limit);

    /**
     * Cancel an in-flight action on a firmware target.
     * <p>
     * {@code force=true} bypasses the cancel-confirmation handshake with the
     * controller. The target stays in an unknown state until its next poll.
     */
    @DELETE
    @Path("{id}/actions/{actionId}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response cancelAction(@BeanParam RequestParams requestParams,
                          @QueryParam("realm") String realm,
                          @PathParam("id") String id,
                          @PathParam("actionId") Long actionId,
                          @QueryParam("force") Boolean force);
}
