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
