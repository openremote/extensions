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

import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypeCreate;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetTypeResource;

public class FirmwareDistributionSetTypeResourceImpl extends ManagerWebResource
        implements FirmwareDistributionSetTypeResource {

    protected final FirmwareService firmwareService;

    public FirmwareDistributionSetTypeResourceImpl(TimerService timerService,
                                                   ManagerIdentityService identityService,
                                                   FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public Response createDistributionSetType(RequestParams requestParams,
                                              FirmwareDistributionSetTypeCreate distributionSetType) {
        return HawkbitResponse.proxy("Failed to create firmware distribution set type",
                () -> firmwareService.distributionSetTypesResource.create(
                        new FirmwareDistributionSetTypeCreate[] { distributionSetType })).asResource();
    }

    @Override
    public Response getDistributionSetTypes(RequestParams requestParams, Integer offset,
                                            Integer limit) {
        return HawkbitResponse.proxy("Failed to retrieve firmware distribution set types",
                () -> firmwareService.distributionSetTypesResource.getDistributionSetTypes(offset, limit)).asPage();
    }

    @Override
    public Response getDistributionSetType(RequestParams requestParams, Long id) {
        return HawkbitResponse.proxy("Failed to retrieve firmware distribution set type '" + id + "'",
                () -> firmwareService.distributionSetTypesResource.get(id)).asResource();
    }

    @Override
    public Response getMandatoryModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                            Integer limit) {
        return HawkbitResponse.proxy(
                "Failed to retrieve mandatory module types for firmware distribution set type '" + id + "'",
                () -> firmwareService.distributionSetTypesResource.getMandatoryModuleTypes(id, offset, limit)).asPage();
    }

    @Override
    public Response getOptionalModuleTypes(RequestParams requestParams, Long id, Integer offset,
                                           Integer limit) {
        return HawkbitResponse.proxy(
                "Failed to retrieve optional module types for firmware distribution set type '" + id + "'",
                () -> firmwareService.distributionSetTypesResource.getOptionalModuleTypes(id, offset, limit)).asPage();
    }

    @Override
    public void deleteDistributionSetType(RequestParams requestParams, Long id) {
        HawkbitResponse.proxy("Failed to delete firmware distribution set type '" + id + "'",
                () -> firmwareService.distributionSetTypesResource.delete(id));
    }
}
