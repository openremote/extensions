package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareArtifacts;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModule;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModules;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("softwaremodules")
public interface HawkbitSoftwareModulesResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareSoftwareModule[] create(FirmwareSoftwareModule[] softwareModules);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModules getSoftwareModules(@QueryParam("offset") Integer offset,
                                               @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModule get(@PathParam("id") Long id);

    @GET
    @Path("{id}/artifacts")
    @Produces(APPLICATION_JSON)
    FirmwareArtifacts getArtifacts(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);

}
