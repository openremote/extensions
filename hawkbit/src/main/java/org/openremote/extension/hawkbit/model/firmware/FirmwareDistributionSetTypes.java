package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSetTypes {
    protected List<FirmwareDistributionSetType> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareDistributionSetTypes() {
    }

    public List<FirmwareDistributionSetType> getContent() {
        return content;
    }

    public FirmwareDistributionSetTypes setContent(List<FirmwareDistributionSetType> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareDistributionSetTypes setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareDistributionSetTypes setSize(int size) {
        this.size = size;
        return this;
    }
}
