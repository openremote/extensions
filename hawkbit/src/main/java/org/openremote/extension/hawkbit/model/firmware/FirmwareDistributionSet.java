/*
 * Copyright 2025, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
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
