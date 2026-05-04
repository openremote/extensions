package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleTypes;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("softwaremoduletypes")
public interface HawkbitSoftwareModuleTypesResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareSoftwareModuleType[] create(FirmwareSoftwareModuleType[] softwareModuleTypes);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleTypes getSoftwareModuleTypes(@QueryParam("offset") Integer offset,
                                                       @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType get(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
