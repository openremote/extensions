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
package org.openremote.extension.hawkbit.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.openremote.model.util.ValueUtil;

import java.util.Map;

public final class HawkbitResponseHandler {

    private static final Map<String, LinkFieldMapping> HAL_LINK_FIELDS = Map.of(
            "distributionset", new LinkFieldMapping("distributionSetId", "distributionSetName"));

    private HawkbitResponseHandler() {
    }

    /**
     * Executes a hawkBit call, adapts the response (strips HAL, flattens collections),
     * and wraps any unexpected failure as {@code BAD_GATEWAY}.
     * <p>
     * The upstream {@link Response} is consumed and closed by this call.
     */
    public static Response call(String errorMessage, HawkbitCall call) {
        try {
            return adaptHawkbitResponse(call.execute());
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            throw new WebApplicationException(errorMessage, e, Response.Status.BAD_GATEWAY);
        }
    }

    /**
     * Consumes the upstream response and builds a new one with HAL fields removed.
     * Preserves non-JSON bodies and empty responses unchanged.
     */
    protected static Response adaptHawkbitResponse(Response response) {
        try (response) {
            Response.ResponseBuilder builder = Response.status(response.getStatus());
            if (!response.hasEntity()) {
                return builder.build();
            }

            String body = response.readEntity(String.class);
            if (body.isBlank()) {
                return builder.build();
            }

            try {
                JsonNode root = ValueUtil.JSON.readTree(body);
                JsonNode formatted = isCollectionResponse(root) ? toFlatPage(root) : cleanHalFields(root);
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
            throw new WebApplicationException("Failed to adapt hawkBit response", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    /**
     * Detects collection responses (arrays, paged objects, or HAL {@code _embedded} arrays).
     * Single resources with {@code _embedded} relations are excluded by checking for
     * typical identifier fields such as {@code id} or {@code controllerId}.
     */
    protected static boolean isCollectionResponse(JsonNode root) {
        if (root.isArray()) {
            return true;
        }
        if (root.path("content").isArray()) {
            return true;
        }
        JsonNode embedded = root.path("_embedded");
        if (embedded.isObject() && !firstArrayItems(embedded).isEmpty()) {
            // Single HAL resources may contain _embedded relations; only treat as collection
            // if the root lacks typical single-resource identifier fields.
            return !root.has("id") && !root.has("controllerId");
        }
        return false;
    }

    protected static ObjectNode toFlatPage(JsonNode root) {
        ArrayNode content = JsonNodeFactory.instance.arrayNode();
        if (root.isArray()) {
            root.forEach(item -> content.add(cleanHalFields(item)));
        } else if (root.has("_embedded")) {
            firstArrayItems(root.get("_embedded")).forEach(item -> content.add(cleanHalFields(item)));
        } else if (root.path("content").isArray()) {
            root.get("content").forEach(item -> content.add(cleanHalFields(item)));
        }

        ObjectNode formatted = JsonNodeFactory.instance.objectNode();
        formatted.set("content", content);
        formatted.put("total", intFieldOrDefault(root, "total",
                intFieldOrDefault(root.path("page"), "totalElements", content.size())));
        formatted.put("size", intFieldOrDefault(root, "size",
                intFieldOrDefault(root.path("page"), "size", content.size())));
        return formatted;
    }

    /**
     * Recursively removes {@code _links} and {@code _embedded} from HAL JSON,
     * copying any mapped links to explicit fields (e.g. {@code distributionSetId}).
     */
    protected static JsonNode cleanHalFields(JsonNode node) {
        return switch (node) {
            case ArrayNode array -> {
                ArrayNode formatted = JsonNodeFactory.instance.arrayNode();
                array.forEach(item -> formatted.add(cleanHalFields(item)));
                yield formatted;
            }
            case ObjectNode object -> {
                ObjectNode formatted = JsonNodeFactory.instance.objectNode();
                for (Map.Entry<String, JsonNode> field : object.properties()) {
                    if ("_links".equals(field.getKey()) || "_embedded".equals(field.getKey())) {
                        continue;
                    }
                    formatted.set(field.getKey(), cleanHalFields(field.getValue()));
                }
                copyMappedHalLinkFields(object.path("_links"), formatted);
                yield formatted;
            }
            default -> node;
        };
    }

    protected static void copyMappedHalLinkFields(JsonNode links, ObjectNode target) {
        if (!links.isObject()) {
            return;
        }

        HAL_LINK_FIELDS.forEach((rel, mapping) -> {
            JsonNode link = links.path(rel);
            Long id = extractIdFromHref(link.path("href"));
            if (id != null && !target.has(mapping.idField())) {
                target.put(mapping.idField(), id);
            }
            JsonNode name = link.path("name");
            if (name.isTextual() && !target.has(mapping.nameField())) {
                target.put(mapping.nameField(), name.textValue());
            }
        });
    }

    protected static Long extractIdFromHref(JsonNode hrefNode) {
        if (!hrefNode.isTextual()) {
            return null;
        }

        String href = hrefNode.textValue();
        int end = href.endsWith("/") ? href.length() - 1 : href.length();
        int start = href.lastIndexOf('/', end - 1) + 1;
        if (start >= end) {
            return null;
        }
        try {
            return Long.parseLong(href.substring(start, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    protected static ArrayNode firstArrayItems(JsonNode embedded) {
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

    protected static int intFieldOrDefault(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.intValue() : defaultValue;
    }

    /**
     * Replaces {@link java.util.function.Supplier} to allow checked exceptions
     * to propagate without {@code UndeclaredThrowableException} wrapping.
     */
    @FunctionalInterface
    public interface HawkbitCall {
        Response execute() throws Exception;
    }

    protected record LinkFieldMapping(String idField, String nameField) {
    }
}
