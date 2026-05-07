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
public class FirmwareRolloutRequest {
    protected String name;
    protected String description;
    protected String targetFilterQuery;
    protected Long distributionSetId;
    protected Integer amountGroups;
    protected Long forcetime;
    protected Long startAt;
    protected Integer weight;
    protected String type;
    protected FirmwareRolloutCondition successCondition;
    protected FirmwareRolloutAction successAction;
    protected FirmwareRolloutCondition errorCondition;
    protected FirmwareRolloutAction errorAction;
    protected Boolean confirmationRequired;

    @JsonCreator
    public FirmwareRolloutRequest() {
    }

    public String getName() {
        return name;
    }

    public FirmwareRolloutRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareRolloutRequest setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getTargetFilterQuery() {
        return targetFilterQuery;
    }

    public FirmwareRolloutRequest setTargetFilterQuery(String targetFilterQuery) {
        this.targetFilterQuery = targetFilterQuery;
        return this;
    }

    public Long getDistributionSetId() {
        return distributionSetId;
    }

    public FirmwareRolloutRequest setDistributionSetId(Long distributionSetId) {
        this.distributionSetId = distributionSetId;
        return this;
    }

    public Integer getAmountGroups() {
        return amountGroups;
    }

    public FirmwareRolloutRequest setAmountGroups(Integer amountGroups) {
        this.amountGroups = amountGroups;
        return this;
    }

    public Long getForcetime() {
        return forcetime;
    }

    public FirmwareRolloutRequest setForcetime(Long forcetime) {
        this.forcetime = forcetime;
        return this;
    }

    public Long getStartAt() {
        return startAt;
    }

    public FirmwareRolloutRequest setStartAt(Long startAt) {
        this.startAt = startAt;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareRolloutRequest setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareRolloutRequest setType(String type) {
        this.type = type;
        return this;
    }

    public FirmwareRolloutCondition getSuccessCondition() {
        return successCondition;
    }

    public FirmwareRolloutRequest setSuccessCondition(FirmwareRolloutCondition successCondition) {
        this.successCondition = successCondition;
        return this;
    }

    public FirmwareRolloutAction getSuccessAction() {
        return successAction;
    }

    public FirmwareRolloutRequest setSuccessAction(FirmwareRolloutAction successAction) {
        this.successAction = successAction;
        return this;
    }

    public FirmwareRolloutCondition getErrorCondition() {
        return errorCondition;
    }

    public FirmwareRolloutRequest setErrorCondition(FirmwareRolloutCondition errorCondition) {
        this.errorCondition = errorCondition;
        return this;
    }

    public FirmwareRolloutAction getErrorAction() {
        return errorAction;
    }

    public FirmwareRolloutRequest setErrorAction(FirmwareRolloutAction errorAction) {
        this.errorAction = errorAction;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareRolloutRequest setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }
}
