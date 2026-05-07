package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRolloutAction {
    protected String action;
    protected String expression;

    @JsonCreator
    protected FirmwareRolloutAction() {
    }

    public String getAction() {
        return action;
    }

    public FirmwareRolloutAction setAction(String action) {
        this.action = action;
        return this;
    }

    public String getExpression() {
        return expression;
    }

    public FirmwareRolloutAction setExpression(String expression) {
        this.expression = expression;
        return this;
    }
}
