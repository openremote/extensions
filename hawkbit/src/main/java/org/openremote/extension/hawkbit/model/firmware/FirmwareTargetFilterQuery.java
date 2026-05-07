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
public class FirmwareTargetFilterQuery {
    protected Long id;
    protected String name;
    protected String query;
    protected Long autoAssignDistributionSet;
    protected String autoAssignActionType;
    protected Integer autoAssignWeight;
    protected Boolean confirmationRequired;

    @JsonCreator
    protected FirmwareTargetFilterQuery() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareTargetFilterQuery setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareTargetFilterQuery setName(String name) {
        this.name = name;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public FirmwareTargetFilterQuery setQuery(String query) {
        this.query = query;
        return this;
    }

    public Long getAutoAssignDistributionSet() {
        return autoAssignDistributionSet;
    }

    public FirmwareTargetFilterQuery setAutoAssignDistributionSet(Long autoAssignDistributionSet) {
        this.autoAssignDistributionSet = autoAssignDistributionSet;
        return this;
    }

    public String getAutoAssignActionType() {
        return autoAssignActionType;
    }

    public FirmwareTargetFilterQuery setAutoAssignActionType(String autoAssignActionType) {
        this.autoAssignActionType = autoAssignActionType;
        return this;
    }

    public Integer getAutoAssignWeight() {
        return autoAssignWeight;
    }

    public FirmwareTargetFilterQuery setAutoAssignWeight(Integer autoAssignWeight) {
        this.autoAssignWeight = autoAssignWeight;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareTargetFilterQuery setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }
}
