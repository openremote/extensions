/*
 * Copyright 2026, OpenRemote Inc.
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
package org.openremote.extension.ems.manager.gopacs;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

/**
 * RESTEasy client proxy for the GOPACS machine announcements endpoint.
 * No authentication required (public API).
 */
@Path("/")
@Produces(APPLICATION_JSON)
public interface GOPACSAnnouncementResource {

    @GET
    @Path("machineannouncements")
    Response fetchAnnouncements(
            @QueryParam("postalcode") String postalCode,
            @QueryParam("starttime") String startTime,
            @QueryParam("endtime") String endTime,
            @QueryParam("type") String type,
            @QueryParam("state") String state
    );
}
