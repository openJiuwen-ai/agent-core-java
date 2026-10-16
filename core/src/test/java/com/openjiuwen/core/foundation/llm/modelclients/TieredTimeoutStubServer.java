/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

/**
 * Scriptable stub model endpoint for timeout tiering tests. Supports delayed
 * response headers, per-segment gaps, silent (hung) connections, and slow blocking bodies,
 * so tests can drive the connect, header-wait, and stream-idle timing domains independently.
 *
 * @since 2026-10-09
 */
final class TieredTimeoutStubServer implements AutoCloseable {
    private static final long HANG_AFTER_SCRIPT_MILLIS = 30_000L;

    private final HttpServer server;
    private final StubScript script;

    private TieredTimeoutStubServer(StubScript script) throws IOException {
        this.script = script;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/chat/completions", this::handle);
        this.server.createContext("/v1/chat/completions", this::handle);
        this.server.start();
    }

    /**
     * Starts a stub server with the given script on a random local port.
     *
     * @param script behavior script
     * @return running stub server
     */
    static TieredTimeoutStubServer start(StubScript script) {
        try {
            return new TieredTimeoutStubServer(script);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Builds a minimal fake streaming response for test subclasses overriding the new
     * {@code postStream} signatures; the body is the joined lines as an input stream.
     *
     * @param status HTTP status code
     * @param lines response body lines
     * @return fake streaming response
     */
    static HttpResponse<InputStream> fakeStreamingResponse(int status, List<String> lines) {
        byte[] bytes = String.join("\n", lines).getBytes(StandardCharsets.UTF_8);
        InputStream body = new ByteArrayInputStream(bytes);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1/")).build();
        HttpHeaders headers = HttpHeaders.of(Map.of(), (name, value) -> true);
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public InputStream body() {
                return body;
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return request.uri();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    /**
     * Builds an SSE data line for an assistant content delta.
     *
     * @param content delta content
     * @return SSE data payload
     */
    static String sseContent(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    /**
     * The SSE terminal frame.
     *
     * @return [DONE] frame
     */
    static String sseDone() {
        return "data: [DONE]\n\n";
    }

    /**
     * A minimal blocking chat completion body.
     *
     * @param content assistant content
     * @return JSON body
     */
    static String blockingBody(String content) {
        return "{\"id\":\"stub\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
                + content + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    /**
     * Base URL of the stub endpoint (without path).
     *
     * @return base URL
     */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        sleepQuietly(script.headerDelayMillis);
        exchange.getResponseHeaders().add("Content-Type", script.contentType);
        exchange.sendResponseHeaders(script.status, 0);
        try (exchange) {
            if (script.status != 200) {
                exchange.getResponseBody().write("{\"error\":\"stub failure\"}".getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                return;
            }
            List<String> events = script.events;
            for (int index = 0; index < events.size(); index++) {
                writeEvent(exchange, events.get(index));
                if (index < events.size() - 1 || script.isSilentAfterEvents) {
                    sleepQuietly(script.gapMillis);
                }
            }
            if (script.isSilentAfterEvents) {
                sleepQuietly(Math.max(script.gapMillis, HANG_AFTER_SCRIPT_MILLIS));
            }
        }
    }

    private void writeEvent(HttpExchange exchange, String event) throws IOException {
        byte[] bytes = event.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseBody().write(bytes);
        exchange.getResponseBody().flush();
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            // Stub teardown may interrupt the hang; swallowing is intentional in tests.
            return;
        }
    }

    /**
     * Behavior script for the stub endpoint.
     */
    static final class StubScript {
        private int status = 200;
        private String contentType = "text/event-stream";
        private long headerDelayMillis;
        private List<String> events = List.of();
        private long gapMillis;
        private boolean isSilentAfterEvents;

        /**
         * Sets the HTTP status returned after the header delay.
         *
         * @param status HTTP status
         * @return this script
         */
        StubScript status(int status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the response content type.
         *
         * @param contentType content type
         * @return this script
         */
        StubScript contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * Sets the delay before response headers are sent.
         *
         * @param headerDelayMillis delay in milliseconds
         * @return this script
         */
        StubScript headerDelayMillis(long headerDelayMillis) {
            this.headerDelayMillis = headerDelayMillis;
            return this;
        }

        /**
         * Sets the raw payloads written sequentially to the body (SSE lines included as-is).
         *
         * @param events body payloads
         * @return this script
         */
        StubScript events(List<String> events) {
            this.events = events;
            return this;
        }

        /**
         * Sets the gap after each payload and before the silent hang.
         *
         * @param gapMillis gap in milliseconds
         * @return this script
         */
        StubScript gapMillis(long gapMillis) {
            this.gapMillis = gapMillis;
            return this;
        }

        /**
         * When true, the connection hangs silently after the scripted payloads.
         *
         * @return this script
         */
        StubScript silentAfterEvents() {
            this.isSilentAfterEvents = true;
            return this;
        }
    }
}
