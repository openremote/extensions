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

@Tag(name = "Firmware Distribution Sets", description = "Management of firmware distribution sets")
@Path("firmware/distributionset")
public interface FirmwareDistributionSetResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareDistributionSet createDistributionSet(@BeanParam RequestParams requestParams,
                                                  FirmwareDistributionSet distributionSet);

    @POST
    @Path("{id}/assign")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareDistributionSetAssignmentResult assignDistributionSet(@BeanParam RequestParams requestParams,
                                                                  @PathParam("id") Long id,
                                                                  FirmwareDistributionSetAssignment assignment);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSets getDistributionSets(@BeanParam RequestParams requestParams,
                                                 @QueryParam("offset") Integer offset,
                                                 @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSet getDistributionSet(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteDistributionSet(@BeanParam RequestParams requestParams, @PathParam("id") Long id);
}
