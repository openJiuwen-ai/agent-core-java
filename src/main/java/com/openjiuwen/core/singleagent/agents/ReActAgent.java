/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.operator.OperatorStream;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager.ToolExecutionEntry;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolCallInterruptRequest;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptEntry;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.rail.SteeringQueue;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.task_loop.LoopQueues;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * ReAct paradigm Agent implementation.
 * <p>
 * ReAct loop: Reasoning → Acting → Observation → Repeat
 * <p>
 * Input format (compatible with legacy):
 * <ul>
 * <li>dict: {"query": "user question", "conversation_id": "session_123"}</li>
 * <li>str: Used directly as query</li>
 * </ul>
 * <p>
 * Output format:
 * <ul>
 * <li>invoke: {"output": "response content", "result_type": "answer|error"}</li>
 * <li>stream: yields OutputSchema objects</li>
 * </ul>
 * 
 * @since 0.1.7
 */
public class ReActAgent extends BaseAgent {
    private static final String INTERRUPTION_KEY = ToolInterruptionState.INTERRUPTION_KEY;
    private static final String RESUME_USER_INPUT_KEY = ToolInterruptionState.RESUME_USER_INPUT_KEY;
    private static final String IDENTITY_SECTION = "identity";
    private static final String SKILLS_SECTION = "skills";
    private static final int IDENTITY_SECTION_PRIORITY = 10;
    private static final int SKILLS_SECTION_PRIORITY = 90;

    // 非流式→流式适配器的分块大小（字符数）
    private static final int STREAM_CHUNK_SIZE = 200;

    /**
     * Stream execution pool. JDK 17 uses the bounded platform pool, while JDK 21 uses
     * one virtual thread per stream task without executor-level concurrency limits.
     */
    private static final ExecutorService STREAM_EXECUTOR =
            OpenJiuwenExecutors.newBoundedModulePool("react-agent-stream", true);

    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private volatile Model llm;
    private final Object llmLock = new Object();
    private SystemPromptBuilder promptBuilder;
    private SystemPromptBuilder systemPromptBuilder;
    private boolean isKvReleaseWarningLogged;

    /**
     * Create a ReAct agent from an agent card.
     * 
     * @param card agent metadata card
     * @since 0.1.7
     */
    public ReActAgent(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        this.contextEngine = new ContextEngine(config.getContextEngineConfig());
        this.llm = null;
        this.promptBuilder = new SystemPromptBuilder();
        this.systemPromptBuilder = this.promptBuilder;
        initMemoryScope();
    }

    /**
     * initMemoryScope.
     * 
     * @since 0.1.7
     */
    private void initMemoryScope() {
        if (config.getMemScopeId() != null && !config.getMemScopeId().isEmpty()) {
            LongTermMemory.getInstance().setScopeConfig(config.getMemScopeId(), new MemoryScopeConfig());
        }
    }

    /**
     * Create the default runtime configuration.
     * 
     * @return default config instance
     * @since 0.1.7
     */
    protected ReActAgentConfig createDefaultConfig() {
        return ReActAgentConfig.builder().build();
    }

    /**
     * configure.
     * 
     * @param configObj configObj
     * @return the result
     * @since 0.1.7
     */
    @Override
    public BaseAgent configure(Object configObj) {
        if (!(configObj instanceof ReActAgentConfig newConfig)) {
            throw new IllegalArgumentException(
                    "Expected ReActAgentConfig, got: " + (configObj != null ? configObj.getClass().getName() : "null"));
        }

        ReActAgentConfig oldConfig = this.config;
        this.config = newConfig;

        // Reset LLM if model config changed
        if (!safeEquals(oldConfig.getModelProvider(), newConfig.getModelProvider())
                || !safeEquals(oldConfig.getApiKey(), newConfig.getApiKey())
                || !safeEquals(oldConfig.getApiBase(), newConfig.getApiBase())) {
            this.llm = null;
        }

        // Update context engine if config changed
        if (!safeEquals(oldConfig.getContextEngineConfig(), newConfig.getContextEngineConfig())) {
            this.contextEngine = new ContextEngine(newConfig.getContextEngineConfig());
            this.isKvReleaseWarningLogged = false;
        }

        // Update memory scope if changed
        if (!safeEquals(oldConfig.getMemScopeId(), newConfig.getMemScopeId())) {
            initMemoryScope();
        }

        // Reset skill if sys operation id changed
        if (!safeEquals(oldConfig.getSysOperationId(), newConfig.getSysOperationId())) {
            lazyInitSkill();
        }

        rebuildPromptBuilderFromConfig();

        return this;
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getConfig() {
        return config;
    }

    /**
     * Return the context engine used by this agent.
     * 
     * @return context engine
     * @since 0.1.7
     */
    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Return the mutable prompt builder.
     * 
     * @return prompt builder
     * @since 0.1.7
     */
    public SystemPromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    /**
     * Return the public system prompt builder alias.
     * 
     * @return system prompt builder
     * @since 0.1.7
     */
    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    /**
     * Add or isReplace a prompt-builder section.
     * 
     * @param name section name
     * @param content section content used for both cn and en in this Java mirror
     * @param priority section priority
     * @since 0.1.7
     */
    public void addPromptBuilderSection(String name, String content, int priority) {
        String text = content != null ? content.trim() : "";
        if (text.isEmpty()) {
            promptBuilder.removeSection(name);
            return;
        }
        Map<String, String> sectionContent = new HashMap<String, String>();
        sectionContent.put("cn", text);
        sectionContent.put("en", text);
        promptBuilder.addPersistentSection(new PromptSection(name, sectionContent, priority));
    }

    /**
     * rebuildPromptBuilderFromConfig.
     * 
     * @since 0.1.7
     */
    private void rebuildPromptBuilderFromConfig() {
        this.promptBuilder = new SystemPromptBuilder(PromptSection.DEFAULT_LANGUAGE, config.getPromptMode());
        this.systemPromptBuilder = this.promptBuilder;
        addPromptBuilderSection(IDENTITY_SECTION, buildIdentityContent(), IDENTITY_SECTION_PRIORITY);
    }

    /**
     * Build the identity system prompt content from the configured prompt template.
     *
     * @return concatenated identity system content
     * @since 0.1.15
     */
    private String buildIdentityContent() {
        StringBuilder systemContent = new StringBuilder();
        if (config.getPromptTemplate() != null) {
            for (Map<String, String> msg : config.getPromptTemplate()) {
                if (msg == null || !"system".equals(msg.get("role"))) {
                    continue;
                }
                String content = msg.get("content");
                if (content == null || content.isBlank()) {
                    continue;
                }
                if (systemContent.length() > 0) {
                    systemContent.append("\n\n");
                }
                systemContent.append(content);
            }
        }
        return systemContent.toString();
    }

    /**
     * Clear per-request (transient) prompt sections left over from a previous invoke while
     * preserving persistent static sections such as identity, skills, and external memory.
     *
     * @since 0.1.15
     */
    private void clearTransientPromptSections() {
        promptBuilder.clearTransient();
    }

    /**
     * Get LLM instance (lazy initialization).
     * 
     * @return the result
     * @since 0.1.7
     */
    protected Model getLlm() {
        Model local = llm;
        if (local != null) {
            return local;
        }
        synchronized (llmLock) {
            local = llm;
            if (local == null) {
                if (config.getModelClientConfig() == null) {
                    throw new IllegalStateException(
                            "model_client_config is required. Use configureModelClient() to set it.");
                }
                local = new Model(config.getModelClientConfig(), config.getModelConfigObj());
                llm = local;
            }
            return local;
        }
    }

    /**
     * Override the active LLM instance for subsequent model calls.
     * <p>
     * Rails such as TaskPlanningRail can use this to route the current
     * task to a configured model, matching the Python runtime {@code set_llm}
     * behavior.
     * </p>
     * 
     * @param llm model instance to use; {@code null} clears the override and
     *            lets the agent lazily rebuild from configuration
     * @since 0.1.7
     */
    public void setLlm(Model llm) {
        synchronized (llmLock) {
            this.llm = llm;
        }
    }

    /**
     * Return the currently active LLM instance, or {@code null} when it has not
     * been initialized yet.
     * 
     * @return active model instance or null
     * @since 0.1.7
     */
    public Model peekLlm() {
        return llm;
    }

    /**
     * Prepare context and call model with rail lifecycle events.
     *
     * @param ctx ctx
     * @param context context
     * @param systemMessages systemMessages
     * @param tools tools
     * @return the result
     * @since 0.1.7
     */
    private AssistantMessage callModel(AgentCallbackContext ctx, ModelContext context, List<BaseMessage> systemMessages,
            List<ToolInfo> tools) {
        var contextWindow = context.getContextWindow(systemMessages, tools != null ? tools : null,
                null, null, buildContextWindowKwargs());

        ctx.setInputs(ModelCallInputs.builder().messages(new ArrayList<>(contextWindow.getMessages()))
                .tools(contextWindow.getToolList()).build());

        return railedModelCall(ctx, buildKvCacheInvokeKwargs(ctx)).orElse(null);
    }

    /**
     * Build kwargs for {@link ModelContext#getContextWindow}, injecting
     * {@code model=llm} when KV cache release is enabled and the underlying
     * client supports it.
     * <p>
     * Mirrors Python's {@code react_agent.py} KV cache release kwargs block:
     * reads {@code ContextEngineConfig.enable_kv_cache_release}, checks
     * {@code llm.supports_kv_cache_release()}, logs a one-time warning when
     * enabled but unsupported, and only injects {@code model} when both hold.
     *
     * @return kwargs map (possibly containing {@code "model"})
     * @since 0.1.7
     */
    private Map<String, Object> buildContextWindowKwargs() {
        Map<String, Object> kwargs = new HashMap<>();
        if (llm == null) {
            return kwargs;
        }
        ContextEngineConfig ceConfig = config.getContextEngineConfig();
        boolean isKvReleaseEnabled = ceConfig != null && ceConfig.isEnableKvCacheRelease();
        boolean isKvReleaseSupported = llm.supportsKvCacheRelease();

        if (isKvReleaseEnabled && !isKvReleaseSupported && !isKvReleaseWarningLogged) {
            Loggers.AGENT.warning("ContextEngineConfig.enable_kv_cache_release is True, "
                    + "but the current LLM does not support KV cache release; "
                    + "KV cache release will not take effect.");
            isKvReleaseWarningLogged = true;
        }

        if (isKvReleaseEnabled && isKvReleaseSupported) {
            kwargs.put("model", llm);
        }
        return kwargs;
    }

    /**
     * Build extra kwargs for {@code Model.invoke}/{@code Model.stream} related
     * to KV cache behavior (session_id, enable_cache_sharing).
     * <p>
     * Mirrors Python's {@code react_agent.py:760-767}: calls
     * {@code llm.build_kv_cache_invoke_kwargs(session, enable_kv_cache_release)}
     * and returns the resulting map. Returns an empty map when the underlying
     * client is not an InferenceAffinity client.
     *
     * @param ctx callback context providing the session
     * @return extra kwargs map (possibly containing {@code session_id} and
     *     {@code enable_cache_sharing}); never {@code null}
     * @since 0.1.15
     */
    private Map<String, Object> buildKvCacheInvokeKwargs(AgentCallbackContext ctx) {
        if (llm == null) {
            return Map.of();
        }
        ContextEngineConfig ceConfig = config.getContextEngineConfig();
        boolean isKvReleaseEnabled = ceConfig != null && ceConfig.isEnableKvCacheRelease();
        return new LinkedHashMap<>(llm.buildKvCacheInvokeKwargs(ctx.getSession(), isKvReleaseEnabled));
    }

    /**
     * logLlmResponse.
     * 
     * @param aiMessage aiMessage
     * @since 0.1.7
     */
    private void logLlmResponse(AssistantMessage aiMessage) {
        if (aiMessage == null) {
            return;
        }
        String content = toText(aiMessage.getContent());
        if (content != null && !content.isEmpty()) {
            Loggers.AGENT.info("[LLM] <<< response");
        }
        if (aiMessage.getToolCalls() != null) {
            for (ToolCall tc : aiMessage.getToolCalls()) {
                Loggers.AGENT.info("[LLM]   tool_call: " + tc.getName());
            }
        }
    }

    /**
     * logLlmRequest.
     * 
     * @param messages messages actually sent after model-call rails
     * @since 0.1.7
     */
    private void logLlmRequest(List<?> messages) {
        if (messages == null) {
            return;
        }
        if (UserConfig.isSensitive()) {
            Loggers.AGENT.info("LLM request messages redacted: message_count={}, role_counts={}",
                    messages.size(), summarizeMessageRoles(messages));
            for (Object message : messages) {
                if ("tool".equals(extractMessageRole(message))) {
                    logLlmMessage(message);
                }
            }
            return;
        }
        for (Object message : messages) {
            logLlmMessage(message);
        }
    }

    /**
     * Log one model input using the stable role/content representation required
     * for tool-call diagnostics.
     *
     * @param message model input message
     * @since 0.1.13
     */
    private void logLlmMessage(Object message) {
        if (message == null) {
            return;
        }
        String role = extractMessageRole(message);
        Loggers.AGENT.info("logLlmMessage role={}", role);
    }

    /**
     * Summarize message roles without including user-controlled role values.
     *
     * @param messages messages to summarize
     * @return counts grouped by safe role names
     * @since 0.1.13
     */
    private Map<String, Integer> summarizeMessageRoles(List<?> messages) {
        Map<String, Integer> roleCounts = new LinkedHashMap<>();
        for (Object message : messages) {
            String role = normalizeRoleForLogging(extractMessageRole(message));
            roleCounts.merge(role, 1, Integer::sum);
        }
        return roleCounts;
    }

    /**
     * Extract a message role from supported model input shapes.
     *
     * @param message model input message
     * @return role or an empty string when unavailable
     * @since 0.1.13
     */
    private String extractMessageRole(Object message) {
        Object role = null;
        if (message instanceof BaseMessage baseMessage) {
            role = baseMessage.getRole();
        } else if (message instanceof Map<?, ?> messageMap) {
            role = messageMap.get("role");
        } else {
            return "";
        }
        return role != null ? String.valueOf(role) : "";
    }

    /**
     * Extract message content from supported model input shapes.
     *
     * @param message model input message
     * @return message content
     * @since 0.1.13
     */
    private Object extractMessageContent(Object message) {
        if (message instanceof BaseMessage baseMessage) {
            return baseMessage.getContent();
        }
        if (message instanceof Map<?, ?> messageMap) {
            return messageMap.get("content");
        }
        return message;
    }

    /**
     * Restrict role metadata to known protocol values before logging it.
     *
     * @param role raw role
     * @return safe role bucket
     * @since 0.1.13
     */
    private String normalizeRoleForLogging(String role) {
        return switch (role) {
            case "system", "developer", "user", "assistant", "tool" -> role;
            default -> "other";
        };
    }

    static String toText(Object content) {
        return content instanceof String text ? text : String.valueOf(content);
    }

    /**
     * Execute LLM call with rail before/after/on_exception hooks.
     *
     * @param ctx ctx
     * @param extraKwargs extra kwargs to pass through to {@code Model.invoke}
     *     (e.g. {@code session_id}, {@code enable_cache_sharing}); may be
     *     {@code null}
     * @return the result
     * @since 0.1.15
     */
    private Optional<AssistantMessage> railedModelCall(AgentCallbackContext ctx, Map<String, Object> extraKwargs) {
        return RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL, AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    Model model = getLlm();
                    ModelCallInputs inputs = (ModelCallInputs) ctx.getInputs();
                    logLlmRequest(inputs.getMessages());

                    AssistantMessage aiMessage = model.invoke(inputs.getMessages(),
                            inputs.getTools() != null && !inputs.getTools().isEmpty() ? inputs.getTools() : null, null,
                            null, config.getModelName(), null, null, null, null, extraKwargs);

                    inputs.setResponse(aiMessage);
                    return aiMessage;
                });
    }

    /**
     * Execute streaming model call with rail before/after/on_exception hooks.
     *
     * @param ctx ctx
     * @param agentSession agentSession
     * @param extraKwargs extra kwargs to pass through to {@code Model.stream}
     *     (e.g. {@code session_id}, {@code enable_cache_sharing}); may be
     *     {@code null}
     * @return the result
     * @since 0.1.15
     */
    private Optional<AssistantMessage> railedModelStreamCall(AgentCallbackContext ctx, AgentSessionApi agentSession,
            Map<String, Object> extraKwargs) {
        return RailExecutor.execute(ctx, AgentCallbackEvent.BEFORE_MODEL_CALL, AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION, () -> {
                    Model model = getLlm();
                    if (!(ctx.getInputs() instanceof ModelCallInputs inputs)) {
                        return null;
                    }
                    logLlmRequest(inputs.getMessages());
                    Iterator<AssistantMessageChunk> stream = model.stream(inputs.getMessages(),
                            inputs.getTools() != null && !inputs.getTools().isEmpty() ? inputs.getTools() : null, null,
                            null, config.getModelName(), null, null, null, null, extraKwargs);
                    AssistantMessageChunk merged = null;
                    int chunkIndex = 0;
                    try {
                        while (stream != null && stream.hasNext()) {
                            // Early-exit if the downstream emitter has been closed (e.g.
                            // the session was finalized mid-stream). Continuing to pull
                            // chunks from the upstream LLM Iterator would only produce
                            // discarded writes and log flooding.
                            if (agentSession != null && agentSession.isStreamClosed()) {
                                Loggers.AGENT.info(
                                        "ReActAgent stream loop aborting early: "
                                                + "downstream emitter closed at chunkIndex={}",
                                        chunkIndex);
                                break;
                            }
                            AssistantMessageChunk chunk = stream.next();
                            if (chunk == null) {
                                continue;
                            }
                            merged = merged == null ? chunk : merged.merge(chunk);
                            inputs.setResponse(merged);
                            writeAssistantStreamChunk(agentSession, chunk, chunkIndex++);
                        }
                    } finally {
                        if (stream instanceof AutoCloseable closeable) {
                            try {
                                closeable.close();
                            } catch (Exception ignored) {
                                // Ignore.
                            }
                        }
                    }
                    if (merged == null) {
                        return null;
                    }
                    AssistantMessage aiMessage = AssistantMessage.builder().role(merged.getRole())
                            .content(merged.getContent()).name(merged.getName()).metadata(merged.getMetadata())
                            .toolCalls(merged.getToolCalls()).usageMetadata(merged.getUsageMetadata())
                            .finishReason(merged.getFinishReason()).parserContent(merged.getParserContent())
                            .reasoningContent(merged.getReasoningContent()).build();
                    inputs.setResponse(aiMessage);
                    return aiMessage;
                });
    }

    /**
     * Execute tool calls and commit tool messages into context.
     * 
     * @param ctx ctx
     * @param toolCalls toolCalls
     * @param session session
     * @param context context
     * @since 0.1.7
     */
    private void executeToolCall(AgentCallbackContext ctx, List<?> toolCalls, Session session, ModelContext context) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        for (Object tc : toolCalls) {
            if (tc instanceof ToolCall toolCall) {
                Loggers.AGENT.info("Executing tool: " + toolCall.getName());
            } else {
                Loggers.AGENT.info("Executing tool: " + tc);
            }
        }

        var results = getAbilityManager().execute(ctx, toolCalls, session, null);

        for (var entry : results) {
            if (entry.toolMessage() != null) {
                context.addMessages(entry.toolMessage());
            }
        }
    }

    /**
     * executeToolCallEntries.
     * 
     * @param ctx ctx
     * @param toolCalls toolCalls
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private List<ToolExecutionEntry> executeToolCallEntries(AgentCallbackContext ctx, List<?> toolCalls,
            Session session, ModelContext context) {
        return executeToolCallEntries(ctx, toolCalls, session, context, null);
    }

    /**
     * Streaming-aware overload of {@link #executeToolCallEntries}.
     * <p>
     * When {@code agentSession} is non-null the underlying AbilityManager
     * forwards tool stream chunks as unified {@code tool_output} events
     * (covering per-chunk output, non-streaming invoke results, and tool
     * errors); when {@code null} this behaves identically to the 4-arg
     * overload.
     * <p>
     * Only the main ReAct loop invokes this with a session; the
     * interrupt-resume branch continues to use the 4-arg (non-streaming)
     * variant because the interrupted tool has already been executed
     * upstream.
     *
     * @param ctx           callback context
     * @param toolCalls     tool calls to execute
     * @param session       session
     * @param context       model context (to append ToolMessage)
     * @param agentSession  nullable stream writer; when set, tool stream
     *                      chunks are forwarded to the outer agent stream
     * @return same as 4-arg executeToolCallEntries
     * @since 0.1.15
     */
    private List<ToolExecutionEntry> executeToolCallEntries(
            AgentCallbackContext ctx,
            List<?> toolCalls,
            Session session,
            ModelContext context,
            AgentSessionApi agentSession
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        for (Object tc : toolCalls) {
            if (tc instanceof ToolCall toolCall) {
                Loggers.AGENT.info("Executing tool: " + toolCall.getName());
            } else {
                Loggers.AGENT.info("Executing tool: " + tc);
            }
        }

        List<ToolExecutionEntry> results;
        if (agentSession == null) {
            results = getAbilityManager().execute(ctx, toolCalls, session, null);
        } else {
            results = getAbilityManager().executeStream(ctx, toolCalls, session, null, agentSession);
        }
        for (ToolExecutionEntry entry : results) {
            if (entry.toolMessage() != null) {
                context.addMessages(entry.toolMessage());
            }
        }
        return results;
    }

    /**
     * Warn when skill prompt is enabled but readFile tool is missing.
     * 
     * @since 0.1.7
     */
    private void warnMissingSkillReadFileTool() {
        List<ToolInfo> toolInfos = getAbilityManager().listToolInfo();

        boolean hasReadFile = false;
        List<String> existingToolNames = new ArrayList<>();

        for (ToolInfo t : toolInfos) {
            String name = t.getName();
            if (name != null && !name.isEmpty()) {
                existingToolNames.add(name);
                if ("readFile".equals(name)) {
                    hasReadFile = true;
                }
            }
        }

        if (hasReadFile) {
            return;
        }

        Loggers.AGENT.warning("skill prompt requires tool 'readFile' but it is not found in ability_manager. "
                + "existing_tools=" + existingToolNames);
    }

    /**
     * updateSkillPromptBuilderSection.
     * 
     * @param shouldIncludeSkillsSection shouldIncludeSkillsSection
     * @since 0.1.7
     */
    private void updateSkillPromptBuilderSection(boolean shouldIncludeSkillsSection) {
        if (!shouldIncludeSkillsSection || getSkillUtil() == null || !getSkillUtil().hasSkill()) {
            promptBuilder.removeSection(SKILLS_SECTION);
            return;
        }
        warnMissingSkillReadFileTool();
        addPromptBuilderSection(SKILLS_SECTION, getSkillUtil().getSkillPrompt(), SKILLS_SECTION_PRIORITY);
    }

    /**
     * Initialize model context for inventory.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private ModelContext initContext(Session session) {
        ModelContext context;
        if (config.getContextProcessors() != null) {
            // With context processors and token counter
            List<ContextEngine.ProcessorSpec> specs = new ArrayList<>();
            for (Object proc : config.getContextProcessors()) {
                if (proc instanceof ContextEngine.ProcessorSpec spec) {
                    specs.add(spec);
                }
            }
            context = contextEngine.createContext(null, session, specs, null, null);
        } else {
            context = contextEngine.createContext(null, session);
        }

        Tool contextReloader = context.reloaderTool();
        if (config.getContextEngineConfig().isEnableReload()) {
            getAbilityManager().add(contextReloader.getCard());
            getAbilityManager().registerSessionTool(
                    session != null ? session.getSessionId() : null, contextReloader);
            String agentTag = getCard() != null ? getCard().getId() : null;
            Object existing = agentTag != null && !agentTag.isBlank()
                    ? Runner.resourceMgr().getTool(contextReloader.getCard().getId(), agentTag, TagMatchStrategy.ALL)
                    : Runner.resourceMgr().getTool(contextReloader.getCard().getId());
            if (existing == null) {
                Runner.resourceMgr().addTool(contextReloader, agentTag);
            }
        } else {
            getAbilityManager().remove(contextReloader.getCard().getName());
        }

        return context;
    }

    /**
     * invoke.
     * 
     * @param inputs inputs
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object invoke(Object inputs, Session session) {
        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String))) {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }

        clearTransientPromptSections();

        Object queryPayload;
        String conversationId = null;
        Map<String, Object> callbackExtra = new HashMap<>();
        if (inputs instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) inputs;
            queryPayload = map.get("query");
            conversationId = map.containsKey("conversation_id") ? String.valueOf(map.get("conversation_id")) : null;
            copyInvokeExtra(map, callbackExtra, "run_kind");
            copyInvokeExtra(map, callbackExtra, "run_context");
            copyInvokeExtra(map, callbackExtra, "is_follow_up");
            copyInvokeExtra(map, callbackExtra, "loop_queues");
        } else {
            queryPayload = inputs;
        }

        String query = extractUserText(queryPayload);

        InvokeInputs invokeInputs =
            InvokeInputs.builder().query(query).queryPayload(queryPayload).conversationId(conversationId).build();

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(this).inputs(invokeInputs).config(config)
                .session(session).extra(callbackExtra).build();
        bindSteeringQueue(ctx);
        Object invokeLifecycleInputs = ctx.getInputs();

        SessionContextHolder.setCurrentSession(session);
        fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        try {
            AgentCallbackContext.ForceFinishRequest earlyFinish = ctx.consumeForceFinish();
            if (earlyFinish != null) {
                invokeInputs.setResult(earlyFinish.getResult());
                return earlyFinish.getResult();
            }

            ModelContext context = initContext(session);
            ctx.setContext(context);

            ToolInterruptionState interruptionState = loadInterruptionState(session);
            if (interruptionState == null) {
                String activeQuery = invokeInputs.getQuery();
                if (activeQuery == null || activeQuery.isEmpty()) {
                    Loggers.AGENT.error("ReActAgent invoke error: Input must contain 'query'");
                    throw new IllegalArgumentException("Input must contain 'query'");
                }
                context.addMessages(new UserMessage(activeQuery));
            } else {
                clearInterruptionState(session);
            }

            updateSkillPromptBuilderSection(promptBuilder.hasSection(IDENTITY_SECTION));
            List<BaseMessage> systemMessages = new ArrayList<BaseMessage>();
            String systemPrompt = promptBuilder.build();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                systemMessages.add(new SystemMessage(systemPrompt));
            }

            int startIteration = 0;

            if (interruptionState != null) {
                Optional<Object> resumePayload =
                    normalizeResumePayload(invokeInputs.getQueryPayload(), interruptionState);
                if (resumePayload.isEmpty()) {
                    throw new IllegalArgumentException(
                            "InteractiveInput is required to resume interrupted tool execution");
                }

                ctx.getExtra().put(RESUME_USER_INPUT_KEY, resumePayload.get());
                List<ToolExecutionEntry> resumedResults =
                    executeToolCallEntries(ctx, toInterruptedToolCalls(interruptionState), session, context);
                ctx.getExtra().remove(RESUME_USER_INPUT_KEY);

                ToolInterruptionState resumedState =
                    collectToolInterrupts(resumedResults, toInterruptedToolCalls(interruptionState),
                            interruptionState.getIteration(), interruptionState.getOriginalQuery());
                if (resumedState != null) {
                    contextEngine.saveContexts(session, null);
                    Map<String, Object> interruptResult = commitInterrupt(session, resumedState);
                    invokeInputs.setResult(interruptResult);
                    return interruptResult;
                }
                startIteration = interruptionState.getIteration() + 1;
            }
            List<ToolInfo> tools = getAbilityManager().listToolInfo();

            for (int iteration = startIteration; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info("ReAct iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                injectPendingSteering(ctx, context);
                AssistantMessage aiMessage = callModel(ctx, context, systemMessages, tools);
                logLlmResponse(aiMessage);
                AgentCallbackContext.ForceFinishRequest finishAfterModel = ctx.consumeForceFinish();
                if (finishAfterModel != null) {
                    contextEngine.saveContexts(session, null);
                    invokeInputs.setResult(finishAfterModel.getResult());
                    return finishAfterModel.getResult();
                }
                if (aiMessage == null) {
                    if (ctx.hasPendingSteering()) {
                        continue;
                    }
                    Map<String, Object> result = buildErrorResult("Model call skipped without terminal result");
                    invokeInputs.setResult(result);
                    return result;
                }

                boolean hasToolCalls = aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty();
                if (hasToolCalls && iteration + 1 >= config.getMaxIterations()) {
                    break;
                }

                context.addMessages(AssistantMessage.builder().content(aiMessage.getContent())
                        .toolCalls(hasToolCalls ? cloneToolCallsForContext(aiMessage.getToolCalls())
                                : aiMessage.getToolCalls())
                        .build());

                if (hasToolCalls) {
                    List<ToolExecutionEntry> results =
                        executeToolCallEntries(ctx, aiMessage.getToolCalls(), session, context);

                    AgentCallbackContext.ForceFinishRequest finishAfterTool = ctx.consumeForceFinish();
                    if (finishAfterTool != null) {
                        contextEngine.saveContexts(session, null);
                        invokeInputs.setResult(finishAfterTool.getResult());
                        return finishAfterTool.getResult();
                    }

                    ToolInterruptionState toolInterruptionState = collectToolInterrupts(results,
                            castToolCalls(aiMessage.getToolCalls()), iteration, invokeInputs.getQuery());
                    if (toolInterruptionState != null) {
                        contextEngine.saveContexts(session, null);
                        Map<String, Object> interruptResult = commitInterrupt(session, toolInterruptionState);
                        invokeInputs.setResult(interruptResult);
                        return interruptResult;
                    }
                } else {
                    if (ctx.hasPendingSteering()) {
                        continue;
                    }
                    contextEngine.saveContexts(session, null);
                    Map<String, Object> result = new HashMap<String, Object>();
                    result.put("output", aiMessage.getContent());
                    result.put("result_type", "answer");
                    invokeInputs.setResult(result);
                    return result;
                }
            }

            contextEngine.saveContexts(session, null);
            Map<String, Object> result = buildErrorResult("Max iterations reached without completion");
            invokeInputs.setResult(result);
            return result;
        } finally {
            if (invokeLifecycleInputs instanceof com.openjiuwen.core.singleagent.rail.EventInputs ei) {
                ctx.setInputs(ei);
            }
            fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
            SessionContextHolder.clearCurrentSession();
        }
    }

    /**
     * copyInvokeExtra.
     * 
     * @param inputs inputs
     * @param target target
     * @param key key
     * @since 0.1.7
     */
    private static void copyInvokeExtra(Map<?, ?> inputs, Map<String, Object> target, String key) {
        if (inputs.containsKey(key)) {
            target.put(key, inputs.get(key));
        }
    }

    /**
     * bindSteeringQueue.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    private void bindSteeringQueue(AgentCallbackContext ctx) {
        if (ctx == null || ctx.getExtra() == null) {
            return;
        }
        Object queues = ctx.getExtra().get("loop_queues");
        if (queues instanceof SteeringQueue steeringQueue) {
            ctx.bindSteeringQueue(steeringQueue);
        } else {
            SteeringQueue autoQueue = new LoopQueues();
            ctx.getExtra().put("loop_queues", autoQueue);
            ctx.bindSteeringQueue(autoQueue);
        }
    }

    /**
     * injectPendingSteering.
     * 
     * @param ctx ctx
     * @param context context
     * @since 0.1.7
     */
    private void injectPendingSteering(AgentCallbackContext ctx, ModelContext context) {
        List<String> steering = ctx.drainSteering();
        if (steering.isEmpty()) {
            return;
        }
        context.addMessages(new UserMessage("[STEERING] " + String.join("\n", steering)));
    }

    /**
     * stream.
     * 
     * @param inputs inputs
     * @param session session
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi agentSession = toAgentSession(session, streamModes);
        if (agentSession == null) {
            throw new IllegalArgumentException("Session is required for streaming");
        }
        preRunStreamSession(agentSession, inputs);
        Future<?> streamFuture;
        try {
            streamFuture = STREAM_EXECUTOR.submit(() -> {
                try {
                    Map<String, Object> finalResult = invokeForStream(inputs, session, agentSession);
                    writeStreamResult(agentSession, finalResult);
                } catch (Exception e) {
                    writeStreamError(agentSession, e);
                } finally {
                    postRunStreamSession(agentSession, session);
                }
            });
        } catch (RejectedExecutionException ex) {
            // 池满（线程 + 队列全占用）：写入流错误事件而不是挂死客户端，
            // 单请求失败，池子保持健康。
            writeStreamError(agentSession, ex);
            return agentSession.streamIterator();
        }
        // 消费方关闭/取消 iterator 时中断后台任务，使阻塞的 LLM HTTP 调用提前退出。
        return OperatorStream.wrap(agentSession.streamIterator(), () -> streamFuture.cancel(true));
    }

    /**
     * Convert a Session to AgentSessionApi.
     * 
     * @param session session
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi toAgentSession(Session session, List<StreamMode> streamModes) {
        AgentSessionApi agentSession = null;
        if (session == null) {
            return agentSession;
        }
        if (session instanceof AgentSessionApi) {
            return (AgentSessionApi) session;
        }
        agentSession = new AgentSessionApi(session.getSessionId(), null, getCard(), streamModes);
        return agentSession;
    }

    /**
     * safeEquals.
     * 
     * @param a a
     * @param b b
     * @return the result
     * @since 0.1.7
     */
    private static boolean safeEquals(Object a, Object b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * loadInterruptionState.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private ToolInterruptionState loadInterruptionState(Session session) {
        ToolInterruptionState interruptionState = null;
        if (session == null) {
            return interruptionState;
        }
        Object state = session.getState(INTERRUPTION_KEY);
        if (state instanceof ToolInterruptionState) {
            interruptionState = (ToolInterruptionState) state;
        }
        return interruptionState;
    }

    /**
     * clearInterruptionState.
     * 
     * @param session session
     * @since 0.1.7
     */
    private void clearInterruptionState(Session session) {
        if (session != null) {
            Map<String, Object> state = new HashMap<String, Object>();
            state.put(INTERRUPTION_KEY, null);
            session.updateState(state);
        }
    }

    /**
     * commitInterrupt.
     * 
     * @param session session
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> commitInterrupt(Session session, ToolInterruptionState state) {
        if (session != null) {
            session.updateState(Map.of(INTERRUPTION_KEY, state));
        }
        List<OutputSchema> stateOutputs = new ArrayList<OutputSchema>();
        List<String> interruptIds = new ArrayList<String>();
        int index = 0;
        for (ToolInterruptEntry entry : state.getInterruptedTools()) {
            InterruptRequest request = entry.getRequest();
            if (request == null) {
                continue;
            }
            String interruptId = request.getInterruptId();
            if (interruptId == null || interruptId.isEmpty()) {
                interruptId = entry.getToolCall() != null ? entry.getToolCall().getId() : "interrupt_" + index;
            }
            interruptIds.add(interruptId);
            ToolCallInterruptRequest payload = ToolCallInterruptRequest.fromToolCall(request, entry.getToolCall());
            stateOutputs
                    .add(new OutputSchema(Constant.INTERACTION, index, new InteractionOutput(interruptId, payload)));
            index++;
        }
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("result_type", "interrupt");
        result.put("state", stateOutputs);
        result.put("interrupt_ids", interruptIds);
        return result;
    }

    /**
     * collectToolInterrupts.
     * 
     * @param results results
     * @param toolCalls toolCalls
     * @param iteration iteration
     * @param originalQuery originalQuery
     * @return the result
     * @since 0.1.7
     */
    private ToolInterruptionState collectToolInterrupts(List<ToolExecutionEntry> results, List<ToolCall> toolCalls,
            int iteration, String originalQuery) {
        ToolInterruptionState interruptionState = null;
        if (results == null || results.isEmpty()) {
            return interruptionState;
        }
        List<ToolInterruptEntry> interruptedTools = new ArrayList<ToolInterruptEntry>();
        for (int i = 0; i < results.size() && i < toolCalls.size(); i++) {
            Object toolResult = results.get(i).result();
            if (toolResult instanceof ToolInterruptException) {
                ToolInterruptException interruptException = (ToolInterruptException) toolResult;
                interruptedTools.add(ToolInterruptEntry.builder().toolCall(
                        interruptException.getToolCall() != null ? interruptException.getToolCall() : toolCalls.get(i))
                        .request(interruptException.getRequest()).build());
            }
        }
        if (interruptedTools.isEmpty()) {
            return interruptionState;
        }
        interruptionState = ToolInterruptionState.builder().iteration(iteration).interruptedTools(interruptedTools)
                .originalQuery(originalQuery).build();
        return interruptionState;
    }

    /**
     * toInterruptedToolCalls.
     * 
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    private List<ToolCall> toInterruptedToolCalls(ToolInterruptionState state) {
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        if (state == null || state.getInterruptedTools() == null) {
            return toolCalls;
        }
        for (ToolInterruptEntry entry : state.getInterruptedTools()) {
            if (entry.getToolCall() != null) {
                toolCalls.add(cloneToolCall(entry.getToolCall()));
            }
        }
        return toolCalls;
    }

    /**
     * castToolCalls.
     * 
     * @param toolCalls toolCalls
     * @return the result
     * @since 0.1.7
     */
    private List<ToolCall> castToolCalls(List<?> toolCalls) {
        List<ToolCall> result = new ArrayList<ToolCall>();
        if (toolCalls == null) {
            return result;
        }
        for (Object toolCall : toolCalls) {
            if (toolCall instanceof ToolCall) {
                result.add((ToolCall) toolCall);
            }
        }
        return result;
    }

    /**
     * cloneToolCall.
     * 
     * @param toolCall toolCall
     * @return the result
     * @since 0.1.7
     */
    private ToolCall cloneToolCall(ToolCall toolCall) {
        ToolCall clonedToolCall = toolCall;
        if (toolCall == null) {
            return clonedToolCall;
        }
        clonedToolCall = ToolCall.builder().id(toolCall.getId()).type(toolCall.getType()).name(toolCall.getName())
                .arguments(toolCall.getArguments()).index(toolCall.getIndex()).build();
        return clonedToolCall;
    }

    /**
     * cloneToolCalls.
     * 
     * @param toolCalls toolCalls
     * @return the result
     * @since 0.1.7
     */
    private List<ToolCall> cloneToolCalls(List<ToolCall> toolCalls) {
        List<ToolCall> cloned = new ArrayList<ToolCall>();
        if (toolCalls == null) {
            return cloned;
        }
        for (ToolCall toolCall : toolCalls) {
            cloned.add(cloneToolCall(toolCall));
        }
        return cloned;
    }

    /**
     * Clone tool calls for context persistence and assign a stable positional index
     * when a non-streaming model response omitted one.
     *
     * @param toolCalls tool calls returned by the model
     * @return cloned tool calls with missing indexes normalized
     * @since 0.1.13
     */
    private List<ToolCall> cloneToolCallsForContext(List<ToolCall> toolCalls) {
        List<ToolCall> cloned = cloneToolCalls(toolCalls);
        for (int index = 0; index < cloned.size(); index++) {
            ToolCall toolCall = cloned.get(index);
            if (toolCall != null && toolCall.getIndex() == null) {
                toolCall.setIndex(index);
            }
        }
        return cloned;
    }

    /**
     * normalizeResumePayload.
     * 
     * @param queryPayload queryPayload
     * @param state state
     * @return the result
     * @since 0.1.7
     */
    private Optional<Object> normalizeResumePayload(Object queryPayload, ToolInterruptionState state) {
        if (queryPayload instanceof InteractiveInput) {
            return Optional.<Object>of(queryPayload);
        }
        if (queryPayload instanceof String && state != null && state.getInterruptedTools() != null
                && !state.getInterruptedTools().isEmpty()) {
            if (state.getInterruptedTools().size() == 1) {
                ToolInterruptEntry entry = state.getInterruptedTools().get(0);
                String toolCallId = entry.getToolCall() != null ? entry.getToolCall().getId() : null;
                if (toolCallId != null && !toolCallId.isEmpty()) {
                    InteractiveInput interactiveInput = new InteractiveInput();
                    interactiveInput.update(toolCallId, queryPayload);
                    return Optional.<Object>of(interactiveInput);
                }
            } else {
                InteractiveInput interactiveInput = new InteractiveInput();
                for (ToolInterruptEntry entry : state.getInterruptedTools()) {
                    String toolCallId = entry.getToolCall() != null ? entry.getToolCall().getId() : null;
                    if (toolCallId != null && !toolCallId.isEmpty()) {
                        interactiveInput.update(toolCallId, queryPayload);
                    }
                }
                if (!interactiveInput.getUserInputs().isEmpty()) {
                    return Optional.<Object>of(interactiveInput);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * extractUserText.
     * 
     * @param userInput userInput
     * @return the result
     * @since 0.1.7
     */
    private String extractUserText(Object userInput) {
        if (userInput == null) {
            return "";
        }
        if (userInput instanceof String) {
            return (String) userInput;
        }
        if (userInput instanceof InteractiveInput) {
            InteractiveInput interactiveInput = (InteractiveInput) userInput;
            Object rawInputs = interactiveInput.getRawInputs();
            if (rawInputs != null) {
                return String.valueOf(rawInputs);
            }
            if (!interactiveInput.getUserInputs().isEmpty()) {
                Object firstValue = interactiveInput.getUserInputs().values().iterator().next();
                return String.valueOf(firstValue);
            }
            return "";
        }
        return String.valueOf(userInput);
    }

    /**
     * buildErrorResult.
     * 
     * @param message message
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildErrorResult(String message) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("output", message);
        result.put("result_type", "error");
        return result;
    }

    /**
     * invokeForStream.
     * 
     * @param inputs inputs
     * @param session session
     * @param agentSession agentSession
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> invokeForStream(Object inputs, Session session, AgentSessionApi agentSession) {
        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String))) {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }

        clearTransientPromptSections();

        Object queryPayload;
        String conversationId = null;
        Map<String, Object> callbackExtra = new HashMap<>();
        if (inputs instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) inputs;
            queryPayload = map.get("query");
            conversationId = map.containsKey("conversation_id") ? String.valueOf(map.get("conversation_id")) : null;
            copyInvokeExtra(map, callbackExtra, "run_kind");
            copyInvokeExtra(map, callbackExtra, "run_context");
            copyInvokeExtra(map, callbackExtra, "is_follow_up");
            copyInvokeExtra(map, callbackExtra, "loop_queues");
        } else {
            queryPayload = inputs;
        }

        String query = extractUserText(queryPayload);
        InvokeInputs invokeInputs =
            InvokeInputs.builder().query(query).queryPayload(queryPayload).conversationId(conversationId).build();

        AgentCallbackContext ctx = AgentCallbackContext.builder().agent(this).inputs(invokeInputs).config(config)
                .session(session).extra(callbackExtra).build();
        bindSteeringQueue(ctx);
        Object invokeLifecycleInputs = ctx.getInputs();

        SessionContextHolder.setCurrentSession(session);
        fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        try {
            AgentCallbackContext.ForceFinishRequest earlyFinish = ctx.consumeForceFinish();
            if (earlyFinish != null) {
                invokeInputs.setResult(earlyFinish.getResult());
                return earlyFinish.getResult();
            }

            ModelContext context = initContext(session);
            ctx.setContext(context);

            ToolInterruptionState interruptionState = loadInterruptionState(session);
            int startIteration = 0;
            if (interruptionState == null) {
                String activeQuery = invokeInputs.getQuery();
                if (activeQuery == null || activeQuery.isEmpty()) {
                    Loggers.AGENT.error("ReActAgent stream error: Input must contain 'query'");
                    throw new IllegalArgumentException("Input must contain 'query'");
                }
                context.addMessages(new UserMessage(activeQuery));
            } else {
                clearInterruptionState(session);
                Optional<Object> resumePayload =
                    normalizeResumePayload(invokeInputs.getQueryPayload(), interruptionState);
                if (resumePayload.isEmpty()) {
                    throw new IllegalArgumentException(
                            "InteractiveInput is required to resume interrupted tool execution");
                }

                ctx.getExtra().put(RESUME_USER_INPUT_KEY, resumePayload.get());
                List<ToolExecutionEntry> resumedResults =
                    executeToolCallEntries(ctx, toInterruptedToolCalls(interruptionState), session, context);
                ctx.getExtra().remove(RESUME_USER_INPUT_KEY);

                ToolInterruptionState resumedState =
                    collectToolInterrupts(resumedResults, toInterruptedToolCalls(interruptionState),
                            interruptionState.getIteration(), interruptionState.getOriginalQuery());
                if (resumedState != null) {
                    contextEngine.saveContexts(session, null);
                    Map<String, Object> interruptResult = commitInterrupt(session, resumedState);
                    invokeInputs.setResult(interruptResult);
                    return interruptResult;
                }
                startIteration = interruptionState.getIteration() + 1;
            }

            updateSkillPromptBuilderSection(promptBuilder.hasSection(IDENTITY_SECTION));
            List<BaseMessage> systemMessages = new ArrayList<BaseMessage>();
            String systemPrompt = promptBuilder.build();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                systemMessages.add(new SystemMessage(systemPrompt));
            }
            List<ToolInfo> tools = getAbilityManager().listToolInfo();

            for (int iteration = startIteration; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info("ReAct stream iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                injectPendingSteering(ctx, context);
                // 流式失败时重试（仅空响应）；异常时不重试，空响应回退非流式并包装为流式发送
                AssistantMessage aiMessage = callModelStreamWithRetry(ctx, context, systemMessages, tools,
                        agentSession);
                logLlmResponse(aiMessage);
                AgentCallbackContext.ForceFinishRequest finishAfterModel = ctx.consumeForceFinish();
                if (finishAfterModel != null) {
                    contextEngine.saveContexts(session, null);
                    invokeInputs.setResult(finishAfterModel.getResult());
                    return finishAfterModel.getResult();
                }
                if (aiMessage == null) {
                    Map<String, Object> result =
                        buildErrorResult("Model stream failed after retries, no terminal result");
                    invokeInputs.setResult(result);
                    return result;
                }

                boolean hasToolCalls = aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty();
                if (hasToolCalls && iteration + 1 >= config.getMaxIterations()) {
                    break;
                }

                context.addMessages(AssistantMessage.builder().content(aiMessage.getContent())
                        .toolCalls(hasToolCalls ? cloneToolCallsForContext(aiMessage.getToolCalls())
                                : aiMessage.getToolCalls())
                        .build());

                if (hasToolCalls) {
                    List<ToolExecutionEntry> results =
                        executeToolCallEntries(ctx, aiMessage.getToolCalls(), session, context, agentSession);

                    AgentCallbackContext.ForceFinishRequest finishAfterTool = ctx.consumeForceFinish();
                    if (finishAfterTool != null) {
                        contextEngine.saveContexts(session, null);
                        invokeInputs.setResult(finishAfterTool.getResult());
                        return finishAfterTool.getResult();
                    }

                    ToolInterruptionState toolInterruptionState = collectToolInterrupts(results,
                            castToolCalls(aiMessage.getToolCalls()), iteration, invokeInputs.getQuery());
                    if (toolInterruptionState != null) {
                        contextEngine.saveContexts(session, null);
                        Map<String, Object> interruptResult = commitInterrupt(session, toolInterruptionState);
                        invokeInputs.setResult(interruptResult);
                        return interruptResult;
                    }
                    continue;
                }

                contextEngine.saveContexts(session, null);
                Map<String, Object> result = new HashMap<String, Object>();
                result.put("output", aiMessage.getContent());
                result.put("result_type", "answer");
                if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
                    result.put("tool_calls", aiMessage.getToolCalls());
                }
                invokeInputs.setResult(result);
                return result;
            }

            contextEngine.saveContexts(session, null);
            Map<String, Object> result = buildErrorResult("Max iterations reached without completion");
            invokeInputs.setResult(result);
            return result;
        } finally {
            if (invokeLifecycleInputs instanceof com.openjiuwen.core.singleagent.rail.EventInputs ei) {
                ctx.setInputs(ei);
            }
            fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
            SessionContextHolder.clearCurrentSession();
        }
    }

    /**
     * callModelStream.
     * 
     * @param ctx ctx
     * @param context context
     * @param systemMessages systemMessages
     * @param tools tools
     * @param agentSession agentSession
     * @return the result
     * @since 0.1.7
     */
    private AssistantMessage callModelStream(AgentCallbackContext ctx, ModelContext context,
            List<BaseMessage> systemMessages, List<ToolInfo> tools, AgentSessionApi agentSession) {
        var contextWindow = context.getContextWindow(systemMessages, tools != null ? tools : null,
                null, null, buildContextWindowKwargs());

        ctx.setInputs(ModelCallInputs.builder().messages(new ArrayList<>(contextWindow.getMessages()))
                .tools(contextWindow.getToolList()).build());

        return railedModelStreamCall(ctx, agentSession, buildKvCacheInvokeKwargs(ctx)).orElse(null);
    }

    /**
     * 带重试的流式模型调用。流式请求返回空时按配置重试；若重试仍为空，
     * 回退为非流式调用并包装为流式 chunk 发送。流式异常时不重试（避免部分 chunk 重复发送）。
     *
     * @param ctx ctx
     * @param context context
     * @param systemMessages systemMessages
     * @param tools tools
     * @param agentSession agentSession
     * @return the result, or null if stream error or all retries exhausted
     * @since 0.1.7
     */
    private AssistantMessage callModelStreamWithRetry(AgentCallbackContext ctx, ModelContext context,
            List<BaseMessage> systemMessages, List<ToolInfo> tools, AgentSessionApi agentSession) {
        int maxRetries = config.getStreamMaxRetries();
        long retryDelayMs = config.getStreamRetryDelayMs();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                AssistantMessage aiMessage = callModelStream(ctx, context, systemMessages, tools, agentSession);
                if (aiMessage != null) {
                    return aiMessage;
                }
                Loggers.AGENT.warning("ReAct stream returned empty (attempt "
                    + (attempt + 1) + "/" + (maxRetries + 1) + ")");
            } catch (Exception e) {
                // 异常时已可能有部分 chunk 被发送，不重试避免重复发送
                Loggers.AGENT.error("ReAct stream error (attempt "
                    + (attempt + 1) + "/" + (maxRetries + 1) + "), aborting retry: " + e.getMessage());
                return null;
            }

            if (attempt < maxRetries && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 流式重试全部返回空（非异常）→ 模型可能不支持流式，回退到非流式并包装为流式发送
        Loggers.AGENT.warning("ReAct stream returned empty after " + (maxRetries + 1)
            + " attempts, falling back to non-stream with stream wrapping");
        AssistantMessage aiMessage = callModel(ctx, context, systemMessages, tools);
        if (aiMessage != null) {
            // 有 tool_call 时：content 必须通过流式发送（因为循环会 continue，writeStreamResult 不会发送此轮 content）
            // 无 tool_call 时：content 由 writeStreamResult 统一发送，避免重复
            boolean hasToolCalls = aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty();
            writeNonStreamAsStreamChunks(agentSession, aiMessage, 0, hasToolCalls);
        }
        return aiMessage;
    }

    /**
     * preRunStreamSession.
     * 
     * @param agentSession agentSession
     * @param inputs inputs
     * @since 0.1.7
     */
    private void preRunStreamSession(AgentSessionApi agentSession, Object inputs) {
        if (agentSession != null) {
            agentSession.preRun(inputs);
        }
    }

    /**
     * writeStreamResult.
     * 
     * @param agentSession agentSession
     * @param finalResult finalResult
     * @since 0.1.7
     */
    private void writeStreamResult(AgentSessionApi agentSession, Map<String, Object> finalResult) {
        if (agentSession == null) {
            return;
        }
        Object resultType = finalResult.get("result_type");
        if ("interrupt".equals(resultType) && finalResult.get("state") instanceof List<?> states) {
            for (Object state : states) {
                if (state instanceof OutputSchema outputSchema) {
                    agentSession.writeStream(outputSchema);
                }
            }
            return;
        }
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("output", finalResult.get("output"));
        payload.put("result_type", resultType != null ? resultType : "answer");
        agentSession.writeStream(new OutputSchema("answer", 0, payload));
    }

    /**
     * writeAssistantStreamChunk.
     * 
     * @param agentSession agentSession
     * @param chunk chunk
     * @param index index
     * @since 0.1.7
     */
    private void writeAssistantStreamChunk(AgentSessionApi agentSession, AssistantMessageChunk chunk, int index) {
        if (agentSession == null || chunk == null) {
            return;
        }
        // 复用 AbilityManager.resolveTaskId 同样的取值方式：从 session state 读 task_id，
        // 与 tool_output / task_output chunk 保持一致。standalone ReActAgent 调用
        // 时 task_id 为空字符串，不影响下游消费。
        Object taskIdRaw = agentSession.getState("task_id");
        String taskId = taskIdRaw == null ? "" : String.valueOf(taskIdRaw);

        if (chunk.getReasoningContent() != null) {
            Map<String, Object> reasoningPayload = new HashMap<String, Object>();
            reasoningPayload.put("task_id", taskId);
            reasoningPayload.put("content", chunk.getReasoningContent());
            reasoningPayload.put("result_type", "answer");
            agentSession.writeStream(new OutputSchema("llm_reasoning", index, reasoningPayload));
        }
        // 仅丢弃空串；保留 "\n" 等纯空白 chunk。流式模型常把换行作为独立 delta 发送，
        // 若按 isBlank() 丢弃会导致下游拼接时丢失换行，Markdown 的标题/列表结构被破坏
        if (chunk.getContent() != null && !(chunk.getContent() instanceof String str && str.isEmpty())) {
            Map<String, Object> contentPayload = new HashMap<String, Object>();
            contentPayload.put("task_id", taskId);
            contentPayload.put("content", chunk.getContent());
            contentPayload.put("result_type", "answer");
            agentSession.writeStream(new OutputSchema("llm_output", index, contentPayload));
        }
        if (chunk.getToolCalls() != null && !chunk.getToolCalls().isEmpty()) {
            Map<String, Object> toolPayload = new HashMap<String, Object>();
            toolPayload.put("task_id", taskId);
            toolPayload.put("tool_calls", cloneToolCalls(chunk.getToolCalls()));
            agentSession.writeStream(new OutputSchema("llm_output", index, toolPayload));
        }
        if (chunk.getUsageMetadata() != null) {
            Map<String, Object> usagePayload = new HashMap<String, Object>();
            usagePayload.put("task_id", taskId);
            usagePayload.put("usage_metadata", chunk.getUsageMetadata());
            usagePayload.put("result_type", "answer");
            agentSession.writeStream(new OutputSchema("llm_usage", 0, usagePayload));
        }
    }

    /**
     * 将非流式 AssistantMessage 的 content 拆分为多个 chunk，通过 SSE 逐步发送。
     * 确保即使获得非流式响应，用户也能收到流式正文。
     *
     * @param agentSession agentSession
     * @param aiMessage non-stream AssistantMessage
     * @param startIndex starting chunk index
     * @param sendContent whether to send content as stream chunks; if false, only tool_calls and usage_metadata are sent
     * @since 0.1.7
     */
    private void writeNonStreamAsStreamChunks(AgentSessionApi agentSession, AssistantMessage aiMessage,
            int startIndex, boolean sendContent) {
        if (agentSession == null || aiMessage == null) {
            return;
        }

        String content = toText(aiMessage.getContent());
        if (sendContent && content != null && !content.isBlank()) {
            // 按固定长度分块
            int chunkIndex = startIndex;
            for (int i = 0; i < content.length(); i += STREAM_CHUNK_SIZE) {
                int end = Math.min(i + STREAM_CHUNK_SIZE, content.length());
                String chunkContent = content.substring(i, end);

                AssistantMessageChunk chunk = AssistantMessageChunk.builder()
                        .content(chunkContent)
                        .build();
                writeAssistantStreamChunk(agentSession, chunk, chunkIndex++);
            }

            // 发送 tool_calls（如果有），确保正文先于工具调用发送
            if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
                AssistantMessageChunk toolChunk = AssistantMessageChunk.builder()
                        .toolCalls(aiMessage.getToolCalls())
                        .build();
                writeAssistantStreamChunk(agentSession, toolChunk, chunkIndex);
            }

            // 发送 usage_metadata（如果有）
            if (aiMessage.getUsageMetadata() != null) {
                AssistantMessageChunk usageChunk = AssistantMessageChunk.builder()
                        .usageMetadata(aiMessage.getUsageMetadata())
                        .build();
                writeAssistantStreamChunk(agentSession, usageChunk, chunkIndex + 1);
            }
            return;
        }

        // 不发送 content（由 writeStreamResult 统一负责），仅发送 tool_calls 和 usage_metadata
        int index = startIndex;
        if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
            AssistantMessageChunk toolChunk = AssistantMessageChunk.builder()
                    .toolCalls(aiMessage.getToolCalls())
                    .build();
            writeAssistantStreamChunk(agentSession, toolChunk, index++);
        }
        if (aiMessage.getUsageMetadata() != null) {
            AssistantMessageChunk usageChunk = AssistantMessageChunk.builder()
                    .usageMetadata(aiMessage.getUsageMetadata())
                    .build();
            writeAssistantStreamChunk(agentSession, usageChunk, index);
        }
    }

    /**
     * Write a stream error chunk for a checked/unchecked Exception.
     *
     * @param agentSession session to write to; ignored when null
     * @param exception stream failure
     * @since 0.1.7
     */
    private void writeStreamError(AgentSessionApi agentSession, Exception exception) {
        writeStreamThrowable(agentSession, exception);
    }

    /**
     * Write a stream error chunk for any Throwable (including Error from worker threads).
     *
     * @param agentSession session to write to; ignored when null
     * @param throwable stream failure
     * @since 0.1.7
     */
    private void writeStreamThrowable(AgentSessionApi agentSession, Throwable throwable) {
        String message = describeStreamThrowable(throwable);
        if (throwable == null) {
            Loggers.AGENT.error("ReActAgent stream error: " + message);
        } else {
            Loggers.AGENT.exception("ReActAgent stream error: " + message, throwable);
        }
        if (agentSession == null) {
            return;
        }
        Map<String, Object> errorResult = new HashMap<String, Object>();
        errorResult.put("output", message);
        errorResult.put("result_type", "error");
        agentSession.writeStream(new OutputSchema("error", 0, errorResult));
    }

    /**
     * Build a non-null stream error description. Exceptions such as
     * {@link java.util.ConcurrentModificationException} often have a null {@code getMessage()};
     * logging that as bare {@code "null"} hides the real failure under load.
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

    /**
     * postRunStreamSession.
     * 
     * @param agentSession agentSession
     * @param session session
     * @since 0.1.7
     */
    private void postRunStreamSession(AgentSessionApi agentSession, Session session) {
        if (agentSession != null) {
            contextEngine.saveContexts(session, null);
            agentSession.postRun();
        }
    }

    /**
     * collectStreamOutputs.
     * 
     * @param agentSession agentSession
     * @param results results
     * @since 0.1.7
     */
    private void collectStreamOutputs(AgentSessionApi agentSession, List<Object> results) {
        if (agentSession == null) {
            return;
        }
        Iterator<Object> streamIter = agentSession.streamIterator();
        while (streamIter.hasNext()) {
            results.add(streamIter.next());
        }
    }
}
