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
package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetAssignment;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetCreate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetResource;

public class FirmwareDistributionSetResourceImpl extends ManagerWebResource
        implements FirmwareDistributionSetResource {

    protected final FirmwareService firmwareService;

    public FirmwareDistributionSetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                               FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response createDistributionSet(RequestParams requestParams,
                                          FirmwareDistributionSetCreate distributionSet) {
        return HawkbitResponse.proxy("Failed to create firmware distribution set",
                () -> firmwareService.distributionSetsResource.create(
                        new FirmwareDistributionSetCreate[] { distributionSet })).asResource();
    }

    @Override
    public Response assignDistributionSet(RequestParams requestParams, Long id,
                                          Boolean offline,
                                          FirmwareDistributionSetAssignment[] targets) {
        if (targets == null || targets.length == 0) {
            throw new WebApplicationException("Assignment requires at least one target", Response.Status.BAD_REQUEST);
        }
        return HawkbitResponse.proxy("Failed to assign firmware distribution set '" + id + "'",
                () -> firmwareService.distributionSetsResource.assignTargets(id, offline, targets)).asResource();
    }

    @Override
    public Response getDistributionSets(RequestParams requestParams, Integer offset, Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve firmware distribution sets",
                () -> firmwareService.distributionSetsResource.getDistributionSets(offset, limit)).asPage();
    }

    @Override
    public Response getDistributionSet(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware distribution set '" + id + "'",
                () -> firmwareService.distributionSetsResource.get(id)).asResource();
    }

    @Override
    public void deleteDistributionSet(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to delete firmware distribution set '" + id + "'",
                () -> firmwareService.distributionSetsResource.delete(id));
    }
}
