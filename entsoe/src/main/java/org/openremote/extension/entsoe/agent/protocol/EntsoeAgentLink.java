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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.openremote.model.asset.agent.AgentLink;

public class EntsoeAgentLink extends AgentLink<EntsoeAgentLink> {

    @NotNull
    @JsonPropertyDescription("Energy Identification Code of zone to fetch data for")
    @Pattern(regexp = "^\\d{2}[A-Z][A-Z0-9-]{12}[A-Z0-9]$")
    private String zone;

    // For Hydrators
    public EntsoeAgentLink() {
    }

    public EntsoeAgentLink(String id) {
        super(id);
    }

    public String getZone() {
        return zone;
    }

    public EntsoeAgentLink setZone(String zone) {
        this.zone = zone;
        return this;
    }
}
