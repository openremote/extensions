package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetType;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypes;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleType;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("distributionsettypes")
public interface HawkbitDistributionSetTypesResource {

    String APPLICATION_HAL_JSON = "application/hal+json";

    @POST
    @Consumes(APPLICATION_HAL_JSON)
    @Produces(APPLICATION_HAL_JSON)
    FirmwareDistributionSetType[] create(FirmwareDistributionSetType[] distributionSetTypes);

    @GET
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSetTypes getDistributionSetTypes(@QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    FirmwareDistributionSetType get(@PathParam("id") Long id);

    @GET
    @Path("{id}/mandatorymoduletypes")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType[] getMandatoryModuleTypes(@PathParam("id") Long id,
                                                         @QueryParam("offset") Integer offset,
                                                         @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}/optionalmoduletypes")
    @Produces(APPLICATION_JSON)
    FirmwareSoftwareModuleType[] getOptionalModuleTypes(@PathParam("id") Long id,
                                                        @QueryParam("offset") Integer offset,
                                                        @QueryParam("limit") Integer limit);

    @DELETE
    @Path("{id}")
    void delete(@PathParam("id") Long id);
}
