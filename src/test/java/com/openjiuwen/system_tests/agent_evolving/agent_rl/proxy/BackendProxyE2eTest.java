/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.agent_evolving.agent_rl.proxy;

import com.openjiuwen.agent_evolving.agent_rl.RlProxy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * System tests for BackendProxy.
 *
 * <p>Mirrors Python's {@code test_backend_proxy_e2e.py} in
 * {@code tests/system_tests/agent_evolving/agent_rl/proxy}.
 */
class BackendProxyE2eTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void testStartThenHealthReturns200() throws Exception {
        RlProxy.BackendProxy proxy = new RlProxy.BackendProxy();
        proxy.start();
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.url() + "/health")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
        } finally {
            proxy.close();
        }
    }

    @Test
    void testUpdateBackendsViaPostProxyBackends() throws Exception {
        RlProxy.BackendProxy proxy = new RlProxy.BackendProxy();
        proxy.start();
        try {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(proxy.url() + "/proxy/backends"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString("{\"servers\":[\"http://127.0.0.1:8000\"]}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals(List.of("http://127.0.0.1:8000"), proxy.backendServers());
        } finally {
            proxy.close();
        }
    }
}
