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

import jakarta.ws.rs.core.Response;
import com.fasterxml.jackson.databind.JsonNode;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseProxy;
import org.openremote.extension.hawkbit.model.resource.SoftwareModuleTypeResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

public class SoftwareModuleTypeResourceImpl extends HawkbitWebResource
        implements SoftwareModuleTypeResource {

    public SoftwareModuleTypeResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                           HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response createSoftwareModuleType(RequestParams requestParams,
                                             String realm,
                                              JsonNode softwareModuleType) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to create firmware software module type",
                () -> hawkbitFirmwareService.softwareModuleTypes().create(softwareModuleType));
    }

    @Override
    public Response getSoftwareModuleTypes(RequestParams requestParams, String realm, Integer offset,
                                            Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware software module types",
                () -> hawkbitFirmwareService.softwareModuleTypes().getSoftwareModuleTypes(offset, limit));
    }

    @Override
    public Response getSoftwareModuleType(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware software module type '" + id + "'",
                () -> hawkbitFirmwareService.softwareModuleTypes().get(id));
    }

    @Override
    public void deleteSoftwareModuleType(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        HawkbitResponseProxy.proxy("Failed to delete firmware software module type '" + id + "'",
                () -> hawkbitFirmwareService.softwareModuleTypes().delete(id));
    }
}
