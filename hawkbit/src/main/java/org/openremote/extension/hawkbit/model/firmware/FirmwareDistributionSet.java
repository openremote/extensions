package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSet {
    protected Long id;
    protected String name;
    protected String description;
    protected String version;
    protected String type;
    protected String typeName;
    protected Boolean locked;
    protected Boolean deleted;
    protected Boolean valid;
    protected Boolean complete;
    protected Boolean requiredMigrationStep;
    protected List<FirmwareSoftwareModule> modules;

    @JsonCreator
    protected FirmwareDistributionSet() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareDistributionSet setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareDistributionSet setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareDistributionSet setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public FirmwareDistributionSet setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareDistributionSet setType(String type) {
        this.type = type;
        return this;
    }

    public String getTypeName() {
        return typeName;
    }

    public FirmwareDistributionSet setTypeName(String typeName) {
        this.typeName = typeName;
        return this;
    }

    public Boolean getLocked() {
        return locked;
    }

    public FirmwareDistributionSet setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public FirmwareDistributionSet setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public Boolean getValid() {
        return valid;
    }

    public FirmwareDistributionSet setValid(Boolean valid) {
        this.valid = valid;
        return this;
    }

    public Boolean getComplete() {
        return complete;
    }

    public FirmwareDistributionSet setComplete(Boolean complete) {
        this.complete = complete;
        return this;
    }

    public Boolean getRequiredMigrationStep() {
        return requiredMigrationStep;
    }

    public FirmwareDistributionSet setRequiredMigrationStep(Boolean requiredMigrationStep) {
        this.requiredMigrationStep = requiredMigrationStep;
        return this;
    }

    public List<FirmwareSoftwareModule> getModules() {
        return modules;
    }

    public FirmwareDistributionSet setModules(List<FirmwareSoftwareModule> modules) {
        this.modules = modules;
        return this;
    }
}
