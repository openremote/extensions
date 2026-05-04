package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareActionLinks {
    protected FirmwareLink distributionset;

    @JsonCreator
    protected FirmwareActionLinks() {
    }

    public FirmwareLink getDistributionset() {
        return distributionset;
    }

    public FirmwareActionLinks setDistributionset(FirmwareLink distributionset) {
        this.distributionset = distributionset;
        return this;
    }
}
