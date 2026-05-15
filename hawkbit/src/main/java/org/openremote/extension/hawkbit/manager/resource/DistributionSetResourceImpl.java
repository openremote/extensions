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

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.extension.hawkbit.manager.HawkbitFirmwareService;
import org.openremote.extension.hawkbit.manager.HawkbitResponseHandler;
import org.openremote.extension.hawkbit.model.hawkbit.DistributionSetAssignmentRequest;
import org.openremote.extension.hawkbit.model.hawkbit.DistributionSetCreateRequest;
import org.openremote.extension.hawkbit.model.resource.DistributionSetResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;

public class DistributionSetResourceImpl extends ManagerWebResource
        implements DistributionSetResource {

    protected final HawkbitFirmwareService hawkbitFirmwareService;

    public DistributionSetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                       HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService);
        this.hawkbitFirmwareService = hawkbitFirmwareService;
    }

    @Override
    public Response createDistributionSet(RequestParams requestParams,
                                          DistributionSetCreateRequest distributionSet) {
        return HawkbitResponseHandler.call("Failed to create firmware distribution set",
                () -> hawkbitFirmwareService.distributionSets().create(
                        new DistributionSetCreateRequest[]{distributionSet}));
    }

    @Override
    public Response assignDistributionSet(RequestParams requestParams, Long id,
                                          Boolean offline,
                                          DistributionSetAssignmentRequest[] targets) {
        if (targets == null || targets.length == 0) {
            throw new WebApplicationException("Assignment requires at least one target", Response.Status.BAD_REQUEST);
        }
        return HawkbitResponseHandler.call("Failed to assign firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().assignTargets(id, offline, targets));
    }

    @Override
    public Response getDistributionSets(RequestParams requestParams, Integer offset, Integer limit) {
        return HawkbitResponseHandler.call("Failed to retrieve firmware distribution sets",
                () -> hawkbitFirmwareService.distributionSets().getDistributionSets(offset, limit));
    }

    @Override
    public Response getDistributionSet(RequestParams requestParams, Long id) {
        return HawkbitResponseHandler.call("Failed to retrieve firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().get(id));
    }

    @Override
    public void deleteDistributionSet(RequestParams requestParams, Long id) {
        HawkbitResponseHandler.call("Failed to delete firmware distribution set '" + id + "'",
                () -> hawkbitFirmwareService.distributionSets().delete(id));
    }
}
