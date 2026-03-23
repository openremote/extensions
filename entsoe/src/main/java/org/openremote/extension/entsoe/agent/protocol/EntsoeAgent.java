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
package org.openremote.extension.entsoe.agent.protocol;

import jakarta.persistence.Entity;
import org.openremote.model.asset.agent.Agent;
import org.openremote.model.asset.agent.AgentDescriptor;
import org.openremote.model.value.AttributeDescriptor;
import org.openremote.model.value.ValueType;

import java.util.Optional;

@Entity
public class EntsoeAgent extends Agent<EntsoeAgent, EntsoeProtocol, EntsoeAgentLink> {

    public static final AgentDescriptor<EntsoeAgent, EntsoeProtocol, EntsoeAgentLink> DESCRIPTOR = new AgentDescriptor<>(
            EntsoeAgent.class, EntsoeProtocol.class, EntsoeAgentLink.class);

    public static final AttributeDescriptor<String> SECURITY_TOKEN = new AttributeDescriptor<>("securityToken", ValueType.TEXT);

    public static final AttributeDescriptor<String> BASE_URL = new AttributeDescriptor<>("baseURL", ValueType.TEXT).withOptional(true);

    public EntsoeAgent() {
    }

    public EntsoeAgent(String name) {
        super(name);
    }

    @Override
    public EntsoeProtocol getProtocolInstance() {
        return new EntsoeProtocol(this);
    }

    public Optional<String> getSecurityToken() {
        return getAttributes().getValue(SECURITY_TOKEN);
    }

    public EntsoeAgent setSecurityToken(String value) {
        getAttributes().getOrCreate(SECURITY_TOKEN).setValue(value);
        return this;
    }

    public Optional<String> getBaseURL() {
        return getAttributes().getValue(BASE_URL);
    }

    public EntsoeAgent setBaseURL(String value) {
        getAttributes().getOrCreate(BASE_URL).setValue(value);
        return this;
    }
}
