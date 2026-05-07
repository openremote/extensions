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
package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSetAssignmentResult {
    protected Integer alreadyAssigned;
    protected Integer assigned;
    protected Integer total;
    protected List<FirmwareAssignedAction> assignedActions;

    @JsonCreator
    protected FirmwareDistributionSetAssignmentResult() {
    }

    public Integer getAlreadyAssigned() {
        return alreadyAssigned;
    }

    public FirmwareDistributionSetAssignmentResult setAlreadyAssigned(Integer alreadyAssigned) {
        this.alreadyAssigned = alreadyAssigned;
        return this;
    }

    public Integer getAssigned() {
        return assigned;
    }

    public FirmwareDistributionSetAssignmentResult setAssigned(Integer assigned) {
        this.assigned = assigned;
        return this;
    }

    public Integer getTotal() {
        return total;
    }

    public FirmwareDistributionSetAssignmentResult setTotal(Integer total) {
        this.total = total;
        return this;
    }

    public List<FirmwareAssignedAction> getAssignedActions() {
        return assignedActions;
    }

    public FirmwareDistributionSetAssignmentResult setAssignedActions(List<FirmwareAssignedAction> assignedActions) {
        this.assignedActions = assignedActions;
        return this;
    }
}
