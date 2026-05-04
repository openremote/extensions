package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareArtifacts {
    protected List<FirmwareArtifact> content;

    protected FirmwareArtifacts() {
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public FirmwareArtifacts(List<FirmwareArtifact> content) {
        this.content = content;
    }

    public List<FirmwareArtifact> getContent() {
        return content;
    }

    public FirmwareArtifacts setContent(List<FirmwareArtifact> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return content == null ? 0 : content.size();
    }

    public FirmwareArtifacts setTotal(int total) {
        return this;
    }

    public int getSize() {
        return content == null ? 0 : content.size();
    }

    public FirmwareArtifacts setSize(int size) {
        return this;
    }
}
