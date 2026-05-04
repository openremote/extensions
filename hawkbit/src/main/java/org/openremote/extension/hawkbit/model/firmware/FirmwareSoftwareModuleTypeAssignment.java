package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareSoftwareModuleTypeAssignment {
    protected Long id;

    @JsonCreator
    protected FirmwareSoftwareModuleTypeAssignment() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareSoftwareModuleTypeAssignment setId(Long id) {
        this.id = id;
        return this;
    }
}
