package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareTargetFilterQueries {
    protected List<FirmwareTargetFilterQuery> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareTargetFilterQueries() {
    }

    public List<FirmwareTargetFilterQuery> getContent() {
        return content;
    }

    public FirmwareTargetFilterQueries setContent(List<FirmwareTargetFilterQuery> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareTargetFilterQueries setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareTargetFilterQueries setSize(int size) {
        this.size = size;
        return this;
    }
}
