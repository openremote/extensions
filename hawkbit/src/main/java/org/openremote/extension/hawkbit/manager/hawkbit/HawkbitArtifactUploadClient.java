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
package org.openremote.extension.hawkbit.manager.hawkbit;

import org.openremote.extension.hawkbit.model.firmware.FirmwareArtifact;
import org.openremote.model.util.TextUtil;
import org.openremote.model.util.ValueUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class HawkbitArtifactUploadClient {

    protected final URI hawkbitManagementUri;
    protected final String hawkbitUsername;
    protected final String hawkbitPassword;

    public HawkbitArtifactUploadClient(URI hawkbitManagementUri, String hawkbitUsername, String hawkbitPassword) {
        this.hawkbitManagementUri = hawkbitManagementUri;
        this.hawkbitUsername = hawkbitUsername;
        this.hawkbitPassword = hawkbitPassword;
    }

    public FirmwareArtifact uploadSoftwareModuleArtifact(Long softwareModuleId, InputStream inputStream,
                                                         String originalFilename, String filename)
            throws IOException, InterruptedException {
        String effectiveFilename = TextUtil.isNullOrEmpty(filename) ? originalFilename : filename;
        String boundary = "----OpenRemoteBoundary" + UUID.randomUUID();
        byte[] payload = buildMultipartPayload(boundary, inputStream.readAllBytes(), effectiveFilename);

        HttpRequest request = HttpRequest.newBuilder(
                        buildArtifactUploadUri(softwareModuleId, effectiveFilename))
                .header("Authorization", HawkbitBasicAuth.buildAuthorizationHeader(hawkbitUsername, hawkbitPassword))
                .header("Accept", "application/hal+json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Artifact upload failed with status " + response.statusCode() + ": " + response.body());
        }

        return ValueUtil.JSON.readValue(response.body(), FirmwareArtifact.class);
    }

    protected URI buildArtifactUploadUri(Long softwareModuleId, String filename) {
        StringBuilder uriBuilder = new StringBuilder(hawkbitManagementUri.toString())
                .append("/softwaremodules/")
                .append(softwareModuleId)
                .append("/artifacts");

        appendQueryParam(uriBuilder, "filename", filename, true);
        return URI.create(uriBuilder.toString());
    }

    protected boolean appendQueryParam(StringBuilder uriBuilder, String name, String value, boolean first) {
        if (TextUtil.isNullOrEmpty(value)) {
            return first;
        }

        uriBuilder.append(first ? '?' : '&')
                .append(name)
                .append('=')
                .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        return false;
    }

    protected byte[] buildMultipartPayload(String boundary, byte[] fileBytes, String filename) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + escapeQuoted(filename)
                + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(fileBytes);
        outputStream.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return outputStream.toByteArray();
    }

    protected String escapeQuoted(String value) {
        return value == null ? "artifact.bin" : value.replace("\"", "\\\"");
    }
}
