package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareActions {
    protected List<FirmwareAction> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareActions() {
    }

    public List<FirmwareAction> getContent() {
        return content;
    }

    public FirmwareActions setContent(List<FirmwareAction> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareActions setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareActions setSize(int size) {
        this.size = size;
        return this;
    }
}
