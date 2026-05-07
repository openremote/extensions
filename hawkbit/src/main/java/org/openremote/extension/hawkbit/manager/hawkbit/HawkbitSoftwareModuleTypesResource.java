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
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleTypes;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("softwaremoduletypes")
public interface HawkbitSoftwareModuleTypesResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareSoftwareModuleType[] create(FirmwareSoftwareModuleType[] softwareModuleTypes);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleTypes getSoftwareModuleTypes(@QueryParam("offset") Integer offset,
                                                       @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType get(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
