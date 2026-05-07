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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareDistributionSetType {
    protected Long id;
    protected String name;
    protected String description;
    protected String key;
    protected String colour;
    protected Boolean deleted;
    @JsonProperty("mandatorymodules")
    protected List<FirmwareSoftwareModuleTypeAssignment> mandatoryModules;
    @JsonProperty("optionalmodules")
    protected List<FirmwareSoftwareModuleTypeAssignment> optionalModules;

    @JsonCreator
    protected FirmwareDistributionSetType() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareDistributionSetType setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareDistributionSetType setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareDistributionSetType setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getKey() {
        return key;
    }

    public FirmwareDistributionSetType setKey(String key) {
        this.key = key;
        return this;
    }

    public String getColour() {
        return colour;
    }

    public FirmwareDistributionSetType setColour(String colour) {
        this.colour = colour;
        return this;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public FirmwareDistributionSetType setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    @JsonProperty("mandatorymodules")
    public List<FirmwareSoftwareModuleTypeAssignment> getMandatoryModules() {
        return mandatoryModules;
    }

    @JsonProperty("mandatorymodules")
    public FirmwareDistributionSetType setMandatoryModules(
            List<FirmwareSoftwareModuleTypeAssignment> mandatoryModules) {
        this.mandatoryModules = mandatoryModules;
        return this;
    }

    @JsonProperty("optionalmodules")
    public List<FirmwareSoftwareModuleTypeAssignment> getOptionalModules() {
        return optionalModules;
    }

    @JsonProperty("optionalmodules")
    public FirmwareDistributionSetType setOptionalModules(
            List<FirmwareSoftwareModuleTypeAssignment> optionalModules) {
        this.optionalModules = optionalModules;
        return this;
    }
}
