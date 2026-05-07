package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRollout {
    protected Long id;
    protected String name;
    protected String description;
    protected String targetFilterQuery;
    protected Long distributionSetId;
    protected String status;
    protected Long totalTargets;
    protected Map<String, Long> totalTargetsPerStatus;
    protected Integer totalGroups;
    protected Long startAt;
    protected Long forcetime;
    protected Boolean deleted;
    protected String type;
    protected String createdBy;
    protected Long createdAt;
    protected String lastModifiedBy;
    protected Long lastModifiedAt;
    protected Integer weight;
    protected Boolean confirmationRequired;
    protected FirmwareRolloutCondition successCondition;
    protected FirmwareRolloutAction successAction;
    protected FirmwareRolloutCondition errorCondition;
    protected FirmwareRolloutAction errorAction;

    @JsonCreator
    protected FirmwareRollout() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareRollout setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareRollout setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareRollout setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getTargetFilterQuery() {
        return targetFilterQuery;
    }

    public FirmwareRollout setTargetFilterQuery(String targetFilterQuery) {
        this.targetFilterQuery = targetFilterQuery;
        return this;
    }

    public Long getDistributionSetId() {
        return distributionSetId;
    }

    public FirmwareRollout setDistributionSetId(Long distributionSetId) {
        this.distributionSetId = distributionSetId;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public FirmwareRollout setStatus(String status) {
        this.status = status;
        return this;
    }

    public Long getTotalTargets() {
        return totalTargets;
    }

    public FirmwareRollout setTotalTargets(Long totalTargets) {
        this.totalTargets = totalTargets;
        return this;
    }

    public Map<String, Long> getTotalTargetsPerStatus() {
        return totalTargetsPerStatus;
    }

    public FirmwareRollout setTotalTargetsPerStatus(Map<String, Long> totalTargetsPerStatus) {
        this.totalTargetsPerStatus = totalTargetsPerStatus;
        return this;
    }

    public Integer getTotalGroups() {
        return totalGroups;
    }

    public FirmwareRollout setTotalGroups(Integer totalGroups) {
        this.totalGroups = totalGroups;
        return this;
    }

    public Long getStartAt() {
        return startAt;
    }

    public FirmwareRollout setStartAt(Long startAt) {
        this.startAt = startAt;
        return this;
    }

    public Long getForcetime() {
        return forcetime;
    }

    public FirmwareRollout setForcetime(Long forcetime) {
        this.forcetime = forcetime;
        return this;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public FirmwareRollout setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareRollout setType(String type) {
        this.type = type;
        return this;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public FirmwareRollout setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
        return this;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public FirmwareRollout setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public FirmwareRollout setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
        return this;
    }

    public Long getLastModifiedAt() {
        return lastModifiedAt;
    }

    public FirmwareRollout setLastModifiedAt(Long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareRollout setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareRollout setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }

    public FirmwareRolloutCondition getSuccessCondition() {
        return successCondition;
    }

    public FirmwareRollout setSuccessCondition(FirmwareRolloutCondition successCondition) {
        this.successCondition = successCondition;
        return this;
    }

    public FirmwareRolloutAction getSuccessAction() {
        return successAction;
    }

    public FirmwareRollout setSuccessAction(FirmwareRolloutAction successAction) {
        this.successAction = successAction;
        return this;
    }

    public FirmwareRolloutCondition getErrorCondition() {
        return errorCondition;
    }

    public FirmwareRollout setErrorCondition(FirmwareRolloutCondition errorCondition) {
        this.errorCondition = errorCondition;
        return this;
    }

    public FirmwareRolloutAction getErrorAction() {
        return errorAction;
    }

    public FirmwareRollout setErrorAction(FirmwareRolloutAction errorAction) {
        this.errorAction = errorAction;
        return this;
    }
}
