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

/**
 * Proxies the hawkBit Management API software-module-type endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitSoftwareModuleTypesClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Software Module Types", description = "Management of firmware software module types")
@Path("firmware/softwaremoduletype")
public interface SoftwareModuleTypeResource {

    /**
     * Create a software-module type. Body matches hawkBit's SoftwareModuleType create payload.
     */
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createSoftwareModuleType(@BeanParam RequestParams requestParams,
                                      @QueryParam("realm") String realm,
                                      JsonNode softwareModuleType);

    /**
     * Retrieve all software-module types, paged.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getSoftwareModuleTypes(@BeanParam RequestParams requestParams,
                                    @QueryParam("realm") String realm,
                                    @QueryParam("offset") Integer offset,
                                    @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single software-module type by id.
     */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getSoftwareModuleType(@BeanParam RequestParams requestParams,
                                   @QueryParam("realm") String realm,
                                   @PathParam("id") Long id);

    /**
     * Delete a software-module type.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteSoftwareModuleType(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") Long id);
}
