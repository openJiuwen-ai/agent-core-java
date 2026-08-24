/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolCallArgumentUtils;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.SessionContextHolder;
import com.openjiuwen.core.session.interaction.AgentInterrupt;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.interrupt.ResumeContext;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptHandler;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.ForceFinishRequest;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.Rails;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.external.ExternalToolCallRequest;
import com.openjiuwen.core.singleagent.external.ExternalToolPendingState;
import com.openjiuwen.core.singleagent.external.ExternalToolResult;
import com.openjiuwen.core.singleagent.skills.SkillUtil;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.lang.reflect.Method;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * ReAct paradigm single-agent implementation.
 *
 * <p>Mirrors Python's {@code ReActAgent} in
 * {@code openjiuwen/core/single_agent/agents/react_agent.py}.</p>
 */
public class ReActAgent extends BaseAgent {
    private static final Logger LOGGER = Logger.getLogger(ReActAgent.class.getName());

    public static final String IDENTITY_SECTION = "identity";
    public static final String SKILLS_SECTION = "skills";
    public static final int IDENTITY_SECTION_PRIORITY = 10;
    public static final int SKILLS_SECTION_PRIORITY = 90;
    public static final String EXTERNAL_TOOL_PENDING_KEY = "__react_agent_external_tool_pending__";

    /** Chunk size (characters) when wrapping non-stream responses as stream chunks. */
    private static final int STREAM_CHUNK_SIZE = 200;

    private static final String STREAM_INDEX_REF_KEY = "_stream_index_ref";
    private static final String EXTERNAL_TOOL_RESULT_ID_ERROR =
            "External tool results must contain exactly all pending tool_call_id values";
    private static final String EXTERNAL_TOOL_RESULTS_REQUIRED_ERROR =
            "External tool results are required before continuing this conversation";

    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private volatile Model llm;
    private final Object llmLock = new Object();
    private SystemPromptBuilder promptBuilder = new SystemPromptBuilder();
    private SystemPromptBuilder systemPromptBuilder = promptBuilder;
    private final ToolInterruptHandler hitlHandler;
    private boolean kvReleaseWarningLogged;
    private boolean agentSession;

    public ReActAgent(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        setConfig(this.config);
        this.contextEngine = new ContextEngine(this.config.getContextEngineConfig());
        this.hitlHandler = new ToolInterruptHandler(this);
        getAbilityManager().setContextEngine(contextEngine);
    }

    public ReActAgentConfig createDefaultConfig() {
        return new ReActAgentConfig();
    }

    public ReActAgentConfig _create_default_config() {
        return createDefaultConfig();
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
            kvReleaseWarningLogged = false;
        }
        if (!Objects.equals(oldConfig.getContextEngineConfig(), effectiveConfig.getContextEngineConfig())) {
            contextEngine = new ContextEngine(effectiveConfig.getContextEngineConfig());
            getAbilityManager().setContextEngine(contextEngine);
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

    public Model _get_llm() {
        return getLlm();
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

    public void add_prompt_builder_section(String name, String content, int priority) {
        addPromptBuilderSection(name, content, priority);
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

    public String _build_rendered_system_prompt(Object inputs, Map<String, String> extraRenderFields) {
        return buildRenderedSystemPrompt(inputs, extraRenderFields);
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
        inputs.setMessages(new ArrayList<>(buildPreviewMessages(context)));
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
        return Rails.run(
                ctx,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> doRailedModelCall(ctx)
        );
    }

    public Object _railed_model_call(AgentCallbackContext ctx) {
        return railedModelCall(ctx);
    }

    public Object doRailedModelCall(AgentCallbackContext ctx) {
        ModelCallInputs modelInputs = (ModelCallInputs) ctx.getInputs();
        Map<String, String> requestHeaders = modelInputs.consumeRequestHeaders();
        Model model = getLlm();
        List<ToolInfo> tools = toolInfoList(modelInputs.getTools());
        boolean enableKvRelease = config.getContextEngineConfig().isEnableKvCacheRelease();
        boolean supportsKvRelease = model.supportsKvCacheRelease();

        if (enableKvRelease && !supportsKvRelease && !kvReleaseWarningLogged) {
            LOGGER.warning("ContextEngineConfig.enable_kv_cache_release is True, "
                    + "but the current LLM does not support KV cache release; "
                    + "KV cache release will not take effect.");
            kvReleaseWarningLogged = true;
        }
        Map<String, Object> contextWindowKwargs = new LinkedHashMap<>();
        if (enableKvRelease && supportsKvRelease) {
            contextWindowKwargs.put("model", model);
        }

        ContextWindow contextWindow = ctx.getContext().getContextWindow(
                List.of(new SystemMessage(promptBuilder.build())),
                tools,
                null,
                null,
                contextWindowKwargs
        ).toCompletableFuture().join();
        List<BaseMessage> messages = contextWindow.getMessages();
        tools = contextWindow.getTools();
        modelInputs.setMessages(new ArrayList<>(messages));
        modelInputs.setTools(new ArrayList<>(tools));
        logLlmRequest(null, messages, tools);

        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.putAll(model.buildKvCacheInvokeKwargs(
                ctx.getSession(),
                enableKvRelease
        ));
        if (config.isLlmReturnTokenIds()) {
            extraFields.put("return_token_ids", true);
        }
        if (config.isLlmLogprobs()) {
            extraFields.put("logprobs", true);
            extraFields.put("top_logprobs", config.getLlmTopLogprobs());
        }

        ModelInvokeOptions.ModelInvokeOptionsBuilder optionsBuilder = ModelInvokeOptions.builder()
                .model(config.getModelName())
                .tools(tools)
                .requestHeaders(requestHeaders)
                .extraFields(extraFields);
        if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) && ctx.getSession() != null) {
            AgentSessionApi session = ctx.getSession();
            optionsBuilder.retryListener(event -> session.writeStream(new OutputSchema(
                    "model_retry",
                    nextStreamIndex(ctx),
                    modelRetryPayload(event)
            )));
        }
        ModelInvokeOptions options = optionsBuilder.build();

        boolean streaming = Boolean.TRUE.equals(ctx.getExtra().get("_streaming"));
        if (!streaming) {
            logModelCallStarted(ctx, messages, tools, false);
            long modelStartNanos = System.nanoTime();
            AssistantMessage aiMessage;
            try {
                aiMessage = model.invoke(messages, options).toCompletableFuture().join();
            } catch (RuntimeException exception) {
                logModelCallCompleted(ctx, false, modelStartNanos, null, exception);
                throw exception;
            }
            modelInputs.setResponse(aiMessage);
            logModelResponse(ctx, aiMessage);
            logModelCallCompleted(ctx, false, modelStartNanos, aiMessage, null);
            return aiMessage;
        }
        return streamModelResponseWithRetry(ctx, model, messages, options, modelInputs);
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
        }
        return Optional.empty();
    }

    public List<AbilityManager.ExecutionResult> _execute_tool_call(AgentCallbackContext ctx, List<ToolCall> toolCalls,
                                                                   AgentSessionApi session, ModelContext context) {
        return executeToolCall(ctx, toolCalls, session, context);
    }

    public static List<UserMessage> buildMultimodalToolResultMessages(Object toolResult) {
        UserMessage message = buildMultimodalToolResultsMessage(List.of(toolResult));
        return message == null ? List.of() : List.of(message);
    }

    public static List<UserMessage> _build_multimodal_tool_result_messages(Object toolResult) {
        return buildMultimodalToolResultMessages(toolResult);
    }

    public static UserMessage buildMultimodalToolResultsMessage(Iterable<?> toolResults) {
        List<Map<String, Object>> content = new ArrayList<>();
        List<String> loadedPaths = new ArrayList<>();
        if (toolResults != null) {
            for (Object toolResult : toolResults) {
                for (Map<String, Object> item : iterMultimodalImageItems(toolResult)) {
                    String sourcePath = Objects.toString(item.getOrDefault("source_path", "unknown image"));
                    Object dataUrl = item.get("data_url");
                    loadedPaths.add(sourcePath);
                    content.add(new LinkedHashMap<>(Map.of(
                            "type", "text",
                            "text", "Image loaded from read_file: " + sourcePath
                    )));
                    content.add(new LinkedHashMap<>(Map.of(
                            "type", "image_url",
                            "image_url", new LinkedHashMap<>(Map.of("url", dataUrl))
                    )));
                }
            }
        }
        if (content.isEmpty()) {
            return null;
        }
        if (loadedPaths.size() > 1) {
            List<String> summaryLines = new ArrayList<>();
            summaryLines.add("Images loaded by tool results:");
            for (int i = 0; i < loadedPaths.size(); i++) {
                summaryLines.add((i + 1) + ". " + loadedPaths.get(i));
            }
            content.add(0, new LinkedHashMap<>(Map.of("type", "text", "text", String.join("\n", summaryLines))));
        }
        UserMessage message = new UserMessage();
        message.setContent(content);
        return message;
    }

    public static UserMessage _build_multimodal_tool_results_message(Iterable<?> toolResults) {
        return buildMultimodalToolResultsMessage(toolResults);
    }

    public static List<Map<String, Object>> iterMultimodalImageItems(Object toolResult) {
        Object data = readAttribute(toolResult, "data");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }
        Object rawItems = dataMap.get("multimodal");
        if (!(rawItems instanceof List<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> imageItems = new ArrayList<>();
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> item = stringObjectMap(rawMap);
            Object type = item.get("type");
            Object dataUrl = item.get("data_url");
            if ("image".equals(type) && dataUrl instanceof String text && text.startsWith("data:image/")) {
                imageItems.add(item);
            }
        }
        return imageItems;
    }

    public static List<Map<String, Object>> _iter_multimodal_image_items(Object toolResult) {
        return iterMultimodalImageItems(toolResult);
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

    public boolean _is_interrupted(Object toolResult) {
        return isInterrupted(toolResult);
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

    public List<String> _extract_component_ids(Object toolResult) {
        return extractComponentIds(toolResult);
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

    public String _extract_workflow_id(ToolCall toolCall) {
        return extractWorkflowId(toolCall);
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
            Map<String, Object> result = new LinkedHashMap<>(Map.of(
                    "output", EXTERNAL_TOOL_RESULT_ID_ERROR,
                    "result_type", "error"
            ));
            invokeInputs.setResult(result);
            return result;
        }

        AssistantMessage resumeAiMessage = state.getAssistantMessage() == null
                ? AssistantMessage.builder().content("").toolCalls(state.getPendingToolCalls()).build()
                : copyAssistantMessage(state.getAssistantMessage());
        ensurePendingAssistantMessagePresent(context, resumeAiMessage, state.getPendingToolCalls());

        List<AbilityManager.ExecutionResult> results = new ArrayList<>();
        for (ToolCall toolCall : state.getPendingToolCalls()) {
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
            if (externalResume) {
                contextEngine.saveContexts(session);
            }
            hitlHandler.commitInterrupt(hitlInterrupt.getState(), session, invokeInputs,
                    hitlInterrupt.getPayloads());
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

    public String _extract_user_text(Object userInput) {
        return extractUserText(userInput);
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
        ModelContext context = contextEngine.createContext(null, session, config.getContextProcessors(), null, null);
        ModelContext.ToolPort contextReloader = context.reloaderTool();
        if (config.getContextEngineConfig().isEnableReload()) {
            getAbilityManager().add(contextReloader);
        } else if (contextReloader != null) {
            getAbilityManager().remove(contextReloader.name());
        }
        return context;
    }

    public ModelContext _init_context(AgentSessionApi session) {
        return initContext(session);
    }

    @Override
    public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
        return invoke(inputs, session, Map.of());
    }

    @Override
    public Object invoke(Object inputs, AgentSession session) {
        return invoke(inputs, (AgentSessionApi) session).toCompletableFuture().join();
    }

    public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session, Map<String, Object> kwargs) {
        if (!(inputs instanceof Map<?, ?>) && !(inputs instanceof String)) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("Input must be dict with 'query' or str"));
            return failed;
        }
        Object query;
        String conversationId = null;
        if (inputs instanceof Map<?, ?> map) {
            query = map.containsKey("query") ? map.get("query") : "";
            Object conversation = map.get("conversation_id");
            conversationId = conversation == null ? null : String.valueOf(conversation);
        } else {
            query = inputs;
        }

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
        try {
            return CompletableFuture.completedFuture(innerInvoke(
                    activeSession,
                    inputs,
                    query,
                    needCleanup,
                    conversationId,
                    kwargs
            ));
        } catch (RuntimeException exception) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(exception);
            return failed;
        }
    }

    public Object innerInvoke(AgentSessionApi session, Object inputs, Object query, boolean needCleanup,
                              String conversationId, Map<String, Object> kwargs) {
        boolean iterationFailureLogged = false;
        boolean streaming = false;
        boolean initializationComplete = false;
        AgentCallbackContext ctx = null;
        Object previousSession = SessionContextHolder.getCurrentSession();
        SessionContextHolder.setCurrentSession(session);
        try {
            promptBuilder.clearTransient();
            InvokeInputs invokeInputs = new InvokeInputs();
            invokeInputs.setQuery(query);
            invokeInputs.setConversationId(conversationId);

            ctx = new AgentCallbackContext(this);
            ctx.setInputs(invokeInputs);
            ctx.setSession(session);
            ctx.setConfig(config);
            streaming = Boolean.TRUE.equals(kwargs.get("_streaming"));
            ctx.getExtra().put("_streaming", streaming);
            if (streaming) {
                ctx.getExtra().put(STREAM_INDEX_REF_KEY, new int[] {0});
            }
            if (inputs instanceof Map<?, ?> map) {
                putExtra(ctx, "user_id", map.get("user_id"));
                putExtra(ctx, "run_kind", map.get("run_kind"));
                putExtra(ctx, "run_context", map.get("run_context"));
                Object steeringQueue = map.get("_steering_queue");
                if (steeringQueue instanceof Queue<?> queue) {
                    @SuppressWarnings("unchecked")
                    Queue<String> typedQueue = (Queue<String>) queue;
                    ctx.bindSteeringQueue(typedQueue);
                }
            }
            initializationComplete = true;
            getAgentCallbackManager().execute(AgentCallbackEvent.BEFORE_INVOKE, ctx).toCompletableFuture().join();
            Object userInput = invokeInputs.getQuery();
            ExternalToolPendingState externalPending = loadExternalToolPendingState(session);
            boolean externalResume = externalPending != null && isExternalToolResumeInput(inputs);
            if (externalPending != null && !externalResume) {
                invokeInputs.setResult(buildExternalToolResultsRequiredResult());
                getAgentCallbackManager().execute(AgentCallbackEvent.AFTER_INVOKE, ctx).toCompletableFuture().join();
                Object result = ctx.getExtra().getOrDefault("invoke_result", invokeInputs.getResult());
                if (Boolean.TRUE.equals(ctx.getExtra().get("_streaming")) && result instanceof Map<?, ?> map) {
                    writeInvokeResultToStreamInternal(stringObjectMap(map), session, streamIndexRef(ctx));
                }
                return result;
            }
            if (!externalResume && (userInput == null || String.valueOf(userInput).isEmpty())) {
                throw new IllegalArgumentException("Input must contain 'query'");
            }

            ToolInterruptionState hitlState = externalResume ? null : hitlHandler.load(session);
            Object interruptionState = hitlState != null ? hitlState
                    : (externalResume ? null : loadInterruptionState(session));
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
                ctx.getExtra().put("_original_query", extractUserText(userInput));
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
                Object resumeResult = handleExternalToolResume(externalPending, inputs, ctx, context, session,
                        invokeInputs);
                if (resumeResult == null) {
                    startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                }
            } else if (interruptionState != null) {
                if (interruptionState instanceof ToolInterruptionState) {
                    handleResume(interruptionState, userInput, ctx, context, session, invokeInputs);
                    startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                } else {
                    context.addMessages(new UserMessage(extractUserText(userInput))).toCompletableFuture().join();
                    Object resumeResult = handleResume(interruptionState, userInput, ctx, context, session,
                            invokeInputs);
                    if (resumeResult == null) {
                        startIteration = popInt(ctx.getExtra(), InterruptConstants.RESUME_START_ITERATION_KEY, 0);
                    }
                }
            } else {
                context.addMessages(new UserMessage(extractUserText(userInput))).toCompletableFuture().join();
            }

            if (invokeInputs.getResult() == null) {
                for (int iteration = startIteration; iteration < config.getMaxIterations(); iteration++) {
                    int iterationNumber = iteration + 1;
                    Loggers.AGENT.info("ReAct iteration {}/{} started",
                            iterationNumber, config.getMaxIterations());
                    try {
                        List<String> steering = ctx.drainSteering();
                        if (!steering.isEmpty()) {
                            context.addMessages(new UserMessage("[STEERING] " + String.join("\n", steering)))
                                    .toCompletableFuture()
                                    .join();
                        }
                        List<ToolInfo> tools = listEffectiveToolInfo(session);
                        Object modelResult = callModel(ctx, context, tools);
                        ForceFinishRequest finish = ctx.consumeForceFinish();
                        if (finish != null) {
                            contextEngine.saveContexts(session);
                            invokeInputs.setResult(finish.getResult());
                            break;
                        }
                        if (!(modelResult instanceof AssistantMessage aiMessage)) {
                            invokeInputs.setResult(
                                    modelResult instanceof Map<?, ?> map ? stringObjectMap(map) : Map.of());
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
                    } catch (RuntimeException exception) {
                        iterationFailureLogged = true;
                        Loggers.AGENT.exception(
                                "ReAct iteration %d/%d failed"
                                        .formatted(iterationNumber, config.getMaxIterations()),
                                exception
                        );
                        throw exception;
                    } finally {
                        Loggers.AGENT.info("ReAct iteration {}/{} ended",
                                iterationNumber, config.getMaxIterations());
                    }
                }
                if (invokeInputs.getResult() == null) {
                    Loggers.AGENT.error(
                            "ReActAgent reached max iterations without completion: {}",
                            config.getMaxIterations()
                    );
                    contextEngine.saveContexts(session);
                    invokeInputs.setResult(new LinkedHashMap<>(Map.of(
                            "output", "Max iterations reached without completion",
                            "result_type", "error"
                    )));
                }
            }
            getAgentCallbackManager().execute(AgentCallbackEvent.AFTER_INVOKE, ctx).toCompletableFuture().join();
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
                SessionContextHolder.restoreCurrentSession(previousSession);
            }
        }
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

    public void writeInvokeResultToStream(Map<String, Object> result, AgentSessionApi session) {
        writeInvokeResultToStreamInternal(result, session);
    }

    public void write_invoke_result_to_stream(Map<String, Object> result, AgentSessionApi session) {
        writeInvokeResultToStream(result, session);
    }

    public void writeInvokeResultToStreamInternal(Map<String, Object> result, AgentSessionApi session) {
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
                new LinkedHashMap<>(Map.of(
                        "output", Objects.toString(result.get("output"), ""),
                        "result_type", Objects.toString(resultType, "")
                ))
        ));
    }

    public void _write_invoke_result_to_stream(Map<String, Object> result, AgentSessionApi session) {
        writeInvokeResultToStreamInternal(result, session);
    }

    @Override
    public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        boolean needCleanup = false;
        AgentSessionApi activeSession = session;
        if (activeSession == null) {
            String conversationId = inputs instanceof Map<?, ?> map && map.get("conversation_id") != null
                    ? String.valueOf(map.get("conversation_id"))
                    : null;
            activeSession = AgentSession.createAgentSession(
                    conversationId == null || conversationId.isBlank() ? "default_session" : conversationId,
                    null,
                    getCard()
            );
            needCleanup = true;
        }
        agentSession = runsStreamInBackground(activeSession);
        activeSession.preRun(inputs instanceof Map<?, ?> ? Map.of("inputs", inputs) : Map.of());
        AgentSessionApi finalSession = activeSession;
        boolean finalNeedCleanup = needCleanup;
        if (agentSession) {
            String streamThreadName = resolveStreamWorkerThreadName(inputs);
            VirtualThreadSupport.startThread(
                    streamThreadName,
                    () -> runStreamingInvoke(inputs, finalSession, finalNeedCleanup));
            return finalSession.streamIterator();
        }
        try {
            invoke(inputs, finalSession, Map.of("_streaming", true)).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            writeInvokeResultToStreamInternal(buildErrorResult(exception), finalSession);
        } finally {
            if (finalNeedCleanup) {
                contextEngine.saveContexts(finalSession);
            }
            closeStreamAndCommit(finalSession);
        }
        return finalSession.streamIterator();
    }

    @Override
    public Iterator<Object> stream(Object inputs, AgentSession session, List<StreamMode> streamModes) {
        return stream(inputs, (AgentSessionApi) session, streamModes);
    }

    private void runStreamingInvoke(Object inputs, AgentSessionApi finalSession, boolean finalNeedCleanup) {
        try {
            invoke(inputs, finalSession, Map.of("_streaming", true)).toCompletableFuture().join();
        } catch (RuntimeException exception) {
            writeInvokeResultToStreamInternal(buildErrorResult(exception), finalSession);
        } finally {
            if (finalNeedCleanup) {
                contextEngine.saveContexts(finalSession);
            }
            closeStreamAndCommit(finalSession);
        }
    }

    private static boolean runsStreamInBackground(AgentSessionApi session) {
        return session instanceof AgentSession;
    }

    public void clearSession(String sessionId) {
        invokeStaticRunnerRelease(sessionId);
        contextEngine.clearContext(null, sessionId);
    }

    public void clear_session(String sessionId) {
        clearSession(sessionId);
    }

    public boolean clearContextMessages(String sessionId, String contextId) {
        String effectiveSessionId = sessionId == null ? ContextEngine.DEFAULT_SESSION_ID : sessionId;
        String effectiveContextId = contextId == null ? ContextEngine.DEFAULT_CONTEXT_ID : contextId;
        ModelContext context = contextEngine.getContext(effectiveContextId, effectiveSessionId);
        if (context == null) {
            return false;
        }
        context.clearMessages(false).toCompletableFuture().join();
        return true;
    }

    public boolean clear_context_messages(String sessionId, String contextId) {
        return clearContextMessages(sessionId, contextId);
    }

    @Override
    public ReActAgentConfig getConfig() {
        return config;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    public SystemPromptBuilder getPromptBuilder() {
        return promptBuilder;
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public boolean isAgentSession() {
        return agentSession;
    }

    public ToolInterruptHandler getHitlHandler() {
        return hitlHandler;
    }

    public boolean isKvReleaseWarningLogged() {
        return kvReleaseWarningLogged;
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

    public static String _summarize_tool_call(Object toolCall) {
        return summarizeToolCall(toolCall);
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
            String content = "";
            if (message instanceof BaseMessage baseMessage) {
                role = baseMessage.getRole() != null ? baseMessage.getRole() : "";
                content = String.valueOf(baseMessage.getContent());
            } else {
                content = String.valueOf(message);
            }
            String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
            Loggers.AGENT.info("{\"role\": \"" + role + "\", \"content\": \"" + escaped + "\"}");
        }
    }

    public static void log_llm_request(Object log, List<? extends Object> messages, List<? extends Object> tools) {
        logLlmRequest(log, messages, tools);
    }

    public static void logLlmResponse(Object log, AssistantMessage aiMessage) {
        if (aiMessage == null) {
            return;
        }
        String content = aiMessage.getContent() instanceof String text ? text : String.valueOf(aiMessage.getContent());
        if (content != null && !content.isEmpty()) {
            Loggers.AGENT.info("[LLM] <<< response: content=" + content);
        }
        if (aiMessage.getToolCalls() != null) {
            for (ToolCall tc : aiMessage.getToolCalls()) {
                Loggers.AGENT.info("[LLM]   tool_call: " + tc.getName() + "(" + tc.getArguments() + ")");
            }
        }
    }

    public static void log_llm_response(Object log, AssistantMessage aiMessage) {
        logLlmResponse(log, aiMessage);
    }

    /**
     * Stream with retry on empty responses; exceptions are not retried.
     * After retries are exhausted, fall back to non-stream invoke and wrap content as stream chunks.
     */
    private AssistantMessage streamModelResponseWithRetry(AgentCallbackContext ctx, Model model,
                                                          List<BaseMessage> messages, ModelInvokeOptions options,
                                                          ModelCallInputs modelInputs) {
        int maxRetries = config.getStreamMaxRetries();
        long retryDelayMs = config.getStreamRetryDelayMs();

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                AssistantMessage aiMessage = streamModelResponse(ctx, model, messages, options, modelInputs);
                if (!isEmptyStreamResult(aiMessage)) {
                    return aiMessage;
                }
                Loggers.AGENT.warning("ReAct stream returned empty (attempt "
                        + (attempt + 1) + "/" + (maxRetries + 1) + ")");
            } catch (BaseError | AgentInterrupt | CompletionException | IllegalArgumentException
                    | IllegalStateException | NullPointerException | ClassCastException
                    | UnsupportedOperationException exception) {
                // Partial chunks may already have been sent; do not retry.
                Loggers.AGENT.error("ReAct stream error (attempt "
                        + (attempt + 1) + "/" + (maxRetries + 1) + "), aborting retry: "
                        + exception.getMessage());
                throw exception;
            }

            if (attempt < maxRetries && retryDelayMs > 0) {
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Loggers.AGENT.warning("ReAct stream returned empty after " + (maxRetries + 1)
                + " attempts, falling back to non-stream with stream wrapping");
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
                                                 ModelInvokeOptions options, ModelCallInputs modelInputs) {
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
                    .usageMetadata(accumulatedChunk.getUsageMetadata())
                    .reasoningContent(accumulatedChunk.getReasoningContent())
                    .promptTokenIds(accumulatedChunk.getPromptTokenIds())
                    .completionTokenIds(accumulatedChunk.getCompletionTokenIds())
                    .logprobs(accumulatedChunk.getLogprobs())
                    .build();
        }
        if (isEmptyStreamResult(aiMessage)) {
            modelInputs.setResponse(aiMessage);
            logModelCallCompleted(ctx, true, callStartTime, aiMessage, null);
            return aiMessage;
        }
        modelInputs.setResponse(aiMessage);
        logModelResponse(ctx, aiMessage);
        logModelCallCompleted(ctx, true, callStartTime, aiMessage, null);
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

    private static Map<String, Object> buildErrorResult(RuntimeException exception) {
        return new LinkedHashMap<>(Map.of(
                "output", describeStreamThrowable(exception),
                "result_type", "error"
        ));
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
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static double roundMillis(double nanos) {
        return Math.round((nanos / 1_000_000.0D) * 100.0D) / 100.0D;
    }
}
