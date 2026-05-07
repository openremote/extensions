package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareTargetFilterQueryRequest {
    protected String name;
    protected String query;

    @JsonCreator
    public FirmwareTargetFilterQueryRequest() {
    }

    public String getName() {
        return name;
    }

    public FirmwareTargetFilterQueryRequest setName(String name) {
        this.name = name;
        return this;
    }

    public String getQuery() {
        return query;
    }

    public FirmwareTargetFilterQueryRequest setQuery(String query) {
        this.query = query;
        return this;
    }
}
