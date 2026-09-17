/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionMemoryManager;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.FullCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.MicroCompactProcessorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.context.processor.offloader.ToolResultBudgetProcessorConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.common.eventbus.EventBus;
import com.openjiuwen.core.common.eventbus.EventBusHolder;
import com.openjiuwen.core.common.eventbus.Subscription;
import com.openjiuwen.core.common.eventbus.events.ModelChangeEvent;
import com.openjiuwen.core.common.eventbus.events.ModelUpdatedEvent;
import com.openjiuwen.core.common.eventbus.events.ModelRemovedEvent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Public class ContextProcessorRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class ContextProcessorRail extends DeepAgentRail {
    private static final String OFFLOAD_SECTION = "offload";
    private static final String DEFAULT_MODEL_KEY = "__default__";

    private final boolean isPreset;
    private final List<String> processorKeys;
    private final boolean isSessionMemoryEnabled;
    private final SessionMemoryManager sessionMemoryManager;

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<ContextEngine.ProcessorSpec> installedProcessors = new ArrayList<>();
    private DeepAgent owner;

    /**
     * ProcessorSpec cache keyed by modelId.
     * <p>
     * - maximumSize=100: at most 100 models' specs are cached
     * - expireAfterAccess=60min: entries not read in 60 minutes are evicted
     * <p>
     * LoadingCache is thread-safe; concurrent getUnchecked(key) calls for the
     * same key will block on the first load and share the result.
     *
     * @since 0.1.16
     */
    private final LoadingCache<String, List<ContextEngine.ProcessorSpec>> specsCache =
        CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(60, TimeUnit.MINUTES)
            .build(new CacheLoader<>() {
                @Override
                public List<ContextEngine.ProcessorSpec> load(String modelId) {
                    ReActAgentConfig agentConfig = (owner != null
                        && owner.getAgent().getConfig() instanceof ReActAgentConfig cfg)
                        ? cfg : null;
                    ModelRequestConfig modelConfig = agentConfig != null
                        ? agentConfig.getModelConfigObj() : null;
                    ModelClientConfig modelClientConfig = agentConfig != null
                        ? agentConfig.getModelClientConfig() : null;
                    // If modelId is not the default placeholder, resolve via ModelMgr
                    if (!DEFAULT_MODEL_KEY.equals(modelId)) {
                        Model resolved = Runner.resourceMgr().resolveModel(
                            modelId, modelClientConfig, modelConfig);
                        if (resolved != null) {
                            modelConfig = resolved.getModelConfig();
                            modelClientConfig = resolved.getModelClientConfig();
                        }
                    }
                    return buildProcessorSpecs(agentConfig, modelConfig, modelClientConfig);
                }
            });

    /**
     * EventBus subscriptions for model change events. Stored so they can be
     * cancelled in {@link #uninit(Object)}.
     */
    private Subscription modelUpdatedSubscription;
    private Subscription modelRemovedSubscription;

    /**
     * Track which modelId's specs are currently installed, to avoid redundant rebuilds.
     */
    private String lastInstalledModelKey = null;

    /**
     * ContextProcessorRail.
     * 
     * @since 0.1.7
     */
    public ContextProcessorRail() {
        this(true, List.of(), false);
    }

    /**
     * ContextProcessorRail.
     * 
     * @param isPreset isPreset
     * @param processorKeys processorKeys
     * @param isSessionMemoryEnabled isSessionMemoryEnabled
     * @since 0.1.7
     */
    public ContextProcessorRail(boolean isPreset, List<String> processorKeys, boolean isSessionMemoryEnabled) {
        this.isPreset = isPreset;
        this.processorKeys = new ArrayList<>(processorKeys == null ? List.of() : processorKeys);
        this.isSessionMemoryEnabled = isSessionMemoryEnabled;
        this.sessionMemoryManager = isSessionMemoryEnabled ? new SessionMemoryManager() : null;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 85;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        this.owner = deepAgent;
        installedProcessors.clear();
        List<ContextEngine.ProcessorSpec> specs;
        if (deepAgent.getAgent().getConfig() instanceof ReActAgentConfig config) {
            specs = buildProcessorSpecs(config);
            installedProcessors.addAll(specs);
            config.configureContextProcessors(new ArrayList<>(installedProcessors));
            deepAgent.getAgent().configure(config);
        } else {
            specs = buildProcessorSpecs(null);
            installedProcessors.addAll(specs);
        }
        lastInstalledModelKey = DEFAULT_MODEL_KEY;

        // Subscribe to model change events via EventBus to invalidate cache on update/remove
        EventBus eventBus = EventBusHolder.getInstance();
        modelUpdatedSubscription = eventBus.subscribe(ModelUpdatedEvent.class,
            e -> invalidateSpecsCache(e.getModelId()));
        modelRemovedSubscription = eventBus.subscribe(ModelRemovedEvent.class,
            e -> invalidateSpecsCache(e.getModelId()));
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            if (deepAgent.getAgent().getConfig() instanceof ReActAgentConfig config) {
                config.configureContextProcessors(List.of());
                deepAgent.getAgent().configure(config);
            }
            deepAgent.getAgent().getPromptBuilder().removeSection(OFFLOAD_SECTION);
        }
        installedProcessors.clear();
        invalidateAllSpecsCache();
        // Cancel EventBus subscriptions
        if (modelUpdatedSubscription != null) {
            modelUpdatedSubscription.cancel();
            modelUpdatedSubscription = null;
        }
        if (modelRemovedSubscription != null) {
            modelRemovedSubscription.cancel();
            modelRemovedSubscription = null;
        }
        owner = null;
    }

    /**
     * beforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        fixIncompleteToolContext(ctx);
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        injectOffloadSection();

        // Dynamic model: if a dynamicModelId is set, rebuild processor specs
        // from cache (or build-and-cache on first use for this modelId)
        String dynamicModelId = ctx.getDynamicModelId();
        String cacheKey = cacheKey(dynamicModelId);

        // Only rebuild if the active modelId differs from the one already installed
        if (!Objects.equals(cacheKey, lastInstalledModelKey)) {
            List<ContextEngine.ProcessorSpec> specs = specsCache.getUnchecked(cacheKey);
            applyProcessorSpecs(specs);
            lastInstalledModelKey = cacheKey;
        }
    }

    /**
     * Normalize a dynamicModelId into a cache key. null/blank → default key.
     *
     * @param dynamicModelId the dynamic model ID to normalize, may be null or blank
     * @return the normalized cache key
     */
    private String cacheKey(String dynamicModelId) {
        return (dynamicModelId == null || dynamicModelId.isBlank())
            ? DEFAULT_MODEL_KEY
            : dynamicModelId;
    }

    /**
     * Apply a list of ProcessorSpecs to the agent's config.
     *
     * @param specs the processor specs to apply
     */
    private void applyProcessorSpecs(List<ContextEngine.ProcessorSpec> specs) {
        if (owner == null) {
            return;
        }
        installedProcessors.clear();
        installedProcessors.addAll(specs);
        if (owner.getAgent().getConfig() instanceof ReActAgentConfig config) {
            config.configureContextProcessors(new ArrayList<>(installedProcessors));
            owner.getAgent().configure(config);
        }
    }

    /**
     * Invalidate the cached ProcessorSpecs for the given modelId.
     * Called when a model is updated or removed via ModelMgr.
     *
     * @param modelId the model ID whose specs should be invalidated; null clears the default
     * @since 0.1.16
     */
    public void invalidateSpecsCache(String modelId) {
        specsCache.invalidate(cacheKey(modelId));
    }

    /**
     * Invalidate all cached ProcessorSpecs.
     *
     * @since 0.1.16
     */
    public void invalidateAllSpecsCache() {
        specsCache.invalidateAll();
        lastInstalledModelKey = null;
    }

    /**
     * afterModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void afterModelCall(AgentCallbackContext ctx) {
        if (!isSessionMemoryEnabled || sessionMemoryManager == null || ctx == null) {
            return;
        }
        sessionMemoryManager.maybeScheduleUpdate(ctx.getSession(), ctx.getContext(),
                owner != null ? owner.getWorkspace() : null);
    }

    /**
     * onModelException.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void onModelException(AgentCallbackContext ctx) {
        fixIncompleteToolContext(ctx);
    }

    /**
     * isPreset.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isPreset() {
        return isPreset;
    }

    /**
     * getProcessorKeys.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> getProcessorKeys() {
        return List.copyOf(processorKeys);
    }

    /**
     * isSessionMemoryEnabled.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isSessionMemoryEnabled() {
        return isSessionMemoryEnabled;
    }

    /**
     * getSessionMemoryManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SessionMemoryManager getSessionMemoryManager() {
        return sessionMemoryManager;
    }

    /**
     * installedProcessors.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<ContextEngine.ProcessorSpec> installedProcessors() {
        return List.copyOf(installedProcessors);
    }

    /**
     * hasOffloadPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasOffloadPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(OFFLOAD_SECTION);
    }

    /**
     * ensureJsonArguments.
     * 
     * @param arguments arguments
     * @return the result
     * @since 0.1.7
     */
    public static String ensureJsonArguments(Object arguments) {
        if (arguments instanceof String string) {
            String trimmed = string.trim();
            return trimmed.startsWith("{") && trimmed.endsWith("}") ? string : "{}";
        }
        if (arguments instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                entries.add("\"" + String.valueOf(entry.getKey()) + "\":\"" + String.valueOf(entry.getValue()) + "\"");
            }
            return "{" + String.join(",", entries) + "}";
        }
        return "{}";
    }

    /**
     * fixIncompleteToolContext.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    public static void fixIncompleteToolContext(AgentCallbackContext ctx) {
        ModelContext context = ctx != null ? ctx.getContext() : null;
        if (context == null) {
            return;
        }
        List<BaseMessage> messages = context.getMessages();
        if (messages == null || messages.isEmpty()) {
            return;
        }
        List<BaseMessage> popped = context.popMessages(messages.size(), true);
        List<ToolCall> pending = new ArrayList<>();
        Map<String, ToolMessage> delayedTools = new LinkedHashMap<>();
        for (BaseMessage message : popped) {
            if (message instanceof AssistantMessage assistant) {
                flushPending(context, pending, delayedTools);
                context.addMessages(assistant);
                if (assistant.getToolCalls() != null) {
                    for (ToolCall call : assistant.getToolCalls()) {
                        call.setArguments(ensureJsonArguments(call.getArguments()));
                        pending.add(call);
                    }
                }
                continue;
            }
            if (message instanceof ToolMessage toolMessage) {
                if (pending.isEmpty()) {
                    context.addMessages(toolMessage);
                } else if (toolMessage.getToolCallId() != null
                        && toolMessage.getToolCallId().equals(pending.get(0).getId())) {
                    context.addMessages(toolMessage);
                    pending.remove(0);
                } else {
                    delayedTools.put(toolMessage.getToolCallId(), toolMessage);
                }
                continue;
            }
            flushPending(context, pending, delayedTools);
            context.addMessages(message);
        }
        flushPending(context, pending, delayedTools);
    }

    /**
     * buildProcessorSpecs.
     * 
     * @param agentConfig agentConfig
     * @return the result
     * @since 0.1.7
     */
    private List<ContextEngine.ProcessorSpec> buildProcessorSpecs(ReActAgentConfig agentConfig) {
        ModelRequestConfig modelConfig = agentConfig != null ? agentConfig.getModelConfigObj() : null;
        ModelClientConfig modelClientConfig = agentConfig != null ? agentConfig.getModelClientConfig() : null;
        return buildProcessorSpecs(agentConfig, modelConfig, modelClientConfig);
    }

    /**
     * buildProcessorSpecs with explicit model configs (for dynamic model support).
     *
     * @param agentConfig agentConfig
     * @param modelConfig request-level model config
     * @param modelClientConfig connection-level model config
     * @return the result
     * @since 0.1.16
     */
    private List<ContextEngine.ProcessorSpec> buildProcessorSpecs(ReActAgentConfig agentConfig,
            ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        Map<String, ContextEngine.ProcessorSpec> specs = new LinkedHashMap<>();
        if (isPreset) {
            if (isSessionMemoryEnabled) {
                putSpec(specs, "ToolResultBudgetProcessor", ToolResultBudgetProcessorConfig.builder().build());
                putSpec(specs, "MicroCompactProcessor", MicroCompactProcessorConfig.builder().build());
                putSpec(specs, "FullCompactProcessor",
                        FullCompactProcessorConfig.builder().model(modelConfig).modelClient(modelClientConfig).build());
            } else {
                putSpec(specs, "MessageSummaryOffloader",
                        MessageSummaryOffloaderConfig.builder().tokensThreshold(60000).largeMessageThreshold(60000)
                                .offloadMessageType(List.of("tool"))
                                .protectedToolNames(List.of("read_file:*SKILL.md", "reload_original_context_messages"))
                                .messagesToKeep(null).keepLastRound(false).model(modelConfig)
                                .modelClient(modelClientConfig).build());
                putSpec(specs, "DialogueCompressor",
                        DialogueCompressorConfig.builder().tokensThreshold(100000).messagesToKeep(10)
                                .keepLastRound(false).compressionTargetTokens(1800).model(modelConfig)
                                .modelClient(modelClientConfig).build());
                putSpec(specs, "CurrentRoundCompressor", CurrentRoundCompressorConfig.builder().tokensThreshold(100000)
                        .messagesToKeep(3).model(modelConfig).modelClient(modelClientConfig).build());
                putSpec(specs, "RoundLevelCompressor",
                        RoundLevelCompressorConfig.builder().triggerTotalTokens(230000).targetTotalTokens(160000)
                                .keepRecentMessages(6).model(modelConfig).modelClient(modelClientConfig).build());
            }
        }
        for (String key : processorKeys) {
            if (!specs.containsKey(key)) {
                putSpec(specs, key, defaultConfigFor(key, modelConfig, modelClientConfig));
            }
        }
        return new ArrayList<>(specs.values());
    }

    /**
     * putSpec.
     * 
     * @param specs specs
     * @param key key
     * @param config config
     * @since 0.1.7
     */
    private static void putSpec(Map<String, ContextEngine.ProcessorSpec> specs, String key, Object config) {
        specs.put(key, new ContextEngine.ProcessorSpec(key, config));
    }

    /**
     * defaultConfigFor.
     * 
     * @param key key
     * @param modelConfig modelConfig
     * @param modelClientConfig modelClientConfig
     * @return the result
     * @since 0.1.7
     */
    private static Object defaultConfigFor(String key, ModelRequestConfig modelConfig,
            ModelClientConfig modelClientConfig) {
        return switch (key) {
            case "MessageSummaryOffloader" ->
                MessageSummaryOffloaderConfig.builder().model(modelConfig).modelClient(modelClientConfig).build();
            case "DialogueCompressor" ->
                DialogueCompressorConfig.builder().model(modelConfig).modelClient(modelClientConfig).build();
            case "CurrentRoundCompressor" ->
                CurrentRoundCompressorConfig.builder().model(modelConfig).modelClient(modelClientConfig).build();
            case "RoundLevelCompressor" ->
                RoundLevelCompressorConfig.builder().model(modelConfig).modelClient(modelClientConfig).build();
            case "MicroCompactProcessor" -> MicroCompactProcessorConfig.builder().build();
            case "FullCompactProcessor" ->
                FullCompactProcessorConfig.builder().model(modelConfig).modelClient(modelClientConfig).build();
            case "ToolResultBudgetProcessor" -> ToolResultBudgetProcessorConfig.builder().build();
            default -> throw new IllegalArgumentException("Unknown context processor: " + key);
        };
    }

    /**
     * injectOffloadSection.
     * 
     * @since 0.1.7
     */
    private void injectOffloadSection() {
        if (owner == null) {
            return;
        }
        if (installedProcessors.isEmpty()) {
            owner.getAgent().getPromptBuilder().removeSection(OFFLOAD_SECTION);
            return;
        }
        String language = owner.getWorkspace().getLanguage();
        String content = "en".equalsIgnoreCase(language)
                ? "## Context Reload\n\n" + "Some older messages may be offloaded. Use reload_original_context_messages"
                        + " with the exact offload handle when you need original content."
                : "## 上下文重载\n\n" + "部分历史消息可能被卸载。需要原始内容时，使用 reload_original_context_messages 并提供准确的 offload"
                        + " handle。";
        owner.getAgent().getPromptBuilder().addSection(new PromptSection(OFFLOAD_SECTION,
                Map.of(language == null || language.isBlank() ? PromptSection.DEFAULT_LANGUAGE : language, content),
                60));
    }

    /**
     * flushPending.
     * 
     * @param context context
     * @param pending pending
     * @param delayedTools delayedTools
     * @since 0.1.7
     */
    private static void flushPending(ModelContext context, List<ToolCall> pending,
            Map<String, ToolMessage> delayedTools) {
        if (pending.isEmpty()) {
            return;
        }
        Set<String> flushed = new LinkedHashSet<>();
        for (ToolCall call : List.copyOf(pending)) {
            ToolMessage delayed = delayedTools.remove(call.getId());
            if (delayed != null) {
                context.addMessages(delayed);
            } else {
                context.addMessages(
                        ToolMessage
                                .builder().toolCallId(call.getId()).content("[Tool execution interrupted] Tool "
                                        + call.getName() + " was interrupted during execution, no result available.")
                                .build());
            }
            flushed.add(call.getId());
        }
        pending.removeIf(call -> flushed.contains(call.getId()));
        for (ToolMessage toolMessage : delayedTools.values()) {
            context.addMessages(toolMessage);
        }
        delayedTools.clear();
    }
}
