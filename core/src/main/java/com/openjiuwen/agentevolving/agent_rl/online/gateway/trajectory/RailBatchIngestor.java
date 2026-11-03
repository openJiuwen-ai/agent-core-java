/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agentevolving.agent_rl.online.gateway.GatewayMessageUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Normalize rail-v1 uploads and stage samples for delayed judge scoring.
 * <p>
 * Mirrors Python's {@code RailBatchIngestor} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/rail_ingest.py}.
 */
public class RailBatchIngestor {

    private static final Logger LOGGER = Logger.getLogger("online_rl.gateway");

    private final PendingJudgeStore pendingJudgeStore;
    private final JudgeDispatcher judgeDispatcher;
    private final String defaultUserId;

    public RailBatchIngestor(PendingJudgeStore pendingJudgeStore, JudgeDispatcher judgeDispatcher, String defaultUserId) {
        this.pendingJudgeStore = pendingJudgeStore;
        this.judgeDispatcher = judgeDispatcher;
        this.defaultUserId = defaultUserId == null ? "" : defaultUserId;
    }

    public Map<String, Object> ingestRailBatch(Map<String, Object> payload) {
        if (!"rail-v1".equals(payload.get("protocol_version"))) {
            throw new IllegalArgumentException("unsupported protocol_version: " + payload.get("protocol_version"));
        }

        String sessionId = pythonString(firstTruthyValue(payload.get("session_id"), ""));
        String trajectoryId = pythonString(firstTruthyValue(payload.get("trajectory_id"), ""));
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
            Map<String, Object> sample = normalizeRawMap(rawMap);
            try {
                Map<String, Object> normalized = normalizeRailSample(payload, sample, defaultUserId);
                pendingJudgeStore.put(normalized);
                accepted += 1;
            } catch (Exception exception) {
                rejected += 1;
                if (firstError == null) {
                    firstError = exception.getMessage();
                }
                LOGGER.log(
                        Level.WARNING,
                        "[Gateway] rail-v1 sample rejected trajectory={0} err={1}",
                        new Object[]{trajectoryId, exception.getMessage()}
                );
            }
        }

        if (accepted == 0 && rejected > 0) {
            throw new IllegalArgumentException(firstError == null ? "all rail-v1 samples were rejected" : firstError);
        }

        int sessionFlushed = 0;
        if (pythonBool(payload.get("session_done"))) {
            sessionFlushed = judgeDispatcher.onSessionDone(sessionId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocol_version", "rail-v1");
        result.put("session_id", sessionId);
        result.put("trajectory_id", trajectoryId);
        result.put("accepted", accepted);
        result.put("rejected", rejected);
        result.put("judged", judged);
        result.put("session_flushed", sessionFlushed);
        return result;
    }

    public static Map<String, Object> normalizeRailSample(Map<String, Object> payload,
                                                          Map<String, Object> sample,
                                                          String defaultUserId) {
        String sessionId = pythonString(firstTruthyValue(sample.get("session_id"), payload.get("session_id"), ""));
        String trajectoryId = pythonString(firstTruthyValue(sample.get("trajectory_id"), payload.get("trajectory_id"), ""));
        int stepIndex = intValue(sample.get("step_index"), 0);
        Map<String, Object> trajectoryMeta = mapValue(payload.get("trajectory_meta"));
        Object messagesRaw = sample.get("messages");
        if (!(messagesRaw instanceof List<?> rawMessages) || rawMessages.isEmpty()) {
            throw new IllegalArgumentException("messages must be a non-empty list");
        }
        List<Map<String, Object>> messages = listOfMaps(rawMessages);
        Map<String, Object> response = sample.get("response") instanceof Map<?, ?> rawResponse
                ? normalizeRawMap(rawResponse)
                : new LinkedHashMap<>();
        String responseText = pythonString(firstTruthyValue(
                sample.get("response_text"),
                response.get("content"),
                ""
        ));
        Object tools = sample.get("tools");
        Object renderFingerprintExpected = sample.get("render_fingerprint_expected");
        if (renderFingerprintExpected != null
                && !pythonString(sample.get("render_fingerprint")).equals(String.valueOf(renderFingerprintExpected))) {
            throw new IllegalArgumentException("render_fingerprint mismatch");
        }

        Object promptIdsRaw = sample.get("prompt_ids");
        if (!(promptIdsRaw instanceof List<?> promptIdList)) {
            throw new IllegalArgumentException("missing prompt_ids; rail-v1 samples must provide prompt token ids");
        }
        List<Integer> promptIds = toIntList(promptIdList);
        String promptText = pythonString(firstTruthyValue(sample.get("prompt_text"), ""));

        Object responseIdsRaw = sample.get("response_tokens");
        if (!(responseIdsRaw instanceof List<?> responseIdList)) {
            throw new IllegalArgumentException("missing response_tokens; rail-v1 samples must provide response token ids");
        }
        List<Integer> responseIds = toIntList(responseIdList);
        List<Double> responseLogprobs = SamplePayloads.coerceLogprobs(sample.get("logprobs"), responseIds.size());

        String userId = pythonString(firstTruthyValue(
                sample.get("user_id"),
                payload.get("user_id"),
                payload.get("tenant_id"),
                sample.get("tenant_id"),
                defaultUserId == null ? "" : defaultUserId,
                ""
        )).trim();
        if (userId.isBlank()) {
            throw new IllegalArgumentException("missing user_id/tenant_id; upload batch samples require a stable user id");
        }

        int turnNum = stepIndex + 1;
        Map<String, Object> railMeta = new LinkedHashMap<>();
        railMeta.put("protocol_version", "rail-v1");
        railMeta.put("sample_meta", mapValue(sample.get("meta")));
        railMeta.put("trajectory_meta", trajectoryMeta);
        railMeta.put("instruction_text", GatewayMessageUtils.extractLastUserInstruction(messages));

        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("trajectory_id", trajectoryId);
        extraFields.put("step_index", stepIndex);
        extraFields.put("rail_meta", railMeta);

        return SamplePayloads.buildSample(
                userId,
                sessionId,
                turnNum,
                "rail-v1",
                "rail",
                firstTruthyValue(sample.get("model_id"), payload.get("model_id")),
                messages,
                tools,
                response,
                mapValue(response.get("usage")),
                stringOrNull(response.get("finish_reason")),
                promptText,
                promptIds,
                responseText,
                responseIds,
                responseLogprobs,
                listOfMaps(response.get("tool_calls")),
                null,
                trajectoryId + ":" + stepIndex,
                null,
                extraFields
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            return normalizeRawMap(rawMap);
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                out.add(normalizeRawMap(rawMap));
            }
        }
        return out;
    }

    private static Map<String, Object> normalizeRawMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static List<Integer> toIntList(List<?> values) {
        List<Integer> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(intValue(value, 0));
        }
        return result;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean pythonBool(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof CharSequence text) {
            return !text.isEmpty();
        }
        if (value instanceof List<?> list) {
            return !list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static Object firstTruthyValue(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (pythonBool(value)) {
                return value;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stringOrDefault(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String pythonString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
