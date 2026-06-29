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
package org.openremote.extension.ems.manager.gopacs

import spock.lang.Specification

/**
 * Unit tests for {@link GOPACSHandler#toCongestionPoint(String)} — the canonicalisation that keeps the
 * contracted-EAN scope check correct whether or not the configured EAN carries the GOPACS "ean."
 * congestion-point prefix (flex messages always carry it; the asset configuration may omit it).
 */
class GOPACSHandlerEanTest extends Specification {

    def "toCongestionPoint canonicalises an EAN to the GOPACS ean.<code> format (#input -> #expected)"() {
        expect: "the optional, case-insensitive ean. prefix is normalised to lower-case and added when missing"
        GOPACSHandler.toCongestionPoint(input) == expected

        where:
        input                        || expected
        "ean.265987182507322951"     || "ean.265987182507322951"
        "265987182507322951"         || "ean.265987182507322951"
        "EAN.265987182507322951"     || "ean.265987182507322951"
        "Ean.265987182507322951"     || "ean.265987182507322951"
        "  ean.265987182507322951  " || "ean.265987182507322951"
        "  265987182507322951  "     || "ean.265987182507322951"
        ""                           || "ean."
        null                         || null
    }
}
