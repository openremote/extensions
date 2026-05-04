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
import jakarta.ws.rs.core.EntityPart;

import org.openremote.model.Constants;
import org.openremote.model.http.RequestParams;

import java.util.List;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Tag(name = "Firmware Software Modules", description = "Management of firmware software modules")
@Path("firmware/softwaremodule")
public interface FirmwareSoftwareModuleResource {

    @POST
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareSoftwareModule createSoftwareModule(@BeanParam RequestParams requestParams,
                                                FirmwareSoftwareModule softwareModule);

    @GET
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModules getSoftwareModules(@BeanParam RequestParams requestParams,
                                               @QueryParam("offset") Integer offset,
                                               @QueryParam("limit") Integer limit);

    @GET
    @Path("{id}")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareSoftwareModule getSoftwareModule(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @GET
    @Path("{id}/artifacts")
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.READ_ADMIN_ROLE})
    FirmwareArtifacts getSoftwareModuleArtifacts(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

    @POST
    @Path("{id}/artifacts")
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    FirmwareArtifact uploadSoftwareModuleArtifact(@BeanParam RequestParams requestParams,
                                                  @PathParam("id") Long id,
                                                  @QueryParam("filename") String filename,
                                                  List<EntityPart> parts);

    @DELETE
    @Path("{id}")
    @RolesAllowed({Constants.WRITE_ADMIN_ROLE})
    void deleteSoftwareModule(@BeanParam RequestParams requestParams, @PathParam("id") Long id);

}
