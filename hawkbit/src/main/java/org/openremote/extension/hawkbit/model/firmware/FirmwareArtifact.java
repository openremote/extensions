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

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareArtifact {
    protected Long id;
    protected String providedFilename;
    protected Long size;
    protected Map<String, String> hashes;
    protected FirmwareArtifactLinks _links;

    @JsonCreator
    protected FirmwareArtifact() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareArtifact setId(Long id) {
        this.id = id;
        return this;
    }

    public String getProvidedFilename() {
        return providedFilename;
    }

    public FirmwareArtifact setProvidedFilename(String providedFilename) {
        this.providedFilename = providedFilename;
        return this;
    }

    public Long getSize() {
        return size;
    }

    public FirmwareArtifact setSize(Long size) {
        this.size = size;
        return this;
    }

    public Map<String, String> getHashes() {
        return hashes;
    }

    public FirmwareArtifact setHashes(Map<String, String> hashes) {
        this.hashes = hashes;
        return this;
    }

    public FirmwareArtifactLinks get_links() {
        return _links;
    }

    public FirmwareArtifact set_links(FirmwareArtifactLinks links) {
        this._links = links;
        return this;
    }
}
