package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareArtifactLinks {
    protected FirmwareLink self;
    protected FirmwareLink download;

    @JsonCreator
    protected FirmwareArtifactLinks() {
    }

    public FirmwareLink getSelf() {
        return self;
    }

    public FirmwareArtifactLinks setSelf(FirmwareLink self) {
        this.self = self;
        return this;
    }

    public FirmwareLink getDownload() {
        return download;
    }

    public FirmwareArtifactLinks setDownload(FirmwareLink download) {
        this.download = download;
        return this;
    }
}
