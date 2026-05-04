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

@Tag(name = "Firmware Distribution Set Types", description = "Management of firmware distribution set types")
@Path("firmware/distributionsettype")
public interface FirmwareDistributionSetTypeResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareDistributionSetType createDistributionSetType(@BeanParam RequestParams requestParams,
                                                          FirmwareDistributionSetType distributionSetType);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSetTypes getDistributionSetTypes(@BeanParam RequestParams requestParams,
                                                         @QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSetType getDistributionSetType(@BeanParam RequestParams requestParams,
                                                       @PathParam("id") Long id);

    @GET
    @Path("{id}/mandatorymoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModuleType[] getMandatoryModuleTypes(@BeanParam RequestParams requestParams,
                                                         @PathParam("id") Long id,
                                                         @QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/optionalmoduletypes")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModuleType[] getOptionalModuleTypes(@BeanParam RequestParams requestParams,
                                                        @PathParam("id") Long id,
                                                        @QueryParam("offset") Integer offset,
                                                        @QueryParam("limit") Integer limit);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteDistributionSetType(@BeanParam RequestParams requestParams, @PathParam("id") Long id);
}
