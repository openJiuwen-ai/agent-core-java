/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.visualization;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Renders Mermaid diagram text to PNG or SVG via the mermaid.ink public service.
 * <p>
 * Mirrors the Python {@code Mermaid} class that uses mermaid.ink for rendering.
 */
final class MermaidRenderer {
    private static final Logger LOGGER = Logger.getLogger(MermaidRenderer.class.getName());
    private static final String DEFAULT_MERMAID_INK_BASE = "https://mermaid.ink";

    /**
     * Duration.OfSeconds.
     *
     * @since 0.1.7
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_ATTEMPTS = 3;

    /**
     * MermaidRenderer.
     *
     * @since 0.1.7
     */
    private MermaidRenderer() {
    }

    /**
     * Render Mermaid syntax as PNG bytes.
     *
     * @param mermaidSyntax the Mermaid diagram text
     * @return PNG bytes
     */
    static byte[] renderPng(String mermaidSyntax) {
        return render(mermaidSyntax, "/img/");
    }

    /**
     * Render Mermaid syntax as SVG bytes.
     *
     * @param mermaidSyntax the Mermaid diagram text
     * @return SVG bytes
     */
    static byte[] renderSvg(String mermaidSyntax) {
        return render(mermaidSyntax, "/svg/");
    }

    /**
     * resolveMermaidInkBase.
     *
     * @return the result
     * @since 0.1.7
     */
    private static String resolveMermaidInkBase() {
        String fromProperty = System.getProperty("MERMAID_INK_SERVER");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return stripTrailingSlash(fromProperty.trim());
        }
        String fromEnv = System.getenv("MERMAID_INK_SERVER");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return stripTrailingSlash(fromEnv.trim());
        }
        return DEFAULT_MERMAID_INK_BASE;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * render.
     *
     * @param mermaidSyntax mermaidSyntax
     * @param pathPrefix pathPrefix
     * @return the result
     * @since 0.1.7
     */
    private static byte[] render(String mermaidSyntax, String pathPrefix) {
        String baseUrl = resolveMermaidInkBase();
        HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

        String encoded =
            Base64.getUrlEncoder().withoutPadding().encodeToString(mermaidSyntax.getBytes(StandardCharsets.UTF_8));
        URI uri = URI.create(baseUrl + pathPrefix + encoded);

        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder().uri(uri).timeout(TIMEOUT).GET().build();

                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    return response.body();
                }
                LOGGER.log(Level.WARNING, "Mermaid rendering returned status {0} on attempt {1}",
                        new Object[]{response.statusCode(), attempt});
            } catch (Exception e) {
                lastException = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                LOGGER.log(Level.WARNING, "Retry Mermaid rendering after failure on attempt {0}", attempt);
            }
        }
        Optional<ConnectException> connectException = findConnectException(lastException);
        if (connectException.isPresent()) {
            ConnectException rootCause = new ConnectException(
                    "Failed to establish a new connection to " + baseUrl);
            throw new MermaidRenderException(rootCause.getMessage(), rootCause);
        }
        throw new MermaidRenderException("Failed to render Mermaid diagram via " + baseUrl, lastException);
    }

    /**
     * findConnectException.
     *
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private static Optional<ConnectException> findConnectException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException connectException) {
                return Optional.of(connectException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * MermaidRenderException.
     *
     * @since 0.1.7
     */
    static final class MermaidRenderException extends RuntimeException {
        /**
         * MermaidRenderException.
         *
         * @param message message
         * @param cause cause
         * @since 0.1.7
         */
        MermaidRenderException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
