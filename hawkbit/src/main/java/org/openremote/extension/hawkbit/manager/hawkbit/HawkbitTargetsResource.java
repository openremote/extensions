/*
 * Copyright 2025, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.extension.hawkbit.manager.hawkbit;

import jakarta.ws.rs.*;
import org.openremote.extension.hawkbit.model.firmware.FirmwareAction;
import org.openremote.extension.hawkbit.model.firmware.FirmwareActions;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareMetadataList;
import org.openremote.extension.hawkbit.model.firmware.FirmwareMetadataUpdate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTarget;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargets;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;


@Path("targets")
public interface HawkbitTargetsResource {
    @GET
    @Produces(APPLICATION_JSON)
    FirmwareTargets getTargets(@QueryParam("q") String query, @QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit);

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
    @Path("{id}/metadata")
    @Produces(APPLICATION_JSON)
    FirmwareMetadataList getMetadata(@PathParam("id") String id);

    @PUT
    @Path("{id}/metadata/{key}")
    @Consumes(APPLICATION_JSON)
    void updateMetadata(@PathParam("id") String id,
                        @PathParam("key") String key,
                        FirmwareMetadataUpdate metadata);

    @DELETE
    @Path("{id}/metadata/{key}")
    void deleteMetadata(@PathParam("id") String id, @PathParam("key") String key);

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
