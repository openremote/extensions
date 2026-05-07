package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRollouts {
    protected List<FirmwareRollout> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareRollouts() {
    }

    public List<FirmwareRollout> getContent() {
        return content;
    }

    public FirmwareRollouts setContent(List<FirmwareRollout> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareRollouts setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareRollouts setSize(int size) {
        this.size = size;
        return this;
    }
}
