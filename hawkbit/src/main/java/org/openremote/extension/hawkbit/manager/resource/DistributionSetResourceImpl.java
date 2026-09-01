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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseProxy;
import org.openremote.extension.hawkbit.model.resource.DistributionSetResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.model.http.RequestParams;

public class DistributionSetResourceImpl extends HawkbitWebResource
        implements DistributionSetResource {

    public DistributionSetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                       HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService, hawkbitFirmwareService);
    }

    @Override
    public Response createDistributionSet(RequestParams requestParams,
                                          String realm,
                                          JsonNode distributionSet) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to create firmware distribution set",
                () -> hawkbitFirmwareService.distributionSets().create(distributionSet));
    }

    @Override
    public Response assignDistributionSet(RequestParams requestParams, String realm, Long id,
                                          Boolean offline,
                                          JsonNode targets) {
        requireHawkbitRealmAccess(realm);
        if (targets == null || !targets.isArray() || targets.isEmpty()) {
            throw new WebApplicationException("Assignment requires at least one target", Response.Status.BAD_REQUEST);
        }
        return HawkbitResponseProxy.proxy("Failed to assign firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().assignTargets(id, offline, targets));
    }

    @Override
    public Response getDistributionSets(RequestParams requestParams, String realm, Integer offset, Integer limit) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware distribution sets",
                () -> hawkbitFirmwareService.distributionSets().getDistributionSets(offset, limit));
    }

    @Override
    public Response getDistributionSet(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().get(id));
    }

    @Override
    public Response deleteDistributionSet(RequestParams requestParams, String realm, Long id) {
        requireHawkbitRealmAccess(realm);
        return HawkbitResponseProxy.proxy("Failed to delete firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().delete(id));
    }
}
