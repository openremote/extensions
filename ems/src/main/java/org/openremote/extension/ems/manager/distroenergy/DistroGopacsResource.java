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
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrder;
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrderRequest;
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsSettlementPage;

/**
 * Distro Energy GOPACS Connector API. All timestamps are epoch seconds. A non-2xx response surfaces
 * as a {@link WebApplicationException} carrying the status, e.g. 423 for a locked order.
 */
@Path("/")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public interface DistroGopacsResource {

  /** 202 for a new order, 200 with the stored order when the reference already exists. */
  @POST
  @Path("gopacs/{tenantId}/orders")
  GopacsOrder createOrder(@PathParam("tenantId") String tenantId, GopacsOrderRequest request);

  @GET
  @Path("gopacs/{tenantId}/orders/{reference}")
  GopacsOrder getOrder(
      @PathParam("tenantId") String tenantId, @PathParam("reference") String reference);

  /** Only quantity, price, metadata and expiration may differ from the stored order. */
  @PUT
  @Path("gopacs/{tenantId}/orders/{reference}")
  GopacsOrder replaceOrder(
      @PathParam("tenantId") String tenantId,
      @PathParam("reference") String reference,
      GopacsOrderRequest request);

  /** Synchronous: returns only once Distro has confirmed the cancellation. */
  @DELETE
  @Path("gopacs/{tenantId}/orders/{reference}")
  void cancelOrder(
      @PathParam("tenantId") String tenantId, @PathParam("reference") String reference);

  /** Matched settlements overlapping [start, end), one per effective trade. */
  @GET
  @Path("settlements/gopacs/{tenantId}")
  GopacsSettlementPage listSettlements(
      @PathParam("tenantId") String tenantId,
      @QueryParam("start") long start,
      @QueryParam("end") long end,
      @QueryParam("orderReference") String orderReference);
}
