/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JDK HttpClient-backed gateway transport.
 * <p>
 * Mirrors Python's concrete upstream transport wiring in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/upstream/upstream_client.py}.
 */
public class JavaNetGatewayHttpTransport implements GatewayHttpTransport, AutoCloseable {

    private final HttpClient httpClient;

    public JavaNetGatewayHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GatewayHttpResponse send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name, values.getFirst());
            }
        });
        return new GatewayHttpResponse(
            response.statusCode(),
            response.body(),
            headers,
            response.headers().firstValue("content-type").orElse(null)
        );
    }

    @Override
    public void close() {
        // java.net.http.HttpClient has no close hook; keep method for symmetry with injected transports.
    }
}
