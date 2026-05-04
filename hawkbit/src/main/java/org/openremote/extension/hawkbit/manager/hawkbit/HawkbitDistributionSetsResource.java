package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetAssignmentResult;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetAssignment;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSets;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("distributionsets")
public interface HawkbitDistributionSetsResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareDistributionSet[] create(FirmwareDistributionSet[] distributionSets);

    @POST
    @Path("{id}/assignedTargets")
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareDistributionSetAssignmentResult assignTargets(@PathParam("id") Long id,
                                                          @QueryParam("offline") Boolean offline,
                                                          FirmwareTargetAssignment[] targets);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSets getDistributionSets(@QueryParam("offset") Integer offset,
                                                 @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSet get(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
