package org.openremote.extension.hawkbit.model.firmware;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FirmwareRolloutCondition {
    protected String condition;
    protected String expression;

    @JsonCreator
    protected FirmwareRolloutCondition() {
    }

    public String getCondition() {
        return condition;
    }

    public FirmwareRolloutCondition setCondition(String condition) {
        this.condition = condition;
        return this;
    }

    public String getExpression() {
        return expression;
    }

    public FirmwareRolloutCondition setExpression(String expression) {
        this.expression = expression;
        return this;
    }
}
