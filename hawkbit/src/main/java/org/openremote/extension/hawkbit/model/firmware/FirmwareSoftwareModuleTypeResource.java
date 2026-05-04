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

@Tag(name = "Firmware Software Module Types", description = "Management of firmware software module types")
@Path("firmware/softwaremoduletype")
public interface FirmwareSoftwareModuleTypeResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareSoftwareModuleType createSoftwareModuleType(@BeanParam RequestParams requestParams,
                                                        FirmwareSoftwareModuleType softwareModuleType);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModuleTypes getSoftwareModuleTypes(@BeanParam RequestParams requestParams,
                                                       @QueryParam("offset") Integer offset,
                                                       @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModuleType getSoftwareModuleType(@BeanParam RequestParams requestParams,
                                                     @PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteSoftwareModuleType(@BeanParam RequestParams requestParams, @PathParam("id") Long id);
}
