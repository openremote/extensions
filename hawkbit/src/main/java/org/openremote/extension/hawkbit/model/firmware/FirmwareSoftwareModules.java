package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareSoftwareModules {
    protected List<FirmwareSoftwareModule> content;
    protected int total;
    protected int size;

    @JsonCreator
    protected FirmwareSoftwareModules() {
    }

    public List<FirmwareSoftwareModule> getContent() {
        return content;
    }

    public FirmwareSoftwareModules setContent(List<FirmwareSoftwareModule> content) {
        this.content = content;
        return this;
    }

    public int getTotal() {
        return total;
    }

    public FirmwareSoftwareModules setTotal(int total) {
        this.total = total;
        return this;
    }

    public int getSize() {
        return size;
    }

    public FirmwareSoftwareModules setSize(int size) {
        this.size = size;
        return this;
    }
}
