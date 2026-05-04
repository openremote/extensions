package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.*;
import org.openremote.extension.hawkbit.model.firmware.FirmwareAction;
import org.openremote.extension.hawkbit.model.firmware.FirmwareActions;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTarget;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargets;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;


@Path("targets")
public interface HawkbitTargetsResource {
    @GET
    @Produces(APPLICATION_JSON)
    FirmwareTargets getTargets(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit);

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    FirmwareTarget[] create(FirmwareTarget[] targets);


    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareTarget get(@PathParam("id") String id);

    @PUT
    @Path("{id}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    FirmwareTarget update(@PathParam("id") String id, FirmwareTarget target);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") String id);

    @GET
    @Path("{id}/assignedDS")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSet getAssignedDs(@PathParam("id") String id);

    @GET
    @Path("{id}/installedDS")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSet getInstalledDs(@PathParam("id") String id);

    @GET
    @Path("{id}/actions")
    @Produces("application/hal+json")
    FirmwareActions getActions(@PathParam("id") String id,
                               @QueryParam("offset") Integer offset,
                               @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/actions/{actionId}")
    @Produces("application/hal+json")
    FirmwareAction getAction(@PathParam("id") String id, @PathParam("actionId") Long actionId);

    @DELETE
    @Path("{id}/actions/{actionId}")
    void cancelAction(@PathParam("id") String id,
                      @PathParam("actionId") Long actionId,
                      @QueryParam("force") Boolean force);
}
