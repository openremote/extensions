package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareAction {
    protected Long id;
    protected String status;
    protected String type;
    protected String forceType;
    protected Long forceTime;
    protected Integer weight;
    protected Boolean active;
    protected Long createdAt;
    protected Long lastModifiedAt;
    protected Integer lastStatusCode;
    protected FirmwareDistributionSet distributionSet;
    protected FirmwareMaintenanceWindow maintenanceWindow;
    @JsonProperty("_links")
    protected FirmwareActionLinks links;

    @JsonCreator
    protected FirmwareAction() {
    }

    public Long getId() {
        return id;
    }

    public FirmwareAction setId(Long id) {
        this.id = id;
        return this;
    }

    public String getStatus() {
        return status;
    }

    public FirmwareAction setStatus(String status) {
        this.status = status;
        return this;
    }

    public String getType() {
        return type;
    }

    public FirmwareAction setType(String type) {
        this.type = type;
        return this;
    }

    public String getForceType() {
        return forceType;
    }

    public FirmwareAction setForceType(String forceType) {
        this.forceType = forceType;
        return this;
    }

    public Long getForceTime() {
        return forceTime;
    }

    public FirmwareAction setForceTime(Long forceTime) {
        this.forceTime = forceTime;
        return this;
    }

    public Integer getWeight() {
        return weight;
    }

    public FirmwareAction setWeight(Integer weight) {
        this.weight = weight;
        return this;
    }

    public Boolean getActive() {
        return active;
    }

    public FirmwareAction setActive(Boolean active) {
        this.active = active;
        return this;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public FirmwareAction setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Long getLastModifiedAt() {
        return lastModifiedAt;
    }

    public FirmwareAction setLastModifiedAt(Long lastModifiedAt) {
        this.lastModifiedAt = lastModifiedAt;
        return this;
    }

    public Integer getLastStatusCode() {
        return lastStatusCode;
    }

    public FirmwareAction setLastStatusCode(Integer lastStatusCode) {
        this.lastStatusCode = lastStatusCode;
        return this;
    }

    public FirmwareDistributionSet getDistributionSet() {
        return distributionSet;
    }

    public FirmwareAction setDistributionSet(FirmwareDistributionSet distributionSet) {
        this.distributionSet = distributionSet;
        return this;
    }

    public FirmwareMaintenanceWindow getMaintenanceWindow() {
        return maintenanceWindow;
    }

    public FirmwareAction setMaintenanceWindow(FirmwareMaintenanceWindow maintenanceWindow) {
        this.maintenanceWindow = maintenanceWindow;
        return this;
    }

    public FirmwareActionLinks getLinks() {
        return links;
    }

    public FirmwareAction setLinks(FirmwareActionLinks links) {
        this.links = links;
        return this;
    }

    public Long getDistributionSetId() {
        if (links == null || links.getDistributionset() == null || links.getDistributionset().getHref() == null) {
            return null;
        }
        String href = links.getDistributionset().getHref();
        String[] parts = href.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
