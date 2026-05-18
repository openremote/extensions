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
package org.openremote.extension.hawkbit.manager.resource;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;

import java.util.Objects;

/**
 * Base resource for hawkBit Management API proxy endpoints.
 * <p>
 * The request path realm remains the OpenRemote authentication realm. The hawkBit target realm is supplied explicitly by
 * each endpoint, matching manager APIs that allow superusers authenticated on {@code master} to operate on another realm.
 */
public abstract class HawkbitWebResource extends ManagerWebResource {

    protected final HawkbitFirmwareService hawkbitFirmwareService;

    protected HawkbitWebResource(TimerService timerService, ManagerIdentityService identityService,
                                  HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService);
        this.hawkbitFirmwareService = hawkbitFirmwareService;
    }

    /**
     * Verifies that the requested hawkBit realm is present, accessible to the caller, and is the realm configured for
     * this hawkBit integration.
     *
     * @param realm target OpenRemote realm for firmware management
     * @throws BadRequestException if no target realm was supplied
     * @throws ForbiddenException if the caller cannot access the realm, or hawkBit is not configured for that realm
     */
    protected void requireHawkbitRealmAccess(String realm) {
        if (realm == null || realm.isBlank()) {
            throw new BadRequestException("Firmware realm is required");
        }
        if (!isRealmActiveAndAccessible(realm)) {
            throw new ForbiddenException("Realm '" + realm + "' is nonexistent, inactive or inaccessible");
        }
        if (!Objects.equals(realm, hawkbitFirmwareService.getRealm())) {
            throw new ForbiddenException("Firmware management is not available for this realm");
        }
    }
}
