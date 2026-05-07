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
