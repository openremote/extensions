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

@Tag(name = "Firmware Distribution Set Types", description = "Management of firmware distribution set types")
@Path("firmware/distributionsettype")
public interface DistributionSetTypeResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createDistributionSetType(@BeanParam RequestParams requestParams,
                                       JsonNode distributionSetType);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSetTypes(@BeanParam RequestParams requestParams,
                                     @QueryParam("offset") Integer offset,
                                     @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSetType(@BeanParam RequestParams requestParams,
                                    @PathParam("id") Long id);

    @GET
    @Path("{id}/mandatorymoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getMandatoryModuleTypes(@BeanParam RequestParams requestParams,
                                     @PathParam("id") Long id,
                                     @QueryParam("offset") Integer offset,
                                     @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/optionalmoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getOptionalModuleTypes(@BeanParam RequestParams requestParams,
                                    @PathParam("id") Long id,
                                    @QueryParam("offset") Integer offset,
                                    @QueryParam("limit") Integer limit);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteDistributionSetType(@BeanParam RequestParams requestParams, @PathParam("id") Long id);
}
