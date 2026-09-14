/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RL training schemas and helpers.
 *
 * <p>Mirrors Python's module helpers in
 * {@code openjiuwen/agent_evolving/agent_rl/schemas.py}.</p>
 */
public final class RlSchemas {

    private RlSchemas() {
    }

    public static List<Rollout> trajectoryToRollouts(Trajectory trajectory) {
        List<Rollout> rollouts = new ArrayList<>();
        if (trajectory == null || trajectory.getSteps() == null) {
            return rollouts;
        }
        for (TrajectoryStep step : trajectory.getSteps()) {
            if (step == null || !"llm".equals(step.getKind())) {
                continue;
            }
            if (!(step.getDetail() instanceof LLMCallDetail detail)) {
                continue;
            }

            Map<String, Object> inputPrompt = new LinkedHashMap<>();
            inputPrompt.put("message", normalizeMessages(detail.getMessages()));
            inputPrompt.put("tools", normalizeTools(detail.getTools()));

            Rollout rollout = new Rollout();
            rollout.setTurnId(rollouts.size());
            rollout.setInputPrompt(inputPrompt);
            rollout.setOutputResponse(normalizeResponse(detail.getResponse()));
            rollout.setLlmConfig(extractLlmConfig(step.getMeta()));
            rollout.setInputPromptIds(emptyToNull(step.getPromptTokenIds()));
            rollout.setOutputResponseIds(emptyToNull(step.getCompletionTokenIds()));
            rollouts.add(rollout);
        }
        return rollouts;
    }

    private static List<Object> normalizeMessages(List<Object> rawMessages) {
        List<Object> normalized = new ArrayList<>();
        if (rawMessages == null) {
            return normalized;
        }
        for (Object message : rawMessages) {
            if (message instanceof Map<?, ?> map) {
                normalized.add(copyMap(map));
                continue;
            }
            Object dumped = modelDump(message);
            normalized.add(dumped != null ? dumped : message);
        }
        return normalized;
    }

    private static List<Object> normalizeTools(List<Map<String, Object>> rawTools) {
        if (rawTools == null) {
            return null;
        }
        List<Object> normalized = new ArrayList<>();
        for (Object tool : rawTools) {
            if (tool instanceof Map<?, ?> map) {
                normalized.add(copyMap(map));
                continue;
            }
            Object dumped = modelDump(tool);
            normalized.add(dumped != null ? dumped : tool);
        }
        return normalized;
    }

    private static Map<String, Object> normalizeResponse(Object rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        if (rawResponse instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        Object dumped = modelDump(rawResponse);
        if (dumped instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        if (dumped instanceof String stringValue) {
            return Map.of("role", "assistant", "content", stringValue);
        }
        return Map.of(
                "role", stringValue(readProperty(rawResponse, "role"), "assistant"),
                "content", stringValue(readProperty(rawResponse, "content"), "")
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extractLlmConfig(Map<String, Object> meta) {
        Object value = meta != null ? meta.get("llm_config") : null;
        return value instanceof Map<?, ?> map ? copyMap(map) : null;
    }

    private static List<Integer> emptyToNull(List<Integer> values) {
        return values == null || values.isEmpty() ? null : new ArrayList<>(values);
    }

    private static Object modelDump(Object value) {
        if (value == null) {
            return null;
        }
        for (String methodName : new String[]{"modelDump", "model_dump"}) {
            try {
                Method method = value.getClass().getMethod(methodName);
                return method.invoke(value);
            } catch (ReflectiveOperationException ignored) {
                // Try next fallback.
            }
        }
        return null;
    }

    private static Object readProperty(Object value, String property) {
        if (value == null) {
            return null;
        }
        try {
            return value.getClass().getMethod("get" + Character.toUpperCase(property.charAt(0)) + property.substring(1))
                    .invoke(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
