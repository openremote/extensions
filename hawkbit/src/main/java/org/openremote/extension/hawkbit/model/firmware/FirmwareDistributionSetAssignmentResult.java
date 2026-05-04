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
