/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * rail-v1 upload batch ingestion for the online-RL gateway.
 * <p>
 * Mirrors the deterministic normalization and staging behavior in
 * {@code openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory.rail_ingest}.
 */
public class RailBatchIngestor {

    private final PendingJudgeStore pendingJudgeStore;
    private final JudgeDispatcher judgeDispatcher;
    private final String defaultUserId;

    public RailBatchIngestor(PendingJudgeStore pendingJudgeStore, JudgeDispatcher judgeDispatcher, String defaultUserId) {
        this.pendingJudgeStore = pendingJudgeStore;
        this.judgeDispatcher = judgeDispatcher;
        this.defaultUserId = defaultUserId != null ? defaultUserId : "";
    }

    public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
        if (!"rail-v1".equals(payload.get("protocol_version"))) {
            throw new IllegalArgumentException("unsupported protocol_version: " + payload.get("protocol_version"));
        }
        String sessionId = String.valueOf(payload.getOrDefault("session_id", ""));
        String trajectoryId = String.valueOf(payload.getOrDefault("trajectory_id", ""));
        Object samplesRaw = payload.get("samples");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id is required");
        }
        if (trajectoryId.isBlank()) {
            throw new IllegalArgumentException("trajectory_id is required");
        }
        if (!(samplesRaw instanceof List<?> samples)) {
            throw new IllegalArgumentException("samples must be a list");
        }

        int judged = judgeDispatcher.onPrevFeedback(sessionId, mapValue(payload.get("prev_feedback")));
        int accepted = 0;
        int rejected = 0;
        String firstError = null;
        for (Object sampleRaw : samples) {
            if (!(sampleRaw instanceof Map<?, ?> rawMap)) {
                rejected += 1;
                if (firstError == null) {
                    firstError = "samples must contain only objects";
                }
                continue;
            }
            Map<String, Object> sample = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> sample.put(String.valueOf(key), value));
            try {
                Map<String, Object> normalized = normalizeRailSample(payload, sample, defaultUserId);
                pendingJudgeStore.put(normalized);
                accepted += 1;
            } catch (Exception exception) {
                rejected += 1;
                if (firstError == null) {
                    firstError = exception.getMessage();
                }
            }
        }
        if (accepted == 0 && rejected > 0) {
            throw new IllegalArgumentException(firstError != null ? firstError : "all rail-v1 samples were rejected");
        }

        int sessionFlushed = Boolean.TRUE.equals(payload.get("session_done")) ? judgeDispatcher.onSessionDone(sessionId) : 0;
        return Map.of(
                "protocol_version", "rail-v1",
                "session_id", sessionId,
                "trajectory_id", trajectoryId,
                "accepted", accepted,
                "rejected", rejected,
                "judged", judged,
                "session_flushed", sessionFlushed
        );
    }

    public static Map<String, Object> normalizeRailSample(Map<String, Object> payload, Map<String, Object> sample, String defaultUserId) {
        String sessionId = String.valueOf(sample.getOrDefault("session_id", payload.getOrDefault("session_id", "")));
        String trajectoryId = String.valueOf(sample.getOrDefault("trajectory_id", payload.getOrDefault("trajectory_id", "")));
        int stepIndex = intValue(sample.get("step_index"), 0);
        List<Map<String, Object>> messages = listOfMaps(sample.get("messages"));
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("messages must be a non-empty list");
        }
        Map<String, Object> response = mapValue(sample.get("response"));
        String responseText = String.valueOf(sample.getOrDefault("response_text", response.getOrDefault("content", "")));
        Object renderFingerprintExpected = sample.get("render_fingerprint_expected");
        if (renderFingerprintExpected != null && !String.valueOf(renderFingerprintExpected).equals(String.valueOf(sample.get("render_fingerprint")))) {
            throw new IllegalArgumentException("render_fingerprint mismatch");
        }

        Object promptIdsRaw = sample.get("prompt_ids");
        if (!(promptIdsRaw instanceof List<?> promptIdsList)) {
            throw new IllegalArgumentException("missing prompt_ids; rail-v1 samples must provide prompt token ids");
        }
        List<Integer> promptIds = toIntList(promptIdsList);
        String promptText = String.valueOf(sample.getOrDefault("prompt_text", ""));

        Object responseTokensRaw = sample.get("response_tokens");
        if (!(responseTokensRaw instanceof List<?> responseIdsList)) {
            throw new IllegalArgumentException("missing response_tokens; rail-v1 samples must provide response token ids");
        }
        List<Integer> responseIds = toIntList(responseIdsList);
        List<Double> responseLogprobs = SamplePayloads.coerceLogprobs(sample.get("logprobs"), responseIds.size());

        String userId = String.valueOf(
                sample.getOrDefault(
                        "user_id",
                        payload.getOrDefault(
                                "user_id",
                                payload.getOrDefault("tenant_id", sample.getOrDefault("tenant_id", defaultUserId != null ? defaultUserId : ""))
                        )
                )
        ).trim();
        if (userId.isBlank()) {
            throw new IllegalArgumentException("missing user_id; rail-v1 requires user or tenant id");
        }

        String model = String.valueOf(sample.getOrDefault("model", payload.getOrDefault("model", "")));
        String mode = String.valueOf(payload.getOrDefault("mode", "online"));
        String ioMode = String.valueOf(payload.getOrDefault("io_mode", "assistant_response"));
        List<Map<String, Object>> toolCalls = listOfMaps(sample.get("tool_calls"));
        Map<String, Object> usage = mapValue(sample.get("usage"));
        String finishReason = sample.get("finish_reason") != null ? String.valueOf(sample.get("finish_reason")) : null;
        Map<String, Object> requestExtras = mapValue(sample.get("request_extras"));

        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("trajectory_id", trajectoryId);
        extraFields.put("step_index", stepIndex);

        return SamplePayloads.buildSample(
                userId,
                sessionId,
                intValue(sample.get("turn_num"), stepIndex),
                mode,
                ioMode,
                model,
                messages,
                sample.get("tools"),
                response,
                usage,
                finishReason,
                promptText,
                promptIds,
                responseText,
                responseIds,
                responseLogprobs,
                toolCalls,
                requestExtras,
                sample.get("sample_id") != null ? String.valueOf(sample.get("sample_id")) : null,
                sample.get("created_at") != null ? String.valueOf(sample.get("created_at")) : null,
                extraFields
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = new LinkedHashMap<>();
                rawMap.forEach((key, val) -> map.put(String.valueOf(key), val));
                out.add(map);
            }
        }
        return out;
    }

    private static List<Integer> toIntList(List<?> values) {
        List<Integer> out = new ArrayList<>();
        for (Object value : values) {
            out.add(intValue(value, 0));
        }
        return out;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
