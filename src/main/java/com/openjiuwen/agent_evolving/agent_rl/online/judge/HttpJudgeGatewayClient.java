/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.judge;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.GatewayHttpResponse;
import com.openjiuwen.agent_evolving.agent_rl.online.gateway.upstream.UpstreamGatewayClient;

import java.util.List;
import java.util.Map;

/**
 * HTTP-backed judge scoring client.
 * <p>
 * Mirrors the gateway-facing judge score call pattern while avoiding full server bootstrap.
 */
public class HttpJudgeGatewayClient implements JudgeGatewayClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final UpstreamGatewayClient upstreamGatewayClient;
    private final String judgeUrl;
    private final String apiKey;

    public HttpJudgeGatewayClient(UpstreamGatewayClient upstreamGatewayClient, String judgeUrl, String apiKey) {
        this.upstreamGatewayClient = upstreamGatewayClient;
        this.judgeUrl = judgeUrl;
        this.apiKey = apiKey != null ? apiKey : "";
    }

    @Override
    public ScoreResponse score(ScoreRequest request) {
        Map<String, String> headers = apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
        GatewayHttpResponse response = upstreamGatewayClient.request(
                "POST",
                trimTrailingSlash(judgeUrl) + "/score",
                Map.of(),
                headers,
                writeJson(Map.of(
                        "response_text", request.responseText(),
                        "instruction_text", request.instructionText(),
                        "followup_user_feedback", request.followupUserFeedback(),
                        "session_id", request.sessionId(),
                        "turn_num", request.turnNum()
                ))
        );
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Judge request failed: HTTP " + response.statusCode() + ": " + response.body());
        }
        try {
            Map<String, Object> payload = OBJECT_MAPPER.readValue(response.body(), MAP_TYPE);
            return new ScoreResponse(
                    ((Number) payload.getOrDefault("score", 0.0)).doubleValue(),
                    ((Number) payload.getOrDefault("overall_raw", 0.0)).doubleValue(),
                    ((List<?>) payload.getOrDefault("votes", List.of())).stream().map(value -> ((Number) value).doubleValue()).toList(),
                    payload.get("details"),
                    String.valueOf(payload.getOrDefault("model", "")),
                    String.valueOf(payload.getOrDefault("session_id", "")),
                    ((Number) payload.getOrDefault("turn_num", 0)).intValue()
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to parse judge response body", exception);
        }
    }

    private static String trimTrailingSlash(String value) {
        String safe = value != null ? value : "";
        while (safe.endsWith("/")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        return safe;
    }

    private static byte[] writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to serialize judge request body", exception);
        }
    }
}
