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
