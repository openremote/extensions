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
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypes;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("distributionsettypes")
public interface HawkbitDistributionSetTypesResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareDistributionSetType[] create(FirmwareDistributionSetType[] distributionSetTypes);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSetTypes getDistributionSetTypes(@QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSetType get(@PathParam("id") Long id);

    @GET
    @Path("{id}/mandatorymoduletypes")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType[] getMandatoryModuleTypes(@PathParam("id") Long id,
                                                         @QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/optionalmoduletypes")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType[] getOptionalModuleTypes(@PathParam("id") Long id,
                                                        @QueryParam("offset") Integer offset,
                                                        @QueryParam("limit") Integer limit);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
