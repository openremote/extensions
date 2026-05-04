package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareSoftwareModuleTypes {
    protected List<FirmwareSoftwareModuleType> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareSoftwareModuleTypes() {
    }

    public List<FirmwareSoftwareModuleType> getContent() {
        return content;
    }

    public FirmwareSoftwareModuleTypes setContent(List<FirmwareSoftwareModuleType> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareSoftwareModuleTypes setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareSoftwareModuleTypes setSize(int size) {
        this.size = size;
        return this;
    }
}
