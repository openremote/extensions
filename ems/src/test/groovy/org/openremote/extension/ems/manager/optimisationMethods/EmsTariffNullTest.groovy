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
package org.openremote.extension.ems.manager.optimisationMethods

import org.openremote.model.datapoint.ValueDatapoint
import spock.lang.Specification

import java.time.LocalDate
import java.time.ZoneId

class EmsTariffNullTest extends Specification {

  private static final long START = LocalDate.of(2026, 10, 28).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

  def "#implementation.simpleName moving average preserves unavailable windows: #scenario"() {
    given:
    def optimisation = implementation.getDeclaredConstructor().newInstance()
    def input = datapoints(values)

    when:
    def result = optimisation.movingAverage(input, window)

    then:
    result.collect { it.value } == expected
    result.collect { it.timestamp } == input.drop(window - 1).collect { it.timestamp }

    where:
    [implementation, scenario, values, window, expected] << [EmsOptimisation, EmsOptimisationBeta].collectMany { type ->
      [
        [type, "negative and zero prices", [-4, 0, 4, 8], 2, [-2d, 2d, 6d]],
        [type, "first unavailable", [null, 2, 4, 6], 2, [null, 3d, 5d]],
        [type, "middle unavailable", [2, 4, null, 6, 8], 2, [3d, null, null, 7d]],
        [type, "last unavailable", [2, 4, 6, null], 2, [3d, 5d, null]],
        [type, "consecutive unavailable", [null, null, 2, 4], 2, [null, null, 3d]],
        [type, "all unavailable", [null, null, null], 2, [null, null]],
        [type, "single point windows", [2, null, 4], 1, [2d, null, 4d]],
        [type, "default eight point window", [1, 2, 3, null, 5, 6, 7, 8], 8, [null]],
        [type, "rounding", [0, 0, 1], 3, [0.3333333d]]
      ]
    }
  }

  def "#implementation.simpleName tariff zones use only complete windows: #scenario"() {
    given:
    def optimisation = implementation.getDeclaredConstructor().newInstance()
    def imports = datapoints(importValues)
    def exports = datapoints(exportValues)
    def expected = [:]
    chargeIndices.each { expected[START + it * 900_000L] = 1 }
    dischargeIndices.each { expected[START + it * 900_000L] = -1 }

    expect:
    optimisation.calculateTariffChargeAndDischargeZones(imports, exports, 8) == expected

    where:
    [implementation, scenario, importValues, exportValues, chargeIndices, dischargeIndices] <<
    [EmsOptimisation, EmsOptimisationBeta].collectMany { type ->
      [
        [
          type,
          "valid windows after an import gap",
          [null] + [-5] * 8 + [100] * 8,
          [100] * 9 + [0] * 8,
          (1..8),
          (9..16)
        ],
        [
          type,
          "valid windows after an export gap",
          [100] * 9 + [0] * 8,
          [null] + [-5] * 8 + [100] * 8,
          (9..16),
          (1..8)
        ],
        [type, "both unavailable", [null] * 8, [null] * 8, [], []],
        [type, "import unavailable", [null] * 8, [10] * 8, [], (0..7)],
        [type, "export unavailable", [10] * 8, [null] * 8, (0..7), []],
        [type, "no complete window", [10] * 4 + [null] + [10] * 3, [null] * 8, [], []]
      ]
    }
  }

  private static List<ValueDatapoint<?>> datapoints(List values) {
    values.withIndex().collect { value, index ->
      new ValueDatapoint<>(START + index * 900_000L, value == null ? null : value.doubleValue())
    }
  }
}
