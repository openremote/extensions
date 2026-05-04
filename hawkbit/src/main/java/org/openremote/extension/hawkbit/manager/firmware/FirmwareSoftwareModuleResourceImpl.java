package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareArtifact;
import org.openremote.extension.hawkbit.model.firmware.FirmwareArtifacts;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModule;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModuleResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareSoftwareModules;

import java.io.InputStream;
import java.util.List;

public class FirmwareSoftwareModuleResourceImpl extends ManagerWebResource
        implements FirmwareSoftwareModuleResource {

    protected final FirmwareService firmwareService;

    public FirmwareSoftwareModuleResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                              FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareSoftwareModule createSoftwareModule(RequestParams requestParams,
                                                       FirmwareSoftwareModule softwareModule) {
        try {
            FirmwareSoftwareModule[] created =
                    firmwareService.softwareModulesResource.create(new FirmwareSoftwareModule[] { softwareModule });
            return created != null && created.length > 0 ? created[0] : null;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware software module", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModules getSoftwareModules(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            return firmwareService.softwareModulesResource.getSoftwareModules(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software modules", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareSoftwareModule getSoftwareModule(RequestParams requestParams, Long id) {
        try {
            return firmwareService.softwareModulesResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware software module '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareArtifacts getSoftwareModuleArtifacts(RequestParams requestParams, Long id) {
        try {
            return firmwareService.softwareModulesResource.getArtifacts(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve artifacts for firmware software module '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @POST
    @Path("{id}/artifacts")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public FirmwareArtifact uploadSoftwareModuleArtifact(RequestParams requestParams,
                                                         @PathParam("id") Long id,
                                                         @QueryParam("filename") String filename,
                                                         List<EntityPart> parts) {
        try {
            EntityPart filePart = parts == null ? null : parts.stream()
                    .filter(part -> "file".equals(part.getName()))
                    .findFirst()
                    .orElse(null);
            if (filePart == null) {
                throw new WebApplicationException("Missing multipart field 'file'", Response.Status.BAD_REQUEST);
            }

            String submittedFileName = filePart.getFileName().orElse(null);
            InputStream inputStream = filePart.getContent(InputStream.class);
            return firmwareService.uploadSoftwareModuleArtifact(id, inputStream, submittedFileName, filename);
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to upload artifact for firmware software module '" + id + "'",
                    e, Response.Status.BAD_GATEWAY);
        }
    }

    @DELETE
    @Path("{id}")
    public void deleteSoftwareModule(RequestParams requestParams, @PathParam("id") Long id) {
        try {
            firmwareService.softwareModulesResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware software module '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
