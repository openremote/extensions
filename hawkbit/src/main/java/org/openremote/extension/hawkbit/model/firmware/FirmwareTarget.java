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
public class FirmwareTarget {
    protected String controllerId;
    protected String name;
    protected String description;
    protected String securityToken;
    protected String updateStatus;
    protected Long lastControllerRequestAt;
    protected Long installedAt;
    protected String ipAddress;
    protected String address;
    protected FirmwarePollStatus pollStatus;
    protected Boolean requestAttributes;
    protected Long targetType;
    protected String targetTypeName;
    protected Boolean autoConfirmActive;

    @JsonCreator
    protected FirmwareTarget() {
    }

    public FirmwareTarget(String controllerId, String name, String description) {
        this.controllerId = controllerId;
        this.name = name;
        this.description = description;
    }

    public String getControllerId() {
        return controllerId;
    }

    public FirmwareTarget setControllerId(String controllerId) {
        this.controllerId = controllerId;
        return this;
    }

    public String getName() {
        return name;
    }

    public FirmwareTarget setName(String name) {
        this.name = name;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public FirmwareTarget setDescription(String description) {
        this.description = description;
        return this;
    }

    public String getSecurityToken() {
        return securityToken;
    }

    public FirmwareTarget setSecurityToken(String securityToken) {
        this.securityToken = securityToken;
        return this;
    }

    public String getUpdateStatus() {
        return updateStatus;
    }

    public FirmwareTarget setUpdateStatus(String updateStatus) {
        this.updateStatus = updateStatus;
        return this;
    }

    public Long getLastControllerRequestAt() {
        return lastControllerRequestAt;
    }

    public FirmwareTarget setLastControllerRequestAt(Long lastControllerRequestAt) {
        this.lastControllerRequestAt = lastControllerRequestAt;
        return this;
    }

    public Long getInstalledAt() {
        return installedAt;
    }

    public FirmwareTarget setInstalledAt(Long installedAt) {
        this.installedAt = installedAt;
        return this;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public FirmwareTarget setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
        return this;
    }

    public String getAddress() {
        return address;
    }

    public FirmwareTarget setAddress(String address) {
        this.address = address;
        return this;
    }

    public FirmwarePollStatus getPollStatus() {
        return pollStatus;
    }

    public FirmwareTarget setPollStatus(FirmwarePollStatus pollStatus) {
        this.pollStatus = pollStatus;
        return this;
    }

    public Boolean getRequestAttributes() {
        return requestAttributes;
    }

    public FirmwareTarget setRequestAttributes(Boolean requestAttributes) {
        this.requestAttributes = requestAttributes;
        return this;
    }

    public Long getTargetType() {
        return targetType;
    }

    public FirmwareTarget setTargetType(Long targetType) {
        this.targetType = targetType;
        return this;
    }

    public String getTargetTypeName() {
        return targetTypeName;
    }

    public FirmwareTarget setTargetTypeName(String targetTypeName) {
        this.targetTypeName = targetTypeName;
        return this;
    }

    public Boolean getAutoConfirmActive() {
        return autoConfirmActive;
    }

    public FirmwareTarget setAutoConfirmActive(Boolean autoConfirmActive) {
        this.autoConfirmActive = autoConfirmActive;
        return this;
    }
}
