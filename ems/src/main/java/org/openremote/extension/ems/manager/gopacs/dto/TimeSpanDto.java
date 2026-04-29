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

/**
 * DTO for GOPACS time span objects used in announcements and effectivity responses.
 * All time values are epoch milliseconds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeSpanDto {

    private Long startTime;
    private Long endTime;
    private Double durationInHours;
    private Integer numberOfQuartersInTimeSpan;

    public TimeSpanDto() {
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public Double getDurationInHours() {
        return durationInHours;
    }

    public void setDurationInHours(Double durationInHours) {
        this.durationInHours = durationInHours;
    }

    public Integer getNumberOfQuartersInTimeSpan() {
        return numberOfQuartersInTimeSpan;
    }

    public void setNumberOfQuartersInTimeSpan(Integer numberOfQuartersInTimeSpan) {
        this.numberOfQuartersInTimeSpan = numberOfQuartersInTimeSpan;
    }
}
