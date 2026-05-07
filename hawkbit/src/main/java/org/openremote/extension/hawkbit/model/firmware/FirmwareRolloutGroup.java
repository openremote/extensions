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

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRolloutGroup {
    protected Long id;
    protected String name;
    protected String description;
    protected FirmwareRolloutCondition successCondition;
    protected FirmwareRolloutAction successAction;
    protected FirmwareRolloutCondition errorCondition;
    protected FirmwareRolloutAction errorAction;
    protected String targetFilterQuery;
    protected Integer targetPercentage;
    protected Boolean confirmationRequired;
    protected String status;
    protected Long totalTargets;
    protected Map<String, Long> totalTargetsPerStatus;

    @JsonCreator
    protected FirmwareRolloutGroup() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareRolloutGroup setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareRolloutGroup setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareRolloutGroup setDescription(String description) {
        this.description = description;
        return this;
    }

    public FirmwareRolloutCondition getSuccessCondition() {
        return successCondition;
    }

    public FirmwareRolloutGroup setSuccessCondition(FirmwareRolloutCondition successCondition) {
        this.successCondition = successCondition;
        return this;
    }

    public FirmwareRolloutAction getSuccessAction() {
        return successAction;
    }

    public FirmwareRolloutGroup setSuccessAction(FirmwareRolloutAction successAction) {
        this.successAction = successAction;
        return this;
    }

    public FirmwareRolloutCondition getErrorCondition() {
        return errorCondition;
    }

    public FirmwareRolloutGroup setErrorCondition(FirmwareRolloutCondition errorCondition) {
        this.errorCondition = errorCondition;
        return this;
    }

    public FirmwareRolloutAction getErrorAction() {
        return errorAction;
    }

    public FirmwareRolloutGroup setErrorAction(FirmwareRolloutAction errorAction) {
        this.errorAction = errorAction;
        return this;
    }

    public String getTargetFilterQuery() {
        return targetFilterQuery;
    }

    public FirmwareRolloutGroup setTargetFilterQuery(String targetFilterQuery) {
        this.targetFilterQuery = targetFilterQuery;
        return this;
    }

    public Integer getTargetPercentage() {
        return targetPercentage;
    }

    public FirmwareRolloutGroup setTargetPercentage(Integer targetPercentage) {
        this.targetPercentage = targetPercentage;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareRolloutGroup setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public FirmwareRolloutGroup setStatus(String status) {
        this.status = status;
        return this;
    }

    public Long getTotalTargets() {
        return totalTargets;
    }

    public FirmwareRolloutGroup setTotalTargets(Long totalTargets) {
        this.totalTargets = totalTargets;
        return this;
    }

    public Map<String, Long> getTotalTargetsPerStatus() {
        return totalTargetsPerStatus;
    }

    public FirmwareRolloutGroup setTotalTargetsPerStatus(Map<String, Long> totalTargetsPerStatus) {
        this.totalTargetsPerStatus = totalTargetsPerStatus;
        return this;
    }
}
