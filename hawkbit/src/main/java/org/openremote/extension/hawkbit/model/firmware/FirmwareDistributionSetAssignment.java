package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSetAssignment {
    protected List<FirmwareTargetAssignment> targets;
    protected Boolean offline;
    protected Long forcetime;
    protected Integer weight;
    protected Boolean confirmationRequired;
    protected String type;
    protected FirmwareMaintenanceWindow maintenanceWindow;

    @JsonCreator
    protected FirmwareDistributionSetAssignment() {
    }

    public List<FirmwareTargetAssignment> getTargets() {
        return targets;
    }

    public FirmwareDistributionSetAssignment setTargets(List<FirmwareTargetAssignment> targets) {
        this.targets = targets;
        return this;
    }

    public Boolean getOffline() {
        return offline;
    }

    public FirmwareDistributionSetAssignment setOffline(Boolean offline) {
        this.offline = offline;
        return this;
    }

    public Long getForcetime() {
        return forcetime;
    }

    public FirmwareDistributionSetAssignment setForcetime(Long forcetime) {
        this.forcetime = forcetime;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareDistributionSetAssignment setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareDistributionSetAssignment setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareDistributionSetAssignment setType(String type) {
        this.type = type;
        return this;
    }

    public FirmwareMaintenanceWindow getMaintenanceWindow() {
        return maintenanceWindow;
    }

    public FirmwareDistributionSetAssignment setMaintenanceWindow(FirmwareMaintenanceWindow maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
        return this;
    }
}
