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

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareTargetAssignment {
    protected String id;
    protected Long forcetime;
    protected Integer weight;
    protected Boolean confirmationRequired;
    protected String type;
    protected FirmwareMaintenanceWindow maintenanceWindow;

    @JsonCreator
    public FirmwareTargetAssignment() {
    }

    public String getId() {
        return id;
    }

    public FirmwareTargetAssignment setId(String id) {
        this.id = id;
        return this;
    }

    public Long getForcetime() {
        return forcetime;
    }

    public FirmwareTargetAssignment setForcetime(Long forcetime) {
        this.forcetime = forcetime;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareTargetAssignment setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareTargetAssignment setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareTargetAssignment setType(String type) {
        this.type = type;
        return this;
    }

    public FirmwareMaintenanceWindow getMaintenanceWindow() {
        return maintenanceWindow;
    }

    public FirmwareTargetAssignment setMaintenanceWindow(FirmwareMaintenanceWindow maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
        return this;
    }
}
