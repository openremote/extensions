package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareTargets {
    protected List<FirmwareTarget> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareTargets() {
    }

    public List<FirmwareTarget> getContent() {
        return content;
    }

    public FirmwareTargets setContent(List<FirmwareTarget> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareTargets setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareTargets setSize(int size) {
        this.size = size;
        return this;
    }
}
