/*
 * Copyright 2026, OpenRemote Inc.
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
package org.openremote.extension.ems.manager.gopacs.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * DTO for GOPACS congestion announcement entries from the /machineannouncements endpoint.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementDto {

    private String id;
    private String message;
    private String type;
    private String complianceType;
    private String announcementState;
    private String organisationName;
    private String problemAreaDescription;
    private String requestAreaDescriptionBuyOrders;
    private String requestAreaDescriptionSellOrders;
    private Long createdTimestamp;
    private Long lastUpdatedTimestamp;
    private Long day;
    private TimeSpanDto problemPeriod;
    private TimeSpanDto bidValidityPeriod;
    private List<Double> remainingProblemProfileInMW;

    public AnnouncementDto() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getComplianceType() {
        return complianceType;
    }

    public void setComplianceType(String complianceType) {
        this.complianceType = complianceType;
    }

    public String getAnnouncementState() {
        return announcementState;
    }

    public void setAnnouncementState(String announcementState) {
        this.announcementState = announcementState;
    }

    public String getOrganisationName() {
        return organisationName;
    }

    public void setOrganisationName(String organisationName) {
        this.organisationName = organisationName;
    }

    public String getProblemAreaDescription() {
        return problemAreaDescription;
    }

    public void setProblemAreaDescription(String problemAreaDescription) {
        this.problemAreaDescription = problemAreaDescription;
    }

    public String getRequestAreaDescriptionBuyOrders() {
        return requestAreaDescriptionBuyOrders;
    }

    public void setRequestAreaDescriptionBuyOrders(String requestAreaDescriptionBuyOrders) {
        this.requestAreaDescriptionBuyOrders = requestAreaDescriptionBuyOrders;
    }

    public String getRequestAreaDescriptionSellOrders() {
        return requestAreaDescriptionSellOrders;
    }

    public void setRequestAreaDescriptionSellOrders(String requestAreaDescriptionSellOrders) {
        this.requestAreaDescriptionSellOrders = requestAreaDescriptionSellOrders;
    }

    public Long getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(Long createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    public Long getLastUpdatedTimestamp() {
        return lastUpdatedTimestamp;
    }

    public void setLastUpdatedTimestamp(Long lastUpdatedTimestamp) {
        this.lastUpdatedTimestamp = lastUpdatedTimestamp;
    }

    public Long getDay() {
        return day;
    }

    public void setDay(Long day) {
        this.day = day;
    }

    public TimeSpanDto getProblemPeriod() {
        return problemPeriod;
    }

    public void setProblemPeriod(TimeSpanDto problemPeriod) {
        this.problemPeriod = problemPeriod;
    }

    public TimeSpanDto getBidValidityPeriod() {
        return bidValidityPeriod;
    }

    public void setBidValidityPeriod(TimeSpanDto bidValidityPeriod) {
        this.bidValidityPeriod = bidValidityPeriod;
    }

    public List<Double> getRemainingProblemProfileInMW() {
        return remainingProblemProfileInMW;
    }

    public void setRemainingProblemProfileInMW(List<Double> remainingProblemProfileInMW) {
        this.remainingProblemProfileInMW = remainingProblemProfileInMW;
    }
}
