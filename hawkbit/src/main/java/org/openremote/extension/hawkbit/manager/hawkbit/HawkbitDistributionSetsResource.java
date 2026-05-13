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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.openremote.extension.hawkbit.manager.hawkbit.HawkbitMediaType.APPLICATION_HAL_JSON;

@Path("distributionsets")
public interface HawkbitDistributionSetsResource {

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    Response create(JsonNode distributionSets);

    @POST
    @Path("{id}/assignedTargets")
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    Response assignTargets(@PathParam("id") Long id,
                           @QueryParam("offline") Boolean offline,
                           JsonNode targets);

    @GET
    @Produces(APPLICATION_JSON)
    Response getDistributionSets(@QueryParam("offset") Integer offset,
                                 @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    Response get(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
