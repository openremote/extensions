package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSets {
    protected List<FirmwareDistributionSet> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareDistributionSets() {
    }

    public List<FirmwareDistributionSet> getContent() {
        return content;
    }

    public FirmwareDistributionSets setContent(List<FirmwareDistributionSet> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareDistributionSets setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareDistributionSets setSize(int size) {
        this.size = size;
        return this;
    }
}
