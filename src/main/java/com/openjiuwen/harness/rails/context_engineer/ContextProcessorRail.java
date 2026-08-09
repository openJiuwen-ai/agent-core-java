/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionMemoryConfig;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentRail;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * Configures context-engine processors for a DeepAgent/ReAct agent.
 *
 * <p>Mirrors Python's {@code ContextProcessorRail} in
 * {@code openjiuwen/harness/rails/context_engineer/context_processor_rail.py}.</p>
 */
public class ContextProcessorRail extends AgentRail {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final boolean preset;
    private final List<ContextEngine.ProcessorSpec> userProcessors = new ArrayList<>();
    private final boolean sessionMemoryEnabled;
    private SessionMemoryConfig sessionMemoryConfig;
    private SessionMemoryManager sessionMemoryManager;
    private List<ContextEngine.ProcessorSpec> allProcessors = new ArrayList<>();
    private SystemPromptBuilder systemPromptBuilder;

    public ContextProcessorRail() {
        this(null, true, null);
    }

    public ContextProcessorRail(List<ContextEngine.ProcessorSpec> processors) {
        this(processors, true, null);
    }

    public ContextProcessorRail(ContextEngine.ProcessorSpec processor) {
        this(processor == null ? null : List.of(processor), true, null);
    }

    public ContextProcessorRail(List<ContextEngine.ProcessorSpec> processors, boolean preset,
                                SessionMemoryConfig sessionMemory) {
        this.preset = preset;
        if (processors != null) {
            this.userProcessors.addAll(processors);
        }
        this.sessionMemoryEnabled = sessionMemory != null;
        this.sessionMemoryConfig = sessionMemory;
        if (this.sessionMemoryConfig != null) {
            this.sessionMemoryManager = new SessionMemoryManager(this.sessionMemoryConfig);
        }
        setPriority(85);
    }

    @Override
    public void init(BaseAgent agent) {
        Object config = readReactConfig(agent).orElse(null);
        if (config == null) {
            return;
        }
        ModelRequestConfig modelConfig = readModelConfig(config);
        ModelClientConfig modelClientConfig = readModelClientConfig(config);
        if (sessionMemoryConfig != null) {
            bindSessionMemoryDefaults(modelConfig, modelClientConfig);
        }

        List<ContextEngine.ProcessorSpec> mergedProcessors = preset
                ? mergeProcessors(buildPresetProcessors(modelConfig, modelClientConfig), userProcessors,
                modelConfig, modelClientConfig)
                : mergeProcessors(List.of(), userProcessors, modelConfig, modelClientConfig);
        writeContextProcessors(config, mergedProcessors);
        allProcessors = new ArrayList<>(mergedProcessors);
    }

    @Override
    public void uninit(BaseAgent agent) {
        if (sessionMemoryManager != null) {
            sessionMemoryManager.shutdown();
        }
        readReactConfig(agent).ifPresent(config -> writeContextProcessors(config, List.of()));
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection("offload");
        }
        allProcessors = new ArrayList<>();
    }

    @Override
    public CompletionStage<Void> beforeInvoke(AgentCallbackContext context) {
        fixIncompleteToolContext(context);
        return completed();
    }

    @Override
    public CompletionStage<Void> beforeModelCall(AgentCallbackContext context) {
        refreshTaskStateRuntime(context);
        maybeInjectOffloadSection(context);
        return completed();
    }

    @Override
    public CompletionStage<Void> afterModelCall(AgentCallbackContext context) {
        refreshTaskStateRuntime(context);
        return completed();
    }

    @Override
    public CompletionStage<Void> afterToolCall(AgentCallbackContext context) {
        refreshTaskStateRuntime(context);
        return completed();
    }

    @Override
    public CompletionStage<Void> onModelException(AgentCallbackContext context) {
        refreshTaskStateRuntime(context);
        fixIncompleteToolContext(context);
        return completed();
    }

    public List<ContextEngine.ProcessorSpec> getAllProcessors() {
        return new ArrayList<>(allProcessors);
    }

    public boolean isSessionMemoryEnabled() {
        return sessionMemoryEnabled;
    }

    public SessionMemoryConfig getSessionMemoryConfig() {
        return sessionMemoryConfig;
    }

    public SessionMemoryManager getSessionMemoryManager() {
        return sessionMemoryManager;
    }

    static List<ContextEngine.ProcessorSpec> mergeProcessors(
            List<ContextEngine.ProcessorSpec> base,
            List<ContextEngine.ProcessorSpec> overrides,
            ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig) {
        Map<String, Object> overrideMap = new LinkedHashMap<>();
        for (ContextEngine.ProcessorSpec spec : overrides == null ? List.<ContextEngine.ProcessorSpec>of() : overrides) {
            overrideMap.put(spec.processorType(), spec.config());
        }
        Set<String> baseOverrideKeys = new LinkedHashSet<>();
        for (ContextEngine.ProcessorSpec spec : base == null ? List.<ContextEngine.ProcessorSpec>of() : base) {
            if (overrideMap.containsKey(spec.processorType())) {
                baseOverrideKeys.add(spec.processorType());
            }
        }

        List<ContextEngine.ProcessorSpec> result = new ArrayList<>();
        for (ContextEngine.ProcessorSpec baseSpec : base == null ? List.<ContextEngine.ProcessorSpec>of() : base) {
            if (overrideMap.containsKey(baseSpec.processorType())) {
                result.add(new ContextEngine.ProcessorSpec(
                        baseSpec.processorType(),
                        buildMergedConfig(baseSpec.processorType(), overrideMap.get(baseSpec.processorType()),
                                baseSpec.config(), modelConfig, modelClientConfig)));
            } else {
                assignModelDefaults(baseSpec.config(), modelConfig, modelClientConfig);
                result.add(baseSpec);
            }
        }
        for (ContextEngine.ProcessorSpec override : overrides == null ? List.<ContextEngine.ProcessorSpec>of() : overrides) {
            if (!baseOverrideKeys.contains(override.processorType())) {
                result.add(new ContextEngine.ProcessorSpec(
                        override.processorType(),
                        buildMergedConfig(override.processorType(), override.config(), null,
                                modelConfig, modelClientConfig)));
            }
        }
        return result;
    }

    private List<ContextEngine.ProcessorSpec> buildPresetProcessors(ModelRequestConfig modelConfig,
                                                                    ModelClientConfig modelClientConfig) {
        if (sessionMemoryEnabled) {
            FullCompactProcessorConfig fullCompact = new FullCompactProcessorConfig();
            fullCompact.setModel(modelConfig);
            fullCompact.setModelClient(modelClientConfig);
            return List.of(
                    new ContextEngine.ProcessorSpec("ToolResultBudgetProcessor",
                            new ToolResultBudgetProcessorConfig()),
                    new ContextEngine.ProcessorSpec("MicroCompactProcessor",
                            new MicroCompactProcessorConfig()),
                    new ContextEngine.ProcessorSpec("FullCompactProcessor", fullCompact)
            );
        }

        MessageSummaryOffloaderConfig offloader = new MessageSummaryOffloaderConfig();
        offloader.setLargeMessageThreshold(10000);
        offloader.setOffloadMessageType(List.of("tool"));
        offloader.setProtectedToolNames(List.of("read_file:*SKILL.md", "reload_original_context_messages"));
        offloader.setModel(modelConfig);
        offloader.setModelClient(modelClientConfig);

        DialogueCompressorConfig dialogue = new DialogueCompressorConfig();
        dialogue.setTokensThreshold(100000);
        dialogue.setMessagesToKeep(10);
        dialogue.setKeepLastRound(false);
        dialogue.setCompressionTargetTokens(1800);
        dialogue.setModel(modelConfig);
        dialogue.setModelClient(modelClientConfig);

        CurrentRoundCompressorConfig currentRound = new CurrentRoundCompressorConfig();
        currentRound.setTokensThreshold(100000);
        currentRound.setMessagesToKeep(3);
        currentRound.setModel(modelConfig);
        currentRound.setModelClient(modelClientConfig);

        RoundLevelCompressorConfig roundLevel = new RoundLevelCompressorConfig();
        roundLevel.setTriggerTotalTokens(230000);
        roundLevel.setTargetTotalTokens(160000);
        roundLevel.setKeepRecentMessages(6);
        roundLevel.setModel(modelConfig);
        roundLevel.setModelClient(modelClientConfig);

        return List.of(
                new ContextEngine.ProcessorSpec("MessageSummaryOffloader", offloader),
                new ContextEngine.ProcessorSpec("DialogueCompressor", dialogue),
                new ContextEngine.ProcessorSpec("CurrentRoundCompressor", currentRound),
                new ContextEngine.ProcessorSpec("RoundLevelCompressor", roundLevel)
        );
    }

    private static Object buildMergedConfig(String key, Object overrideConfig, Object baseConfig,
                                            ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        Object mergedConfig;
        if (baseConfig != null) {
            if (overrideConfig instanceof Map<?, ?> rawMap) {
                mergedConfig = mergeConfigWithOverrides(baseConfig, rawMap);
            } else {
                mergedConfig = overrideConfig;
            }
        } else if (overrideConfig instanceof Map<?, ?>) {
            throw new IllegalArgumentException("Processor '" + key
                    + "' does not exist in preset and cannot create config from map.");
        } else {
            mergedConfig = overrideConfig;
        }
        assignModelDefaults(mergedConfig, modelConfig, modelClientConfig);
        return mergedConfig;
    }

    static Object mergeConfigWithOverrides(Object baseConfig, Map<?, ?> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return baseConfig;
        }
        Object copy = OBJECT_MAPPER.convertValue(baseConfig, baseConfig.getClass());
        Map<String, Object> normalized = new LinkedHashMap<>();
        overrides.forEach((key, value) -> normalized.put(String.valueOf(key), value));
        try {
            return OBJECT_MAPPER.updateValue(copy, normalized);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to merge processor config overrides", ex);
        }
    }

    private void bindSessionMemoryDefaults(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        if (sessionMemoryConfig.getModel() == null) {
            sessionMemoryConfig.setModel(modelConfig);
        }
        if (sessionMemoryConfig.getModelClient() == null) {
            sessionMemoryConfig.setModelClient(modelClientConfig);
        }
        sessionMemoryManager.bindModelDefaults(modelConfig, modelClientConfig);
    }

    private static void assignModelDefaults(Object config, ModelRequestConfig modelConfig,
                                            ModelClientConfig modelClientConfig) {
        if (config == null) {
            return;
        }
        assignIfNull(config, "getModel", "setModel", ModelRequestConfig.class, modelConfig);
        assignIfNull(config, "getModelClient", "setModelClient", ModelClientConfig.class, modelClientConfig);
    }

    private static void assignIfNull(Object target, String getterName, String setterName, Class<?> setterType,
                                     Object value) {
        if (value == null) {
            return;
        }
        try {
            Method getter = target.getClass().getMethod(getterName);
            if (getter.invoke(target) != null) {
                return;
            }
            Method setter = target.getClass().getMethod(setterName, setterType);
            setter.invoke(target, value);
        } catch (ReflectiveOperationException ignored) {
            // Some processor configs do not carry model defaults.
        }
    }

    public static void fixIncompleteToolContext(AgentCallbackContext context) {
        if (context == null) {
            return;
        }
        context.getExtra().put("incomplete_tool_context_checked", true);
        ModelContext modelContext = context.getContext();
        if (modelContext == null) {
            return;
        }
        List<BaseMessage> messages = modelContext.getMessages(null, true);
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<BaseMessage> popped = modelContext.popMessages(messages.size(), true);
        List<BaseMessage> repaired = repairToolContext(popped == null || popped.isEmpty() ? messages : popped);
        if (!repaired.isEmpty()) {
            modelContext.addMessages(repaired).toCompletableFuture().join();
        }
    }

    public static String ensureJsonArguments(Object value) {
        if (value instanceof Map<?, ?> map) {
            try {
                return pythonStyleJson(OBJECT_MAPPER.writeValueAsString(map));
            } catch (Exception ignored) {
                return "{}";
            }
        }
        if (!(value instanceof String text) || text.isBlank()) {
            return "{}";
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(text, Object.class);
            return parsed instanceof Map<?, ?> ? text : "{}";
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private static String pythonStyleJson(String compactJson) {
        return compactJson == null ? "{}" : compactJson.replace(":", ": ").replace(",", ", ");
    }

    private static List<BaseMessage> repairToolContext(List<BaseMessage> messages) {
        List<BaseMessage> repaired = new ArrayList<>();
        Map<String, ToolCall> pendingToolCalls = new LinkedHashMap<>();
        for (BaseMessage message : messages) {
            if (message instanceof ToolMessage toolMessage) {
                pendingToolCalls.remove(Objects.toString(toolMessage.getToolCallId(), ""));
                repaired.add(toolMessage);
                continue;
            }
            flushMissingToolMessages(repaired, pendingToolCalls);
            if (message instanceof AssistantMessage assistantMessage) {
                sanitizeToolCalls(assistantMessage);
                repaired.add(assistantMessage);
                if (assistantMessage.getToolCalls() != null) {
                    for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                        pendingToolCalls.put(Objects.toString(toolCall.getId(), ""), toolCall);
                    }
                }
                continue;
            }
            repaired.add(message);
        }
        flushMissingToolMessages(repaired, pendingToolCalls);
        return repaired;
    }

    private static void sanitizeToolCalls(AssistantMessage assistantMessage) {
        if (assistantMessage.getToolCalls() == null) {
            return;
        }
        for (ToolCall toolCall : assistantMessage.getToolCalls()) {
            toolCall.setArguments(ensureJsonArguments(toolCall.getArguments()));
        }
    }

    private static void flushMissingToolMessages(List<BaseMessage> repaired, Map<String, ToolCall> pendingToolCalls) {
        for (String toolCallId : new ArrayList<>(pendingToolCalls.keySet())) {
            repaired.add(new ToolMessage("[Tool execution interrupted]", toolCallId));
        }
        pendingToolCalls.clear();
    }

    private static void refreshTaskStateRuntime(AgentCallbackContext context) {
        if (context == null) {
            return;
        }
        Object session = context.getSession();
        if (session == null) {
            return;
        }
        readSessionState(session).ifPresent(state -> {
            Object taskState = state.getOrDefault("task_state", state);
            context.getExtra().put("task_state", taskState);
            Object iteration = state.containsKey("iteration")
                    ? state.get("iteration")
                    : readNestedIteration(state.get("stop_condition_state"));
            if (iteration != null) {
                context.getExtra().put("iteration", iteration);
            }
            Map<String, Object> update = new LinkedHashMap<>(state);
            if (state.get("plan_mode") instanceof Map<?, ?> planModeRaw) {
                Map<String, Object> planMode = new LinkedHashMap<>();
                planModeRaw.forEach((key, value) -> planMode.put(String.valueOf(key), value));
                Object mode = planMode.getOrDefault("mode", "normal");
                planMode.putIfAbsent("pre_plan_mode", mode);
                update.put("plan_mode", planMode);
            }
            invokeUpdateState(session, update);
        });
    }

    private static Object readNestedIteration(Object stopConditionState) {
        if (stopConditionState instanceof Map<?, ?> map) {
            return map.get("iteration");
        }
        return null;
    }

    private static Optional<Map<String, Object>> readSessionState(Object session) {
        if (session instanceof AgentSessionApi sessionApi) {
            return asStringObjectMap(sessionApi.getState(null));
        }
        try {
            Method method = session.getClass().getMethod("getState");
            Object value = method.invoke(session);
            return asStringObjectMap(value);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = session.getClass().getMethod("get_state");
                Object value = method.invoke(session);
                return asStringObjectMap(value);
            } catch (ReflectiveOperationException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    private static void invokeUpdateState(Object session, Map<String, Object> update) {
        if (session instanceof AgentSessionApi sessionApi) {
            sessionApi.updateState(update);
            return;
        }
        try {
            Method method = session.getClass().getMethod("updateState", Map.class);
            method.invoke(session, update);
            return;
        } catch (ReflectiveOperationException ignored) {
            // Fall through to Python-style snake_case.
        }
        try {
            Method method = session.getClass().getMethod("update_state", Map.class);
            method.invoke(session, update);
        } catch (ReflectiveOperationException ignored) {
            // Dynamic sessions may expose read-only state.
        }
    }

    private void maybeInjectOffloadSection(AgentCallbackContext context) {
        if (context != null && context.getExtra() != null) {
            Object builder = context.getExtra().get("system_prompt_builder");
            boolean shouldInject = builder instanceof SystemPromptBuilder && !allProcessors.isEmpty();
            if (builder instanceof SystemPromptBuilder systemPromptBuilder) {
                this.systemPromptBuilder = systemPromptBuilder;
                if (shouldInject) {
                    systemPromptBuilder.addSection(new PromptSection(
                            "offload",
                            Map.of(
                                    "cn", "上下文压缩提示: 如需查看已卸载内容, 调用 reload_original_context_messages。",
                                    "en", "Context Compression: call reload_original_context_messages for offloaded content."
                            ),
                            90
                    ));
                } else {
                    systemPromptBuilder.removeSection("offload");
                }
                context.getExtra().put("offload_section_enabled", shouldInject);
                return;
            }
            context.getExtra().put("offload_section_enabled", true);
        }
    }

    private static Optional<Map<String, Object>> asStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> rawMap)) {
            return Optional.empty();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
        return Optional.of(result);
    }

    private static Optional<Object> readReactConfig(BaseAgent agent) {
        if (agent == null) {
            return Optional.empty();
        }
        Optional<Object> reactAgent = readField(agent, "react_agent").or(() -> readField(agent, "reactAgent"));
        return reactAgent.flatMap(value -> readField(value, "_config").or(() -> readField(value, "config")));
    }

    private static Optional<Object> readField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return Optional.ofNullable(field.get(target));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static ModelRequestConfig readModelConfig(Object config) {
        if (config instanceof ReActAgentConfig reactConfig) {
            return reactConfig.getModelConfigObj();
        }
        return invokeGetter(config, "getModelConfigObj", ModelRequestConfig.class).orElse(null);
    }

    private static ModelClientConfig readModelClientConfig(Object config) {
        if (config instanceof ReActAgentConfig reactConfig) {
            return reactConfig.getModelClientConfig();
        }
        return invokeGetter(config, "getModelClientConfig", ModelClientConfig.class).orElse(null);
    }

    private static <T> Optional<T> invokeGetter(Object target, String methodName, Class<T> valueType) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            return valueType.isInstance(value) ? Optional.of(valueType.cast(value)) : Optional.empty();
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private static void writeContextProcessors(Object config, List<ContextEngine.ProcessorSpec> processors) {
        if (config instanceof ReActAgentConfig reactConfig) {
            reactConfig.setContextProcessors(processors);
            return;
        }
        try {
            Method method = config.getClass().getMethod("setContextProcessors", List.class);
            method.invoke(config, processors);
        } catch (ReflectiveOperationException ignored) {
            try {
                Method method = config.getClass().getMethod("configureContextProcessors", List.class);
                method.invoke(config, processors);
            } catch (ReflectiveOperationException ignoredAgain) {
                // Dynamic test agents may not expose a JavaBean setter.
            }
        }
    }
}
