/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.utils.TextTruncate;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.context.SessionModelContext;
import com.openjiuwen.core.context.offload.api.OffloadCatalog;
import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.cleanup.OffloadJanitor;
import com.openjiuwen.core.context.offload.cleanup.SessionOffloadGate;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.FileOffloadStore;
import com.openjiuwen.core.context.offload.store.MemoryOffloadStore;
import com.openjiuwen.core.context.offload.store.RedisOffloadStore;
import com.openjiuwen.core.context.processor.offloader.MessageOffloader;
import com.openjiuwen.core.context.processor.offloader.MessageOffloaderConfig;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.classification.ModelCallClassification;
import com.openjiuwen.core.foundation.llm.modelclients.ModelTimeoutResolver;
import com.openjiuwen.core.foundation.llm.multimodal.ContentBlock;
import com.openjiuwen.core.foundation.llm.multimodal.DefaultMultimodalContentNormalizer;
import com.openjiuwen.core.foundation.llm.multimodal.ImageMaterialProvider;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessConfig;
import com.openjiuwen.core.foundation.llm.multimodal.MaterialAccessContext;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalContentNormalizer;
import com.openjiuwen.core.foundation.llm.multimodal.MultimodalContentResolver;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolCallArgumentUtils;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.session.AgentGroupSession;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.AgentTeamSession;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.PersistenceCheckpointer;
import com.openjiuwen.core.session.checkpointer.step.KvStepJournal;
import com.openjiuwen.core.session.checkpointer.step.PolicyEvaluator;
import com.openjiuwen.core.session.checkpointer.step.StepBoundary;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointPolicy;
import com.openjiuwen.core.session.checkpointer.step.StepCheckpointTrigger;
import com.openjiuwen.core.session.checkpointer.step.StepJournal;
import com.openjiuwen.core.session.checkpointer.step.StepSignalContext;
import com.openjiuwen.core.session.checkpointer.step.StepStatus;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.external.ExternalToolCallRequest;
import com.openjiuwen.core.singleagent.external.ExternalToolPendingState;
import com.openjiuwen.core.singleagent.external.ExternalToolResult;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ResumeContext;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentTerminationReason;
import com.openjiuwen.core.singleagent.rail.ClassifiedModelFailureRail;
import com.openjiuwen.core.singleagent.rail.ForceFinishRequest;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.Rails;
import com.openjiuwen.core.singleagent.runbudget.RunBudgetState;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.harness.task_loop.LoopQueues;
import com.openjiuwen.harness.tools.multimodal.FileSystemImageAssetStore;
import com.openjiuwen.harness.tools.multimodal.LocalImageMaterialProvider;
import com.openjiuwen.harness.tools.multimodal.MaterialAccessContexts;
import com.openjiuwen.spi.memory.MemoryRuntimeResolver;

import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * ReAct paradigm single-agent implementation.
 *
 * <p>Mirrors Python's {@code ReActAgent} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
public class ReActAgent extends BaseAgent implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ReActAgent.class.getName());

    public static final String IDENTITY_SECTION = "identity";
    public static final String SKILLS_SECTION = "skills";
    public static final int IDENTITY_SECTION_PRIORITY = 10;
    public static final int SKILLS_SECTION_PRIORITY = 90;
    public static final String EXTERNAL_TOOL_PENDING_KEY = "__react_agent_external_tool_pending__";

    /** Chunk size (characters) when wrapping non-stream responses as stream chunks. */
    private static final int STREAM_CHUNK_SIZE = 200;

    private static final int MAX_CAUSE_CHAIN_DEPTH = 32;

    /** JDK 17: bounded platform pool; JDK 21+: virtual thread per task. */
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("react-agent-stream", true);

    private static final String STREAM_INDEX_REF_KEY = "_stream_index_ref";
    private static final String MODEL_CALL_TIMEOUT_LEASE_ID_KEY = "_model_call_timeout_lease_id";
    private static final String MODEL_CALL_IDLE_TIMEOUT_MS_KEY = "_model_call_idle_timeout_ms";

    /** Stateless normalizer shared by all structured multimodal entry inputs. */
    private static final MultimodalContentNormalizer MULTIMODAL_NORMALIZER = new DefaultMultimodalContentNormalizer();
    private static final String EXTERNAL_TOOL_RESULT_ID_ERROR =
            "External tool results must contain exactly all pending tool_call_id values";
    private static final String EXTERNAL_TOOL_RESULTS_REQUIRED_ERROR =
            "External tool results are required before continuing this conversation";

    private final Object contextEngineLock = new Object();
    private final Object llmLock = new Object();
    private final ToolInterruptHandler hitlHandler;

    private ReActAgentConfig config;
    private volatile ContextEngine contextEngine;
    private ContextEngineConfig appliedContextEngineConfig;
    private volatile Model llm;
    private volatile ImageMaterialProvider materialProvider;
    private SystemPromptBuilder promptBuilder = new SystemPromptBuilder();
    private SystemPromptBuilder systemPromptBuilder = promptBuilder;
    private final com.openjiuwen.core.singleagent.kvcache.KVCacheModelCallHook kvCacheModelCallHook =
            new com.openjiuwen.core.singleagent.kvcache.KVCacheModelCallHook();

    /** Shared v2 offload pipeline; {@code null} until {@link #initContext} enables it. */
    private volatile OffloadService offloadService;

    /** Background reaper for offloaded payloads; non-null iff the pipeline is running. */
    private volatile OffloadJanitor offloadJanitor;

    /** Previous LLM-bound window per session, used for affinity window-diff eviction. */
    private final ConcurrentHashMap<String, ContextWindow> lastAffinityWindows = new ConcurrentHashMap<>();

    /** Step boundary trigger coordinating policy evaluation, cursor persistence, and audit. */
    private volatile StepCheckpointTrigger stepCheckpointTrigger =
            new StepCheckpointTrigger(
                    null,
                    null,
                    StepCheckpointPolicy.STEP_AUTO);

    public ReActAgent(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        setConfig(this.config);
        ContextEngineConfig initialConfig = effectiveContextEngineConfig(this.config.getContextEngineConfig());
        this.appliedContextEngineConfig = copyContextEngineConfig(initialConfig);
        this.contextEngine = new ContextEngine(this.appliedContextEngineConfig);
        this.hitlHandler = new ToolInterruptHandler(this);
        getAbilityManager().setContextEngine(contextEngine);
        initMemoryScope();
        registerInstanceRail(new ClassifiedModelFailureRail()).toCompletableFuture().join();
    }

    private void initMemoryScope() {
        if (config.getMemScopeId() != null && !config.getMemScopeId().isEmpty()) {
            MemoryRuntimeResolver.require().configureScope(config.getMemScopeId());
        }
    }

    public ReActAgentConfig createDefaultConfig() {
        return new ReActAgentConfig();
    }

    @Override
    public ReActAgent configure(Object config) {
        if (config != null && !(config instanceof ReActAgentConfig)) {
            throw new IllegalArgumentException("config must be ReActAgentConfig");
        }
        return configure((ReActAgentConfig) config);
    }

    public ReActAgent configure(ReActAgentConfig newConfig) {
        ReActAgentConfig oldConfig = this.config == null ? createDefaultConfig() : this.config;
        ReActAgentConfig effectiveConfig = newConfig == null ? createDefaultConfig() : newConfig;
        this.config = effectiveConfig;
        setConfig(effectiveConfig);

        if (!Objects.equals(oldConfig.getModelProvider(), effectiveConfig.getModelProvider())
                || !Objects.equals(oldConfig.getApiKey(), effectiveConfig.getApiKey())
                || !Objects.equals(oldConfig.getApiBase(), effectiveConfig.getApiBase())) {
            synchronized (llmLock) {
                llm = null;
            }
            kvCacheModelCallHook.resetWarnings();
        }
        refreshContextEngineIfNeeded();
        if (!Objects.equals(oldConfig.getMemScopeId(), effectiveConfig.getMemScopeId())) {
            initMemoryScope();
        }
        String systemContent = joinSystemPromptTemplate(effectiveConfig.getPromptTemplate());
        promptBuilder = new SystemPromptBuilder();
        systemPromptBuilder = promptBuilder;
        addPersistentPromptBuilderSection(IDENTITY_SECTION, systemContent, IDENTITY_SECTION_PRIORITY);
        return this;
    }

    public ReActAgent configure(ReActAgentConfig config, boolean ignored) {
        return configure(config);
    }

    public void setLlm(Model llm) {
        synchronized (llmLock) {
            this.llm = llm;
        }
    }

    public void set_llm(Model llm) {
        setLlm(llm);
    }

    public Model getLlm() {
        Model local = llm;
        if (local != null) {
            return local;
        }
        synchronized (llmLock) {
            local = llm;
            if (local != null) {
                return local;
            }
            if (config.getModelClientConfig() == null) {
                throw new IllegalStateException("model_client_config is required. Use configure_model_client() to set it.");
            }
            local = new Model(config.getModelClientConfig(), config.getModelConfigObj());
            llm = local;
            return local;
        }
    }

    /**
     * Resolves the model for the current request.
     * <p>
     * Priority: {@code ctx.dynamicModelId} / {@code target_model_id} extra, existing llm,
     * ResourceMgr default, then lazy config load.
     *
     * @param ctx callback context providing a dynamic model id; may be null
     * @return resolved model
     * @since 0.1.15
     */
    protected Model getLlm(AgentCallbackContext ctx) {
        String dynamicModelId = resolveDynamicModelId(ctx);
        if (dynamicModelId != null && !dynamicModelId.isBlank()) {
            ResourceMgr resourceMgr = Runner.resourceMgr();
            if (resourceMgr != null) {
                try {
                    return resourceMgr.resolveModel(
                            dynamicModelId,
                            config.getModelClientConfig(),
                            config.getModelConfigObj()
                    );
                } catch (IllegalStateException | IllegalArgumentException | CompletionException ignored) {
                    // Fall through to existing llm / default resolution.
                }
            }
        }
        if (llm != null) {
            return llm;
        }
        if (config.getModelClientConfig() == null) {
            ResourceMgr resourceMgr = Runner.resourceMgr();
            if (resourceMgr != null) {
                try {
                    return resourceMgr.resolveModel(null, null, null);
                } catch (IllegalStateException | IllegalArgumentException | CompletionException ignored) {
                    // Fall through to getLlm(), which throws a clearer config error.
                }
            }
        }
        return getLlm();
    }

    /**
     * Resolves the effective model name from the given Model instance, falling back
     * to {@code config.getModelName()} when the model or its modelConfig is null.
     * <p>
     * Ensures that when a dynamic model is selected via {@code ctx.dynamicModelId},
     * the provider receives that model's name rather than the static default from
     * {@code ReActAgentConfig}.
     *
     * @param model the resolved model, may be null
     * @return the effective model name
     * @since 0.1.15
     */
    private String resolveModelName(Model model) {
        if (model != null && model.getModelConfig() != null) {
            String modelName = model.getModelConfig().getModelName();
            if (modelName != null && !modelName.isBlank()) {
                return modelName;
            }
        }
        return config.getModelName();
    }

    private static String resolveDynamicModelId(AgentCallbackContext ctx) {
        if (ctx == null) {
            return null;
        }
        if (ctx.getDynamicModelId() != null && !ctx.getDynamicModelId().isBlank()) {
            return ctx.getDynamicModelId();
        }
        Object fromExtra = ctx.getExtra() == null ? null : ctx.getExtra().get("target_model_id");
        return fromExtra instanceof String text ? text : null;
    }

    public void addPromptBuilderSection(String name, String content, int priority) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            promptBuilder.removeSection(name);
            return;
        }
        promptBuilder.addSection(new PromptSection(name, Map.of("cn", text, "en", text), priority));
    }

    public void addPersistentPromptBuilderSection(String name, String content, int priority) {
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            promptBuilder.removeSection(name);
            return;
        }
        promptBuilder.addPersistentSection(new PromptSection(name, Map.of("cn", text, "en", text), priority));
    }

    public String buildRenderedSystemPrompt(Object inputs, Map<String, String> extraRenderFields) {
        List<SystemMessage> systemMessages = new ArrayList<>();
        for (Map<String, Object> item : config.getPromptTemplate()) {
            Object role = item.get("role");
            Object content = item.get("content");
            if ("system".equals(role) && content instanceof String text) {
                systemMessages.add(new SystemMessage(text));
            }
        }
        renderSystemMessages(systemMessages, inputs, extraRenderFields);
        List<String> rendered = new ArrayList<>();
        for (SystemMessage message : systemMessages) {
            Object content = message.getContent();
            if (content instanceof String text && !text.isEmpty()) {
                rendered.add(text);
            }
        }
        return String.join("\n\n", rendered);
    }

    public void updateSkillPromptBuilderSection(String renderedSystemPrompt) {
        SkillUtil skillUtil = getSkillUtil();
        if (renderedSystemPrompt == null || renderedSystemPrompt.isBlank() || skillUtil == null
                || !skillUtil.hasSkill()) {
            promptBuilder.removeSection(SKILLS_SECTION);
            return;
        }
        warnMissingSkillReadFileTool();
        addPersistentPromptBuilderSection(SKILLS_SECTION, skillUtil.getSkillPrompt(), SKILLS_SECTION_PRIORITY);
    }

    public List<BaseMessage> buildPreviewMessages(ModelContext context) {
        List<BaseMessage> previewMessages = new ArrayList<>(context.getMessages(null, true));
        String previewSystemPrompt = promptBuilder.build();
        if (!previewSystemPrompt.isBlank()) {
            previewMessages.add(0, new SystemMessage(previewSystemPrompt));
        }
        return previewMessages;
    }

    public Object callModel(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setTools(tools == null ? null : new ArrayList<>(tools));
        inputs.setModelContext(context);
        ctx.setInputs(inputs);

        Object aiMessage = railedModelCall(ctx);
        if (aiMessage instanceof AssistantMessage assistantMessage) {
            logLlmResponse(null, assistantMessage);
        }
        return aiMessage;
    }

    public Object _call_model(AgentCallbackContext ctx, ModelContext context, List<ToolInfo> tools) {
        return callModel(ctx, context, tools);
    }

    public Object railedModelCall(AgentCallbackContext ctx) {
        prepareModelRequest(ctx);
        boolean isModelCallSuccessful = false;
        try {
            Object result = Rails.run(
                    ctx,
                    AgentCallbackEvent.BEFORE_MODEL_CALL,
                    AgentCallbackEvent.AFTER_MODEL_CALL,
                    AgentCallbackEvent.ON_MODEL_EXCEPTION,
                    new Rails.RailedOperation() {
                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public Object execute() {
                            return doRailedModelCall(ctx);
                        }

                        /**
                         * {@inheritDoc}
                         */
                        @Override
                        public void onNoRetry() {
                            renewModelCallTimeout(ctx, Long.MAX_VALUE, 0L);
                        }
                    }
            );
            isModelCallSuccessful = true;
            return result;
        } finally {
            if (isModelCallSuccessful) {
                endModelCallTimeout(ctx);
            }
        }
    }

    public Object doRailedModelCall(AgentCallbackContext ctx) {
        ModelCallInputs modelInputs = ModelCallInputs.class.cast(ctx.getInputs());
        Map<String, String> requestHeaders = modelInputs.consumeRequestHeaders();
        Model model = getLlm(ctx);
        ContextWindow contextWindow = prepareModelContextWindow(modelInputs);
        List<BaseMessage> messages = contextWindow.getMessages();
        List<ToolInfo> tools = contextWindow.getTools();
        modelInputs.setMessages(new ArrayList<>(messages));
        modelInputs.setTools(new ArrayList<>(tools));
        logLlmRequest(null, messages, tools);

        ModelCallRuntime runtime = prepareModelCallRuntime(ctx, model, contextWindow);
        ModelInvokeOptions.ModelInvokeOptionsBuilder optionsBuilder =
                modelInvokeOptions(ctx, model, tools, requestHeaders, runtime.extraFields());
        com.openjiuwen.core.kvcache.KVCacheModelHook.RuntimeLease kvLease = kvCacheModelCallHook
                .beginInferenceLease(runtime.capabilities(), ctx.getSession(), runtime.extraFields())
                .join();
        runtime.extraFields().remove("__openjiuwen_kvc_model");
        ModelInvokeOptions options = optionsBuilder.build();
        renewModelCallTimeout(ctx, options);
        StreamModelCall modelCall = new StreamModelCall(ctx, model, messages, options, modelInputs);
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
            return invokeNonStreaming(modelCall, kvLease);
        }
        return streamModelResponseWithRetry(modelCall, kvLease);
    }

    private void prepareModelRequest(AgentCallbackContext ctx) {
        ModelCallInputs previous = ModelCallInputs.class.cast(ctx.getInputs());
        ModelContext context = previous.getModelContext();
        if (context == null) {
            context = ctx.getContext();
        }
        String systemPrompt = promptBuilder.build();
        List<BaseMessage> systemMessages = new ArrayList<>();
        if (!systemPrompt.isBlank()) {
            systemMessages.add(new SystemMessage(systemPrompt));
        }
        ContextWindow window = context.getContextWindow(systemMessages, toolInfoList(previous.getTools()),
                null, null, Map.of())
                .toCompletableFuture().join();
        List<BaseMessage> messages = window.getMessages();
        previous.setMessages(new ArrayList<>(messages));
        previous.setTools(new ArrayList<>(window.getTools()));
        previous.setModelContext(context);
    }

    private ContextWindow prepareModelContextWindow(ModelCallInputs inputs) {
        ContextWindow window = new ContextWindow();
        applyModelRequestMessages(window, inputs);
        window.setTools(toolInfoList(inputs.getTools()));
        return window;
    }

    private static void applyModelRequestMessages(ContextWindow window, ModelCallInputs inputs) {
        List<BaseMessage> messages = inputs.getMessages().stream().map(BaseMessage.class::cast).toList();
        int systemPrefixSize = 0;
        while (systemPrefixSize < messages.size() && "system".equals(messages.get(systemPrefixSize).getRole())) {
            systemPrefixSize++;
        }
        window.setSystemMessages(messages.subList(0, systemPrefixSize));
        window.setContextMessages(messages.subList(systemPrefixSize, messages.size()));
    }

    private ModelCallRuntime prepareModelCallRuntime(AgentCallbackContext ctx, Model model,
            ContextWindow contextWindow) {
        com.openjiuwen.core.singleagent.kvcache.KVCacheModelCallHook.KVCacheCallCapabilities kvCapabilities =
                kvCacheModelCallHook.resolveRuntime(model, config.isEnableKvCacheAffinity());
        String affinitySessionId = ctx.getSession() != null
                ? ctx.getSession().getSessionId()
                : ctx.getContext().sessionId();
        com.openjiuwen.core.kvcache.KVCacheMetadata.Lineage kvLineage =
                kvCacheModelCallHook.resolveLineage(kvCapabilities, ctx.getSession(), affinitySessionId);
        kvCacheModelCallHook.handleContextWindowChange(
                kvCapabilities,
                model,
                contextWindow,
                lastAffinityWindows.get(affinitySessionId),
                kvLineage
        ).join();
        lastAffinityWindows.put(affinitySessionId, contextWindow);

        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.putAll(kvCacheModelCallHook.buildInvokeKwargs(kvCapabilities, model, ctx.getSession(),
                kvLineage));
        extraFields.put("__openjiuwen_kvc_model", model);
        if (config.isLlmReturnTokenIds()) {
            extraFields.put("return_token_ids", true);
        }
        if (config.isLlmLogprobs()) {
            extraFields.put("logprobs", true);
            extraFields.put("top_logprobs", config.getLlmTopLogprobs());
        }
        return new ModelCallRuntime(kvCapabilities, extraFields);
    }

    private ModelInvokeOptions.ModelInvokeOptionsBuilder modelInvokeOptions(AgentCallbackContext ctx, Model model,
            List<ToolInfo> tools,
            Map<String, String> requestHeaders, Map<String, Object> extraFields) {
        // Use the resolved model's name (may differ from config when a dynamic
        // model was selected via ctx.dynamicModelId) so the provider receives
        // the correct model name.
        Float perCallIdleTimeout = resolvePerCallIdleTimeout(model);
        ModelInvokeOptions.ModelInvokeOptionsBuilder optionsBuilder = ModelInvokeOptions.builder()
                .model(resolveModelName(model))
                .tools(tools)
                .requestHeaders(requestHeaders)
                .extraFields(extraFields)
                .timeout(perCallIdleTimeout);
        clampModelCallTimeoutToRemainingBudget(ctx, optionsBuilder, perCallIdleTimeout);
        if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) && ctx.getSession() != null) {
            AgentSessionApi session = ctx.getSession();
            optionsBuilder.retryListener(event -> {
                renewClientModelCallTimeout(ctx, event);
                session.writeStream(new OutputSchema(
                        "model_retry",
                        nextStreamIndex(ctx),
                        modelRetryPayload(event)
                ));
            });
        }

        return optionsBuilder;
    }

    private static void renewModelCallTimeout(AgentCallbackContext ctx, ModelInvokeOptions options) {
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) || ctx.getSession() == null
                || options == null || options.getTimeout() == null) {
            return;
        }
        long idleTimeoutMs = ModelTimeoutResolver.toDuration(options.getTimeout().doubleValue()).toMillis();
        ctx.getExtra().put(MODEL_CALL_IDLE_TIMEOUT_MS_KEY, idleTimeoutMs);
        renewModelCallTimeout(ctx, idleTimeoutMs, 0L);
    }

    private static void renewClientModelCallTimeout(AgentCallbackContext ctx, ModelRetryEvent event) {
        Object actualIdle = ctx.getExtra().get(MODEL_CALL_IDLE_TIMEOUT_MS_KEY);
        if (!(actualIdle instanceof Number idleTimeout)) {
            return;
        }
        long delayMs = event.delay() == null ? 0L : event.delay().toMillis();
        renewModelCallTimeout(ctx, idleTimeout.longValue(), delayMs);
    }

    private static void renewModelCallTimeout(AgentCallbackContext ctx, long idleTimeoutMs, long delayMs) {
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) || ctx.getSession() == null) {
            return;
        }
        long leaseId = currentModelCallLeaseId(ctx);
        long renewedLease = ctx.getSession().renewModelCallTimeout(leaseId, idleTimeoutMs, delayMs);
        ctx.getExtra().put(MODEL_CALL_TIMEOUT_LEASE_ID_KEY, renewedLease);
    }

    private static long currentModelCallLeaseId(AgentCallbackContext ctx) {
        Object currentLease = ctx.getExtra().get(MODEL_CALL_TIMEOUT_LEASE_ID_KEY);
        return currentLease instanceof Number number ? number.longValue() : 0L;
    }

    private static void endModelCallTimeout(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getSession() == null
                || !Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
            return;
        }
        long leaseId = currentModelCallLeaseId(ctx);
        if (leaseId == 0L) {
            return;
        }
        ctx.getSession().endModelCallTimeout(leaseId);
        ctx.getExtra().put(MODEL_CALL_TIMEOUT_LEASE_ID_KEY, 0L);
    }

    private record ModelCallRuntime(
            com.openjiuwen.core.singleagent.kvcache.KVCacheModelCallHook.KVCacheCallCapabilities capabilities,
            Map<String, Object> extraFields) {
    }

    private AssistantMessage invokeNonStreaming(StreamModelCall call,
            com.openjiuwen.core.kvcache.KVCacheModelHook.RuntimeLease kvLease) {
        List<?> tools = call.options() == null ? List.of() : call.options().getTools();
        logModelCallStarted(call.ctx(), call.messages(), tools, false);
        long modelStartNanos = System.nanoTime();
        AssistantMessage aiMessage;
        boolean isKvSucceeded = false;
        try {
            aiMessage = call.model().invoke(call.messages(), call.options())
                    .toCompletableFuture().join();
            isKvSucceeded = true;
        } catch (BaseError | CompletionException | IllegalArgumentException | IllegalStateException
                | NullPointerException | ClassCastException | UnsupportedOperationException
                | UncheckedIOException exception) {
            logModelCallCompleted(call.ctx(), false, modelStartNanos, null, exception);
            com.openjiuwen.core.kvcache.KVCacheModelHook.end(kvLease, false).join();
            throw exception;
        }
        com.openjiuwen.core.kvcache.KVCacheModelHook.end(kvLease, isKvSucceeded).join();
        call.modelInputs().setResponse(aiMessage);
        logModelResponse(call.ctx(), aiMessage);
        logModelCallCompleted(call.ctx(), false, modelStartNanos, aiMessage, null);
        return aiMessage;
    }

    /**
     * Clamp the per-call model timeout to the remaining time budget.
     *
     * <p>No-op when no time-budgeted run-budget state is present, keeping the legacy path
     * byte-identical. The clamped value travels through {@code ModelInvokeOptions.timeout},
     * the single-call timeout channel. Baseline is the call's effective timeout — after
     * the call is the resolved idle-window value rather than the legacy default.</p>
     *
     * @param ctx callback context carrying the run-budget state
     * @param builder options builder under construction
     */
    private void clampModelCallTimeoutToRemainingBudget(AgentCallbackContext ctx,
                                                        ModelInvokeOptions.ModelInvokeOptionsBuilder builder,
                                                        Float effectiveTimeout) {
        Optional<RunBudgetState> budgetState = RunBudgetState.from(ctx.getExtra());
        if (budgetState.isEmpty() || !budgetState.get().isTimeEnabled()) {
            return;
        }
        double remaining = budgetState.get().remainingSeconds();
        if (remaining <= 0) {
            return;
        }
        double baseline = effectiveTimeout != null
                ? effectiveTimeout.doubleValue() : ModelClientConfig.DEFAULT_TIMEOUT_SECONDS;
        if (remaining < baseline) {
            builder.timeout((float) remaining);
        }
    }

    private Float resolvePerCallIdleTimeout(Model model) {
        ModelClientConfig clientConfig = model == null
                ? config.getModelClientConfig()
                : model.getModelClientConfig();
        return ModelTimeoutResolver.resolvePerCallIdleTimeout(
                config.getModelStreamIdleSeconds(), clientConfig);
    }

    public static void renderSystemMessages(List<SystemMessage> systemMessages, Object inputs,
                                            Map<String, String> extraRenderFields) {
        Map<String, String> renderFields = new LinkedHashMap<>();
        if (inputs instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getValue() instanceof String text) {
                    renderFields.put(String.valueOf(entry.getKey()), text);
                }
            }
        } else if (!(inputs instanceof InteractiveInput) && inputs != null) {
            renderFields.put("query", String.valueOf(inputs));
        }
        if (extraRenderFields != null) {
            renderFields.putAll(extraRenderFields);
        }
        if (renderFields.isEmpty()) {
            return;
        }
        for (SystemMessage message : systemMessages) {
            Object content = message.getContent();
            if (content instanceof String text) {
                message.setContent(renderPlaceholders(text, renderFields));
            }
        }
    }

    public List<AbilityManager.ExecutionResult> executeToolCall(AgentCallbackContext ctx, List<ToolCall> toolCalls,
                                                                AgentSessionApi session, ModelContext context) {
        if (ctx != null) {
            ToolInterruptionState.railState(ctx);
        }
        List<AbilityManager.ExecutionResult> results = executeToolCallsAndWriteToolMessages(ctx, toolCalls, session,
                context);
        appendMultimodalToolResultsMessage(results, context);
        return results;
    }

    private List<AbilityManager.ExecutionResult> executeToolCallsAndWriteToolMessages(
            AgentCallbackContext ctx,
            List<ToolCall> toolCalls,
            AgentSessionApi session,
            ModelContext context
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        listEffectiveToolInfo(session);
        Object previousSession = SessionContextHolder.getCurrentSession();
        if (previousSession == null && session != null) {
            SessionContextHolder.setCurrentSession(session);
        }
        List<Long> toolStartNanos = new ArrayList<>(toolCalls.size());
        List<Boolean> notFoundFlags = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            toolStartNanos.add(System.nanoTime());
            logToolCallStarted(ctx, toolCall);
            notFoundFlags.add(isToolRuntimeMissing(toolCall, session));
        }
        List<AbilityManager.ExecutionResult> results;
        try {
            results = getAbilityManager().execute(
                    ctx,
                    toolCalls,
                    session,
                    config == null || config.isParallelToolCalls(),
                    null,
                    toolCall -> findActiveSkillTool(toolCall.getName(), session)
            );
        } catch (RuntimeException exception) {
            for (int i = 0; i < toolCalls.size(); i++) {
                logToolCallCompleted(ctx, toolCalls.get(i), "exception", toolStartNanos.get(i));
            }
            throw exception;
        } finally {
            if (previousSession == null) {
                SessionContextHolder.restoreCurrentSession(null);
            }
        }
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall toolCall = toolCalls.get(i);
            AbilityManager.ExecutionResult result = i < results.size() ? results.get(i) : null;
            if (result != null && result.toolMessage() != null) {
                context.addMessages(result.toolMessage()).toCompletableFuture().join();
            }
            List<AbilityManager.ExecutionResult> perToolResults =
                    result == null ? List.of() : List.of(result);
            logToolCallCompleted(ctx, toolCall,
                    classifyToolOutcome(perToolResults, Boolean.TRUE.equals(notFoundFlags.get(i))),
                    toolStartNanos.get(i));
        }
        maybeForceFinishOnToolError(ctx, toolCalls, results);
        return results;
    }

    /**
     * When {@link ReActAgentConfig#isShouldFailTaskOnToolError()} is enabled, escalate tool
     * execution failures to a force-finish so the task does not close as COMPLETED (issue #51).
     */
    private void maybeForceFinishOnToolError(AgentCallbackContext ctx,
                                             List<ToolCall> toolCalls,
                                             List<AbilityManager.ExecutionResult> results) {
        if (ctx == null || ctx.hasForceFinishRequest()
                || config == null || !config.isShouldFailTaskOnToolError()
                || toolCalls == null || results == null) {
            return;
        }
        List<Map<String, Object>> outcomes = new ArrayList<>();
        String firstError = null;
        for (int i = 0; i < toolCalls.size(); i++) {
            ToolCall toolCall = toolCalls.get(i);
            AbilityManager.ExecutionResult result = i < results.size() ? results.get(i) : null;
            if (toolCall == null || result == null || !isAbilityExecutionError(result)) {
                continue;
            }
            String errorMsg = result.toolMessage() == null ? "Ability execution error"
                    : String.valueOf(result.toolMessage().getContent());
            if (firstError == null) {
                firstError = errorMsg;
            }
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("tool_name", toolCall.getName());
            outcome.put("tool_call_id", toolCall.getId());
            outcome.put("status", "failed");
            outcome.put("error", errorMsg);
            outcomes.add(outcome);
        }
        if (outcomes.isEmpty()) {
            return;
        }
        Map<String, Object> finishResult = new LinkedHashMap<>();
        finishResult.put("output", firstError);
        finishResult.put("result_type", "error");
        finishResult.put("tool_outcomes", outcomes);
        ctx.requestForceFinish(finishResult);
    }

    private static boolean isAbilityExecutionError(AbilityManager.ExecutionResult result) {
        if (result.result() != null || result.toolMessage() == null) {
            return false;
        }
        Object content = result.toolMessage().getContent();
        return isToolOrAbilityExecutionError(content == null ? null : String.valueOf(content));
    }

    private boolean isToolRuntimeMissing(ToolCall toolCall, AgentSessionApi session) {
        if (toolCall == null) {
            return true;
        }
        Optional<Tool> skillTool = findActiveSkillTool(toolCall.getName(), session);
        Object ability = getAbilityManager().get(toolCall.getName()).orElse(null);
        boolean toolRuntimeMissing = ability instanceof ToolCard toolCard
                && Runner.resourceMgr().getTool(toolCard.getId()) == null;
        return skillTool.isEmpty()
                && (ability == null || toolRuntimeMissing)
                && !getAbilityManager().isExternalTool(toolCall.getName());
    }

    private void appendMultimodalToolResultsMessage(List<AbilityManager.ExecutionResult> results,
                                                    ModelContext context) {
        UserMessage multimodalMessage = buildMultimodalToolResultsMessage(
                (results == null ? List.<AbilityManager.ExecutionResult>of() : results)
                        .stream()
                        .map(AbilityManager.ExecutionResult::result)
                        .toList()
        );
        if (multimodalMessage != null) {
            context.addMessages(multimodalMessage).toCompletableFuture().join();
        }
    }

    private void activateSkillsLoadedByToolCalls(List<ToolCall> toolCalls,
                                                 List<AbilityManager.ExecutionResult> results,
                                                 AgentSessionApi session) {
        if (toolCalls == null || toolCalls.isEmpty() || session == null) {
            return;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            ToolCall toolCall = toolCalls.get(index);
            if (toolCall == null || !isReadFileTool(toolCall.getName())) {
                continue;
            }
            AbilityManager.ExecutionResult result = results != null && index < results.size() ? results.get(index) : null;
            if (!isSuccessfulReadResultForRequestedPath(toolCall, result)) {
                continue;
            }
            extractPathArgument(toolCall)
                    .flatMap(this::findSkillNameByDocumentPath)
                    .ifPresent(skillName -> activateSkill(skillName, session));
        }
    }

    private boolean isSuccessfulReadResultForRequestedPath(ToolCall toolCall, AbilityManager.ExecutionResult result) {
        Optional<String> requestedPath = extractPathArgument(toolCall);
        if (requestedPath.isEmpty() || result == null || result.result() == null) {
            return false;
        }
        Object value = result.result();
        Object success = readAttribute(value, "success");
        if (Boolean.FALSE.equals(success)) {
            return false;
        }
        Object code = readAttribute(value, "code");
        if (code instanceof Number number && number.intValue() != 0) {
            return false;
        }
        Object data = readAttribute(value, "data");
        Object content = readAttribute(data, "content");
        Object resultPath = readAttribute(data, "path");
        if (content == null) {
            content = readAttribute(value, "content");
            if (resultPath == null) {
                resultPath = readAttribute(value, "path");
            }
        }
        if (content == null) {
            return false;
        }
        return resultPath != null && isSameNormalizedPath(requestedPath.get(), String.valueOf(resultPath));
    }

    private boolean isSameNormalizedPath(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        Optional<Path> normalizedLeft = normalizePath(left);
        Optional<Path> normalizedRight = normalizePath(right);
        return normalizedLeft.isPresent() && normalizedLeft.equals(normalizedRight);
    }

    private Optional<Path> normalizePath(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(String.valueOf(value)).toAbsolutePath().normalize());
        } catch (InvalidPathException exception) {
            return Optional.empty();
        }
    }

    private boolean isReadFileTool(String toolName) {
        return "readFile".equals(toolName)
                || "read_file".equals(toolName)
                || (toolName != null && (toolName.endsWith(".readFile") || toolName.endsWith(".read_file")));
    }

    private Optional<String> extractPathArgument(ToolCall toolCall) {
        if (toolCall == null || toolCall.getArguments() == null || toolCall.getArguments().isBlank()) {
            return Optional.empty();
        }
        try {
            Object parsedArguments = AbilityManager.parseToolArguments(toolCall.getArguments());
            if (parsedArguments instanceof Map<?, ?> map
                    && map.get("path") instanceof String path
                    && !path.isBlank()) {
                return Optional.of(path);
            }
        } catch (IllegalArgumentException ignored) {
            // expected: no action needed
        }
        return Optional.empty();
    }

    public static List<UserMessage> buildMultimodalToolResultMessages(Object toolResult) {
        UserMessage message = buildMultimodalToolResultsMessage(List.of(toolResult));
        return message == null ? List.of() : List.of(message);
    }

    public static UserMessage buildMultimodalToolResultsMessage(Iterable<?> toolResults) {
        List<Map<String, Object>> content = new ArrayList<>();
        List<String> loadedPaths = new ArrayList<>();
        if (toolResults != null) {
            for (Object toolResult : toolResults) {
                appendMultimodalParts(iterMultimodalImageItems(toolResult), "Image", "image_url", content,
                        loadedPaths);
                appendMultimodalParts(iterMultimodalVideoItems(toolResult), "Video", "video_url", content,
                        loadedPaths);
            }
        }
        if (content.isEmpty()) {
            return null;
        }
        if (loadedPaths.size() > 1) {
            content.add(0, new LinkedHashMap<>(Map.of("type", "text", "text", loadSummary(content, loadedPaths))));
        }
        UserMessage message = new UserMessage();
        message.setContent(content);
        return message;
    }

    /**
     * Collect image items from one tool result's {@code data.multimodal} list.
     *
     * <p>An item matches when its {@code type} is {@code image} and its
     * {@code data_url} is a {@code data:image/} base64 URI; HTTP URLs and
     * other types are ignored.</p>
     *
     * @param toolResult tool output carrying a multimodal list, may be {@code null}
     * @return matched image items, never {@code null}
     * @since 0.1.17
     */
    public static List<Map<String, Object>> iterMultimodalImageItems(Object toolResult) {
        return iterMultimodalItems(toolResult, "image", "data:image/");
    }

    /**
     * Collect video items from one tool result's {@code data.multimodal} list.
     *
     * <p>An item matches when its {@code type} is {@code video} and its
     * {@code data_url} is a {@code data:video/} base64 URI; HTTP URLs and
     * other types are ignored, mirroring the image filtering rules.</p>
     *
     * @param toolResult tool output carrying a multimodal list, may be {@code null}
     * @return matched video items, never {@code null}
     * @since 0.1.17
     */
    public static List<Map<String, Object>> iterMultimodalVideoItems(Object toolResult) {
        return iterMultimodalItems(toolResult, "video", "data:video/");
    }

    private static List<Map<String, Object>> iterMultimodalItems(Object toolResult, String itemType,
            String dataPrefix) {
        Object data = readAttribute(toolResult, "data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object rawItems = dataMap.get("multimodal");
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> matchedItems = new ArrayList<>();
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> item = stringObjectMap(rawMap);
            Object dataUrl = item.get("data_url");
            if (itemType.equals(item.get("type")) && dataUrl instanceof String text && text.startsWith(dataPrefix)) {
                matchedItems.add(item);
            }
        }
        return matchedItems;
    }

    private static void appendMultimodalParts(List<Map<String, Object>> items, String label, String partType,
            List<Map<String, Object>> content, List<String> loadedPaths) {
        for (Map<String, Object> item : items) {
            String sourcePath = Objects.toString(item.getOrDefault("source_path",
                    "unknown " + label.toLowerCase(Locale.ROOT)));
            Object dataUrl = item.get("data_url");
            loadedPaths.add(sourcePath);
            content.add(new LinkedHashMap<>(Map.of(
                    "type", "text",
                    "text", label + " loaded from read_file: " + sourcePath
            )));
            content.add(new LinkedHashMap<>(Map.of(
                    "type", partType,
                    partType, new LinkedHashMap<>(Map.of("url", dataUrl))
            )));
        }
    }

    private static String loadSummary(List<Map<String, Object>> content, List<String> loadedPaths) {
        List<String> summaryLines = new ArrayList<>();
        summaryLines.add(loadSummaryHeader(content));
        for (int i = 0; i < loadedPaths.size(); i++) {
            summaryLines.add((i + 1) + ". " + loadedPaths.get(i));
        }
        return String.join("\n", summaryLines);
    }

    private static String loadSummaryHeader(List<Map<String, Object>> content) {
        boolean hasImage = false;
        boolean hasVideo = false;
        for (Map<String, Object> part : content) {
            hasImage = hasImage || "image_url".equals(part.get("type"));
            hasVideo = hasVideo || "video_url".equals(part.get("type"));
        }
        if (hasImage && hasVideo) {
            return "Multimodal assets loaded by tool results:";
        }
        return hasVideo ? "Videos loaded by tool results:" : "Images loaded by tool results:";
    }

    public boolean isInterrupted(Object toolResult) {
        Object normalized = normalizedToolResult(toolResult);
        if (normalized instanceof WorkflowOutput workflowOutput) {
            return workflowOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED;
        }
        if (normalized instanceof List<?> list) {
            if (list.stream().anyMatch(ReActAgent::isInteractionItem)) {
                return true;
            }
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?> firstMap) {
                return isInterruptMap(firstMap);
            }
            return false;
        }
        return normalized instanceof Map<?, ?> map && isInterruptMap(map);
    }

    public List<String> extractComponentIds(Object toolResult) {
        Object normalized = normalizedToolResult(toolResult);
        List<String> ids = new ArrayList<>();
        if (normalized instanceof WorkflowOutput workflowOutput && workflowOutput.getResult() instanceof List<?> list) {
            for (Object item : list) {
                appendInteractionId(ids, item, "id");
            }
            return ids.stream().sorted().toList();
        }
        if (normalized instanceof List<?> list) {
            for (Object item : list) {
                appendInteractionId(ids, item, "component_id");
            }
            return ids.stream().sorted().toList();
        }
        if (!(normalized instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object componentIds = map.get("component_ids");
        if (componentIds instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        Object componentId = map.get("component_id");
        return componentId == null ? List.of() : List.of(String.valueOf(componentId));
    }

    public String extractWorkflowId(ToolCall toolCall) {
        if (toolCall == null) {
            return "workflow";
        }
        for (Object ability : getAbilityManager().list()) {
            Object name = readAttribute(ability, "name");
            if (Objects.equals(name, toolCall.getName())) {
                Object id = readAttribute(ability, "id");
                if (id != null && !String.valueOf(id).isBlank()) {
                    return String.valueOf(id);
                }
            }
        }
        if (toolCall.getName() != null && !toolCall.getName().isBlank()) {
            return toolCall.getName();
        }
        return toolCall.getId() == null || toolCall.getId().isBlank() ? "workflow" : toolCall.getId();
    }

    public InterruptionState afterExecuteToolCall(List<AbilityManager.ExecutionResult> results,
                                                  List<ToolCall> toolCalls,
                                                  AssistantMessage aiMessage,
                                                  int iteration,
                                                  String originalQuery) {
        Map<String, WorkflowInterruptEntry> interrupted = new LinkedHashMap<>();
        if (results == null || toolCalls == null) {
            return null;
        }
        int size = Math.min(results.size(), toolCalls.size());
        for (int i = 0; i < size; i++) {
            Object toolResult = results.get(i).result();
            if (!isInterrupted(toolResult)) {
                continue;
            }
            String workflowId = extractWorkflowId(toolCalls.get(i));
            interrupted.put(workflowId, new WorkflowInterruptEntry(
                    toolCalls.get(i),
                    extractComponentIds(toolResult),
                    workflowExecutionState(toolResult),
                    null
            ));
        }
        if (interrupted.isEmpty()) {
            return null;
        }
        InterruptionState state = new InterruptionState();
        state.setAiMessage(aiMessage);
        state.setIteration(iteration);
        state.setOriginalQuery(originalQuery);
        state.setInterruptedWorkflows(interrupted);
        Map.Entry<String, WorkflowInterruptEntry> first = interrupted.entrySet().iterator().next();
        state.setPendingWorkflowId(first.getKey());
        state.setPendingComponentId(first.getValue().getComponentIds().isEmpty()
                ? ""
                : first.getValue().getComponentIds().get(0));
        return state;
    }

    public void saveInterruptionState(InterruptionState state, AgentSessionApi session) {
        if (session != null) {
            session.updateState(Map.of(InterruptConstants.INTERRUPTION_KEY, state));
        }
    }

    public InterruptionState loadInterruptionState(AgentSessionApi session) {
        if (session == null) {
            return null;
        }
        Object state = session.getState(InterruptConstants.INTERRUPTION_KEY);
        return state instanceof InterruptionState interruptionState ? interruptionState : null;
    }

    public void clearInterruptionState(AgentSessionApi session) {
        if (session != null) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(InterruptConstants.INTERRUPTION_KEY, null);
            session.updateState(update);
        }
    }

    public Map<String, Object> buildInterruptResult(InterruptionState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "interrupt");
        result.put("workflow_execution_state", state);
        result.put("component_ids", state.getPendingComponentId() == null
                ? List.of()
                : List.of(state.getPendingComponentId()));
        return result;
    }

    public Map<String, Object> commitInterrupt(InterruptionState state, AgentSessionApi session,
                                               InvokeInputs invokeInputs) {
        saveInterruptionState(state, session);
        Map<String, Object> result = buildInterruptResult(state);
        invokeInputs.setResult(result);
        return result;
    }

    private void saveExternalToolPendingState(ExternalToolPendingState state, AgentSessionApi session) {
        if (session != null) {
            session.updateState(Map.of(EXTERNAL_TOOL_PENDING_KEY, state));
        }
    }

    private ExternalToolPendingState loadExternalToolPendingState(AgentSessionApi session) {
        if (session == null) {
            return null;
        }
        Object state = session.getState(EXTERNAL_TOOL_PENDING_KEY);
        return state instanceof ExternalToolPendingState pendingState ? pendingState : null;
    }

    private void clearExternalToolPendingState(AgentSessionApi session) {
        if (session != null) {
            Map<String, Object> update = new LinkedHashMap<>();
            update.put(EXTERNAL_TOOL_PENDING_KEY, null);
            session.updateState(update);
        }
    }

    private Object handleExternalToolResume(ExternalToolPendingState state, Object inputs, AgentCallbackContext ctx,
                                            ModelContext context, AgentSessionApi session,
                                            InvokeInputs invokeInputs) {
        ExternalResumeValidation validation = validateExternalToolResults(state, externalToolResultsInput(inputs));
        if (!validation.valid()) {
            return setExternalResumeError(invokeInputs, EXTERNAL_TOOL_RESULT_ID_ERROR);
        }

        ToolCall rejectedToolCall = state.getPendingToolCalls().stream()
                .filter(toolCall -> getAbilityManager().isExternalTool(toolCall.getName()))
                .filter(toolCall -> !getAbilityManager()
                        .isToolAvailableForExecution(toolCall.getName(), session))
                .findFirst()
                .orElse(null);
        if (rejectedToolCall != null) {
            return setExternalResumeError(
                    invokeInputs,
                    "Tool '" + rejectedToolCall.getName() + "' is not active. "
                            + "Activate its tool group before resuming execution.");
        }

        AssistantMessage resumeAiMessage = state.getAssistantMessage() == null
                ? AssistantMessage.builder().content("").toolCalls(state.getPendingToolCalls()).build()
                : copyAssistantMessage(state.getAssistantMessage());
        ensurePendingAssistantMessagePresent(context, resumeAiMessage, state.getPendingToolCalls());

        List<AbilityManager.ExecutionResult> results =
                executeExternalToolResume(
                        ctx, context, session, state.getPendingToolCalls(), validation);
        clearExternalToolPendingState(session);
        boolean completed = completeToolExecutionTurn(
                ctx,
                context,
                session,
                invokeInputs,
                state.getPendingToolCalls(),
                results,
                resumeAiMessage,
                state.getIteration(),
                state.getOriginalQuery(),
                ToolExecutionTurnOrigin.EXTERNAL_RESUME
        );
        return completed ? invokeInputs.getResult() : null;
    }

    private Map<String, Object> setExternalResumeError(InvokeInputs invokeInputs, String output) {
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
                "output", output,
                "result_type", "error"
        ));
        invokeInputs.setResult(result);
        return result;
    }

    private List<AbilityManager.ExecutionResult> executeExternalToolResume(
            AgentCallbackContext ctx,
            ModelContext context,
            AgentSessionApi session,
            List<ToolCall> toolCalls,
            ExternalResumeValidation validation) {
        List<AbilityManager.ExecutionResult> results = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            if (getAbilityManager().isExternalTool(toolCall.getName())) {
                ExternalToolResult externalResult = validation.resultsById().get(toolCall.getId());
                Object value = externalToolMessageValue(externalResult);
                ToolMessage message = new ToolMessage(AbilityManager.buildToolMessageContent(value),
                        toolCall.getId(), toolCall.getName());
                context.addMessages(message).toCompletableFuture().join();
                results.add(new AbilityManager.ExecutionResult(value, message));
                continue;
            }
            List<AbilityManager.ExecutionResult> executionResults = executeToolCallsAndWriteToolMessages(ctx,
                    List.of(toolCall), session, context);
            activateSkillsLoadedByToolCalls(List.of(toolCall), executionResults, session);
            results.addAll(executionResults);
        }
        return results;
    }

    private enum ToolExecutionTurnOrigin {
        NORMAL_TOOL_LOOP,
        EXTERNAL_RESUME
    }

    private boolean completeToolExecutionTurn(AgentCallbackContext ctx,
                                              ModelContext context,
                                              AgentSessionApi session,
                                              InvokeInputs invokeInputs,
                                              List<ToolCall> toolCalls,
                                              List<AbilityManager.ExecutionResult> results,
                                              AssistantMessage aiMessage,
                                              int iteration,
                                              String originalQuery,
                                              ToolExecutionTurnOrigin origin) {
        boolean externalResume = origin == ToolExecutionTurnOrigin.EXTERNAL_RESUME;
        if (externalResume) {
            appendMultimodalToolResultsMessage(results, context);
        }
        writeToolResultOutputs(ctx, session, toolCalls, results);

        ForceFinishRequest finish = ctx.consumeForceFinish();
        if (finish != null) {
            contextEngine.saveContexts(session);
            invokeInputs.setResult(finish.getResult());
            ctx.finish(AgentTerminationReason.FORCE_FINISH);
            return true;
        }

        ToolInterruptHandler.InterruptStateResult hitlInterrupt = hitlHandler.buildInterruptState(
                results.stream().map(AbilityManager.ExecutionResult::result).toList(),
                toolCalls,
                aiMessage,
                iteration,
                originalQuery
        );
        if (hitlInterrupt.getState() != null) {
            hitlInterrupt.getState().copyRailStateFrom(ToolInterruptionState.railState(ctx));
            if (externalResume) {
                contextEngine.saveContexts(session);
            }
            hitlHandler.commitInterrupt(hitlInterrupt.getState(), session, invokeInputs,
                    hitlInterrupt.getPayloads());
            ctx.finish(AgentTerminationReason.TOOL_INTERRUPT);
            return true;
        }

        InterruptionState workflowInterrupt = afterExecuteToolCall(
                results,
                toolCalls,
                aiMessage,
                iteration,
                originalQuery
        );
        if (workflowInterrupt != null) {
            if (externalResume) {
                contextEngine.saveContexts(session);
            }
            commitInterrupt(workflowInterrupt, session, invokeInputs);
            ctx.finish(AgentTerminationReason.TOOL_INTERRUPT);
            return true;
        }

        if (externalResume) {
            ctx.getExtra().put(InterruptConstants.RESUME_START_ITERATION_KEY, iteration + 1);
        }
        return false;
    }

    public Object handleResume(Object interruptionState, Object userInput, AgentCallbackContext ctx,
                               ModelContext context, AgentSessionApi session, InvokeInputs invokeInputs) {
        if (interruptionState instanceof ToolInterruptionState toolState) {
            ResumeContext resumeContext = new ResumeContext();
            resumeContext.setState(toolState);
            resumeContext.setUserInput(userInput);
            resumeContext.setCtx(ctx);
            resumeContext.setContext(context);
            resumeContext.setSession(session);
            resumeContext.setInvokeInputs(invokeInputs);
            resumeContext.setExecuteToolCall((callbackContext, calls, activeSession, activeContext) -> {
                List<AbilityManager.ExecutionResult> executionResults = executeToolCall(callbackContext, calls,
                        activeSession, activeContext);
                // Streaming resumes need the tool result chunk to pair with the confirmed tool
                // call chunk; the helper no-ops unless the invocation is streaming.
                writeToolResultOutputs(callbackContext, activeSession, calls, executionResults);
                return executionResults.stream().map(AbilityManager.ExecutionResult::result).toList();
            });
            return hitlHandler.handleResume(resumeContext);
        }
        if (interruptionState instanceof InterruptionState workflowState) {
            WorkflowInterruptEntry entry = workflowState.getInterruptedWorkflows().get(workflowState.getPendingWorkflowId());
            if (entry != null) {
                entry.setCollectedInput(buildInteractiveInput(userInput, pendingComponentIds(workflowState)));
            }
            boolean allCollected = workflowState.getInterruptedWorkflows()
                    .values()
                    .stream()
                    .allMatch(item -> item.getCollectedInput() != null);
            if (!allCollected) {
                for (Map.Entry<String, WorkflowInterruptEntry> pending : workflowState.getInterruptedWorkflows().entrySet()) {
                    if (pending.getValue().getCollectedInput() == null) {
                        workflowState.setPendingWorkflowId(pending.getKey());
                        List<String> componentIds = pending.getValue().getComponentIds();
                        workflowState.setPendingComponentId(componentIds.isEmpty() ? "" : componentIds.get(0));
                        return commitInterrupt(workflowState, session, invokeInputs);
                    }
                }
            }

            AssistantMessage resumeAiMessage = copyAssistantMessage(workflowState.getAiMessage());
            context.addMessages(resumeAiMessage).toCompletableFuture().join();
            List<ToolCall> allToolCalls = workflowState.getInterruptedWorkflows()
                    .values()
                    .stream()
                    .map(item -> copyToolCall(item.getToolCall(), item.getCollectedInput()))
                    .toList();
            List<AbilityManager.ExecutionResult> results = executeToolCall(ctx, allToolCalls, session, context);
            InterruptionState workflowInterrupt = afterExecuteToolCall(
                    results,
                    allToolCalls,
                    resumeAiMessage,
                    workflowState.getIteration(),
                    workflowState.getOriginalQuery());
            if (workflowInterrupt != null) {
                return commitInterrupt(workflowInterrupt, session, invokeInputs);
            }
            ctx.getExtra().put(InterruptConstants.RESUME_START_ITERATION_KEY, workflowState.getIteration() + 1);
        }
        return null;
    }

    private boolean isExternalToolResumeInput(Object inputs) {
        return inputs instanceof Map<?, ?> map && map.containsKey("external_tool_results");
    }

    private Object externalToolResultsInput(Object inputs) {
        return inputs instanceof Map<?, ?> map ? map.get("external_tool_results") : null;
    }

    private boolean hasExternalToolCall(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return false;
        }
        return toolCalls.stream().anyMatch(toolCall -> getAbilityManager().isExternalTool(toolCall.getName()));
    }

    private List<ExternalToolCallRequest> externalToolCallRequests(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        List<ExternalToolCallRequest> requests = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            if (getAbilityManager().isExternalTool(toolCall.getName())) {
                requests.add(new ExternalToolCallRequest(
                        toolCall.getId(),
                        toolCall.getName(),
                        toolCall.getArguments()
                ));
            }
        }
        return List.copyOf(requests);
    }

    private Map<String, Object> buildExternalToolPendingResult(ExternalToolPendingState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("result_type", "external_tool_call_required");
        result.put("external_tool_calls", state.getExternalToolCalls()
                .stream()
                .map(ExternalToolCallRequest::toMap)
                .toList());
        return result;
    }

    private Map<String, Object> buildExternalToolResultsRequiredResult() {
        return new LinkedHashMap<>(Map.of(
                "output", EXTERNAL_TOOL_RESULTS_REQUIRED_ERROR,
                "result_type", "error"
        ));
    }

    private ExternalResumeValidation validateExternalToolResults(ExternalToolPendingState state, Object value) {
        List<ExternalToolResult> results;
        try {
            results = ExternalToolResult.fromInput(value);
        } catch (IllegalArgumentException exception) {
            return ExternalResumeValidation.invalid();
        }
        List<String> pendingIds = state.getExternalToolCalls()
                .stream()
                .map(ExternalToolCallRequest::getToolCallId)
                .toList();
        Map<String, ExternalToolResult> byId = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (ExternalToolResult result : results) {
            if (!seen.add(result.getToolCallId())) {
                return ExternalResumeValidation.invalid();
            }
            byId.put(result.getToolCallId(), result);
        }
        if (byId.size() != pendingIds.size()) {
            return ExternalResumeValidation.invalid();
        }
        for (String pendingId : pendingIds) {
            if (!byId.containsKey(pendingId)) {
                return ExternalResumeValidation.invalid();
            }
        }
        return new ExternalResumeValidation(true, byId);
    }

    private Object externalToolMessageValue(ExternalToolResult result) {
        if (result.getError() != null) {
            return Map.of("success", false, "error", result.getError());
        }
        return result.getResult();
    }

    private void ensurePendingAssistantMessagePresent(ModelContext context, AssistantMessage assistantMessage,
                                                      List<ToolCall> pendingToolCalls) {
        if (hasAssistantWithToolCallIds(context.getMessages(null, true), pendingToolCalls)) {
            return;
        }
        context.addMessages(assistantMessage).toCompletableFuture().join();
    }

    private static boolean hasAssistantWithToolCallIds(List<BaseMessage> messages, List<ToolCall> pendingToolCalls) {
        Set<String> pendingIds = toolCallIds(pendingToolCalls);
        if (pendingIds.isEmpty()) {
            return false;
        }
        for (BaseMessage message : messages == null ? List.<BaseMessage>of() : messages) {
            if (message instanceof AssistantMessage assistantMessage
                    && toolCallIds(assistantMessage.getToolCalls()).containsAll(pendingIds)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> toolCallIds(List<ToolCall> toolCalls) {
        Set<String> ids = new HashSet<>();
        for (ToolCall toolCall : toolCalls == null ? List.<ToolCall>of() : toolCalls) {
            if (toolCall != null && toolCall.getId() != null) {
                ids.add(toolCall.getId());
            }
        }
        return ids;
    }

    private record ExternalResumeValidation(boolean valid, Map<String, ExternalToolResult> resultsById) {
        private static ExternalResumeValidation invalid() {
            return new ExternalResumeValidation(false, Map.of());
        }
    }

    public String extractUserText(Object userInput) {
        if (userInput instanceof InteractiveInput interactiveInput) {
            if (!interactiveInput.getUserInputs().isEmpty()) {
                return String.valueOf(interactiveInput.getUserInputs().values().iterator().next());
            }
            return interactiveInput.getRawInputs() == null ? "" : String.valueOf(interactiveInput.getRawInputs());
        }
        return String.valueOf(userInput);
    }

    /**
     * Normalize one user input into ordered multimodal content blocks.
     *
     * <p>Companion of {@link #extractUserText(Object)}: the text extractor
     * keeps its original semantics for {@code _original_query} and other text
     * usages, while this method exposes the normalized block view required by
     * the multimodal contract. Both coexist.</p>
     *
     * @param userInput string, content block list, structured message map or
     *                  {@link BaseMessage}; may be {@code null}
     * @return immutable ordered blocks, never {@code null}
     * @throws com.openjiuwen.core.common.exception.BaseError with
     *         {@code details.reason=CONTENT_INVALID} when the structure
     *         violates the content block contract
     * @since 0.1.17
     */
    public List<ContentBlock> extractUserContentBlocks(Object userInput) {
        return MULTIMODAL_NORMALIZER.normalize(userInput);
    }

    /**
     * Inject the image material provider used to resolve structured entry input.
     *
     * <p>Material resolution is an injected capability, not a static utility:
     * the contract lives on the message side while this default implementation
     * lives in the image domain of the harness.</p>
     *
     * @param materialProvider provider instance; {@code null} restores the
     *                         framework default built per request
     * @since 0.1.17
     */
    public void setMaterialProvider(ImageMaterialProvider materialProvider) {
        this.materialProvider = materialProvider;
    }

    /**
     * Return the injected image material provider.
     *
     * @return injected provider, or {@code null} when the default applies
     * @since 0.1.17
     */
    public ImageMaterialProvider getMaterialProvider() {
        return materialProvider;
    }

    private boolean isMultimodalContentInput(Object inputs, Object userInput) {
        return inputs instanceof List
                || inputs instanceof BaseMessage
                || MultimodalContentNormalizer.isMultimodalContent(userInput);
    }

    private String extractOriginalQueryText(Object inputs, Object userInput) {
        if (!isMultimodalContentInput(inputs, userInput)) {
            return extractUserText(userInput);
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : MULTIMODAL_NORMALIZER.normalize(userInput)) {
            if (block instanceof ContentBlock.Text textBlock) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(textBlock.text());
            }
        }
        return text.toString();
    }

    private UserMessage buildEntryUserMessage(Object inputs, Object userInput) {
        if (!isMultimodalContentInput(inputs, userInput)) {
            return new UserMessage(extractUserText(userInput));
        }
        MaterialAccessConfig materialAccess = config.getMaterialAccess();
        MaterialAccessContext access = MaterialAccessContexts.fromConfig(materialAccess);
        List<ContentBlock> blocks = MULTIMODAL_NORMALIZER.normalize(userInput);
        Loggers.AGENT.debug("structured multimodal input accepted, blocks={}", blocks.size());
        String imageDetail = materialAccess == null ? null : materialAccess.getImageDetail();
        MultimodalContentResolver resolver =
                new MultimodalContentResolver(resolveMaterialProvider(access), imageDetail);
        MultimodalContentResolver.ResolutionResult result = resolver.resolve(blocks, access);
        UserMessage message = new UserMessage();
        message.setContent(result.contentParts());
        attachEntryMultimodalMetadata(message, result);
        return message;
    }

    private void attachEntryMultimodalMetadata(UserMessage message,
                                               MultimodalContentResolver.ResolutionResult result) {
        if (result.itemErrors().isEmpty() && result.provenance().isEmpty()) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (!result.itemErrors().isEmpty()) {
            metadata.put("multimodal_item_errors", result.itemErrors());
        }
        if (!result.provenance().isEmpty()) {
            metadata.put("multimodal_provenance", result.provenance());
        }
        message.setMetadata(metadata);
    }

    private ImageMaterialProvider resolveMaterialProvider(MaterialAccessContext access) {
        ImageMaterialProvider injected = materialProvider;
        if (injected != null) {
            return injected;
        }
        return new LocalImageMaterialProvider(new FileSystemImageAssetStore(Path.of(access.assetRoot())));
    }

    public InteractiveInput buildInteractiveInput(Object userQuery, List<String> componentIds) {
        if (userQuery instanceof InteractiveInput interactiveInput) {
            Set<String> providedIds = new HashSet<>(interactiveInput.getUserInputs().keySet());
            Object fallback = providedIds.isEmpty()
                    ? ""
                    : interactiveInput.getUserInputs().values().iterator().next();
            for (String componentId : componentIds == null ? List.<String>of() : componentIds) {
                if (!providedIds.contains(componentId)) {
                    interactiveInput.update(componentId, fallback);
                }
            }
            return interactiveInput;
        }
        InteractiveInput interactiveInput = new InteractiveInput();
        if (componentIds != null && !componentIds.isEmpty()) {
            for (String componentId : componentIds) {
                interactiveInput.update(componentId, String.valueOf(userQuery));
            }
        } else {
            interactiveInput.setRawInputs(String.valueOf(userQuery));
        }
        return interactiveInput;
    }

    public ModelContext initContext(AgentSessionApi session) {
        ContextEngine activeContextEngine = refreshContextEngineIfNeeded();
        ModelContext context = activeContextEngine.createContext(null, session, config.getContextProcessors(), null,
                null);
        bindOffloadPipeline(context, session);
        ModelContext.ToolPort contextReloader = context.reloaderTool();
        if (appliedContextEngineConfig.isEnableReload()) {
            Tool reloader = asSessionReloader(contextReloader);
            if (reloader != null) {
                getAbilityManager().add(reloader.getCard());
                getAbilityManager().registerSessionTool(
                        session == null ? null : session.getSessionId(), reloader);
            }
        } else if (contextReloader != null) {
            getAbilityManager().remove(contextReloader.name());
        }
        return context;
    }

    private static Tool asSessionReloader(ModelContext.ToolPort port) {
        if (port instanceof Tool tool) {
            return tool;
        }
        if (port instanceof SessionModelContext.ReloaderTool reloader) {
            return new ReloaderAbility(reloader);
        }
        return null;
    }

    /**
     * Wires the Context Offload v2 pipeline into a freshly created context
     * (架构文档 §2.2, T-P0-05). Legacy mode stays untouched when
     * {@code enable_offload} is false.
     *
     * <p>Assembly order matters: build the store chain first, then the
     * service, and bind the context before the processors so a concurrent
     * save never observes a half-wired pipeline.</p>
     */
    private void bindOffloadPipeline(ModelContext context, AgentSessionApi session) {
        ContextEngineConfig engineConfig = appliedContextEngineConfig;
        if (engineConfig == null || !engineConfig.isEnableOffload() || !(context instanceof SessionModelContext smc)) {
            return;
        }
        if (smc.getOffloadService() != null) {
            // Context reuse (engine pool): already bound — keep the single shared pipeline.
            return;
        }
        try {
            OffloadConfig offloadConfig = engineConfig.getOffload() == null
                    ? new OffloadConfig()
                    : engineConfig.getOffload();
            CompositeOffloadStore compositeStore = buildCompositeStore(offloadConfig);
            OffloadCatalog catalog = new OffloadCatalogImpl();
            OffloadService service = new OffloadServiceImpl(catalog, compositeStore,
                    new SessionOffloadGate(), offloadConfig);
            Supplier<OffloadNamespace> namespaceSupplier = () -> OffloadNamespace.of("",
                    session == null ? smc.sessionId() : session.getSessionId(), smc.contextId());
            smc.bindOffloadService(service, namespaceSupplier);
            for (MessageOffloader offloader : findOffloaders(smc)) {
                syncProtectionConfig(offloader, offloadConfig);
                offloader.setOffloadService(service, namespaceSupplier);
            }
            this.offloadService = service;
            startOffloadJanitor(catalog, compositeStore, service, offloadConfig);
            Loggers.AGENT.info("Context offload v2 pipeline bound, session={}", namespaceSupplier.get());
        } catch (RuntimeException ex) {
            // Intentional: agent loop must not propagate exceptions to the model
            // Offload is an enhancement, never a hard dependency: degrade to legacy mode.
            Loggers.AGENT.warning("Context offload v2 pipeline binding failed, falling back to legacy mode: "
                    + ex.getMessage());
        }
    }

    /**
     * Builds the backend chain honoring primary/fallback/final_fallback with
     * the canonical FILE → REDIS → MEMORY default (架构文档 §3.4).
     */
    private static CompositeOffloadStore buildCompositeStore(OffloadConfig offloadConfig) {
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        addBackendToChain(chain, offloadConfig.getPrimaryBackend(), offloadConfig);
        addBackendToChain(chain, offloadConfig.getFallbackBackend(), offloadConfig);
        addBackendToChain(chain, offloadConfig.getFinalFallback(), offloadConfig);
        return new CompositeOffloadStore(chain);
    }

    private static void addBackendToChain(LinkedHashMap<OffloadBackend, OffloadStore> chain,
                                          OffloadBackend backend, OffloadConfig offloadConfig) {
        if (backend == null || backend == OffloadBackend.COMPOSITE || chain.containsKey(backend)) {
            return;
        }
        switch (backend) {
            case FILESYSTEM -> chain.put(backend, new FileOffloadStore(resolveWorkspaceRoot(offloadConfig),
                    offloadConfig.isEnableFileLock()));
            case REDIS -> {
                if (offloadConfig.getRedis() != null) {
                    chain.put(backend, new RedisOffloadStore(offloadConfig.getRedis()));
                }
            }
            case MEMORY -> chain.put(backend, new MemoryOffloadStore());
            default -> Loggers.AGENT.info("Offload backend not part of a concrete chain, skipped: {}", backend);
        }
    }

    private static java.nio.file.Path resolveWorkspaceRoot(OffloadConfig offloadConfig) {
        String configured = offloadConfig.getWorkspaceRoot();
        if (configured != null && !configured.isBlank()) {
            return java.nio.file.Path.of(configured);
        }
        return java.nio.file.Path.of(System.getProperty("user.dir"), "offload");
    }

    /**
     * Finds every offloader processor instance (MessageOffloader and its
     * {@code ToolResultBudgetProcessor} subclass) carried by the context.
     */
    private static List<MessageOffloader> findOffloaders(SessionModelContext smc) {
        List<MessageOffloader> offloaders = new ArrayList<>();
        for (SessionModelContext.ContextProcessorPort processor : smc.getProcessors()) {
            if (processor instanceof MessageOffloader offloader) {
                offloaders.add(offloader);
            }
        }
        return offloaders;
    }

    /**
     * V-3 单一配置源: the pipeline-level {@code OffloadConfig} overrides the
     * processor defaults for the shared protection window so
     * {@code recent_message_protection_count} / {@code protected_tool_names}
     * configured on {@code enableOffload.offload} take effect (they were dead
     * settings before). Explicit non-default processor values are preserved.
     */
    private static void syncProtectionConfig(MessageOffloader offloader, OffloadConfig offloadConfig) {
        if (offloadConfig == null) {
            return;
        }
        MessageOffloaderConfig target = offloader.getConfig();
        if (target == null) {
            return;
        }
        if (offloadConfig.getRecentMessageProtectionCount() != 5) {
            target.setRecentMessageProtectionCount(offloadConfig.getRecentMessageProtectionCount());
        }
        List<String> pipelineTools = offloadConfig.getProtectedToolNames();
        List<String> defaultTools = List.of("reload_original_context_messages");
        if (pipelineTools != null && !pipelineTools.equals(defaultTools)) {
            target.setProtectedToolNames(pipelineTools);
        }
    }

    /**
     * Starts one shared janitor per agent; the singleton guards make restarts
     * (e.g. after {@code refreshContextEngineIfNeeded}) no-ops.
     */
    private void startOffloadJanitor(OffloadCatalog catalog, CompositeOffloadStore store,
                                     OffloadService service, OffloadConfig offloadConfig) {
        if (offloadJanitor != null) {
            return;
        }
        synchronized (this) {
            if (offloadJanitor == null) {
                OffloadJanitor janitor = new OffloadJanitor(catalog, store, service, offloadConfig);
                janitor.start();
                this.offloadJanitor = janitor;
            }
        }
    }

    /**
     * Releases agent-scoped resources: stops the offload janitor daemon
     * (D-3 资源回收). Idempotent; the agent stays usable for reads after
     * close, only the background reaping stops.
     */
    @Override
    public void close() {
        OffloadJanitor janitor = this.offloadJanitor;
        if (janitor != null) {
            janitor.close();
        }
    }

    public OffloadService getOffloadService() {
        return offloadService;
    }

    private static final class ReloaderAbility extends Tool {
        private final SessionModelContext.ReloaderTool delegate;

        private ReloaderAbility(SessionModelContext.ReloaderTool delegate) {
            super(cardOf(delegate));
            this.delegate = delegate;
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            Map<String, Object> safe = inputs == null ? Map.of() : inputs;
            return delegate.reloadOriginalContextMessages(
                    stringArg(safe.get("offload_handle")),
                    stringArg(safe.get("offload_type")));
        }

        private static ToolCard cardOf(SessionModelContext.ReloaderTool reloader) {
            ToolInfo info = reloader.toolInfo();
            String name = info == null || info.getName() == null || info.getName().isBlank()
                    ? reloader.name()
                    : info.getName();
            return ToolCard.builder()
                    .id(name)
                    .name(name)
                    .description(info == null || info.getDescription() == null ? "" : info.getDescription())
                    .inputParams(info == null || info.getParameters() == null ? Map.of() : info.getParameters())
                    .build();
        }

        private static String stringArg(Object value) {
            return value == null ? "" : String.valueOf(value);
        }
    }

    @Override
    public Object invoke(Object inputs, AgentSessionApi session) {
        return invoke(inputs, session, Map.of());
    }

    @Override
    public Object invoke(Object inputs, AgentSession session) {
        AgentSessionApi apiSession = session;
        return invoke(inputs, apiSession);
    }

    /**
     * Run one agent invocation.
     *
     * <p>Accepted input shapes: a {@code Map} carrying {@code query} (the
     * query value may itself be a string, a multimodal content block list, a
     * structured message with {@code content}, or a {@link BaseMessage}), a
     * plain string, a multimodal content block list, or a {@link BaseMessage}
     * instance. Structured shapes share one dispatch point with the streaming
     * entry because {@code stream} delegates here.</p>
     *
     * @param inputs agent input; see the accepted shapes above
     * @param session target session; {@code null} creates a temporary one
     * @param kwargs extra invoke options such as {@code _streaming}
     * @return invocation result map, never {@code null}
     * @throws IllegalArgumentException when the input shape is not accepted
     * @throws com.openjiuwen.core.common.exception.BaseError with
     *         {@code details.reason} such as {@code CONTENT_INVALID} or
     *         {@code SOURCE_UNRESOLVED} when structured input violates the
     *         multimodal contract; parsing never falls back to stringifying
     * @since 0.1.17
     */
    public Object invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String)
                && !(inputs instanceof List) && !(inputs instanceof BaseMessage))) {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }
        Object query;
        String conversationId = null;
        StepCheckpointPolicy invokePolicyOverride = null;
        if (inputs instanceof Map<?, ?> map) {
            query = map.containsKey("query") ? map.get("query") : "";
            Object conversation = map.get("conversation_id");
            conversationId = conversation == null ? null : String.valueOf(conversation);
            invokePolicyOverride = StepCheckpointPolicy.fromString(map.get("step_checkpoint_policy"));
        } else {
            query = inputs;
        }
        bindStepCheckpointTrigger(session, invokePolicyOverride);

        boolean needCleanup = false;
        AgentSessionApi activeSession = session;
        if (activeSession == null) {
            activeSession = AgentSession.createAgentSession(
                    conversationId == null || conversationId.isBlank() ? "default_session" : conversationId,
                    null,
                    getCard()
            );
            activeSession.preRun(inputs instanceof Map<?, ?> ? Map.of("inputs", inputs) : Map.of());
            needCleanup = true;
        }
        return innerInvoke(
                activeSession,
                inputs,
                query,
                needCleanup,
                conversationId,
                kwargs
        );
    }

    /**
     * Bind the step checkpoint trigger to the current invocation: wire the
     * session's checkpointer, a journal when the KV store is available, and
     * the effective policy resolved from the agent-level default and the
     * per-invocation override.
     *
     * @param session invocation session, may be null
     * @param invokeOverride per-invocation policy override; null means "not overridden"
     */
    private void bindStepCheckpointTrigger(AgentSessionApi session, StepCheckpointPolicy invokeOverride) {
        Checkpointer checkpointer = resolveCheckpointer(session);
        StepJournal journal = resolveStepJournal(checkpointer);
        StepCheckpointPolicy effectivePolicy = new PolicyEvaluator()
                .resolve(config.getStepCheckpointPolicy(), invokeOverride);
        stepCheckpointTrigger = new StepCheckpointTrigger(checkpointer, journal, effectivePolicy,
                new PolicyEvaluator());
    }

    private Checkpointer resolveCheckpointer(AgentSessionApi session) {
        if (session instanceof AgentSession typedSession && typedSession.getInner() != null
                && typedSession.getInner().checkpointer() != null) {
            return typedSession.getInner().checkpointer();
        }
        return null;
    }

    private StepJournal resolveStepJournal(Checkpointer checkpointer) {
        if (checkpointer instanceof PersistenceCheckpointer persistence) {
            return new KvStepJournal(persistence.kvStore());
        }
        return null;
    }

    /**
     * Unwrap the product session to the internal {@link BaseSession} expected
     * by the checkpointer step entry points.
     *
     * @param session invocation session, may be null
     * @return the internal session, or null when unavailable
     */
    private static BaseSession typedBaseSession(AgentSessionApi session) {
        if (session instanceof AgentSession typedSession) {
            return typedSession.getInner();
        }
        if (session instanceof BaseSession baseSession) {
            return baseSession;
        }
        return null;
    }

    /**
     * Build the AUTO signal context for the current invocation.
     *
     * @param session invocation session, may be null
     * @return signal context populated with tool count and HITL capability
     */
    private StepSignalContext stepSignals(AgentSessionApi session) {
        List<ToolInfo> tools = listEffectiveToolInfo(session);
        boolean hasInteractiveCapability = tools.stream()
                .anyMatch(tool -> "ask_user".equals(tool.getName()));
        return new StepSignalContext(
                "",
                false,
                hasInteractiveCapability,
                tools.size(),
                stepCheckpointTrigger.observedSteps(),
                0L);
    }

    /**
     * Join tool call names for cursor step-name recording.
     *
     * @param toolCalls tool calls of the finished step, may be null
     * @return comma-joined names, empty when no calls
     */
    private static String toolCallNames(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "";
        }
        List<String> names = new ArrayList<>(toolCalls.size());
        for (ToolCall toolCall : toolCalls) {
            names.add(toolCall.getName() == null ? "" : toolCall.getName());
        }
        return String.join(",", names);
    }

    /**
     * Duration of the current ReAct iteration in millis, derived from the
     * loop start timestamp recorded by the callback context.
     *
     * @param ctx callback context of the running invocation
     * @return elapsed millis since the iteration started; 0 when unknown
     */
    private static long iterationDurationMillis(AgentCallbackContext ctx) {
        Object startedAt = ctx == null ? null : ctx.getExtra().get("_iteration_started_at_millis");
        if (!(startedAt instanceof Number number)) {
            return 0L;
        }
        return Math.max(0L, System.currentTimeMillis() - number.longValue());
    }

    public Object innerInvoke(AgentSessionApi session, Object inputs, Object query, boolean needCleanup,
                              String conversationId, Map<String, Object> kwargs) {
        boolean iterationFailureLogged = false;
        boolean streaming = false;
        boolean initializationComplete = false;
        AgentCallbackContext ctx = null;
        InvokeInputs invokeInputs = new InvokeInputs();
        Object previousSession = SessionContextHolder.getCurrentSession();
        SessionContextHolder.setCurrentSession(session);
        try {
            promptBuilder.clearTransient();
            invokeInputs.setQuery(query);
            invokeInputs.setConversationId(conversationId);

            ctx = new AgentCallbackContext(this);
            ctx.setInputs(invokeInputs);
            ctx.setSession(session);
            ctx.setConfig(config);
            ctx.initializeLoop(config.getMaxIterations());
            streaming = Boolean.TRUE.equals(kwargs.get("_streaming"));
            ctx.getExtra().put("_streaming", streaming);
            if (streaming) {
                ctx.getExtra().put(STREAM_INDEX_REF_KEY, new int[] {0});
            }
            Queue<String> steeringQueue = null;
            if (inputs instanceof Map<?, ?> map) {
                putExtra(ctx, "user_id", map.get("user_id"));
                copyRemainingExtras(map, ctx);
                bindDynamicModelIdFromInputs(map, ctx);
                Object rawSteeringQueue = map.get("_steering_queue");
                if (rawSteeringQueue instanceof Queue<?> queue) {
                    @SuppressWarnings("unchecked")
                    Queue<String> typedQueue = (Queue<String>) queue;
                    steeringQueue = typedQueue;
                }
            }
            bindSteeringQueue(ctx, steeringQueue);
            initializationComplete = true;
            fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);
            Object userInput = invokeInputs.getQuery();
            ExternalToolPendingState externalPending = loadExternalToolPendingState(session);
            boolean externalResume = externalPending != null && isExternalToolResumeInput(inputs);
            ToolInterruptionState hitlState = externalResume ? null : hitlHandler.load(session);
            Object interruptionState = hitlState != null ? hitlState
                    : (externalResume ? null : loadInterruptionState(session));
            if (externalPending == null && interruptionState == null) {
                ForceFinishRequest earlyFinish = ctx.consumeForceFinish();
                if (earlyFinish != null) {
                    invokeInputs.setResult(earlyFinish.getResult());
                    ctx.finish(AgentTerminationReason.FORCE_FINISH);
                    ctx.fire(AgentCallbackEvent.AFTER_INVOKE);
                    Object result = ctx.getExtra().getOrDefault("invoke_result", invokeInputs.getResult());
                    if (streaming && result instanceof Map<?, ?> map) {
                        writeInvokeResultToStreamInternal(stringObjectMap(map), session, streamIndexRef(ctx));
                    }
                    return result;
                }
            }
            if (externalPending != null && !externalResume) {
                invokeInputs.setResult(buildExternalToolResultsRequiredResult());
                ctx.finish(AgentTerminationReason.ERROR);
                ctx.fire(AgentCallbackEvent.AFTER_INVOKE);
                Object result = ctx.getExtra().getOrDefault("invoke_result", invokeInputs.getResult());
                if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) && result instanceof Map<?, ?> map) {
                    writeInvokeResultToStreamInternal(stringObjectMap(map), session, streamIndexRef(ctx));
                }
                return result;
            }
            if (!externalResume && (userInput == null || String.valueOf(userInput).isEmpty())) {
                throw new IllegalArgumentException("Input must contain 'query'");
            }

            if (interruptionState != null) {
                if (hitlState != null) {
                    hitlHandler.clear(session);
                } else {
                    clearInterruptionState(session);
                }
                if (interruptionState instanceof ToolInterruptionState toolInterruptionState) {
                    ctx.getExtra().put("_original_query", toolInterruptionState.getOriginalQuery());
                } else if (interruptionState instanceof InterruptionState workflowState) {
                    ctx.getExtra().put("_original_query", workflowState.getOriginalQuery());
                }
            } else if (externalResume) {
                ctx.getExtra().put("_original_query", externalPending.getOriginalQuery());
            } else {
                ctx.getExtra().put("_original_query", extractOriginalQueryText(inputs, userInput));
            }

            ModelContext context = initContext(session);
            ctx.setContext(context);
            String renderedSystemPrompt = buildRenderedSystemPrompt(
                    inputs,
                    stringStringMap(ctx.getExtra().get("memory_variables"))
            );
            addPersistentPromptBuilderSection(IDENTITY_SECTION, renderedSystemPrompt, IDENTITY_SECTION_PRIORITY);
            updateSkillPromptBuilderSection(renderedSystemPrompt);

            int startIteration = 0;
            if (externalResume) {
                ctx.enterIteration(externalPending.getIteration());
                Object resumeResult = handleExternalToolResume(externalPending, inputs, ctx, context, session,
                        invokeInputs);
                if (resumeResult == null) {
                    startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                }
            } else if (interruptionState != null) {
                if (interruptionState instanceof ToolInterruptionState toolInterruptionState) {
                    ctx.enterIteration(toolInterruptionState.getIteration());
                    handleResume(interruptionState, userInput, ctx, context, session, invokeInputs);
                    startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                } else if (interruptionState instanceof InterruptionState workflowState) {
                    ctx.enterIteration(workflowState.getIteration());
                    context.addMessages(buildEntryUserMessage(inputs, userInput)).toCompletableFuture().join();
                    Object resumeResult = handleResume(interruptionState, userInput, ctx, context, session,
                            invokeInputs);
                    if (resumeResult == null) {
                        startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                    }
                }
            } else {
                context.addMessages(buildEntryUserMessage(inputs, userInput)).toCompletableFuture().join();
            }

            if (invokeInputs.getResult() == null) {
                for (int iteration = startIteration; iteration < ctx.getMaxIterations(); iteration++) {
                    ctx.enterIteration(iteration);
                    int iterationNumber = iteration + 1;
                    ctx.getExtra().put("_iteration_started_at_millis", System.currentTimeMillis());
                    Loggers.AGENT.info("ReAct iteration {}/{} started",
                            iterationNumber, ctx.getMaxIterations());
                    try {
                        List<String> steering = ctx.drainSteering();
                        if (!steering.isEmpty()) {
                            context.addMessages(new UserMessage("[STEERING] " + String.join("\n", steering)))
                                    .toCompletableFuture()
                                    .join();
                        }
                        List<ToolInfo> tools = listEffectiveToolInfo(session);
                        Object modelResult;
                        try {
                            modelResult = callModel(ctx, context, tools);
                        } catch (RuntimeException exception) {
                            ctx.finish(AgentTerminationReason.MODEL_ERROR);
                            throw exception;
                        }
                        ForceFinishRequest finish = ctx.consumeForceFinish();
                        boolean hasForceFinishSignal = ctx.consumeForceFinishSignal();
                        if (finish != null || hasForceFinishSignal) {
                            contextEngine.saveContexts(session);
                            invokeInputs.setResult(finish == null
                                    ? terminationResult(modelResult)
                                    : finish.getResult());
                            ctx.finish(AgentTerminationReason.FORCE_FINISH);
                            break;
                        }
                        if (!(modelResult instanceof AssistantMessage aiMessage)) {
                            if (ctx.hasPendingSteering()) {
                                continue;
                            }
                            invokeInputs.setResult(
                                    modelResult instanceof Map<?, ?> map ? stringObjectMap(map) : Map.of());
                            ctx.finish(terminationReasonForResult(modelResult, AgentTerminationReason.MODEL_ERROR));
                            break;
                        }
                        List<ToolCall> toolCalls = aiMessage.getToolCalls();
                        ensureToolCallIds(toolCalls);
                        repairToolCallJsonObjectArguments(toolCalls);
                        context.addMessages(copyAssistantMessage(aiMessage)).toCompletableFuture().join();
                        if (toolCalls == null || toolCalls.isEmpty()) {
                            if (ctx.hasPendingSteering()) {
                                continue;
                            }
                            contextEngine.saveContexts(session);
                            invokeInputs.setResult(new LinkedHashMap<>(Map.of(
                                    "output", Objects.toString(aiMessage.getContent(), ""),
                                    "result_type", "answer"
                            )));
                            ctx.finish(AgentTerminationReason.TEXT_TERMINATION);
                            break;
                        }
                        writeToolCallOutputs(ctx, session, toolCalls);
                        if (hasExternalToolCall(toolCalls)) {
                            logExternalToolPending(ctx, toolCalls, iteration);
                            ExternalToolPendingState pendingState = new ExternalToolPendingState(
                                    copyAssistantMessage(aiMessage),
                                    iteration,
                                    Objects.toString(ctx.getExtra().get("_original_query"), ""),
                                    toolCalls,
                                    externalToolCallRequests(toolCalls)
                            );
                            saveExternalToolPendingState(pendingState, session);
                            contextEngine.saveContexts(session);
                            writeExternalToolPendingOutput(ctx, session, pendingState);
                            invokeInputs.setResult(buildExternalToolPendingResult(pendingState));
                            ctx.finish(AgentTerminationReason.TOOL_INTERRUPT);
                            break;
                        }
                        List<AbilityManager.ExecutionResult> results = executeToolCall(
                                ctx, toolCalls, session, context);
                        activateSkillsLoadedByToolCalls(toolCalls, results, session);
                        if (completeToolExecutionTurn(
                                ctx,
                                context,
                                session,
                                invokeInputs,
                                toolCalls,
                                results,
                                aiMessage,
                                iteration,
                                Objects.toString(ctx.getExtra().get("_original_query"), ""),
                                ToolExecutionTurnOrigin.NORMAL_TOOL_LOOP)) {
                            break;
                        }
                        stepCheckpointTrigger.stepCompleted(
                                typedBaseSession(session),
                                null,
                                stepSignals(session),
                                new StepBoundary("TOOL_CALL", toolCallNames(toolCalls), StepStatus.COMPLETED,
                                        iterationDurationMillis(ctx)));
                    } catch (RuntimeException exception) {
                        iterationFailureLogged = true;
                        stepCheckpointTrigger.stepCompleted(
                                typedBaseSession(session),
                                null,
                                null,
                                new StepBoundary("ITERATION", "iteration-" + iterationNumber, StepStatus.FAILED,
                                        iterationDurationMillis(ctx)));
                        Loggers.AGENT.exception(
                                "ReAct iteration %d/%d failed"
                                        .formatted(iterationNumber, ctx.getMaxIterations()),
                                exception
                        );
                        throw exception;
                    } finally {
                        Loggers.AGENT.info("ReAct iteration {}/{} ended",
                                iterationNumber, ctx.getMaxIterations());
                    }
                }
                if (invokeInputs.getResult() == null) {
                    Loggers.AGENT.error(
                            "ReActAgent reached max iterations without completion: {}",
                            ctx.getMaxIterations()
                    );
                    contextEngine.saveContexts(session);
                    invokeInputs.setResult(new LinkedHashMap<>(Map.of(
                            "output", "Max iterations reached without completion",
                            "result_type", "error"
                    )));
                    ctx.finish(AgentTerminationReason.MAX_ITERATIONS);
                }
            }
            if (ctx.getTerminationReason() == null) {
                ctx.finish(terminationReasonForResult(invokeInputs.getResult(), AgentTerminationReason.ERROR));
            }
            ctx.setInputs(invokeInputs);
            ctx.fire(AgentCallbackEvent.AFTER_INVOKE);
            Object result = ctx.getExtra().getOrDefault("invoke_result", invokeInputs.getResult());
            if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
                if (result instanceof Map<?, ?> map) {
                    writeInvokeResultToStreamInternal(stringObjectMap(map), session, streamIndexRef(ctx));
                } else if (result instanceof List<?> list) {
                    for (Object schema : list) {
                        session.writeStream(schema);
                    }
                }
            }
            return result;
        } catch (RuntimeException exception) {
            if (ctx != null && ctx.getTerminationReason() == null) {
                ctx.finish(AgentTerminationReason.ERROR);
            }
            if (initializationComplete && ctx.getEvent() != AgentCallbackEvent.AFTER_INVOKE) {
                ctx.setInputs(invokeInputs);
                ctx.fire(AgentCallbackEvent.AFTER_INVOKE);
            }
            if (!iterationFailureLogged) {
                Loggers.AGENT.exception("ReActAgent invoke failed", exception);
            }
            if (initializationComplete && streaming) {
                try {
                    Map<String, Object> errorResult = buildErrorResult(exception);
                    writeInvokeResultToStreamInternal(errorResult, session, streamIndexRef(ctx));
                    return errorResult;
                } catch (RuntimeException streamingErrorHandlingFailure) {
                    Loggers.AGENT.exception(
                            "ReActAgent streaming error handling failed",
                            streamingErrorHandlingFailure
                    );
                    throw streamingErrorHandlingFailure;
                }
            }
            throw exception;
        } finally {
            try {
                if (needCleanup && initializationComplete) {
                    try {
                        contextEngine.saveContexts(session);
                        closeStreamAndCommit(session);
                    } catch (RuntimeException exception) {
                        Loggers.AGENT.exception("ReActAgent cleanup failed", exception);
                        throw exception;
                    }
                }
            } finally {
                try {
                    endModelCallTimeout(ctx);
                } finally {
                    SessionContextHolder.restoreCurrentSession(previousSession);
                }
            }
        }
    }

    private static AgentTerminationReason terminationReasonForResult(Object result,
                                                                     AgentTerminationReason fallback) {
        if (!(result instanceof Map<?, ?> map)) {
            return fallback;
        }
        Object resultType = map.get("result_type");
        if ("answer".equals(resultType)) {
            return AgentTerminationReason.TEXT_TERMINATION;
        }
        if ("interrupt".equals(resultType) || "external_tool_call_required".equals(resultType)) {
            return AgentTerminationReason.TOOL_INTERRUPT;
        }
        return fallback;
    }

    private static Map<String, Object> terminationResult(Object result) {
        if (result instanceof Map<?, ?> map) {
            return stringObjectMap(map);
        }
        return new LinkedHashMap<>(Map.of(
                "output", Objects.toString(result, ""),
                "result_type", "answer"
        ));
    }

    private int[] streamIndexRef(AgentCallbackContext ctx) {
        if (ctx == null) {
            return new int[] {0};
        }
        Object existing = ctx.getExtra().get(STREAM_INDEX_REF_KEY);
        if (existing instanceof int[] ref) {
            return ref;
        }
        int[] ref = new int[] {0};
        ctx.getExtra().put(STREAM_INDEX_REF_KEY, ref);
        return ref;
    }

    private int nextStreamIndex(AgentCallbackContext ctx) {
        int[] ref = streamIndexRef(ctx);
        return ref[0]++;
    }

    /**
     * Renews the current model wait through the guardrail delay, then publishes the retry event.
     * Observation is best-effort and cannot revoke the pending model retry.
     *
     * @param context current model callback context
     * @param retryCount guardrail-local retry sequence, starting at one
     * @param maxRetries guardrail-local retry budget
     * @param delaySeconds delay requested for the guardrail retry, in seconds
     */
    public void publishGuardrailRetry(AgentCallbackContext context, int retryCount, int maxRetries,
            double delaySeconds) {
        if (context == null || context.getSession() == null
                || !Boolean.TRUE.equals(context.getExtra().get("_streaming"))) {
            return;
        }
        renewGuardrailModelCallTimeout(context, delaySeconds);
        Map<String, Object> payload = guardrailRetryPayload(retryCount, maxRetries, delaySeconds);
        try {
            context.getSession().writeStream(new OutputSchema(
                    "model_retry",
                    nextStreamIndex(context),
                    payload
            ));
        } catch (IllegalStateException | IllegalArgumentException | NullPointerException exception) {
            Loggers.AGENT.warning("Guardrail retry event write failed; retry continues. {}", Map.of(
                    "retry_count", retryCount,
                    "write_exception_type", exception.getClass().getName()));
        }
    }

    private static void renewGuardrailModelCallTimeout(AgentCallbackContext context, double delaySeconds) {
        Object actualIdle = context.getExtra().get(MODEL_CALL_IDLE_TIMEOUT_MS_KEY);
        if (!(actualIdle instanceof Number idleTimeout)) {
            return;
        }
        long delayMs = guardrailDelayMillis(delaySeconds);
        renewModelCallTimeout(context, idleTimeout.longValue(), delayMs);
    }

    private static long guardrailDelayMillis(double delaySeconds) {
        long roundedMillis = BigDecimal.valueOf(delaySeconds)
                .multiply(BigDecimal.valueOf(1000L))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();
        return Math.max(0L, roundedMillis);
    }

    private static Map<String, Object> guardrailRetryPayload(int retryCount, int maxRetries, double delaySeconds) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("retry_count", retryCount);
        payload.put("max_retries", maxRetries);
        payload.put("delay_ms", guardrailDelayMillis(delaySeconds));
        payload.put("delay_source", "guardrail");
        payload.put("message", "模型调用预算耗尽，护栏层发起整轮重试 %d/%d".formatted(retryCount, maxRetries));
        payload.put("retry_tier", "rail");
        return payload;
    }

    private static Map<String, Object> modelRetryPayload(ModelRetryEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("retry_count", event.retryCount());
        payload.put("max_retries", event.maxRetries());
        payload.put("delay_ms", event.delay() == null ? 0L : event.delay().toMillis());
        payload.put("delay_source", event.delaySource());
        payload.put("message", "模型请求失败，正在进行第 %d/%d 次重试"
                .formatted(event.retryCount(), event.maxRetries()));
        if (event.statusCode() != null) {
            payload.put("status_code", event.statusCode());
        } else if (event.exceptionType() != null) {
            payload.put("exception_type", event.exceptionType());
        }
        payload.put("retry_tier", "client");
        return payload;
    }

    private void ensureToolCallIds(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        for (ToolCall toolCall : toolCalls) {
            ToolLifecycleOutputFactory.ensureToolCallId(toolCall);
        }
    }

    private void repairToolCallJsonObjectArguments(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        for (ToolCall toolCall : toolCalls) {
            if (toolCall != null) {
                toolCall.setArguments(ToolCallArgumentUtils.repairJsonObject(toolCall.getArguments()));
            }
        }
    }

    private void writeToolCallOutputs(
            AgentCallbackContext ctx,
            AgentSessionApi session,
            List<ToolCall> toolCalls
    ) {
        if (session == null || toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
            return;
        }
        for (ToolCall toolCall : toolCalls) {
            session.writeStream(ToolLifecycleOutputFactory.buildToolCallOutput(toolCall, nextStreamIndex(ctx)));
        }
    }

    private void writeExternalToolPendingOutput(
            AgentCallbackContext ctx,
            AgentSessionApi session,
            ExternalToolPendingState pendingState
    ) {
        if (session == null || pendingState == null) {
            return;
        }
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
            return;
        }
        session.writeStream(ToolLifecycleOutputFactory.buildExternalToolPendingOutput(
                pendingState.getExternalToolCalls(),
                nextStreamIndex(ctx)
        ));
    }

    private void writeToolResultOutputs(
            AgentCallbackContext ctx,
            AgentSessionApi session,
            List<ToolCall> toolCalls,
            List<AbilityManager.ExecutionResult> results
    ) {
        if (session == null || toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        if (!Boolean.TRUE.equals(ctx.getExtra().get("_streaming"))) {
            return;
        }
        for (int index = 0; index < toolCalls.size(); index++) {
            AbilityManager.ExecutionResult result = results != null && index < results.size()
                    ? results.get(index)
                    : null;
            if (isPendingInterruptResult(result)) {
                continue;
            }
            session.writeStream(ToolLifecycleOutputFactory.buildToolResultOutput(
                    toolCalls.get(index),
                    result,
                    nextStreamIndex(ctx)
            ));
        }
    }

    private static boolean isPendingInterruptResult(AbilityManager.ExecutionResult result) {
        if (result == null) {
            return false;
        }
        Object value = result.result();
        return value instanceof ToolInterruptException || ToolInterruptHandler.isSubAgentInterrupt(value);
    }

    private void writeInvokeResultToStreamInternal(Map<String, Object> result, AgentSessionApi session) {
        writeInvokeResultToStreamInternal(result, session, null);
    }

    private void writeInvokeResultToStreamInternal(
            Map<String, Object> result,
            AgentSessionApi session,
            int[] streamIndexRef
    ) {
        if (result == null || session == null) {
            return;
        }
        Object resultType = result.get("result_type");
        if ("external_tool_call_required".equals(resultType)) {
            return;
        }
        if ("interrupt".equals(resultType)) {
            if (result.containsKey("interrupt_ids")) {
                ToolInterruptHandler.writeInterruptToStream(result, session);
                return;
            }
            Object workflowState = result.get("workflow_execution_state");
            Object componentIds = result.get("component_ids");
            String pendingId = componentIds instanceof List<?> list && !list.isEmpty()
                    ? String.valueOf(list.get(0))
                    : null;
            Object schemas = readAttribute(workflowState, "result");
            if (!(schemas instanceof List<?>) && workflowState instanceof InterruptionState state) {
                WorkflowInterruptEntry entry = state.getInterruptedWorkflows().get(state.getPendingWorkflowId());
                Object pendingState = entry == null ? null : entry.getWorkflowExecutionState();
                schemas = readAttribute(pendingState, "result");
            }
            if (schemas instanceof List<?> list) {
                for (Object schema : list) {
                    Object payload = readAttribute(schema, "payload");
                    Object id = readAttribute(payload, "id");
                    if (pendingId == null || pendingId.equals(id)) {
                        session.writeStream(schema);
                    }
                }
            }
            return;
        }
        // Align with Python react_agent: final answer OutputSchema always uses index=0.
        if (streamIndexRef != null) {
            streamIndexRef[0]++;
        }
        session.writeStream(new OutputSchema(
                "answer",
                0,
                toAnswerPayload(result)
        ));
    }

    /**
     * Builds the wire payload for a final answer chunk, preserving the structured failure
     * descriptor when the invoke result carries one.
     *
     * @param result invoke result map
     * @return answer payload with output, result type, and an optional failure entry
     * @since 0.1.17
     */
    static Map<String, Object> toAnswerPayload(Map<String, Object> result) {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of(
                "output", Objects.toString(result.get("output"), ""),
                "result_type", Objects.toString(result.get("result_type"), "")
        ));
        if (result.get("failure") instanceof Map<?, ?> failure) {
            payload.put("failure", failure);
        }
        return payload;
    }

    @Override
    public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        if (session == null) {
            throw new IllegalArgumentException("Session is required for streaming");
        }
        session.preRun(streamPreRunPayload(inputs));
        if (!runsStreamInBackground(session)) {
            runStreamingInvoke(inputs, session, false);
            return session.streamIterator();
        }
        return startBackgroundStream(inputs, session);
    }

    @Override
    public Iterator<Object> stream(Object inputs, AgentSession session, List<StreamMode> streamModes) {
        AgentSessionApi apiSession = session;
        return stream(inputs, apiSession, streamModes);
    }

    private Iterator<Object> startBackgroundStream(Object inputs, AgentSessionApi session) {
        String streamThreadName = resolveStreamWorkerThreadName(inputs);
        Future<?> streamFuture;
        try {
            streamFuture = STREAM_EXECUTOR.submit(() -> runNamedStreamingInvoke(inputs, session, streamThreadName));
        } catch (RejectedExecutionException exception) {
            writeInvokeResultToStreamInternal(buildErrorResult(exception), session);
            closeStreamAndCommit(session);
            return session.streamIterator();
        }
        return OperatorStream.wrap(session.streamIterator(), () -> streamFuture.cancel(true));
    }

    private void runNamedStreamingInvoke(Object inputs, AgentSessionApi session, String streamThreadName) {
        Thread worker = Thread.currentThread();
        String previousName = worker.getName();
        worker.setName(streamThreadName);
        try {
            runStreamingInvoke(inputs, session, false);
        } finally {
            worker.setName(previousName);
        }
    }

    private void runStreamingInvoke(Object inputs, AgentSessionApi finalSession, boolean finalNeedCleanup) {
        try {
            invoke(inputs, finalSession, Map.of("_streaming", true));
        } catch (RuntimeException exception) {
            writeInvokeResultToStreamInternal(buildErrorResult(exception), finalSession);
        } finally {
            if (finalNeedCleanup) {
                contextEngine.saveContexts(finalSession);
            }
            closeStreamAndCommit(finalSession);
        }
    }

    private static Map<String, Object> streamPreRunPayload(Object inputs) {
        if (inputs instanceof Map<?, ?>) {
            return Map.of("inputs", inputs);
        }
        return Map.of();
    }

    private static boolean runsStreamInBackground(AgentSessionApi session) {
        return session instanceof AgentSession
                || session instanceof AgentTeamSession
                || session instanceof AgentGroupSession
                || "DeepAgentSession".equals(session.getClass().getSimpleName());
    }

    public void clearSession(String sessionId) {
        invokeStaticRunnerRelease(sessionId);
        cleanupOffloadNamespaces(sessionId, null);
        contextEngine.clearContext(null, sessionId);
        if (sessionId != null) {
            // Release the last LLM-bound affinity window: it keeps a shallow
            // copy of every conversation message reachable after the session
            // ends, which leaked one ContextWindow per session in
            // long-running scenarios (reported heap leak root cause).
            lastAffinityWindows.remove(sessionId);
        }
    }

    /**
     * Release the per-session affinity window (the heap-leak fix entry) plus
     * the context engine state, WITHOUT the checkpointer {@code release}
     * side-effect of {@link #clearSession(String)}.
     *
     * <p>Some teardown paths (e.g. DeepAgent task-loop shutdown) run while the
     * session may still be resumed later (interrupt-resume, follow-up rounds);
     * dropping the checkpointer's agent store there breaks subsequent
     * invocations with "agent store not found". Callers that truly end the
     * session must use {@link #clearSession(String)}.</p>
     *
     * @param sessionId session whose in-memory runtime state should be released
     * @since 0.1.17
     */
    public void releaseSessionRuntime(String sessionId) {
        cleanupOffloadNamespaces(sessionId, null);
        contextEngine.clearContext(null, sessionId);
        if (sessionId != null) {
            lastAffinityWindows.remove(sessionId);
        }
    }

    public boolean clearContextMessages(String sessionId, String contextId) {
        String effectiveSessionId = sessionId == null ? ContextEngine.DEFAULT_SESSION_ID : sessionId;
        String effectiveContextId = contextId == null ? ContextEngine.DEFAULT_CONTEXT_ID : contextId;
        cleanupOffloadNamespaces(effectiveSessionId, effectiveContextId);
        ModelContext context = contextEngine.getContext(effectiveContextId, effectiveSessionId);
        if (context == null) {
            return false;
        }
        context.clearMessages(false).toCompletableFuture().join();
        return true;
    }

    /**
     * AC-4-10 session 结束清理: removes every offload entry owned by the
     * cleared session contexts before the contexts themselves go away.
     * Best-effort — cleanup failures never block context clearing
     * (offload is an enhancement, 共享约定 13).
     */
    private void cleanupOffloadNamespaces(String sessionId, String contextId) {
        OffloadService service = this.offloadService;
        if (service == null || sessionId == null) {
            return;
        }
        try {
            OffloadNamespace namespace = OffloadNamespace.of("", sessionId,
                    contextId == null ? "" : contextId);
            service.deleteNamespace(namespace);
            if (contextId == null) {
                // Whole-session sweep: also cover the context-scoped
                // namespaces created per contextId by the binding supplier.
                for (ModelContext context : contextEngine.getContexts(sessionId)) {
                    if (context instanceof SessionModelContext smc && smc.getOffloadService() == service) {
                        service.deleteNamespace(OffloadNamespace.of("", sessionId, smc.contextId()));
                    }
                }
            }
        } catch (RuntimeException ex) {
            // Intentional: agent loop must not propagate exceptions to the model
            Loggers.AGENT.debug("Offload namespace cleanup skipped: {}", ex.getMessage());
        }
    }

    @Override
    public Object getConfig() {
        return config;
    }

    public ContextEngine getContextEngine() {
        return refreshContextEngineIfNeeded();
    }

    /**
     * Refresh the context engine when the mutable agent configuration changed.
     *
     * @return the active context engine
     * @since 0.1.15
     */
    private ContextEngine refreshContextEngineIfNeeded() {
        synchronized (contextEngineLock) {
            ContextEngineConfig currentConfig = effectiveContextEngineConfig(config.getContextEngineConfig());
            if (Objects.equals(appliedContextEngineConfig, currentConfig)) {
                return contextEngine;
            }
            ContextEngineConfig configSnapshot = copyContextEngineConfig(currentConfig);
            ContextEngine refreshedContextEngine = new ContextEngine(configSnapshot);
            appliedContextEngineConfig = configSnapshot;
            contextEngine = refreshedContextEngine;
            getAbilityManager().setContextEngine(refreshedContextEngine);
            return contextEngine;
        }
    }

    /**
     * Copy context-engine settings so later in-place mutations remain detectable.
     *
     * @param source source configuration
     * @return an independent configuration snapshot
     * @since 0.1.15
     */
    private static ContextEngineConfig copyContextEngineConfig(ContextEngineConfig source) {
        ContextEngineConfig.Builder snapshotBuilder = ContextEngineConfig.builder()
                .maxContextMessageNum(source.getMaxContextMessageNum())
                .defaultWindowMessageNum(source.getDefaultWindowMessageNum())
                .defaultWindowRoundNum(source.getDefaultWindowRoundNum())
                .enableReload(source.isEnableReload())
                .enableTiktokenCounter(source.isTiktokenCounterEnabled())
                .contextWindowTokens(source.getContextWindowTokens())
                .modelName(source.getModelName())
                .enableOpenrouterModelContextWindowTokens(source.isEnableOpenrouterModelContextWindowTokens())
                .openrouterRequestTimeout(source.getOpenrouterRequestTimeout())
                .enableOffload(source.isEnableOffload())
                .offload(source.getOffload());
        Map<String, Integer> modelWindowTokens = source.getModelContextWindowTokens();
        if (modelWindowTokens != null) {
            snapshotBuilder.modelContextWindowTokens(new LinkedHashMap<>(modelWindowTokens));
        }
        return snapshotBuilder.build();
    }

    /**
     * Normalize an absent context-engine configuration to the engine defaults.
     *
     * @param source configured settings, possibly absent
     * @return configured settings or an empty default configuration
     * @since 0.1.15
     */
    private static ContextEngineConfig effectiveContextEngineConfig(ContextEngineConfig source) {
        return source != null ? source : ContextEngineConfig.builder().build();
    }

    public SystemPromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public static String summarizeToolCall(Object toolCall) {
        if (toolCall instanceof Map<?, ?> map) {
            Object function = map.get("function");
            Map<?, ?> functionMap = function instanceof Map<?, ?> fn ? fn : Map.of();
            String name = Objects.toString(functionMap.containsKey("name") ? functionMap.get("name") : "?");
            String args = truncate(Objects.toString(functionMap.containsKey("arguments")
                    ? functionMap.get("arguments")
                    : ""), 100);
            return name + "(" + args + ")";
        }
        Object function = readAttribute(toolCall, "function");
        Object target = function == null ? toolCall : function;
        Object name = readAttribute(target, "name");
        if (name == null) {
            name = readAttribute(toolCall, "name");
        }
        Object args = readAttribute(target, "arguments");
        if (args == null) {
            args = readAttribute(toolCall, "arguments");
        }
        return Objects.toString(name, "?") + "(" + truncate(Objects.toString(args, ""), 100) + ")";
    }

    public static void logLlmRequest(Object log, List<? extends Object> messages, List<? extends Object> tools) {
        if (messages == null) {
            return;
        }
        for (Object message : messages) {
            if (message == null) {
                continue;
            }
            String role = "";
            if (message instanceof BaseMessage baseMessage) {
                role = baseMessage.getRole() != null ? baseMessage.getRole() : "";
            }
            Loggers.AGENT.info("logLlmMessage role=" + role);
        }
    }

    public static void logLlmResponse(Object log, AssistantMessage aiMessage) {
        if (aiMessage == null) {
            return;
        }
        Loggers.AGENT.info("[LLM] <<< response: role=assistant");
        if (aiMessage.getToolCalls() != null) {
            for (ToolCall tc : aiMessage.getToolCalls()) {
                Loggers.AGENT.info("[LLM]   tool_call: " + tc.getName());
            }
        }
    }

    /** Inputs shared by the streaming retry loop. */
    private record StreamModelCall(AgentCallbackContext ctx, Model model,
            List<BaseMessage> messages, ModelInvokeOptions options, ModelCallInputs modelInputs) {
    }

    /**
     * Retry empty streams and transport failures before the first chunk. Once a chunk is visible,
     * never replay the request. Empty or reasoning-only results fall back to a blocking invocation.
     */
    private AssistantMessage streamModelResponseWithRetry(
            StreamModelCall call, com.openjiuwen.core.kvcache.KVCacheModelHook.RuntimeLease kvLease) {
        boolean isKvStreamSucceeded = false;
        try {
            AssistantMessage aiMessage = streamWithRetry(call, config.getStreamMaxRetries(),
                    config.getStreamRetryDelayMs());
            isKvStreamSucceeded = true;
            return aiMessage;
        } finally {
            com.openjiuwen.core.kvcache.KVCacheModelHook.end(kvLease, isKvStreamSucceeded).join();
        }
    }

    private AssistantMessage streamWithRetry(StreamModelCall call, int maxRetries, long retryDelayMs) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            StreamAttempt streamAttempt = new StreamAttempt();
            try {
                AssistantMessage aiMessage = streamModelResponse(call.ctx(), call.model(), call.messages(),
                        call.options(), streamAttempt);
                call.modelInputs().setResponse(aiMessage);
                if (!isEmptyStreamResult(aiMessage)) {
                    return aiMessage;
                }
                if (hasReasoningContent(aiMessage)) {
                    Loggers.AGENT.warning("ReAct stream completed with reasoning but no answer content or "
                            + "tool calls; falling back to non-stream");
                    return fallbackToNonStream(call.ctx(), call.model(), call.messages(), call.options(),
                            call.modelInputs());
                }
                Loggers.AGENT.warning("ReAct stream returned empty (attempt "
                        + (attempt + 1) + "/" + (maxRetries + 1) + ")");
            } catch (BaseError | AgentInterrupt | CompletionException | IllegalArgumentException
                    | IllegalStateException | NullPointerException | ClassCastException
                    | UnsupportedOperationException | UncheckedIOException exception) {
                boolean isClassified = exception instanceof BaseError
                        && ModelCallClassification.of(exception).isPresent();
                if (!isClassified && !streamAttempt.hasReceivedChunks() && hasIoCause(exception)
                        && attempt < maxRetries) {
                    Loggers.AGENT.warning("ReAct stream transport failed before the first chunk (attempt "
                            + (attempt + 1) + "/" + (maxRetries + 1) + "), retrying: "
                            + exception.getMessage());
                    if (delayStreamRetry(clampStreamRetryDelayMs(call.ctx(), retryDelayMs))) {
                        continue;
                    }
                }
                Loggers.AGENT.error("ReAct stream error (attempt "
                        + (attempt + 1) + "/" + (maxRetries + 1) + "), aborting retry: "
                        + exception.getMessage());
                throw exception;
            }

            if (attempt < maxRetries && !delayStreamRetry(clampStreamRetryDelayMs(call.ctx(), retryDelayMs))) {
                break;
            }
        }

        Loggers.AGENT.warning("ReAct stream returned empty after " + (maxRetries + 1)
                + " attempts, falling back to non-stream with stream wrapping");
        return fallbackToNonStream(call.ctx(), call.model(), call.messages(), call.options(), call.modelInputs());
    }

    private AssistantMessage fallbackToNonStream(AgentCallbackContext ctx, Model model,
                                                  List<BaseMessage> messages, ModelInvokeOptions options,
                                                  ModelCallInputs modelInputs) {
        logModelCallStarted(ctx, messages, options == null ? List.of() : options.getTools(), false);
        long callStartTime = System.nanoTime();
        AssistantMessage aiMessage;
        try {
            aiMessage = model.invoke(messages, options).toCompletableFuture().join();
        } catch (BaseError | CompletionException | IllegalArgumentException
                | IllegalStateException | NullPointerException exception) {
            logModelCallCompleted(ctx, false, callStartTime, null, exception);
            throw exception;
        }
        if (aiMessage == null) {
            aiMessage = AssistantMessage.builder().content("").toolCalls(List.of()).build();
        }
        modelInputs.setResponse(aiMessage);
        logModelResponse(ctx, aiMessage);
        logModelCallCompleted(ctx, false, callStartTime, aiMessage, null);
        // Always stream content on fallback; tool_calls (if any) follow content.
        writeNonStreamAsStreamChunks(ctx, aiMessage, true);
        return aiMessage;
    }

    private AssistantMessage streamModelResponse(AgentCallbackContext ctx, Model model, List<BaseMessage> messages,
                                                 ModelInvokeOptions options, StreamAttempt streamAttempt) {
        logModelCallStarted(ctx, messages, options == null ? List.of() : options.getTools(), true);
        long callStartTime = System.nanoTime();
        Iterator<AssistantMessageChunk> iterator = null;
        AssistantMessageChunk accumulatedChunk = null;
        Long firstTokenTime = null;
        Long lastTokenTime = null;
        int chunkCount = 0;
        try {
            iterator = model.stream(messages, options);
            while (iterator.hasNext()) {
                AssistantMessageChunk chunk = iterator.next();
                streamAttempt.markChunkReceived();
                accumulatedChunk = accumulatedChunk == null ? chunk : (AssistantMessageChunk) accumulatedChunk.merge(chunk);
                if (firstTokenTime == null) {
                    firstTokenTime = System.nanoTime();
                }
                lastTokenTime = System.nanoTime();
                chunkCount++;
                AgentSessionApi session = ctx.getSession();
                if (session != null && chunk.getReasoningContent() != null && !chunk.getReasoningContent().isEmpty()) {
                    session.writeStream(new OutputSchema("llm_reasoning", nextStreamIndex(ctx),
                            new LinkedHashMap<>(Map.of("content", chunk.getReasoningContent(), "result_type", "answer"))));
                }
                if (session != null && chunk.getContent() != null && !String.valueOf(chunk.getContent()).isEmpty()) {
                    session.writeStream(new OutputSchema("llm_output", nextStreamIndex(ctx),
                            new LinkedHashMap<>(Map.of("content", chunk.getContent(), "result_type", "answer"))));
                }
            }
        } catch (RuntimeException exception) {
            logModelCallCompleted(ctx, true, callStartTime, null, exception);
            throw exception;
        } finally {
            closeIterator(iterator);
        }
        AssistantMessage aiMessage;
        if (accumulatedChunk == null) {
            aiMessage = AssistantMessage.builder().content("").toolCalls(List.of()).build();
        } else {
            aiMessage = AssistantMessage.builder()
                    .content(accumulatedChunk.getContent() == null ? "" : accumulatedChunk.getContent())
                    .toolCalls(accumulatedChunk.getToolCalls() == null ? List.of() : accumulatedChunk.getToolCalls())
                    .finishReason(accumulatedChunk.getFinishReason())
                    .usageMetadata(accumulatedChunk.getUsageMetadata())
                    .reasoningContent(accumulatedChunk.getReasoningContent())
                    .promptTokenIds(accumulatedChunk.getPromptTokenIds())
                    .completionTokenIds(accumulatedChunk.getCompletionTokenIds())
                    .logprobs(accumulatedChunk.getLogprobs())
                    .build();
        }
        if (isEmptyStreamResult(aiMessage)) {
            logModelCallCompleted(ctx, true, callStartTime, aiMessage, null);
            return aiMessage;
        }
        logModelResponse(ctx, aiMessage);
        logModelCallCompleted(ctx, true, callStartTime, aiMessage, null);
        // In streaming mode, tool_calls are accumulated but not yet written
        // to the stream as an llm_output chunk (content chunks were written
        // incrementally above). Emit the accumulated tool_calls here so the
        // stream contains a llm_output chunk with tool_calls, matching the
        // non-streaming path (writeNonStreamAsStreamChunks).
        if (ctx.getSession() != null && aiMessage.getToolCalls() != null
                && !aiMessage.getToolCalls().isEmpty()) {
            Map<String, Object> toolPayload = new LinkedHashMap<>();
            toolPayload.put("tool_calls", aiMessage.getToolCalls());
            ctx.getSession().writeStream(new OutputSchema("llm_output", nextStreamIndex(ctx), toolPayload));
        }
        if (ctx.getSession() != null && aiMessage.getUsageMetadata() != null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("usage_metadata", aiMessage.getUsageMetadata().modelDump());
            payload.put("result_type", "answer");
            payload.put("total_latency_ms", roundMillis(System.nanoTime() - callStartTime));
            if (firstTokenTime != null) {
                payload.put("ttft_ms", roundMillis(firstTokenTime - callStartTime));
            }
            if (firstTokenTime != null && lastTokenTime != null && chunkCount > 1) {
                payload.put("tpot_ms", roundMillis((lastTokenTime - firstTokenTime) / (double) (chunkCount - 1)));
            }
            ctx.getSession().writeStream(new OutputSchema("llm_usage", nextStreamIndex(ctx), payload));
        }
        return aiMessage;
    }

    static boolean isEmptyStreamResult(AssistantMessage message) {
        if (message == null) {
            return true;
        }
        String content = message.getContent() == null ? "" : String.valueOf(message.getContent()).trim();
        boolean hasToolCalls = message.getToolCalls() != null && !message.getToolCalls().isEmpty();
        return content.isEmpty() && !hasToolCalls;
    }

    private static boolean hasReasoningContent(AssistantMessage message) {
        return message != null && message.getReasoningContent() != null
                && !message.getReasoningContent().isBlank();
    }

    private static boolean hasIoCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean delayStreamRetry(long retryDelayMs) {
        if (retryDelayMs <= 0) {
            return true;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(retryDelayMs));
        return !Thread.currentThread().isInterrupted();
    }

    /**
     * Clamp the stream-retry wait to the remaining time budget.
     *
     * <p>Returns the original delay unchanged when no time-budgeted run-budget state is
     * present; an exhausted budget yields zero so the retry no longer sleeps and the next
     * checkpoint terminates the run.</p>
     *
     * @param ctx callback context carrying the run-budget state
     * @param retryDelayMs configured retry delay in milliseconds
     * @return the clamped delay in milliseconds
     */
    private static long clampStreamRetryDelayMs(AgentCallbackContext ctx, long retryDelayMs) {
        Optional<RunBudgetState> budgetState = RunBudgetState.from(ctx.getExtra());
        if (budgetState.isEmpty() || !budgetState.get().isTimeEnabled()) {
            return retryDelayMs;
        }
        return Math.min(retryDelayMs, Math.max(0L, budgetState.get().remainingMillis()));
    }

    /**
     * Tracks whether an attempt has emitted anything to downstream consumers.
     */
    private static final class StreamAttempt {
        private boolean hasReceivedChunks;

        /**
         * Mark the attempt as externally observable.
         */
        private void markChunkReceived() {
            hasReceivedChunks = true;
        }

        /**
         * Return whether at least one chunk was received.
         *
         * @return {@code true} after the first chunk
         */
        private boolean hasReceivedChunks() {
            return hasReceivedChunks;
        }
    }

    /**
     * Wrap a non-stream AssistantMessage as SSE llm_output chunks (content first, then tool_calls).
     */
    void writeNonStreamAsStreamChunks(AgentCallbackContext ctx, AssistantMessage aiMessage, boolean sendContent) {
        if (ctx == null || aiMessage == null) {
            return;
        }
        AgentSessionApi session = ctx.getSession();
        if (session == null) {
            return;
        }
        String content = aiMessage.getContent() == null ? "" : String.valueOf(aiMessage.getContent());
        if (sendContent && !content.isBlank()) {
            for (int i = 0; i < content.length(); i += STREAM_CHUNK_SIZE) {
                int end = Math.min(i + STREAM_CHUNK_SIZE, content.length());
                String chunkContent = content.substring(i, end);
                session.writeStream(new OutputSchema("llm_output", nextStreamIndex(ctx),
                        new LinkedHashMap<>(Map.of("content", chunkContent, "result_type", "answer"))));
            }
        }
        if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
            Map<String, Object> toolPayload = new LinkedHashMap<>();
            toolPayload.put("tool_calls", aiMessage.getToolCalls());
            session.writeStream(new OutputSchema("llm_output", nextStreamIndex(ctx), toolPayload));
        }
        if (aiMessage.getUsageMetadata() != null) {
            Map<String, Object> usagePayload = new LinkedHashMap<>();
            usagePayload.put("usage_metadata", aiMessage.getUsageMetadata().modelDump());
            usagePayload.put("result_type", "answer");
            session.writeStream(new OutputSchema("llm_usage", nextStreamIndex(ctx), usagePayload));
        }
    }

    private void warnMissingSkillReadFileTool() {
        boolean hasReadFile = getAbilityManager().listToolInfo().stream()
                .map(ToolInfo::getName)
                .anyMatch("read_file"::equals);
        if (!hasReadFile) {
            Loggers.AGENT.warning("event=react_skill_read_tool_missing agent_id={} required_tool=read_file "
                    + "available_tool_count={}", safeLogValue(getCard().getId()), getAbilityManager().listToolInfo().size());
        }
    }

    private void logModelCallStarted(AgentCallbackContext ctx, List<?> messages, List<?> tools, boolean streaming) {
        StringBuilder builder = eventFields("react_model_call_started", ctx);
        appendField(builder, "model_name", config.getModelName());
        appendField(builder, "streaming", streaming);
        appendField(builder, "message_count", messages == null ? 0 : messages.size());
        appendField(builder, "tool_count", tools == null ? 0 : tools.size());
        Loggers.LLM.info(builder.toString());
    }

    private void logModelResponse(AgentCallbackContext ctx, AssistantMessage aiMessage) {
        StringBuilder builder = eventFields("react_model_response", ctx);
        appendField(builder, "content_length", textLength(aiMessage == null ? null : aiMessage.getContent()));
        appendField(builder, "reasoning_length", textLength(aiMessage == null ? null : aiMessage.getReasoningContent()));
        List<ToolCall> toolCalls = aiMessage == null ? null : aiMessage.getToolCalls();
        appendField(builder, "tool_call_count", toolCalls == null ? 0 : toolCalls.size());
        appendField(builder, "usage_present", aiMessage != null && aiMessage.getUsageMetadata() != null);
        if (aiMessage != null) {
            appendField(builder, "finish_reason", aiMessage.getFinishReason());
        }
        Loggers.LLM.info(builder.toString());
    }

    private void logModelCallCompleted(AgentCallbackContext ctx, boolean streaming, long startNanos,
                                       AssistantMessage aiMessage, RuntimeException exception) {
        StringBuilder builder = eventFields("react_model_call_completed", ctx);
        appendField(builder, "streaming", streaming);
        appendField(builder, "status", exception == null ? "success" : "exception");
        appendField(builder, "model_duration_ms", roundMillis(System.nanoTime() - startNanos));
        if (exception != null) {
            appendField(builder, "exception_type", exception.getClass().getSimpleName());
        }
        if (aiMessage != null && aiMessage.getUsageMetadata() != null) {
            appendField(builder, "prompt_tokens", aiMessage.getUsageMetadata().getInputTokens());
            appendField(builder, "completion_tokens", aiMessage.getUsageMetadata().getOutputTokens());
            appendField(builder, "total_tokens", aiMessage.getUsageMetadata().getTotalTokens());
        }
        Loggers.LLM.info(builder.toString());
    }

    private void logToolCallStarted(AgentCallbackContext ctx, ToolCall toolCall) {
        StringBuilder builder = eventFields("react_tool_call_started", ctx);
        appendField(builder, "tool_call_id", toolCall == null ? null : toolCall.getId());
        appendField(builder, "tool_name", toolCall == null ? null : toolCall.getName());
        Loggers.TOOL.info(builder.toString());
        if (toolCall != null && toolCall.getName() != null) {
            Loggers.TOOL.info("Executing tool: " + toolCall.getName());
        }
    }

    private void logToolCallCompleted(AgentCallbackContext ctx, ToolCall toolCall, String outcome, long startNanos) {
        StringBuilder builder = eventFields("react_tool_call_completed", ctx);
        appendField(builder, "tool_call_id", toolCall == null ? null : toolCall.getId());
        appendField(builder, "tool_name", toolCall == null ? null : toolCall.getName());
        appendField(builder, "outcome", outcome);
        appendField(builder, "duration_ms", roundMillis(System.nanoTime() - startNanos));
        Loggers.TOOL.info(builder.toString());
    }

    private String classifyToolOutcome(List<AbilityManager.ExecutionResult> results, boolean notFound) {
        if (notFound) {
            return "not_found";
        }
        if (results == null || results.isEmpty()) {
            return null;
        }
        for (AbilityManager.ExecutionResult result : results) {
            if (isPendingInterruptResult(result)) {
                return "interrupted";
            }
            if (isToolExecutionExceptionResult(result)) {
                return "exception";
            }
            if (isInvalidArgumentsResult(result)) {
                return "invalid_arguments";
            }
            if (isBusinessErrorResult(result)) {
                return "business_error";
            }
        }
        return results.stream().anyMatch(result -> result != null && result.result() != null) ? "success" : null;
    }

    private void logExternalToolPending(AgentCallbackContext ctx, List<ToolCall> toolCalls, int iteration) {
        List<ToolCall> externalCalls = toolCalls == null ? List.of() : toolCalls.stream()
                .filter(toolCall -> toolCall != null && getAbilityManager().isExternalTool(toolCall.getName()))
                .toList();
        if (externalCalls.isEmpty()) {
            return;
        }
        StringBuilder builder = eventFields("react_external_tool_pending", ctx);
        appendField(builder, "iteration", iteration + 1);
        appendField(builder, "tool_call_count", externalCalls.size());
        appendField(builder, "tool_names", externalCalls.stream().map(ToolCall::getName).toList());
        Loggers.AGENT.info(builder.toString());
        for (ToolCall toolCall : externalCalls) {
            long startNanos = System.nanoTime();
            logToolCallStarted(ctx, toolCall);
            logToolCallCompleted(ctx, toolCall, "external_pending", startNanos);
        }
    }

    private static boolean isToolExecutionExceptionResult(AbilityManager.ExecutionResult result) {
        if (result == null || result.result() != null || result.toolMessage() == null) {
            return false;
        }
        Object content = result.toolMessage().getContent();
        return content instanceof String text && isToolOrAbilityExecutionError(text);
    }

    private static boolean isToolOrAbilityExecutionError(String text) {
        return text != null && (text.startsWith("Ability execution error:")
                || text.startsWith("Tool execution error:"));
    }

    private static boolean isInvalidArgumentsResult(AbilityManager.ExecutionResult result) {
        if (result == null || result.result() != null || result.toolMessage() == null) {
            return false;
        }
        Object content = result.toolMessage().getContent();
        return content instanceof String text && text.startsWith("Invalid tool arguments JSON:");
    }

    private static boolean isBusinessErrorResult(AbilityManager.ExecutionResult result) {
        if (result == null || result.result() == null) {
            return false;
        }
        Object value = result.result();
        Object success = readAttribute(value, "success");
        Object error = readAttribute(value, "error");
        return Boolean.FALSE.equals(success) || (error != null && !String.valueOf(error).isBlank());
    }

    private String resolveStreamWorkerThreadName(Object inputs) {
        String fallback = "react-agent-stream-" + getCard().getId();
        if (!(inputs instanceof Map<?, ?> map)) {
            return fallback;
        }
        Object configuredName = map.get("work_thread_name");
        if (!(configuredName instanceof String text)) {
            return fallback;
        }
        String normalized = normalizeThreadName(text);
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String normalizeThreadName(String text) {
        String trimmed = text == null ? "" : text.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int index = 0; index < trimmed.length(); index++) {
            char ch = trimmed.charAt(index);
            builder.append(Character.isISOControl(ch) || Character.isWhitespace(ch) ? '_' : ch);
        }
        return builder.toString();
    }

    private StringBuilder eventFields(String event, AgentCallbackContext ctx) {
        StringBuilder builder = new StringBuilder("event=").append(event);
        appendField(builder, "agent_id", getCard().getId());
        if (ctx != null && ctx.getSession() != null) {
            appendField(builder, "session_id", ctx.getSession().getSessionId());
        }
        return builder;
    }

    private static void appendField(StringBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = safeLogValue(value);
        if (text.isBlank()) {
            return;
        }
        builder.append(' ').append(name).append('=').append(text);
    }

    private static String safeLogValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            builder.append(Character.isISOControl(ch) || Character.isWhitespace(ch) ? '_' : ch);
        }
        return builder.toString();
    }

    private static int textLength(Object value) {
        return value == null ? 0 : String.valueOf(value).length();
    }

    private static void closeStreamAndCommit(AgentSessionApi session) {
        if (session == null) {
            return;
        }
        try {
            session.commit();
        } finally {
            session.closeStream();
        }
    }

    private static void closeIterator(Iterator<?> iterator) {
        if (iterator instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                Loggers.AGENT.debug("Failed to close stream iterator. {}", exception.getMessage());
            }
        }
    }

    private static void invokeStaticRunnerRelease(String sessionId) {
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Method method = runnerType.getMethod("release", String.class);
            method.invoke(null, sessionId);
        } catch (ReflectiveOperationException ignored) {
            // Runner may not be available in focused tests.
        }
    }

    /**
     * Copy all String-keyed entries from {@code inputs} into {@code ctx}'s extra map, skipping
     * reserved keys ({@code "query"}, {@code "conversation_id"}, {@code "user_id"},
     * {@code "_streaming"}, {@code "_steering_queue"}) which are handled separately. This
     * allows any custom parameter name (e.g. {@code model_id}, {@code llm_id},
     * {@code target_model_id}, or any user-defined key) to be transparently forwarded to
     * {@code ctx.getExtra()}.
     *
     * @param inputs the invoke inputs map
     * @param ctx the callback context whose extra map to populate
     * @since 0.1.16
     */
    private static void copyRemainingExtras(Map<?, ?> inputs, AgentCallbackContext ctx) {
        Map<String, Object> extra = ctx.getExtra();
        for (Map.Entry<?, ?> entry : inputs.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (!"query".equals(key) && !"conversation_id".equals(key)
                    && !"user_id".equals(key) && !"_streaming".equals(key)
                    && !"_steering_queue".equals(key) && entry.getValue() != null) {
                extra.put(key, entry.getValue());
            }
        }
    }

    /**
     * Binds request-scoped model selection from invoke inputs onto the callback context.
     *
     * @param inputs invoke input map
     * @param ctx callback context to update
     */
    private static void bindDynamicModelIdFromInputs(Map<?, ?> inputs, AgentCallbackContext ctx) {
        String modelId = firstString(inputs, "dynamic_model_id", "target_model_id", "model_id");
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        ctx.setDynamicModelId(modelId);
        ctx.getExtra().put("target_model_id", modelId);
        ctx.getExtra().put("model_id", modelId);
    }

    private static String firstString(Map<?, ?> inputs, String... keys) {
        for (String key : keys) {
            Object value = inputs.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static void bindSteeringQueue(AgentCallbackContext ctx, Queue<String> steeringQueue) {
        Object queues = ctx.getExtra().get("loop_queues");
        if (queues instanceof LoopQueues loopQueues) {
            ctx.bindSteeringQueue(loopQueues.steering());
            return;
        }
        if (steeringQueue != null) {
            ctx.bindSteeringQueue(steeringQueue);
            return;
        }
        LoopQueues autoQueue = new LoopQueues();
        ctx.getExtra().put("loop_queues", autoQueue);
        ctx.bindSteeringQueue(autoQueue.steering());
    }

    private static void putExtra(AgentCallbackContext ctx, String key, Object value) {
        if (value != null) {
            ctx.getExtra().put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        map.forEach((key, mapValue) -> {
            if (mapValue instanceof String text) {
                result.put(String.valueOf(key), text);
            }
        });
        return result;
    }

    private static int popInt(Map<String, Object> map, String key, int fallback) {
        Object value = map.remove(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static AssistantMessage copyAssistantMessage(AssistantMessage aiMessage) {
        return AssistantMessage.builder()
                .content(aiMessage.getContent())
                .toolCalls(aiMessage.getToolCalls())
                .reasoningContent(aiMessage.getReasoningContent())
                .usageMetadata(aiMessage.getUsageMetadata())
                .build();
    }

    private static ToolCall copyToolCall(ToolCall source, Object arguments) {
        if (source == null) {
            return null;
        }
        return ToolCall.builder()
                .id(source.getId())
                .type(source.getType())
                .name(source.getName())
                .arguments(arguments == null ? source.getArguments() : String.valueOf(arguments))
                .index(source.getIndex())
                .build();
    }

    private static List<String> pendingComponentIds(InterruptionState state) {
        String componentId = state.getPendingComponentId();
        return componentId == null ? List.of() : List.of(componentId);
    }

    private static Object tupleFirst(Object value) {
        if (value instanceof List<?> list && !list.isEmpty()) {
            return list.get(0);
        }
        if (value instanceof Object[] array && array.length > 0) {
            return array[0];
        }
        return value;
    }

    private static Object normalizedToolResult(Object value) {
        if (value instanceof Object[] array && array.length > 0) {
            return array[0];
        }
        return value;
    }

    private static boolean isInterruptMap(Map<?, ?> map) {
        return "interrupt".equals(map.get("result_type")) && map.containsKey("workflow_execution_state");
    }

    private static boolean isInteractionItem(Object item) {
        return "__interaction__".equals(readAttribute(item, "type"));
    }

    private static void appendInteractionId(List<String> ids, Object item, String preferredKey) {
        if (!isInteractionItem(item)) {
            return;
        }
        Object payload = readAttribute(item, "payload");
        Object id = readAttribute(payload, preferredKey);
        if (id == null && !"component_id".equals(preferredKey)) {
            id = readAttribute(payload, "component_id");
        }
        if (id == null && !"id".equals(preferredKey)) {
            id = readAttribute(payload, "id");
        }
        if (id != null) {
            ids.add(String.valueOf(id));
        }
    }

    private static Object workflowExecutionState(Object toolResult) {
        Object normalized = normalizedToolResult(toolResult);
        if (normalized instanceof Map<?, ?> map && map.containsKey("workflow_execution_state")) {
            return map.get("workflow_execution_state");
        }
        return normalized;
    }

    private static List<ToolInfo> toolInfoList(List<Object> values) {
        if (values == null) {
            return List.of();
        }
        List<ToolInfo> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof ToolInfo toolInfo) {
                result.add(toolInfo);
            }
        }
        return result;
    }

    private static String joinSystemPromptTemplate(List<Map<String, Object>> promptTemplate) {
        List<String> parts = new ArrayList<>();
        for (Map<String, Object> message : promptTemplate == null ? List.<Map<String, Object>>of() : promptTemplate) {
            if ("system".equals(message.get("role")) && message.get("content") instanceof String text
                    && !text.isEmpty()) {
                parts.add(text);
            }
        }
        return String.join("\n\n", parts);
    }

    private static String renderPlaceholders(String template, Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return rendered;
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * Builds the error result written to the stream when a streaming invoke fails. When the
     * failure chain carries a {@link BaseError}, its stable code, numeric code, and recoverable
     * flag are attached as a structured failure entry so remote adapters can reconstruct a
     * programmatic failure descriptor instead of parsing the flattened output text.
     *
     * @param exception streaming failure
     * @return error result map with output, result type, and an optional failure entry
     * @since 0.1.17
     */
    static Map<String, Object> buildErrorResult(RuntimeException exception) {
        Map<String, Object> result = new LinkedHashMap<>(Map.of(
                "output", describeStreamThrowable(exception),
                "result_type", "error"
        ));
        findBaseError(exception).ifPresent(baseError -> result.put("failure", buildFailurePayload(baseError)));
        return result;
    }

    /**
     * Finds the first {@link BaseError} in the cause chain.
     *
     * @param throwable throwable to inspect
     * @return the first base error in the cause chain, or empty when none exists
     * @since 0.1.17
     */
    static Optional<BaseError> findBaseError(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_CHAIN_DEPTH) {
            if (current instanceof BaseError baseError) {
                return Optional.of(baseError);
            }
            current = current.getCause();
            depth++;
        }
        return Optional.empty();
    }

    private static Map<String, Object> buildFailurePayload(BaseError baseError) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("code", baseError.getStatus() == null ? "AGENT_CORE_ERROR" : baseError.getStatus().name());
        failure.put("numericCode", baseError.getCode());
        failure.put("retryable", baseError.isRecoverable());
        return failure;
    }

    private static String exceptionMessage(RuntimeException exception) {
        return describeStreamThrowable(exception);
    }

    /**
     * Build a non-null stream/invoke error description.
     *
     * <p>Exceptions such as {@link java.util.ConcurrentModificationException} often have a null
     * {@code getMessage()}; logging that as bare {@code "null"} hides the real failure under load
     * (issue #66 / commit 19c4f1fd).</p>
     *
     * @param throwable throwable
     * @return human-readable error text for logs and stream payloads
     * @since 0.1.14
     */
    static String describeStreamThrowable(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        String msg = throwable.getMessage();
        if (msg == null || msg.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getSimpleName() + ": " + msg;
    }

    private static Object readAttribute(Object target, String name) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        try {
            return target.getClass().getMethod(getter).invoke(target);
        } catch (ReflectiveOperationException ignored) {
            try {
                return target.getClass().getField(name).get(target);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static String truncate(String text, int maxLength) {
        return TextTruncate.safePrefix(text, maxLength);
    }

    private static double roundMillis(double nanos) {
        return Math.round((nanos / 1_000_000.0D) * 100.0D) / 100.0D;
    }
}
