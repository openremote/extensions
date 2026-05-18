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
 * Proxies the hawkBit Management API distribution-set endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitDistributionSetsClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Distribution Sets", description = "Management of firmware distribution sets")
@Path("firmware/distributionset")
public interface DistributionSetResource {

    /**
     * Create a distribution set. Body is a hawkBit DistributionSet create payload.
     */
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createDistributionSet(@BeanParam RequestParams requestParams,
                                   @QueryParam("realm") String realm,
                                   JsonNode distributionSet);

    /**
     * Assign a distribution set to one or more firmware targets.
     * <p>
     * {@code targets} must be a non-empty JSON array. Empty or non-array bodies
     * return {@code 400}.
     * <p>
     * {@code offline=true} records the assignment as already-installed and skips
     * the controller download step. Use this when the target was flashed out-of-band.
     */
    @POST
    @Path("{id}/assign")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response assignDistributionSet(@BeanParam RequestParams requestParams,
                                   @QueryParam("realm") String realm,
                                   @PathParam("id") Long id,
                                   @QueryParam("offline") Boolean offline,
                                   JsonNode targets);

    /**
     * Retrieve all distribution sets, paged.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSets(@BeanParam RequestParams requestParams,
                                 @QueryParam("realm") String realm,
                                 @QueryParam("offset") Integer offset,
                                 @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single distribution set by id.
     */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getDistributionSet(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") Long id);

    /**
     * Delete a distribution set.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteDistributionSet(@BeanParam RequestParams requestParams, @QueryParam("realm") String realm, @PathParam("id") Long id);
}
