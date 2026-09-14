/*
 * Copyright 2026, OpenRemote Inc.
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openremote.extension.ems.manager.distroenergy;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

import jakarta.ws.rs.*;
import org.openremote.extension.ems.manager.distroenergy.dto.DayAheadSubmission;

@Path("trader")
public interface DayAheadResource {

  @POST
  @Consumes({APPLICATION_JSON})
  @Path("{portfolio}/day-ahead/data")
  void postDayAhead(
      @PathParam("portfolio") String portfolio,
      @HeaderParam("x-client-key") String clientKey,
      DayAheadSubmission dayAheadSubmission);
}
