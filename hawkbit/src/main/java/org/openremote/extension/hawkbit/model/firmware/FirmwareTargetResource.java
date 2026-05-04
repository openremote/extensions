package org.openremote.extension.hawkbit.model.firmware;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Firmware Targets", description = "Management of firmware targets")
@Path("firmware/target")
public interface FirmwareTargetResource {

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareTargets getTargets(@BeanParam RequestParams requestParams,
                               @QueryParam("offset") Integer offset,
                               @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareTarget getTarget(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/assignedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSet getAssignedDs(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/installedDS")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareDistributionSet getInstalledDs(@BeanParam RequestParams requestParams, @PathParam("id") String id);

    @GET
    @Path("{id}/actions")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareActions getActions(@BeanParam RequestParams requestParams,
                               @PathParam("id") String id,
                               @QueryParam("offset") Integer offset,
                               @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/actions/{actionId}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareAction getAction(@BeanParam RequestParams requestParams,
                             @PathParam("id") String id,
                             @PathParam("actionId") Long actionId);

    @DELETE
    @Path("{id}/actions/{actionId}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void cancelAction(@BeanParam RequestParams requestParams,
                      @PathParam("id") String id,
                      @PathParam("actionId") Long actionId,
                      @QueryParam("force") Boolean force);
}
