/*
 * Copyright 2026, OpenRemote Inc.
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openremote.extension.ems.manager.distroenergy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One ETPA trade of an order. Units are as Distro sends them, either KW, KWH and EUR/KWH or MW, MWH
 * and EUR/MWH; cost is in EUR.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GopacsSettlement(
    String tradeId,
    String orderReference,
    String ean,
    String direction,
    long start,
    long end,
    double capacity,
    String capacityUnit,
    double volume,
    String volumeUnit,
    double price,
    String priceUnit,
    double cost,
    long settledAt,
    long modifiedAt) {}
