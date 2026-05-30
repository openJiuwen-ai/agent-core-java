/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RlOnlineRailFactoryTest {

    @Test
    void envTruthinessMatchesPythonFactory() {
        assertAll(
                () -> assertTrue(RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of("USE_RL_ONLINE_RAIL", "1"))),
                () -> assertTrue(RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of("USE_RL_ONLINE_RAIL", " TRUE "))),
                () -> assertTrue(RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of("USE_RL_ONLINE_RAIL", "yes"))),
                () -> assertTrue(RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of("USE_RL_ONLINE_RAIL", "On"))),
                () -> assertEquals(false, RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of())),
                () -> assertEquals(false, RlOnlineRailFactory.isRlOnlineRailEnabledFromEnv(Map.of("USE_RL_ONLINE_RAIL", "0")))
        );
    }

    @Test
    void disabledEnvReturnsNullAndGatewayHelpersKeepPythonDefaults() {
        assertNull(RlOnlineRailFactory.buildRlOnlineRailFromEnv(Map.of()));
        assertEquals("http://127.0.0.1:18080", RlOnlineRailFactory.getGatewayUrlFromEnv(Map.of()));
        assertEquals("", RlOnlineRailFactory.getGatewayUrlFromEnv(Map.of("TRAJECTORY_GATEWAY_URL", "")));
        assertEquals("", RlOnlineRailFactory.getApiKeyFromEnv(Map.of()));
        assertNull(RlOnlineRailFactory.getTenantIdFromEnv(Map.of("RL_ONLINE_TENANT_ID", "   ")));
    }

    @Test
    void enabledEnvBuildsRailWithGatewayUploader() {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("USE_RL_ONLINE_RAIL", "yes");
        env.put("TRAJECTORY_GATEWAY_URL", "http://gateway.local///");
        env.put("TRAJECTORY_GATEWAY_API_KEY", "");
        env.put("RL_ONLINE_TENANT_ID", " tenant-a ");

        RLOnlineRail rail = RlOnlineRailFactory.buildRlOnlineRailFromEnv(env);

        assertNotNull(rail);
        assertEquals("", readField(rail, "sessionId"));
        assertEquals("http://gateway.local", readField(rail, "gatewayEndpoint"));
        assertEquals("tenant-a", readField(rail, "tenantId"));
        Object uploader = readField(rail, "uploader");
        assertTrue(uploader instanceof GatewayTrajectoryUploader);
        GatewayTrajectoryUploader gatewayUploader = (GatewayTrajectoryUploader) uploader;
        assertEquals("http://gateway.local", gatewayUploader.getGatewayEndpoint());
        assertEquals("", gatewayUploader.getApiKey());
    }

    @Test
    void gatewayUploaderPostsBatchToGatewayEndpoint() throws Exception {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/v1/gateway/upload/batch", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(202, 0);
            exchange.getResponseBody().close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            GatewayTrajectoryUploader uploader = new GatewayTrajectoryUploader(
                    baseUrl,
                    "secret",
                    HttpClient.newHttpClient(),
                    Duration.ofSeconds(5));
            OnlineRlBatch batch = new OnlineRlBatch();
            batch.setTenantId("tenant-1");
            OnlineRlSample sample = new OnlineRlSample();
            sample.setResponseText("hello");
            batch.getSamples().add(sample);

            uploader.enqueue(batch);

            assertEquals("/v1/gateway/upload/batch", path.get());
            assertEquals("Bearer secret", authorization.get());
            assertTrue(body.get().contains("\"tenant_id\":\"tenant-1\""));
            assertTrue(body.get().contains("\"response_text\":\"hello\""));
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException exception) {
                throw new AssertionError(exception);
            }
        }
        throw new AssertionError("field not found: " + fieldName);
    }
}
