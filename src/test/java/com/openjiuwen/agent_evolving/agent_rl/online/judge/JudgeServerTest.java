/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @Test
    void healthzReturnsConfiguredModelAndVotes() throws Exception {
        JudgeConfig config = new JudgeConfig("http://llm.local", "judge-model");
        config.setNumVotes(3);

        try (JudgeServer server = new JudgeServer("127.0.0.1", 0, config,
                request -> new ScoreResponse(0.6, 8.0, List.of(8.0), Map.of("overall", 8.0), "judge-model", request.sessionId(), request.turnNum()))) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/healthz"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> payload = OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
            assertEquals(200, response.statusCode());
            assertEquals(true, payload.get("ok"));
            assertEquals("judge-model", payload.get("model"));
            assertEquals(3, ((Number) payload.get("num_votes")).intValue());
        }
    }

    @Test
    void scoreRequiresBearerTokenWhenConfigured() throws Exception {
        JudgeConfig config = new JudgeConfig("http://llm.local", "judge-model");
        config.setExpectedApiKey("secret");

        try (JudgeServer server = new JudgeServer("127.0.0.1", 0, config,
                request -> new ScoreResponse(0.6, 8.0, List.of(8.0), Map.of("overall", 8.0), "judge-model", request.sessionId(), request.turnNum()))) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String body = "{\"response_text\":\"resp\"}";

            HttpResponse<String> missing = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/score"))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            HttpResponse<String> invalid = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/score"))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer wrong")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            assertEquals(401, missing.statusCode());
            assertTrue(missing.body().contains("missing bearer token"));
            assertEquals(403, invalid.statusCode());
            assertTrue(invalid.body().contains("invalid bearer token"));
        }
    }

    @Test
    void scoreReturnsHandlerPayloadForValidRequest() throws Exception {
        JudgeConfig config = new JudgeConfig("http://llm.local", "judge-model");
        config.setExpectedApiKey("secret");
        AtomicReference<ScoreRequest> captured = new AtomicReference<>();

        try (JudgeServer server = new JudgeServer("127.0.0.1", 0, config, request -> {
            captured.set(request);
            return new ScoreResponse(0.75, 8.75, List.of(8.5, 9.0), Map.of("overall", 8.75), "judge-model", request.sessionId(), request.turnNum());
        })) {
            server.start();
            HttpClient client = HttpClient.newHttpClient();
            String body = "{" +
                    "\"response_text\":\"resp\"," +
                    "\"instruction_text\":\"inst\"," +
                    "\"followup_user_feedback\":\"next\"," +
                    "\"session_id\":\"s1\"," +
                    "\"turn_num\":2" +
                    "}";

            HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + server.getPort() + "/score"))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer secret")
                            .build(),
                    HttpResponse.BodyHandlers.ofString());

            Map<String, Object> payload = OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
            assertEquals(200, response.statusCode());
            assertEquals("inst", captured.get().instructionText());
            assertEquals("s1", captured.get().sessionId());
            assertEquals(0.75, ((Number) payload.get("score")).doubleValue());
            assertEquals("judge-model", payload.get("model"));
            assertEquals(2, ((Number) payload.get("turn_num")).intValue());
        }
    }
}
