package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareLink {
    protected String href;
    protected String name;

    @JsonCreator
    protected FirmwareLink() {
    }

    public String getHref() {
        return href;
    }

    public FirmwareLink setHref(String href) {
        this.href = href;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareLink setName(String name) {
        this.name = name;
        return this;
    }
}
