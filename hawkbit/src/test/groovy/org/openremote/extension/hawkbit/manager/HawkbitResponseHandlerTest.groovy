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

class HawkbitResponseHandlerTest extends Specification {

    def "strips HAL fields from single resource response"() {
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

        when: "the response is adapted"
        def formatted = HawkbitResponseHandler.call("Failed to call hawkBit", { upstream })
        def body = readJson(formatted)

        then: "HAL fields are removed and normal fields are preserved"
        formatted.status == 200
        formatted.mediaType == MediaType.APPLICATION_JSON_TYPE
        body.path("id").asInt() == 42
        body.path("name").asText() == "target-a"
        body.path("nested").path("enabled").asBoolean()
        !body.has("_links")
        !body.has("_embedded")
        !body.path("nested").has("_links")
    }

    def "maps distribution set link to explicit fields"() {
        given: "a hawkBit resource response with a distribution set link"
        def upstream = jsonResponse('''
        {
          "id": 7,
          "_links": {
            "distributionset": {
              "href": "http://hawkbit/rest/v1/distributionsets/123",
              "name": "Release 1.2.3"
            }
          }
        }
        ''')

        when: "the response is adapted"
        def body = readJson(HawkbitResponseHandler.call("Failed to call hawkBit", { upstream }))

        then: "the link is represented as explicit fields"
        body.path("id").asInt() == 7
        body.path("distributionSetId").asLong() == 123L
        body.path("distributionSetName").asText() == "Release 1.2.3"
        !body.has("_links")
    }

    def "flattens embedded HAL collection to paged response"() {
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

        when: "the response is adapted"
        def body = readJson(HawkbitResponseHandler.call("Failed to call hawkBit", { upstream }))

        then: "embedded items become page content and HAL fields are stripped"
        body.path("total").asInt() == 10
        body.path("size").asInt() == 2
        body.path("content").size() == 2
        body.path("content").get(0).path("controllerId").asText() == "target-a"
        body.path("content").get(1).path("controllerId").asText() == "target-b"
        !body.path("content").get(0).has("_links")
        !body.path("content").get(1).has("_embedded")
    }

    def "flattens root array to paged response"() {
        given: "a JSON array response"
        def upstream = jsonResponse('''
        [
          {"id": 1, "_links": {"self": {"href": "http://hawkbit/rest/v1/items/1"}}},
          {"id": 2}
        ]
        ''')

        when: "the response is adapted"
        def body = readJson(HawkbitResponseHandler.call("Failed to call hawkBit", { upstream }))

        then: "array items become page content"
        body.path("total").asInt() == 2
        body.path("size").asInt() == 2
        body.path("content").size() == 2
        body.path("content").get(0).path("id").asInt() == 1
        body.path("content").get(1).path("id").asInt() == 2
        !body.path("content").get(0).has("_links")
    }

    def "flattens content array to paged response"() {
        given: "a Spring Data REST paged response"
        def upstream = jsonResponse('''
        {
          "content": [
            {"id": 3, "_links": {"self": {"href": "http://hawkbit/rest/v1/items/3"}}},
            {"id": 4}
          ],
          "page": {
            "totalElements": 5,
            "size": 2
          }
        }
        ''')

        when: "the response is adapted"
        def body = readJson(HawkbitResponseHandler.call("Failed to call hawkBit", { upstream }))

        then: "content items are preserved and HAL fields are stripped"
        body.path("total").asInt() == 5
        body.path("size").asInt() == 2
        body.path("content").size() == 2
        body.path("content").get(0).path("id").asInt() == 3
        body.path("content").get(1).path("id").asInt() == 4
        !body.path("content").get(0).has("_links")
    }

    def "preserves non-JSON body and media type"() {
        given: "an upstream response with a non-JSON body"
        def upstream = Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity("upstream unavailable")
                .build()

        when: "the response is adapted"
        def formatted = HawkbitResponseHandler.call("Failed to call hawkBit", { upstream })

        then: "the original body and media type are preserved"
        formatted.status == Response.Status.BAD_GATEWAY.statusCode
        formatted.mediaType == MediaType.TEXT_PLAIN_TYPE
        formatted.readEntity(String) == "upstream unavailable"
    }

    def "returns empty response when no entity"() {
        given: "an upstream response without an entity"
        def upstream = Response.noContent().build()

        when: "the response is adapted"
        def formatted = HawkbitResponseHandler.call("Failed to call hawkBit", { upstream })

        then: "the empty status is preserved"
        formatted.status == Response.Status.NO_CONTENT.statusCode
        !formatted.hasEntity()
    }

    def "wraps checked exceptions as bad gateway"() {
        when: "an adapted call throws an unexpected exception"
        HawkbitResponseHandler.call("Failed to call hawkBit", {
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

        when: "an adapted call throws it"
        HawkbitResponseHandler.call("Failed to call hawkBit", {
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
