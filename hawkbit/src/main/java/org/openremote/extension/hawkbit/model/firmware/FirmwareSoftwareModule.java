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

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareSoftwareModule {
    protected Long id;
    protected String name;
    protected String description;
    protected String version;
    protected String type;
    protected String typeName;
    protected String vendor;
    protected Boolean encrypted;
    protected Boolean locked;
    protected Boolean deleted;
    protected Boolean complete;

    @JsonCreator
    protected FirmwareSoftwareModule() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareSoftwareModule setId(Long id) {
        this.id = id;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareSoftwareModule setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareSoftwareModule setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public FirmwareSoftwareModule setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareSoftwareModule setType(String type) {
        this.type = type;
        return this;
    }

    public String getTypeName() {
        return typeName;
    }

    public FirmwareSoftwareModule setTypeName(String typeName) {
        this.typeName = typeName;
        return this;
    }

    public String getVendor() {
        return vendor;
    }

    public FirmwareSoftwareModule setVendor(String vendor) {
        this.vendor = vendor;
        return this;
    }

    public Boolean getEncrypted() {
        return encrypted;
    }

    public FirmwareSoftwareModule setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
        return this;
    }

    public Boolean getLocked() {
        return locked;
    }

    public FirmwareSoftwareModule setLocked(Boolean locked) {
        this.locked = locked;
        return this;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public FirmwareSoftwareModule setDeleted(Boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public Boolean getComplete() {
        return complete;
    }

    public FirmwareSoftwareModule setComplete(Boolean complete) {
        this.complete = complete;
        return this;
    }
}
