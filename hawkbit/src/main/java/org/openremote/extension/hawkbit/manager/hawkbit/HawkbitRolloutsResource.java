package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRollout;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutGroup;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutGroups;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRolloutRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareRollouts;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("rollouts")
public interface HawkbitRolloutsResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareRollouts getRollouts(@QueryParam("offset") Integer offset,
                                 @QueryParam("limit") Integer limit,
                                 @QueryParam("representation") String representation);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareRollout get(@PathParam("id") Long id);

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareRollout create(FirmwareRolloutRequest rollout);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);

    @POST
    @Path("{id}/start")
    @Produces(APPLICATION_HAL_JSON)
    FirmwareRollout start(@PathParam("id") Long id);

    @POST
    @Path("{id}/pause")
    void pause(@PathParam("id") Long id);

    @GET
    @Path("{id}/deploygroups")
    @Produces(APPLICATION_JSON)
    FirmwareRolloutGroups getRolloutGroups(@PathParam("id") Long id,
                                           @QueryParam("offset") Integer offset,
                                           @QueryParam("limit") Integer limit,
                                           @QueryParam("representation") String representation);

    @GET
    @Path("{id}/deploygroups/{groupId}")
    @Produces(APPLICATION_JSON)
    FirmwareRolloutGroup getRolloutGroup(@PathParam("id") Long id,
                                          @PathParam("groupId") Long groupId);
}
