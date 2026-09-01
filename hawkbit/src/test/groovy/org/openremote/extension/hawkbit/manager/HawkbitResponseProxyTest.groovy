/*
 * Copyright 2025, OpenRemote Inc.
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
package org.openremote.extension.hawkbit.manager

import com.fasterxml.jackson.databind.JsonNode
import jakarta.ws.rs.WebApplicationException
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.openremote.model.util.ValueUtil
import spock.lang.Specification

class HawkbitResponseProxyTest extends Specification {

    def "preserves JSON response body and media type"() {
        given: "a hawkBit HAL resource response"
        def upstream = jsonResponse('''
        {
          "id": 42,
          "name": "target-a",
          "nested": {
            "enabled": true,
            "_links": {
              "self": {"href": "http://hawkbit/rest/v1/nested/7"}
            }
          },
          "_links": {
            "self": {"href": "http://hawkbit/rest/v1/targets/42"}
          },
          "_embedded": {
            "ignored": [{"id": 1}]
          }
        }
        ''')

        when: "the response is copied"
        def formatted = HawkbitResponseProxy.proxy("Failed to call hawkBit", { upstream })
        def body = readJson(formatted)

        then: "the original JSON body and media type are preserved"
        formatted.status == 200
        formatted.mediaType == MediaType.APPLICATION_JSON_TYPE
        body.path("id").asInt() == 42
        body.path("name").asText() == "target-a"
        body.path("nested").path("enabled").asBoolean()
        body.path("_links").path("self").path("href").asText() == "http://hawkbit/rest/v1/targets/42"
        body.path("_embedded").path("ignored").size() == 1
        body.path("nested").path("_links").path("self").path("href").asText() == "http://hawkbit/rest/v1/nested/7"
    }

    def "preserves embedded HAL collection response"() {
        given: "a hawkBit HAL collection response"
        def upstream = jsonResponse('''
        {
          "_embedded": {
            "targets": [
              {"controllerId": "target-a", "_links": {"self": {"href": "http://hawkbit/rest/v1/targets/target-a"}}},
              {"controllerId": "target-b", "_embedded": {"ignored": []}}
            ]
          },
          "page": {
            "totalElements": 10,
            "size": 2
          }
        }
        ''')

        when: "the response is copied"
        def body = readJson(HawkbitResponseProxy.proxy("Failed to call hawkBit", { upstream }))

        then: "embedded items and page metadata stay in hawkBit's shape"
        body.path("_embedded").path("targets").size() == 2
        body.path("_embedded").path("targets").get(0).path("controllerId").asText() == "target-a"
        body.path("_embedded").path("targets").get(0).path("_links").path("self").path("href").asText() == "http://hawkbit/rest/v1/targets/target-a"
        body.path("page").path("totalElements").asInt() == 10
        body.path("page").path("size").asInt() == 2
    }

    def "preserves non-JSON body and media type"() {
        given: "an upstream response with a non-JSON body"
        def upstream = Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("upstream unavailable")
                .build()

        when: "the response is copied"
        def formatted = HawkbitResponseProxy.proxy("Failed to call hawkBit", { upstream })

        then: "the original body and media type are preserved"
        formatted.status == Response.Status.BAD_GATEWAY.statusCode
        formatted.mediaType == MediaType.TEXT_PLAIN_TYPE
        formatted.readEntity(String) == "upstream unavailable"
    }

    def "returns empty response when no entity"() {
        given: "an upstream response without an entity"
        def upstream = Response.noContent().build()

        when: "the response is copied"
        def formatted = HawkbitResponseProxy.proxy("Failed to call hawkBit", { upstream })

        then: "the empty status is preserved"
        formatted.status == Response.Status.NO_CONTENT.statusCode
        !formatted.hasEntity()
    }

    def "wraps checked exceptions as bad gateway"() {
        when: "a hawkBit call throws an unexpected exception"
        HawkbitResponseProxy.proxy("Failed to call hawkBit", {
            throw new IOException("connection failed")
        })

        then: "the exception is wrapped as a bad gateway response"
        def e = thrown(WebApplicationException)
        e.response.status == Response.Status.BAD_GATEWAY.statusCode
        e.message == "Failed to call hawkBit"
        e.cause instanceof IOException
    }

    def "rethrows web application exceptions unchanged"() {
        given: "an existing web application exception"
        def original = new WebApplicationException("not found", Response.Status.NOT_FOUND)

        when: "a hawkBit call throws it"
        HawkbitResponseProxy.proxy("Failed to call hawkBit", {
            throw original
        })

        then: "the original exception is rethrown"
        def e = thrown(WebApplicationException)
        e.is(original)
        e.response.status == Response.Status.NOT_FOUND.statusCode
    }

    private static Response jsonResponse(String body) {
        Response.ok(body, MediaType.APPLICATION_JSON_TYPE).build()
    }

    private static JsonNode readJson(Response response) {
        ValueUtil.JSON.readTree(response.readEntity(String))
    }
}
