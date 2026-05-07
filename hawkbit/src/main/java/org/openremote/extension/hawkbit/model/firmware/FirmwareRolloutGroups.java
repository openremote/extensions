package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRolloutGroups {
    protected List<FirmwareRolloutGroup> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareRolloutGroups() {
    }

    public List<FirmwareRolloutGroup> getContent() {
        return content;
    }

    public FirmwareRolloutGroups setContent(List<FirmwareRolloutGroup> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareRolloutGroups setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareRolloutGroups setSize(int size) {
        this.size = size;
        return this;
    }
}
