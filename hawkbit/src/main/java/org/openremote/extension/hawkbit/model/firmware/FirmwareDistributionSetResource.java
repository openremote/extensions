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
package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Firmware Distribution Sets", description = "Management of firmware distribution sets")
@Path("firmware/distributionset")
public interface FirmwareDistributionSetResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createDistributionSet(@BeanParam RequestParams requestParams,
                                   JsonNode distributionSet);

    @POST
    @Path("{id}/assign")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response assignDistributionSet(@BeanParam RequestParams requestParams,
                                   @PathParam("id") Long id,
                                   @QueryParam("offline") Boolean offline,
                                   JsonNode targets);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSets(@BeanParam RequestParams requestParams,
                                 @QueryParam("offset") Integer offset,
                                 @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSet(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteDistributionSet(@BeanParam RequestParams requestParams, @PathParam("id") Long id);
}
