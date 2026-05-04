package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwarePollStatus {
    protected Long lastRequestAt;
    protected Long nextExpectedRequestAt;
    protected Boolean overdue;

    @JsonCreator
    protected FirmwarePollStatus() {
    }

    public Long getLastRequestAt() {
        return lastRequestAt;
    }

    public FirmwarePollStatus setLastRequestAt(Long lastRequestAt) {
        this.lastRequestAt = lastRequestAt;
        return this;
    }

    public Long getNextExpectedRequestAt() {
        return nextExpectedRequestAt;
    }

    public FirmwarePollStatus setNextExpectedRequestAt(Long nextExpectedRequestAt) {
        this.nextExpectedRequestAt = nextExpectedRequestAt;
        return this;
    }

    public Boolean getOverdue() {
        return overdue;
    }

    public FirmwarePollStatus setOverdue(Boolean overdue) {
        this.overdue = overdue;
        return this;
    }
}
