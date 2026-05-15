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
package org.openremote.extension.hawkbit.manager;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public final class HawkbitResponseProxy {

    private HawkbitResponseProxy() {
    }

    /**
     * Executes a hawkBit call and wraps any unexpected failure as {@code BAD_GATEWAY}.
     * <p>
     * The upstream {@link Response} is copied, consumed, and closed by this call.
     */
    public static Response proxy(String errorMessage, HawkbitCall call) {
        try {
            return copyResponse(call.execute());
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(errorMessage, e, Response.Status.BAD_GATEWAY);
        }
    }

    private static Response copyResponse(Response response) {
        try (response) {
            Response.ResponseBuilder builder = Response.fromResponse(response);
            if (!response.hasEntity()) {
                return builder.build();
            }

            String body = response.readEntity(String.class);
            if (body.isBlank()) {
                return builder.entity(null).build();
            }
            return builder.entity(body).build();
        } catch (Exception e) {
            throw new WebApplicationException("Failed to copy hawkBit response", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    /**
     * Replaces {@link java.util.function.Supplier} to allow checked exceptions
     * to propagate without {@code UndeclaredThrowableException} wrapping.
     */
    @FunctionalInterface
    public interface HawkbitCall {
        Response execute() throws Exception;
    }
}
