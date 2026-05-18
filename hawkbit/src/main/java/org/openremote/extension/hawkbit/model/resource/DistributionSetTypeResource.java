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
 * Proxies the hawkBit Management API distribution-set-type endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitDistributionSetTypesClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Distribution Set Types", description = "Management of firmware distribution set types")
@Path("firmware/distributionsettype")
public interface DistributionSetTypeResource {

    /** Create a distribution-set type. Body is a hawkBit DistributionSetType create payload. */
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createDistributionSetType(@BeanParam RequestParams requestParams,
                                       @QueryParam("realm") String realm,
                                        JsonNode distributionSetType);

    /** Retrieve all distribution-set types, paged. */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSetTypes(@BeanParam RequestParams requestParams,
                                     @QueryParam("realm") String realm,
                                      @QueryParam("offset") Integer offset,
                                      @QueryParam("limit") Integer limit);

    /** Retrieve a single distribution-set type by id. */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSetType(@BeanParam RequestParams requestParams,
                                    @QueryParam("realm") String realm,
                                     @PathParam("id") Long id);

    /**
     * Retrieve the module types every DS of this type must include.
     * <p>
     * Describes the composition contract of the DS type, not the modules of any
     * specific distribution set.
     */
    @GET
    @Path("{id}/mandatorymoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getMandatoryModuleTypes(@BeanParam RequestParams requestParams,
                                     @QueryParam("realm") String realm,
                                      @PathParam("id") Long id,
                                      @QueryParam("offset") Integer offset,
                                      @QueryParam("limit") Integer limit);

    /**
     * Retrieve the module types that a DS of this type may optionally include.
     * <p>
     * Describes the <em>composition contract</em> of the DS type, not the modules
     * of any specific distribution set. See {@link #getMandatoryModuleTypes}.
     */
    @GET
    @Path("{id}/optionalmoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getOptionalModuleTypes(@BeanParam RequestParams requestParams,
                                    @QueryParam("realm") String realm,
                                     @PathParam("id") Long id,
                                     @QueryParam("offset") Integer offset,
                                     @QueryParam("limit") Integer limit);

    /** Delete a distribution-set type. */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteDistributionSetType(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") Long id);
}
