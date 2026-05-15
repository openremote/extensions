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
import org.openremote.extension.hawkbit.model.hawkbit.SoftwareModuleTypeCreateRequest;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.openremote.extension.hawkbit.manager.hawkbit.HawkbitMediaType.APPLICATION_HAL_JSON;

@Path("softwaremoduletypes")
public interface HawkbitSoftwareModuleTypesClient {

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    Response create(SoftwareModuleTypeCreateRequest[] softwareModuleTypes);

    @GET
    @Produces(APPLICATION_JSON)
    Response getSoftwareModuleTypes(@QueryParam("offset") Integer offset,
                                    @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    Response get(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    Response delete(@PathParam("id") Long id);
}
