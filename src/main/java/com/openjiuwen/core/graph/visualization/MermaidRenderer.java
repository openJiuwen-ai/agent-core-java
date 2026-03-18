/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.graph.visualization;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders Mermaid diagram text to PNG or SVG via the mermaid.ink public service.
 * <p>
 * Mirrors the Python {@code Mermaid} class that uses mermaid.ink for rendering.
 */
final class MermaidRenderer {

    private static final Logger LOGGER = Logger.getLogger(MermaidRenderer.class.getName());
    private static final String MERMAID_INK_BASE = "https://mermaid.ink";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private MermaidRenderer() {
    }

    /**
     * Render Mermaid syntax as PNG bytes.
     *
     * @param mermaidSyntax the Mermaid diagram text
     * @return PNG bytes, or empty array on failure
     */
    static byte[] renderPng(String mermaidSyntax) {
        return render(mermaidSyntax, "/img/");
    }

    /**
     * Render Mermaid syntax as SVG bytes.
     *
     * @param mermaidSyntax the Mermaid diagram text
     * @return SVG bytes, or empty array on failure
     */
    static byte[] renderSvg(String mermaidSyntax) {
        return render(mermaidSyntax, "/svg/");
    }

    private static byte[] render(String mermaidSyntax, String pathPrefix) {
        try {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mermaidSyntax.getBytes(StandardCharsets.UTF_8));
            URI uri = URI.create(MERMAID_INK_BASE + pathPrefix + encoded);

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                return response.body();
            }
            LOGGER.log(Level.WARNING, "Mermaid rendering returned status {0}", response.statusCode());
            return new byte[0];
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to render Mermaid diagram", e);
            return new byte[0];
        }
    }
}
