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
package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.openremote.extension.hawkbit.model.hawkbit.MetadataUpdateRequest;
import org.openremote.extension.hawkbit.model.hawkbit.TargetCreateRequest;
import org.openremote.extension.hawkbit.model.hawkbit.TargetUpdateRequest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.openremote.extension.hawkbit.manager.hawkbit.HawkbitMediaType.APPLICATION_HAL_JSON;


@Path("targets")
public interface HawkbitTargetsClient {
    @GET
    @Produces(APPLICATION_JSON)
    Response getTargets(@QueryParam("q") String query, @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit);

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Response create(TargetCreateRequest[] targets);


    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    Response get(@PathParam("id") String id);

    @PUT
    @Path("{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    Response update(@PathParam("id") String id, TargetUpdateRequest target);

    @DELETE
    @Path("{id}")
    Response delete(@PathParam("id") String id);

    @GET
    @Path("{id}/metadata")
    @Produces(APPLICATION_JSON)
    Response getMetadata(@PathParam("id") String id);

    @PUT
    @Path("{id}/metadata/{key}")
    @Consumes(APPLICATION_JSON)
    Response updateMetadata(@PathParam("id") String id,
                            @PathParam("key") String key,
                            MetadataUpdateRequest metadata);

    @DELETE
    @Path("{id}/metadata/{key}")
    Response deleteMetadata(@PathParam("id") String id, @PathParam("key") String key);

    @GET
    @Path("{id}/assignedDS")
    @Produces(APPLICATION_JSON)
    Response getAssignedDs(@PathParam("id") String id);

    @GET
    @Path("{id}/installedDS")
    @Produces(APPLICATION_JSON)
    Response getInstalledDs(@PathParam("id") String id);

    @GET
    @Path("{id}/actions")
    @Produces(APPLICATION_HAL_JSON)
    Response getActions(@PathParam("id") String id,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/actions/{actionId}")
    @Produces(APPLICATION_HAL_JSON)
    Response getAction(@PathParam("id") String id, @PathParam("actionId") Long actionId);

    @GET
    @Path("{id}/actions/{actionId}/status")
    @Produces(APPLICATION_HAL_JSON)
    Response getActionStatus(@PathParam("id") String id,
                             @PathParam("actionId") Long actionId,
                             @QueryParam("offset") Integer offset,
                             @QueryParam("limit") Integer limit);

    @DELETE
    @Path("{id}/actions/{actionId}")
    Response cancelAction(@PathParam("id") String id,
                          @PathParam("actionId") Long actionId,
                          @QueryParam("force") Boolean force);
}
