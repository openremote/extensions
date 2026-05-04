package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareMaintenanceWindow {
    protected String schedule;
    protected String duration;
    protected String timezone;

    @JsonCreator
    protected FirmwareMaintenanceWindow() {
    }

    public String getSchedule() {
        return schedule;
    }

    public FirmwareMaintenanceWindow setSchedule(String schedule) {
        this.schedule = schedule;
        return this;
    }

    public String getDuration() {
        return duration;
    }

    public FirmwareMaintenanceWindow setDuration(String duration) {
        this.duration = duration;
        return this;
    }

    public String getTimezone() {
        return timezone;
    }

    public FirmwareMaintenanceWindow setTimezone(String timezone) {
        this.timezone = timezone;
        return this;
    }
}
