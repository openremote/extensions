package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareAssignedAction {
    protected Long id;

    @JsonCreator
    protected FirmwareAssignedAction() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareAssignedAction setId(Long id) {
        this.id = id;
        return this;
    }
}
