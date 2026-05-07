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

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetAssignment;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetAssignmentResult;
import org.openremote.model.http.RequestParams;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSetResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSets;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetAssignment;

import java.util.List;

public class FirmwareDistributionSetResourceImpl extends ManagerWebResource
        implements FirmwareDistributionSetResource {

    protected final FirmwareService firmwareService;

    public FirmwareDistributionSetResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                               FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareDistributionSet createDistributionSet(RequestParams requestParams,
                                                         FirmwareDistributionSet distributionSet) {
        try {
            FirmwareDistributionSet[] created =
                    firmwareService.distributionSetsResource.create(new FirmwareDistributionSet[] { distributionSet });
            return created != null && created.length > 0 ? created[0] : null;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware distribution set", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSetAssignmentResult assignDistributionSet(RequestParams requestParams, Long id,
                                                                        FirmwareDistributionSetAssignment assignment) {
        try {
            if (assignment == null || assignment.getTargets() == null || assignment.getTargets().isEmpty()) {
                throw new WebApplicationException("Assignment requires at least one target", Response.Status.BAD_REQUEST);
            }

            List<FirmwareTargetAssignment> targets = assignment.getTargets().stream()
                    .map(target -> new FirmwareTargetAssignment()
                            .setId(target.getId())
                            .setForcetime(target.getForcetime() != null ? target.getForcetime() : assignment.getForcetime())
                            .setWeight(target.getWeight() != null ? target.getWeight() : assignment.getWeight())
                            .setConfirmationRequired(target.getConfirmationRequired() != null
                                    ? target.getConfirmationRequired()
                                    : assignment.getConfirmationRequired())
                            .setType(target.getType() != null ? target.getType() : assignment.getType())
                            .setMaintenanceWindow(target.getMaintenanceWindow() != null
                                    ? target.getMaintenanceWindow()
                                    : assignment.getMaintenanceWindow()))
                    .toList();

            return firmwareService.distributionSetsResource.assignTargets(id, assignment.getOffline(),
                    targets.toArray(FirmwareTargetAssignment[]::new));
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException("Failed to assign firmware distribution set '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSets getDistributionSets(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            return firmwareService.distributionSetsResource.getDistributionSets(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution sets", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSet getDistributionSet(RequestParams requestParams, Long id) {
        try {
            return firmwareService.distributionSetsResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware distribution set '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @DELETE
    @Path("{id}")
    public void deleteDistributionSet(RequestParams requestParams, @PathParam("id") Long id) {
        try {
            firmwareService.distributionSetsResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware distribution set '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
