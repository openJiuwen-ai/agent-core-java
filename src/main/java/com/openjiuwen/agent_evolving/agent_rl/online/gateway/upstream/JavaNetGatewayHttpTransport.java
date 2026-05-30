/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * JDK HttpClient-backed gateway transport.
 * <p>
 * Mirrors Python's concrete HTTPX transport wiring for upstream calls.
 */
public class JavaNetGatewayHttpTransport implements GatewayHttpTransport, AutoCloseable {

    private final HttpClient httpClient;

    public JavaNetGatewayHttpTransport(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GatewayHttpResponse send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new GatewayHttpResponse(response.statusCode(), response.body());
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
