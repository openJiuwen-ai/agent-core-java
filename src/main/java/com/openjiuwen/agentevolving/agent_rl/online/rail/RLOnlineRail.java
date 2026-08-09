/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.online.rail;

import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryBuilder;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Rail-based online RL collector and uploader.
 *
 * <p>Mirrors Python's {@code RLOnlineRail} in
 * {@code openjiuwen/agent_evolving/agent_rl/online/rail/online_rail.py}.</p>
 */
public class RLOnlineRail extends EvolutionRail {

    private static final Logger LOGGER = Logger.getLogger(RLOnlineRail.class.getName());

    private final String sessionId;
    private String tenantId;
    private final TrajectoryUploader uploader;
    private final OnlineTrajectoryConverter converter;
    private final boolean sessionDoneOnInvokeEnd;
    private final Integer maxTrajectorySteps;

    private TrajectoryBuilder builder;
    private int llmStepCount;
    private double startedAt;

    public RLOnlineRail(String sessionId, String gatewayEndpoint, String tenantId, TrajectoryUploader uploader) {
        this(sessionId, gatewayEndpoint, tenantId, uploader, null, true, null);
    }

    public RLOnlineRail(
            String sessionId,
            String gatewayEndpoint,
            String tenantId,
            TrajectoryUploader uploader,
            OnlineTrajectoryConverter converter,
            boolean sessionDoneOnInvokeEnd,
            Integer maxTrajectorySteps
    ) {
        this.sessionId = sessionId == null ? "" : sessionId;
        this.tenantId = tenantId;
        this.uploader = uploader != null ? uploader : new TrajectoryUploader(gatewayEndpoint);
        this.converter = converter != null ? converter : new OnlineTrajectoryConverter(tenantId);
        this.sessionDoneOnInvokeEnd = sessionDoneOnInvokeEnd;
        this.maxTrajectorySteps = maxTrajectorySteps;
        setPriority(100);
    }

    /**
     * Java-visible extension point matching Python's {@code _on_before_invoke}.
     *
     * @param ctx callback context
     */
    public void onBeforeInvoke(AgentCallbackContext ctx) {
        llmStepCount = 0;
        startedAt = epochSeconds();
        enableTokenCapture(ctx);
        if (tenantId == null) {
            String userId = resolveUserId(ctx);
            tenantId = userId.isBlank() ? null : userId;
        }
        if (builder != null) {
            if (!sessionId.isBlank()) {
                builder.setSessionId(sessionId);
            }
            builder.setSource("rl_online");
            builder.getMeta().put("tenant_id", tenantId);
            builder.getMeta().put("status", "ok");
            builder.getMeta().put("started_at", startedAt);
        }
    }

    public CompletionStage<Void> beforeInvoke(AgentCallbackContext ctx) {
        if (ctx != null && ctx.getInputs() instanceof InvokeInputs inputs) {
            ensureBuilder(ctx, inputs);
        }
        onBeforeInvoke(ctx);
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> afterModelCall(AgentCallbackContext ctx) {
        if (builder == null || ctx == null || !(ctx.getInputs() instanceof ModelCallInputs inputs)) {
            return CompletableFuture.completedFuture(null);
        }
        Object response = inputs.getResponse();
        SplitResponse splitResponse = splitResponseTokenFields(response);
        LLMCallDetail detail = LLMCallDetail.builder()
                .model(resolveModelName(ctx))
                .messages(inputs.getMessages())
                .response(splitResponse.responseForDetail())
                .tools(normalizeTools(inputs.getTools()))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .promptTokenIds(splitResponse.promptTokenIds())
                .completionTokenIds(splitResponse.completionTokenIds())
                .logprobs(splitResponse.logprobs())
                .meta(stepMeta(ctx))
                .build();
        builder.recordStep(step);
        onAfterModelCall(ctx);
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Java-visible extension point matching Python's {@code _on_after_model_call}.
     *
     * @param ctx callback context
     */
    public void onAfterModelCall(AgentCallbackContext ctx) {
        llmStepCount += 1;
        if (builder == null || builder.getSteps().isEmpty()) {
            return;
        }
        TrajectoryStep lastStep = builder.getSteps().get(builder.getSteps().size() - 1);
        if (!"llm".equals(lastStep.getKind())) {
            return;
        }
        Object response = ctx != null && ctx.getInputs() instanceof ModelCallInputs inputs ? inputs.getResponse() : null;
        if (lastStep.getPromptTokenIds() == null) {
            List<Integer> promptIds = LlmResponseUtils.extractPromptIds(response);
            if (promptIds != null) {
                lastStep.setPromptTokenIds(promptIds);
            }
        }
        if (lastStep.getCompletionTokenIds() == null) {
            List<Integer> tokenIds = LlmResponseUtils.extractTokenIds(response);
            if (tokenIds != null) {
                lastStep.setCompletionTokenIds(tokenIds);
            }
        }
        if (lastStep.getLogprobs() == null) {
            List<Double> logprobs = LlmResponseUtils.extractLogprobs(response);
            if (logprobs != null) {
                lastStep.setLogprobs(logprobs);
            }
        }
        lastStep.getMeta().put("turn_id", llmStepCount - 1);
        lastStep.getMeta().put("source", "rl_online");
        lastStep.getMeta().put("tenant_id", tenantId);
    }

    public CompletionStage<Void> onModelException(AgentCallbackContext ctx) {
        if (builder != null) {
            builder.getMeta().put("status", "invoke_error");
            builder.getMeta().put("exception", ctx != null ? String.valueOf(ctx.getException()) : "null");
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> afterInvoke(AgentCallbackContext ctx) {
        if (builder == null) {
            return CompletableFuture.completedFuture(null);
        }
        Trajectory trajectory = builder.build();
        resetTrajectoryBuilder();
        return runEvolution(trajectory, ctx, null);
    }

    public CompletionStage<Void> safeRunEvolution(Map<String, Object> snapshot) {
        Object trajectory = snapshot != null ? snapshot.get("trajectory") : null;
        if (trajectory instanceof Trajectory typedTrajectory) {
            return runEvolution(typedTrajectory, null, snapshot);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletionStage<Void> runEvolution(
            Trajectory trajectory,
            AgentCallbackContext ctx,
            Map<String, Object> snapshot
    ) {
        if (trajectory == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (trajectory.getMeta() == null) {
            trajectory.setMeta(new LinkedHashMap<>());
        }
        trajectory.getMeta().putIfAbsent("tenant_id", tenantId);
        trajectory.getMeta().putIfAbsent("status", "ok");
        trajectory.getMeta().put("ended_at", epochSeconds());
        RailV1Batch batch = converter.convert(trajectory, tenantId, sessionDoneOnInvokeEnd);
        if (batch.getSamples().isEmpty()) {
            LOGGER.fine(() -> "[RLOnlineRail] no LLM samples to upload trajectory=" + trajectory.getExecutionId());
            return CompletableFuture.completedFuture(null);
        }
        return uploader.enqueue(batch);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public int getLlmStepCount() {
        return llmStepCount;
    }

    public double getStartedAt() {
        return startedAt;
    }

    private void ensureBuilder(AgentCallbackContext ctx, InvokeInputs inputs) {
        String resolvedSessionId = resolveTrajectorySessionId(inputs);
        if (builder != null && resolvedSessionId.equals(builder.getSessionId())) {
            return;
        }
        String memberId = resolveMemberId(ctx);
        builder = TrajectoryBuilder.builder()
                .sessionId(resolvedSessionId)
                .source("online")
                .memberId(memberId)
                .maxSteps(maxTrajectorySteps)
                .build();
    }

    private String resolveTrajectorySessionId(InvokeInputs inputs) {
        if (!sessionId.isBlank()) {
            return sessionId;
        }
        return inputs != null && inputs.getConversationId() != null ? inputs.getConversationId() : "";
    }

    protected void resetTrajectoryBuilder() {
        builder = null;
    }

    private void enableTokenCapture(AgentCallbackContext ctx) {
        Object agent = ctx != null ? ctx.getAgent() : null;
        Object reactAgent = readMember(agent, "react_agent", "reactAgent");
        Object config = readMember(reactAgent, "config");
        if (config == null) {
            return;
        }
        writeMember(config, true, "llm_return_token_ids", "llmReturnTokenIds");
        writeMember(config, true, "llm_logprobs", "llmLogprobs");
        writeMember(config, 1, "llm_top_logprobs", "llmTopLogprobs");
        String userId = resolveUserId(ctx);
        if (userId.isBlank()) {
            return;
        }
        Map<String, Object> headers = toHeaderMap(readMember(config, "custom_headers", "customHeaders"));
        String existingKey = headers.keySet().stream()
                .filter(key -> "x-user-id".equalsIgnoreCase(key))
                .findFirst()
                .orElse(null);
        if (existingKey != null) {
            headers.put(existingKey, userId);
        } else {
            headers.put("x-user-id", userId);
        }
        if (!invokeOneArg(config, headers, "configure_custom_headers", "configureCustomHeaders")) {
            writeMember(config, headers, "custom_headers", "customHeaders");
        }
    }

    private String resolveUserId(AgentCallbackContext ctx) {
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId.trim();
        }
        Object value = ctx != null && ctx.getExtra() != null ? ctx.getExtra().get("user_id") : null;
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveModelName(AgentCallbackContext ctx) {
        Object agent = ctx != null ? ctx.getAgent() : null;
        Object config = agent instanceof BaseAgent baseAgent ? baseAgent.getConfig() : readMember(agent, "config");
        Object model = readMember(config, "model", "modelName", "model_name");
        return model == null || String.valueOf(model).isBlank() ? "unknown" : String.valueOf(model);
    }

    private String resolveMemberId(AgentCallbackContext ctx) {
        Object agent = ctx != null ? ctx.getAgent() : null;
        if (agent instanceof BaseAgent baseAgent && baseAgent.getCard() != null) {
            return baseAgent.getCard().getId();
        }
        return null;
    }

    private Map<String, Object> stepMeta(AgentCallbackContext ctx) {
        Map<String, Object> meta = new LinkedHashMap<>();
        String agentId = resolveMemberId(ctx);
        String resolvedAgentId = agentId == null || agentId.isBlank() ? "unknown" : agentId;
        meta.put("operator_id", resolvedAgentId + "/llm_main");
        meta.put("agent_id", resolvedAgentId);
        return meta;
    }

    private static SplitResponse splitResponseTokenFields(Object response) {
        if (!(response instanceof Map<?, ?> map)) {
            return new SplitResponse(response, null, null, null);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        List<Integer> promptTokenIds = toIntList(copy.remove("prompt_token_ids"));
        List<Integer> completionTokenIds = toIntList(copy.remove("completion_token_ids"));
        Object logprobs = copy.remove("logprobs");
        return new SplitResponse(copy, promptTokenIds, completionTokenIds, logprobs);
    }

    private static List<Map<String, Object>> normalizeTools(List<Object> tools) {
        if (tools == null) {
            return null;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object tool : tools) {
            if (tool instanceof Map<?, ?> map) {
                Map<String, Object> item = new LinkedHashMap<>();
                map.forEach((key, value) -> item.put(String.valueOf(key), value));
                normalized.add(item);
            }
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<Integer> toIntList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Integer> parsed = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                parsed.add(number.intValue());
            } else if (item != null) {
                try {
                    parsed.add(Integer.parseInt(String.valueOf(item)));
                } catch (NumberFormatException ignored) {
                    // Mirrors Python's permissive handling of provider token ids.
                }
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    private static Map<String, Object> toHeaderMap(Object rawHeaders) {
        Map<String, Object> headers = new LinkedHashMap<>();
        if (rawHeaders instanceof Map<?, ?> map) {
            map.forEach((key, value) -> headers.put(String.valueOf(key), value));
        }
        return headers;
    }

    private static Object readMember(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Object value = invokeNoArg(target, name);
            if (value != null) {
                return value;
            }
            String camel = toCamel(name);
            value = invokeNoArg(target, "get" + capitalize(camel));
            if (value != null) {
                return value;
            }
            value = invokeNoArg(target, "is" + capitalize(camel));
            if (value != null) {
                return value;
            }
            Field field = findField(target.getClass(), name);
            if (field == null && !camel.equals(name)) {
                field = findField(target.getClass(), camel);
            }
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void writeMember(Object target, Object value, String... names) {
        if (target == null) {
            return;
        }
        for (String name : names) {
            String camel = toCamel(name);
            if (invokeOneArg(target, value, "set" + capitalize(camel), name)) {
                return;
            }
            Field field = findField(target.getClass(), name);
            if (field == null && !camel.equals(name)) {
                field = findField(target.getClass(), camel);
            }
            if (field != null) {
                try {
                    field.setAccessible(true);
                    field.set(target, value);
                    return;
                } catch (ReflectiveOperationException ignored) {
                    return;
                }
            }
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        Method method = findMethod(target.getClass(), methodName);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean invokeOneArg(Object target, Object value, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findOneArgMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignored) {
                return false;
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static Method findOneArgMethod(Class<?> type, String methodName) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static String toCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean upperNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                upperNext = true;
            } else if (upperNext) {
                builder.append(Character.toUpperCase(ch));
                upperNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static double epochSeconds() {
        return Instant.now().toEpochMilli() / 1000.0d;
    }

    private record SplitResponse(Object responseForDetail,
                                 List<Integer> promptTokenIds,
                                 List<Integer> completionTokenIds,
                                 Object logprobs) {
    }
}
