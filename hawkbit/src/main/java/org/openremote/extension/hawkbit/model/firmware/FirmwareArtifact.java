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
