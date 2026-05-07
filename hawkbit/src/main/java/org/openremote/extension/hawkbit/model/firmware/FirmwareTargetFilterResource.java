package org.openremote.extension.hawkbit.model.firmware;

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

import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Tag(name = "Firmware Target Filters", description = "Management of firmware target filter queries")
@Path("firmware/targetfilter")
public interface FirmwareTargetFilterResource {

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareTargetFilterQueries getTargetFilters(@BeanParam RequestParams requestParams,
                                                  @QueryParam("offset") Integer offset,
                                                  @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareTargetFilterQuery getTargetFilter(@BeanParam RequestParams requestParams,
                                               @PathParam("id") Long id);

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareTargetFilterQuery createTargetFilter(@BeanParam RequestParams requestParams,
                                                  FirmwareTargetFilterQueryRequest filter);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteTargetFilter(@BeanParam RequestParams requestParams,
                             @PathParam("id") Long id);

    @GET
    @Path("{id}/autoAssignDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSet getAutoAssignDS(@BeanParam RequestParams requestParams,
                                             @PathParam("id") Long id);

    @POST
    @Path("{id}/autoAssignDS")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareTargetFilterQuery setAutoAssignDS(@BeanParam RequestParams requestParams,
                                               @PathParam("id") Long id,
                                               FirmwareAutoAssignRequest request);

    @DELETE
    @Path("{id}/autoAssignDS")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteAutoAssignDS(@BeanParam RequestParams requestParams,
                             @PathParam("id") Long id);
}
