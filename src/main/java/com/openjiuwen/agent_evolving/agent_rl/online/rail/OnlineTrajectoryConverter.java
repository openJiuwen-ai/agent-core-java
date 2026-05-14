/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal trajectory converter for RL online rail batches.
 * <p>
 * Mirrors Python's {@code OnlineTrajectoryConverter} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.converter}.
 */
public class OnlineTrajectoryConverter {

    private final String tenantId;

    public OnlineTrajectoryConverter(String tenantId) {
        this.tenantId = tenantId;
    }

    public OnlineRlBatch convert(Trajectory trajectory) {
        OnlineRlBatch batch = new OnlineRlBatch();
        batch.setTenantId(tenantId);
        if (trajectory == null || trajectory.getSteps() == null) {
            return batch;
        }
        for (TrajectoryStep step : trajectory.getSteps()) {
            if (!"llm".equals(step.getKind())) {
                continue;
            }
            OnlineRlSample sample = new OnlineRlSample();
            extractMessages(step, sample);
            extractPromptIds(step, sample);
            extractResponseTokens(step, sample);
            extractResponseLogprobs(step, sample);
            sample.setResponseText(extractResponseText(step.getOutputs()));
            batch.getSamples().add(sample);
        }
        return batch;
    }

    @SuppressWarnings("unchecked")
    private static void extractMessages(TrajectoryStep step, OnlineRlSample sample) {
        Object inputs = step.getInputs();
        if (inputs instanceof Map<?, ?> map) {
            Object messages = map.get("messages");
            if (messages instanceof List<?> list) {
                for (Object item : list) {
                    sample.getMessages().add(messageToDict(item));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void extractPromptIds(TrajectoryStep step, OnlineRlSample sample) {
        Map<String, Object> meta = step.getMeta();
        Object provider = meta != null ? meta.get("provider_response_json") : null;
        if (provider instanceof Map<?, ?> map) {
            Object prompt = map.get("prompt_token_ids");
            if (prompt instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Number n) {
                        sample.getPromptIds().add(n.intValue());
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void extractResponseTokens(TrajectoryStep step, OnlineRlSample sample) {
        Map<String, Object> meta = step.getMeta();
        Object provider = meta != null ? meta.get("provider_response_json") : null;
        if (provider instanceof Map<?, ?> map) {
            Object choices = map.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
                Object tokenIds = choice.get("token_ids");
                if (tokenIds instanceof List<?> tokenList) {
                    for (Object item : tokenList) {
                        if (item instanceof Number n) {
                            sample.getResponseTokens().add(n.intValue());
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void extractResponseLogprobs(TrajectoryStep step, OnlineRlSample sample) {
        Map<String, Object> meta = step.getMeta();
        Object provider = meta != null ? meta.get("provider_response_json") : null;
        if (provider instanceof Map<?, ?> map) {
            Object choices = map.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> choice) {
                Object logprobs = choice.get("logprobs");
                if (logprobs instanceof List<?> logprobList) {
                    for (Object item : logprobList) {
                        if (item instanceof Number n) {
                            sample.getResponseLogprobs().add(n.doubleValue());
                        }
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String extractResponseText(Object outputs) {
        if (outputs instanceof Map<?, ?> map) {
            Object content = map.get("content");
            if (content != null) {
                return String.valueOf(content);
            }
            Object response = map.get("response");
            if (response instanceof Map<?, ?> responseMap && responseMap.get("content") != null) {
                return String.valueOf(responseMap.get("content"));
            }
        }
        return outputs == null ? "" : String.valueOf(outputs);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> messageToDict(Object message) {
        if (message instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
            return out;
        }
        Map<String, Object> dumped = tryModelDump(message);
        if (dumped != null) {
            return dumped;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        Object role = readField(message, "role");
        Object content = readField(message, "content");
        out.put("role", role != null ? String.valueOf(role) : "unknown");
        out.put("content", content != null ? content : "");
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tryModelDump(Object value) {
        if (value == null) {
            return null;
        }
        try {
            Method method = value.getClass().getMethod("model_dump");
            Object result = method.invoke(value);
            if (result instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
                return out;
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return null;
            }
        }
        return null;
    }
}
