/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.gateway.trajectory;

import com.openjiuwen.agent_evolving.agent_rl.online.gateway.GatewayCommon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared helpers for normalized gateway sample payloads.
 * <p>
 * Mirrors Python's helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/online/gateway/trajectory/sample_payloads.py}.
 */
public final class SamplePayloads {

    private SamplePayloads() {
    }

    public static List<Double> coerceLogprobs(Object values, int expectedLen) {
        List<Double> out = new ArrayList<>();
        if (values instanceof List<?> list) {
            for (Object item : list) {
                try {
                    out.add(Double.parseDouble(String.valueOf(item)));
                } catch (Exception ignored) {
                    // Python skips malformed values.
                }
            }
        }
        return GatewayCommon.fitList(out, expectedLen);
    }

    public static Map<String, Object> buildSample(String userId,
                                                  String sessionId,
                                                  int turnNum,
                                                  String mode,
                                                  String ioMode,
                                                  Object model,
                                                  List<Map<String, Object>> messages,
                                                  Object tools,
                                                  Map<String, Object> assistantMessage,
                                                  Map<String, Object> usage,
                                                  String finishReason,
                                                  String promptText,
                                                  List<Integer> promptIds,
                                                  String responseText,
                                                  List<Integer> responseIds,
                                                  List<Double> responseLogprobs,
                                                  List<Map<String, Object>> toolCalls,
                                                  Map<String, Object> requestExtras,
                                                  String sampleId,
                                                  String createdAt,
                                                  Map<String, Object> extraFields) {
        List<Integer> inputIds = new ArrayList<>(promptIds);
        inputIds.addAll(responseIds);
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("sample_id", sampleId != null ? sampleId : UUID.randomUUID().toString());
        sample.put("created_at", createdAt != null ? createdAt : GatewayCommon.utcNowIso());
        sample.put("user_id", userId);
        sample.put("session_id", sessionId);
        sample.put("turn_num", turnNum);
        sample.put("mode", mode);
        sample.put("io_mode", ioMode);
        sample.put("model", model);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messages", messages);
        request.put("tools", tools);
        if (requestExtras != null) {
            request.putAll(requestExtras);
        }
        sample.put("request", request);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", assistantMessage);
        response.put("usage", usage);
        response.put("finish_reason", finishReason);
        sample.put("response", response);

        Map<String, Object> trajectory = new LinkedHashMap<>();
        trajectory.put("input_ids", inputIds);
        trajectory.put("attention_mask", java.util.Collections.nCopies(inputIds.size(), 1));
        List<Integer> responseMask = new ArrayList<>(java.util.Collections.nCopies(promptIds.size(), 0));
        responseMask.addAll(java.util.Collections.nCopies(responseIds.size(), 1));
        trajectory.put("response_mask", responseMask);
        trajectory.put("prompt_text", promptText);
        trajectory.put("prompt_ids", promptIds);
        trajectory.put("response_text", responseText);
        trajectory.put("response_ids", responseIds);
        trajectory.put("response_logprobs", responseLogprobs);
        trajectory.put("tool_calls", toolCalls);
        sample.put("trajectory", trajectory);

        if (extraFields != null) {
            sample.putAll(extraFields);
        }
        return sample;
    }
}
