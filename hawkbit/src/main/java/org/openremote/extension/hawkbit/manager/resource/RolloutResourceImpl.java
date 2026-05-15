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
import org.openremote.extension.hawkbit.model.resource.RolloutResource;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.model.http.RequestParams;

public class RolloutResourceImpl extends ManagerWebResource
        implements RolloutResource {

    protected final HawkbitFirmwareService hawkbitFirmwareService;

    public RolloutResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                               HawkbitFirmwareService hawkbitFirmwareService) {
        super(timerService, identityService);
        this.hawkbitFirmwareService = hawkbitFirmwareService;
    }

    @Override
    public Response getRollouts(RequestParams requestParams, Integer offset, Integer limit) {
        // Request full representation so totalTargetsPerStatus and totalGroups are populated; hawkBit defaults to compact.
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware rollouts",
                () -> hawkbitFirmwareService.rollouts().getRollouts(offset, limit, "full"));
    }

    @Override
    public Response getRollout(RequestParams requestParams, Long id) {
        return HawkbitResponseProxy.proxy("Failed to retrieve firmware rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().get(id));
    }

    @Override
    public Response createRollout(RequestParams requestParams, JsonNode rollout) {
        return HawkbitResponseProxy.proxy("Failed to create firmware rollout",
                () -> hawkbitFirmwareService.rollouts().create(rollout));
    }

    @Override
    public void deleteRollout(RequestParams requestParams, Long id) {
        HawkbitResponseProxy.proxy("Failed to delete firmware rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().delete(id));
    }

    @Override
    public Response startRollout(RequestParams requestParams, Long id) {
        return HawkbitResponseProxy.proxy("Failed to start firmware rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().start(id));
    }

    @Override
    public void pauseRollout(RequestParams requestParams, Long id) {
        HawkbitResponseProxy.proxy("Failed to pause firmware rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().pause(id));
    }

    @Override
    public Response getRolloutGroups(RequestParams requestParams, Long id,
                                     Integer offset, Integer limit) {
        return HawkbitResponseProxy.proxy("Failed to retrieve groups for rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().getRolloutGroups(id, offset, limit, "full"));
    }

    @Override
    public Response getRolloutGroup(RequestParams requestParams, Long id, Long groupId) {
        return HawkbitResponseProxy.proxy(
                "Failed to retrieve group '" + groupId + "' for rollout '" + id + "'",
                () -> hawkbitFirmwareService.rollouts().getRolloutGroup(id, groupId));
    }
}
