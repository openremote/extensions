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
 * Proxies the hawkBit Management API target-filter endpoints.
 * <p>
 * Delegates to {@link org.openremote.extension.hawkbit.manager.hawkbit.HawkbitTargetFiltersClient}
 * and returns the upstream response body unchanged.
 */
@Tag(name = "Firmware Target Filters", description = "Management of firmware target filter queries")
@Path("firmware/targetfilter")
public interface TargetFilterResource {

    /**
     * Retrieve all target filter queries, paged.
     */
    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTargetFilters(@BeanParam RequestParams requestParams,
                              @QueryParam("realm") String realm,
                              @QueryParam("offset") Integer offset,
                              @QueryParam("limit") Integer limit);

    /**
     * Retrieve a single target filter query by id.
     */
    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getTargetFilter(@BeanParam RequestParams requestParams,
                             @QueryParam("realm") String realm,
                             @PathParam("id") Long id);

    /**
     * Create a target filter query. Body matches hawkBit's TargetFilterQuery create payload.
     */
    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response createTargetFilter(@BeanParam RequestParams requestParams,
                                @QueryParam("realm") String realm,
                                JsonNode filter);

    /**
     * Delete a target filter query.
     */
    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteTargetFilter(@BeanParam RequestParams requestParams,
                                @QueryParam("realm") String realm,
                                @PathParam("id") Long id);

    /**
     * Retrieve the auto-assign distribution set configured for a target filter.
     */
    @GET
    @Path("{id}/autoAssignDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    Response getAutoAssignDS(@BeanParam RequestParams requestParams,
                             @QueryParam("realm") String realm,
                             @PathParam("id") Long id);

    /**
     * Configure the auto-assign distribution set for a target filter.
     * <p>
     * Body matches hawkBit's {@code AutoAssignDistributionSetRequest} shape
     * (distribution-set {@code id} plus optional {@code actionType}).
     */
    @POST
    @Path("{id}/autoAssignDS")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response setAutoAssignDS(@BeanParam RequestParams requestParams,
                             @QueryParam("realm") String realm,
                             @PathParam("id") Long id,
                             JsonNode request);

    /**
     * Remove the auto-assign distribution set from a target filter.
     */
    @DELETE
    @Path("{id}/autoAssignDS")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    Response deleteAutoAssignDS(@BeanParam RequestParams requestParams,
                                @QueryParam("realm") String realm,
                                @PathParam("id") Long id);
}
