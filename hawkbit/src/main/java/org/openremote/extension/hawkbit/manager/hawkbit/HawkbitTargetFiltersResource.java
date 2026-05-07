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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareAutoAssignRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueries;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQuery;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueryRequest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("targetfilters")
public interface HawkbitTargetFiltersResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareTargetFilterQueries getTargetFilters(@QueryParam("offset") Integer offset,
                                                  @QueryParam("limit") Integer limit);

    @GET
    @Path("{filterId}")
    @Produces(APPLICATION_JSON)
    FirmwareTargetFilterQuery get(@PathParam("filterId") Long filterId);

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareTargetFilterQuery create(FirmwareTargetFilterQueryRequest filter);

    @DELETE
    @Path("{filterId}")
    void delete(@PathParam("filterId") Long filterId);

    @GET
    @Path("{filterId}/autoAssignDS")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSet getAutoAssignDS(@PathParam("filterId") Long filterId);

    @POST
    @Path("{filterId}/autoAssignDS")
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareTargetFilterQuery setAutoAssignDS(@PathParam("filterId") Long filterId,
                                               FirmwareAutoAssignRequest request);

    @DELETE
    @Path("{filterId}/autoAssignDS")
    void deleteAutoAssignDS(@PathParam("filterId") Long filterId);
}
