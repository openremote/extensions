package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareAutoAssignRequest {
    protected Long id;
    protected String type;
    protected Integer weight;
    protected Boolean confirmationRequired;

    @JsonCreator
    public FirmwareAutoAssignRequest() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareAutoAssignRequest setId(Long id) {
        this.id = id;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareAutoAssignRequest setType(String type) {
        this.type = type;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareAutoAssignRequest setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public FirmwareAutoAssignRequest setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }
}
