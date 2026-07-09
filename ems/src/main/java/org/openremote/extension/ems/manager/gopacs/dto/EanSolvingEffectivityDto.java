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

import java.util.Map;
import java.util.Set;

/**
 * DTO for the GOPACS EAN solving effectivity response from
 * /public-api/1.0/announcements/{id}/eansolvingeffectivity.
 * <p>
 * The {@code eansByCategory} maps effectivity category names to sets of EANs
 * that fall into that category for the given announcement.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EanSolvingEffectivityDto {

    private TimeSpanDto during;
    private Map<String, Set<String>> eansByCategory;

    public EanSolvingEffectivityDto() {
    }

    public TimeSpanDto getDuring() {
        return during;
    }

    public void setDuring(TimeSpanDto during) {
        this.during = during;
    }

    public Map<String, Set<String>> getEansByCategory() {
        return eansByCategory;
    }

    public void setEansByCategory(Map<String, Set<String>> eansByCategory) {
        this.eansByCategory = eansByCategory;
    }
}
