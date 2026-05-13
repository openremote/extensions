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
package org.openremote.extension.hawkbit.manager.firmware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.openremote.model.util.ValueUtil;

import java.util.LinkedHashMap;
import java.util.Map;

class HawkbitResponse {

    private static final Map<String, String> DEFAULT_LINK_ID_FIELDS = Map.of(
            "distributionset", "distributionSetId");

    private final Response response;
    private final Map<String, String> linkIdFields = new LinkedHashMap<>(DEFAULT_LINK_ID_FIELDS);

    private HawkbitResponse(Response response) {
        this.response = response;
    }

    static HawkbitResponse from(Response response) {
        return new HawkbitResponse(response);
    }

    HawkbitResponse withLinkId(String rel, String fieldName) {
        linkIdFields.put(rel, fieldName);
        return this;
    }

    Response asResource() {
        return formatResponse(Mode.HAL);
    }

    Response asPage() {
        return formatResponse(Mode.PAGED);
    }

    Response raw() {
        return response;
    }

    private Response formatResponse(Mode mode) {
        try (response) {
            Response.ResponseBuilder builder = Response.status(response.getStatus());
            if (!response.hasEntity()) {
                return builder.build();
            }

            String body = response.readEntity(String.class);
            if (body == null || body.isBlank()) {
                return builder.build();
            }

            try {
                JsonNode formatted = switch (mode) {
                    case HAL -> stripHal(ValueUtil.JSON.readTree(body));
                    case PAGED -> toPagedResponse(ValueUtil.JSON.readTree(body));
                };
                return builder
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .entity(ValueUtil.JSON.writeValueAsString(formatted))
                        .build();
            } catch (Exception ignored) {
                MediaType mediaType = response.getMediaType() == null
                        ? MediaType.TEXT_PLAIN_TYPE
                        : response.getMediaType();
                return builder.type(mediaType).entity(body).build();
            }
        } catch (Exception e) {
            throw new WebApplicationException("Failed to format hawkBit response", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    private ObjectNode toPagedResponse(JsonNode root) {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        if (root.isArray()) {
            root.forEach(item -> content.add(stripHal(item)));
        } else if (root.isObject() && root.has("_embedded")) {
            embeddedContent(root.get("_embedded")).forEach(item -> content.add(stripHal(item)));
        } else if (root.isObject() && root.path("content").isArray()) {
            root.get("content").forEach(item -> content.add(stripHal(item)));
        } else if (!root.isMissingNode() && !root.isNull()) {
            content.add(stripHal(root));
        }

        ObjectNode formatted = JsonNodeFactory.instance.objectNode();
        formatted.set("content", content);
        formatted.put("total", intValue(root, "total", intValue(root.path("page"), "totalElements", content.size())));
        formatted.put("size", intValue(root, "size", intValue(root.path("page"), "size", content.size())));
        return formatted;
    }

    private JsonNode stripHal(JsonNode node) {
        if (node.isArray()) {
            ArrayNode formatted = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> formatted.add(stripHal(item)));
            return formatted;
        }

        if (!node.isObject()) {
            return node;
        }

        ObjectNode formatted = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if ("_links".equals(field.getKey()) || "_embedded".equals(field.getKey())) {
                continue;
            }
            formatted.set(field.getKey(), stripHal(field.getValue()));
        }
        addMappedLinkIds(node.path("_links"), formatted);
        return formatted;
    }

    private void addMappedLinkIds(JsonNode links, ObjectNode target) {
        if (!links.isObject()) {
            return;
        }

        linkIdFields.forEach((rel, fieldName) -> {
            Long id = idFromHref(links.path(rel).path("href"));
            if (id != null && !target.has(fieldName)) {
                target.put(fieldName, id);
            }
        });
    }

    private static Long idFromHref(JsonNode hrefNode) {
        if (!hrefNode.isTextual()) {
            return null;
        }

        String href = hrefNode.textValue();
        int end = href.endsWith("/") ? href.length() - 1 : href.length();
        int start = href.lastIndexOf('/', end - 1) + 1;
        if (start < 0 || start >= end) {
            return null;
        }
        try {
            return Long.parseLong(href.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ArrayNode embeddedContent(JsonNode embedded) {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        if (!embedded.isObject()) {
            return content;
        }

        for (JsonNode value : embedded) {
            if (value.isArray()) {
                value.forEach(content::add);
                return content;
            }
        }
        return content;
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : defaultValue;
    }

    private enum Mode {
        HAL,
        PAGED
    }
}
