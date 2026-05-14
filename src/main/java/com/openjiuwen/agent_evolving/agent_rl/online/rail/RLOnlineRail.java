/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * Minimal online RL rail.
 * <p>
 * Mirrors Python's {@code RLOnlineRail} in
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.online_rail}.
 * <p>
 * This batch covers the smallest tested seam only: enabling token capture before invoke
 * and uploading a minimal tenant/sample batch from a trajectory.
 */
public class RLOnlineRail extends AgentRail {

    private final String sessionId;
    private final String gatewayEndpoint;
    private String tenantId;
    private final TrajectoryUploader uploader;

    public RLOnlineRail(String sessionId, String gatewayEndpoint, String tenantId, TrajectoryUploader uploader) {
        this.sessionId = sessionId;
        this.gatewayEndpoint = gatewayEndpoint;
        this.tenantId = tenantId;
        this.uploader = uploader;
        setPriority(100);
    }

    public void onBeforeInvoke(AgentCallbackContext ctx) {
        Object agent = ctx.getAgent();
        if (agent == null) {
            return;
        }
        Object reactAgent = readField(agent, "react_agent");
        Object config = reactAgent != null ? readField(reactAgent, "config") : null;
        if (config == null) {
            return;
        }
        writeField(config, "llm_return_token_ids", true);
        writeField(config, "llm_logprobs", true);
        writeField(config, "llm_top_logprobs", 1);

        String userId = resolveUserId(ctx);
        if (!userId.isBlank()) {
            Object customHeaders = readField(config, "custom_headers");
            @SuppressWarnings("unchecked")
            Map<String, Object> headers = customHeaders instanceof Map<?, ?> map ? (Map<String, Object>) map : new java.util.LinkedHashMap<>();
            String existingKey = null;
            for (String key : headers.keySet()) {
                if ("x-user-id".equalsIgnoreCase(key)) {
                    existingKey = key;
                    break;
                }
            }
            if (existingKey != null) {
                headers.put(existingKey, userId);
            } else {
                headers.put("x-user-id", userId);
            }
            writeField(config, "custom_headers", headers);
            if (tenantId == null || tenantId.isBlank()) {
                tenantId = userId;
            }
        }
    }

    public void safeRunEvolution(Map<String, Object> snapshot) {
        if (uploader == null || snapshot == null) {
            return;
        }
        Object trajectoryObj = snapshot.get("trajectory");
        if (!(trajectoryObj instanceof Trajectory trajectory)) {
            return;
        }
        OnlineRlBatch batch = new OnlineRlBatch();
        batch.setTenantId(tenantId);

        List<TrajectoryStep> steps = trajectory.getSteps();
        if (steps != null) {
            for (TrajectoryStep step : steps) {
                if (!"llm".equals(step.getKind())) {
                    continue;
                }
                Object outputs = step.getOutputs();
                OnlineRlSample sample = new OnlineRlSample();
                sample.setResponseText(extractResponseText(outputs));
                batch.getSamples().add(sample);
            }
        }
        if (!batch.getSamples().isEmpty()) {
            uploader.enqueue(batch);
        }
    }

    private String resolveUserId(AgentCallbackContext ctx) {
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }
        Object value = ctx.getExtra() != null ? ctx.getExtra().get("user_id") : null;
        return value == null ? "" : String.valueOf(value).trim();
    }

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

    private static Object readField(Object target, String fieldName) {
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

    private static void writeField(Object target, String fieldName, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException ignored) {
                return;
            }
        }
    }
}
