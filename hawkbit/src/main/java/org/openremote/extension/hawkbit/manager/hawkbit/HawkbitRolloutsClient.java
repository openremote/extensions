/*
 * Copyright 2025, OpenRemote Inc.
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openremote.extension.hawkbit.manager.hawkbit;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.openremote.extension.hawkbit.manager.hawkbit.HawkbitMediaType.APPLICATION_HAL_JSON;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("rollouts")
public interface HawkbitRolloutsClient {

  @GET
  @Produces(APPLICATION_JSON)
  Response getRollouts(
      @QueryParam("offset") Integer offset,
      @QueryParam("limit") Integer limit,
      @QueryParam("representation") String representation);

  @GET
  @Path("{id}")
  @Produces(APPLICATION_JSON)
  Response get(@PathParam("id") Long id);

  @POST
  @Consumes(APPLICATION_HAL_JSON)
  @Produces(APPLICATION_HAL_JSON)
  Response create(JsonNode rollout);

  @DELETE
  @Path("{id}")
  Response delete(@PathParam("id") Long id);

  @POST
  @Path("{id}/start")
  Response start(@PathParam("id") Long id);

  @POST
  @Path("{id}/pause")
  Response pause(@PathParam("id") Long id);

  @GET
  @Path("{id}/deploygroups")
  @Produces(APPLICATION_JSON)
  Response getRolloutGroups(
      @PathParam("id") Long id,
      @QueryParam("offset") Integer offset,
      @QueryParam("limit") Integer limit,
      @QueryParam("representation") String representation);

  @GET
  @Path("{id}/deploygroups/{groupId}")
  @Produces(APPLICATION_JSON)
  Response getRolloutGroup(@PathParam("id") Long id, @PathParam("groupId") Long groupId);
}
