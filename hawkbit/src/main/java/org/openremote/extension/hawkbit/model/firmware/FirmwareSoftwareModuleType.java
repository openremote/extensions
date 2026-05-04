package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareSoftwareModuleType {
    protected Long id;
    protected String name;
    protected String description;
    protected String key;
    protected Integer minArtifacts;
    protected Integer maxAssignments;
    protected String colour;
    protected Boolean deleted;

    @JsonCreator
    protected FirmwareSoftwareModuleType() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareSoftwareModuleType setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareSoftwareModuleType setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareSoftwareModuleType setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getKey() {
        return key;
    }

    public FirmwareSoftwareModuleType setKey(String key) {
        this.key = key;
        return this;
    }

    public Integer getMinArtifacts() {
        return minArtifacts;
    }

    public FirmwareSoftwareModuleType setMinArtifacts(Integer minArtifacts) {
        this.minArtifacts = minArtifacts;
        return this;
    }

    public Integer getMaxAssignments() {
        return maxAssignments;
    }

    public FirmwareSoftwareModuleType setMaxAssignments(Integer maxAssignments) {
        this.maxAssignments = maxAssignments;
        return this;
    }

    public String getColour() {
        return colour;
    }

    public FirmwareSoftwareModuleType setColour(String colour) {
        this.colour = colour;
        return this;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public FirmwareSoftwareModuleType setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }
}
