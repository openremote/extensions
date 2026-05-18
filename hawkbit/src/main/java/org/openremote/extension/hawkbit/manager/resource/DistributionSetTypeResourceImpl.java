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
import org.openremote.extension.hawkbit.model.resource.DistributionSetTypeResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

public class DistributionSetTypeResourceImpl extends HawkbitWebResource
        implements DistributionSetTypeResource {

    public DistributionSetTypeResourceImpl(TimerService timerService,
                                            ManagerIdentityService identityService,
                                            HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response createDistributionSetType(RequestParams requestParams,
                                              String realm,
                                               JsonNode distributionSetType) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to create firmware distribution set type",
                () -> hawkbitFirmwareService.distributionSetTypes().create(distributionSetType));
    }

    @Override
    public Response getDistributionSetTypes(RequestParams requestParams, String realm, Integer offset,
                                             Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware distribution set types",
                () -> hawkbitFirmwareService.distributionSetTypes().getDistributionSetTypes(offset, limit));
    }

    @Override
    public Response getDistributionSetType(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware distribution set type '" + id + "'",
                () -> hawkbitFirmwareService.distributionSetTypes().get(id));
    }

    @Override
    public Response getMandatoryModuleTypes(RequestParams requestParams, String realm, Long id, Integer offset,
                                             Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to retrieve mandatory module types for firmware distribution set type '" + id + "'",
                () -> hawkbitFirmwareService.distributionSetTypes().getMandatoryModuleTypes(id, offset, limit));
    }

    @Override
    public Response getOptionalModuleTypes(RequestParams requestParams, String realm, Long id, Integer offset,
                                            Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy(
                "Failed to retrieve optional module types for firmware distribution set type '" + id + "'",
                () -> hawkbitFirmwareService.distributionSetTypes().getOptionalModuleTypes(id, offset, limit));
    }

    @Override
    public void deleteDistributionSetType(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        HawkbitResponseProxy.proxy("Failed to delete firmware distribution set type '" + id + "'",
                () -> hawkbitFirmwareService.distributionSetTypes().delete(id));
    }
}
