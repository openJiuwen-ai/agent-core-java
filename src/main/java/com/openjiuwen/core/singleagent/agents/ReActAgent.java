/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ReAct paradigm Agent implementation.
 *
 * <p>ReAct loop: Reasoning → Acting → Observation → Repeat
 *
 * <p>Input format (compatible with legacy):
 * <ul>
 *   <li>dict: {"query": "user question", "conversation_id": "session_123"}</li>
 *   <li>str: Used directly as query</li>
 * </ul>
 *
 * <p>Output format:
 * <ul>
 *   <li>invoke: {"output": "response content", "result_type": "answer|error"}</li>
 *   <li>stream: yields OutputSchema objects</li>
 * </ul>
 */
public class ReActAgent extends BaseAgent {

    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private Model llm;

    public ReActAgent(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        this.contextEngine = new ContextEngine(config.getContextEngineConfig());
        this.llm = null;
        initMemoryScope();
    }

    private void initMemoryScope() {
        if (config.getMemScopeId() != null && !config.getMemScopeId().isEmpty()) {
            LongTermMemory.getInstance().setScopeConfig(
                    config.getMemScopeId(),
                    new MemoryScopeConfig()
            );
        }
    }

    protected ReActAgentConfig createDefaultConfig() {
        return ReActAgentConfig.builder().build();
    }

    @Override
    public BaseAgent configure(Object configObj) {
        if (!(configObj instanceof ReActAgentConfig newConfig)) {
            throw new IllegalArgumentException("Expected ReActAgentConfig, got: "
                    + (configObj != null ? configObj.getClass().getName() : "null"));
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
        }

        // Update memory scope if changed
        if (!safeEquals(oldConfig.getMemScopeId(), newConfig.getMemScopeId())) {
            initMemoryScope();
        }

        // Reset skill if sys operation id changed
        if (!safeEquals(oldConfig.getSysOperationId(), newConfig.getSysOperationId())) {
            lazyInitSkill();
        }

        return this;
    }

    @Override
    public Object getConfig() {
        return config;
    }

    public ContextEngine getContextEngine() {
        return contextEngine;
    }

    /**
     * Get LLM instance (lazy initialization).
     */
    protected Model getLlm() {
        if (llm == null) {
            if (config.getModelClientConfig() == null) {
                throw new IllegalStateException(
                        "model_client_config is required. Use configureModelClient() to set it.");
            }
            llm = new Model(config.getModelClientConfig(), config.getModelConfigObj());
        }
        return llm;
    }

    /**
     * Prepare context and call model with rail lifecycle events.
     */
    private AssistantMessage callModel(
            AgentCallbackContext ctx,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools
    ) {
        var contextWindow = context.getContextWindow(
                systemMessages,
                tools != null ? tools : null,
                (Integer) null,
                (Integer) null
        );

        ctx.setInputs(ModelCallInputs.builder()
                .messages(new ArrayList<>(contextWindow.getMessages()))
                .tools(contextWindow.getToolList())
                .build());

        return railedModelCall(ctx);
    }

    /**
     * Execute LLM call with rail before/after/on_exception hooks.
     */
    private AssistantMessage railedModelCall(AgentCallbackContext ctx) {
        return RailExecutor.execute(
                ctx,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    Model model = getLlm();
                    ModelCallInputs inputs = (ModelCallInputs) ctx.getInputs();

                    AssistantMessage aiMessage = model.invoke(
                            inputs.getMessages(),
                            inputs.getTools() != null && !inputs.getTools().isEmpty()
                                    ? inputs.getTools() : null,
                            null, null,
                            config.getModelName(),
                            null, null, null, null, null
                    );

                    inputs.setResponse(aiMessage);
                    return aiMessage;
                }
        );
    }

    /**
     * Execute tool calls and commit tool messages into context.
     */
    private void executeToolCall(
            AgentCallbackContext ctx,
            List<?> toolCalls,
            Session session,
            ModelContext context
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        for (Object tc : toolCalls) {
            Loggers.AGENT.info("Executing tool: " + tc);
        }

        var results = getAbilityManager().execute(ctx, toolCalls, session, null);

        for (var entry : results) {
            if (entry.toolMessage() != null) {
                context.addMessages(entry.toolMessage());
            }
        }
    }

    /**
     * Warn when skill prompt is enabled but readFile tool is missing.
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

        Loggers.AGENT.warning(
                "skill prompt requires tool 'readFile' but it is not found in ability_manager. "
                        + "existing_tools=" + existingToolNames
        );
    }

    /**
     * Initialize model context for inventory.
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
            context = contextEngine.createContext(
                    null,
                    session,
                    specs,
                    null,
                    null
            );
        } else {
            context = contextEngine.createContext(null, session);
        }

        Tool contextReloader = context.reloaderTool();
        if (config.getContextEngineConfig().isEnableReload()) {
            getAbilityManager().add(contextReloader.getCard());
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

    @Override
    public Object invoke(Object inputs, Session session) {
        InvokeInputs invokeInputs = buildInvokeInputs(inputs);

        // Create shared context for the entire invoke lifecycle
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(this)
                .inputs(invokeInputs)
                .session(session)
                .build();
        Object invokeLifecycleInputs = ctx.getInputs();

        // Fire BEFORE_INVOKE
        fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        try {
            PreparedExecution prepared = prepareExecution(ctx, session);
            TerminalOutcome terminalOutcome = runSharedLoop(ctx, prepared, session, null);
            invokeInputs.setResult(terminalOutcome.invokeResult());
            return terminalOutcome.invokeResult();

        } finally {
            // Fire AFTER_INVOKE
            ctx.setInputs((EventInputs) invokeLifecycleInputs);
            fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi agentSession = toAgentSession(session, streamModes);
        Session runtimeSession = session != null ? session : agentSession;
        agentSession.preRun(inputs);

        startStreamProducer(() -> {
            InvokeInputs invokeInputs = null;
            AgentCallbackContext ctx = null;
            Object invokeLifecycleInputs = null;

            try {
                invokeInputs = buildInvokeInputs(inputs);
                ctx = AgentCallbackContext.builder()
                        .agent(this)
                        .inputs(invokeInputs)
                        .session(runtimeSession)
                        .build();
                invokeLifecycleInputs = ctx.getInputs();

                fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

                PreparedExecution prepared = prepareExecution(ctx, runtimeSession);
                TerminalOutcome terminalOutcome = runSharedLoop(ctx, prepared, runtimeSession, agentSession);
                invokeInputs.setResult(terminalOutcome.invokeResult());
                writeTerminalOutcome(agentSession, terminalOutcome);
            } catch (Throwable t) {
                String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
                Loggers.AGENT.error("ReActAgent stream error: " + errorMsg);
                TerminalOutcome terminalOutcome = buildFailureOutcome(errorMsg);
                if (invokeInputs != null) {
                    invokeInputs.setResult(terminalOutcome.invokeResult());
                }
                writeTerminalOutcome(agentSession, terminalOutcome);
            } finally {
                try {
                    contextEngine.saveContexts(runtimeSession, null);
                } finally {
                    agentSession.postRun();
                    if (ctx != null && invokeLifecycleInputs instanceof EventInputs eventInputs) {
                        ctx.setInputs(eventInputs);
                        fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
                    }
                }
            }
        });

        return agentSession.streamIterator();
    }

    /**
     * Convert a Session to AgentSessionApi.
     */
    private AgentSessionApi toAgentSession(Session session, List<StreamMode> streamModes) {
        if (session instanceof AgentSessionApi asa) {
            return asa;
        }
        String sessionId = session != null ? session.getSessionId() : null;
        return AgentSessionApi.create(sessionId, null, getCard(), streamModes);
    }

    private record PreparedExecution(
            InvokeInputs invokeInputs,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools
    ) {
    }

    private enum TerminalBranch {
        SUCCESS,
        FAILURE,
        INTERRUPT_PENDING
    }

    private record TerminalOutcome(
            TerminalBranch branch,
            Map<String, Object> invokeResult,
            OutputSchema streamTerminal
    ) {
    }

    private static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Execute streaming LLM call with rail lifecycle events.
     */
    private AssistantMessage railedModelStreamCall(
            AgentCallbackContext ctx,
            AgentSessionApi agentSession,
            int[] chunkIndexRef
    ) {
        return RailExecutor.execute(
                ctx,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    Model model = getLlm();
                    ModelCallInputs inputs = (ModelCallInputs) ctx.getInputs();

                    Iterator<AssistantMessageChunk> chunks = model.stream(
                            inputs.getMessages(),
                            inputs.getTools() != null && !inputs.getTools().isEmpty()
                                    ? inputs.getTools() : null,
                            null, null,
                            config.getModelName(),
                            null, null, null, null, null
                    );

                    AssistantMessageChunk mergedChunk = null;
                    while (chunks.hasNext()) {
                        AssistantMessageChunk chunk = chunks.next();
                        if (chunk == null) {
                            continue;
                        }

                        mergedChunk = mergedChunk == null ? chunk : mergedChunk.merge(chunk);

                        String chunkText = normalizeChunkText(chunk.getContent());
                        writeIncrementalAnswerChunk(agentSession, chunkIndexRef, chunkText);
                    }

                    AssistantMessageChunk finalChunk = mergedChunk != null
                            ? mergedChunk
                            : AssistantMessageChunk.builder().build();
                    inputs.setResponse(finalChunk);
                    return AssistantMessage.builder()
                            .content(finalChunk.getContent())
                            .toolCalls(finalChunk.getToolCalls())
                            .usageMetadata(finalChunk.getUsageMetadata())
                            .finishReason(finalChunk.getFinishReason())
                            .parserContent(finalChunk.getParserContent())
                            .reasoningContent(finalChunk.getReasoningContent())
                            .build();
                }
        );
    }

    /**
     * Prepare context and call model stream with rail lifecycle events.
     */
    private AssistantMessage callModelStream(
            AgentCallbackContext ctx,
            ModelContext context,
            List<BaseMessage> systemMessages,
            List<ToolInfo> tools,
            AgentSessionApi agentSession,
            int[] chunkIndexRef
    ) {
        var contextWindow = context.getContextWindow(
                systemMessages,
                tools != null ? tools : null,
                (Integer) null,
                (Integer) null
        );

        ctx.setInputs(ModelCallInputs.builder()
                .messages(new ArrayList<>(contextWindow.getMessages()))
                .tools(contextWindow.getToolList())
                .build());

        return railedModelStreamCall(ctx, agentSession, chunkIndexRef);
    }

    private InvokeInputs buildInvokeInputs(Object inputs) {
        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String))) {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }

        String query;
        String conversationId = null;
        if (inputs instanceof Map<?, ?> map) {
            Object rawQuery = map.get("query");
            query = rawQuery != null ? String.valueOf(rawQuery) : "";
            conversationId = map.containsKey("conversation_id")
                    ? String.valueOf(map.get("conversation_id")) : null;
        } else {
            query = (String) inputs;
        }

        if (query == null || query.isBlank()) {
            Loggers.AGENT.error("ReActAgent invoke error: Input dict must contain 'query'");
            throw new IllegalArgumentException("Input dict must contain 'query'");
        }

        return InvokeInputs.builder()
                .query(query)
                .conversationId(conversationId)
                .build();
    }

    private PreparedExecution prepareExecution(AgentCallbackContext ctx, Session session) {
        String userInput = ((InvokeInputs) ctx.getInputs()).getQuery();
        if (userInput == null || userInput.isEmpty()) {
            Loggers.AGENT.error("ReActAgent invoke error: Input dict must contain 'query'");
            throw new IllegalArgumentException("Input dict must contain 'query'");
        }

        ModelContext context = initContext(session);
        ctx.setContext(context);
        context.addMessages(new UserMessage(userInput));

        List<BaseMessage> systemMessages = buildSystemMessages();
        List<ToolInfo> tools = getAbilityManager().listToolInfo();
        return new PreparedExecution((InvokeInputs) ctx.getInputs(), context, systemMessages, tools);
    }

    private List<BaseMessage> buildSystemMessages() {
        List<BaseMessage> systemMessages = new ArrayList<>();
        if (config.getPromptTemplate() != null) {
            for (Map<String, String> msg : config.getPromptTemplate()) {
                if ("system".equals(msg.get("role"))) {
                    systemMessages.add(new SystemMessage(msg.get("content")));
                }
            }
        }

        if (!systemMessages.isEmpty() && getSkillUtil() != null && getSkillUtil().hasSkill()) {
            warnMissingSkillReadFileTool();
            String skillPrompt = getSkillUtil().getSkillPrompt();
            BaseMessage lastMsg = systemMessages.get(systemMessages.size() - 1);
            lastMsg.setContent((lastMsg.getContent() != null ? lastMsg.getContent() : "") + "\n" + skillPrompt);
        }
        return systemMessages;
    }

    private TerminalOutcome runSharedLoop(
            AgentCallbackContext ctx,
            PreparedExecution prepared,
            Session session,
            AgentSessionApi agentSession
    ) {
        int[] chunkIndexRef = new int[] {0};
        try {
            for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info("ReAct iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                AssistantMessage aiMessage = agentSession == null
                        ? callModel(ctx, prepared.context(), prepared.systemMessages(), prepared.tools())
                        : callModelStream(
                                ctx,
                                prepared.context(),
                                prepared.systemMessages(),
                                prepared.tools(),
                                agentSession,
                                chunkIndexRef
                        );

                prepared.context().addMessages(AssistantMessage.builder()
                        .content(aiMessage.getContent())
                        .toolCalls(aiMessage.getToolCalls())
                        .build());

                if (hasToolCalls(aiMessage)) {
                    executeToolCall(ctx, aiMessage.getToolCalls(), session, prepared.context());
                } else {
                    TerminalOutcome terminalOutcome = buildSuccessOutcome(aiMessage);
                    if (agentSession == null) {
                        contextEngine.saveContexts(session, null);
                    }
                    return terminalOutcome;
                }
            }

            TerminalOutcome terminalOutcome = buildFailureOutcome("Max iterations reached without completion");
            if (agentSession == null) {
                contextEngine.saveContexts(session, null);
            }
            return terminalOutcome;
        } catch (Throwable t) {
            String errorMsg = t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
            Loggers.AGENT.error("ReActAgent shared loop error: " + errorMsg);
            return buildFailureOutcome(errorMsg);
        }
    }

    private void startStreamProducer(Runnable producer) {
        try {
            Thread.ofVirtual()
                    .name("react-agent-stream-" + getCard().getId())
                    .start(producer);
            return;
        } catch (UnsupportedOperationException | NoSuchMethodError ignored) {
            // Fall back to a daemon platform thread below.
        }

        Thread worker = new Thread(producer, "react-agent-stream-" + getCard().getId());
        worker.setDaemon(true);
        worker.start();
    }

    private String normalizeChunkText(Object content) {
        return content == null ? "" : String.valueOf(content);
    }

    private boolean hasToolCalls(AssistantMessage aiMessage) {
        return aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty();
    }

    private TerminalOutcome buildSuccessOutcome(AssistantMessage aiMessage) {
        return new TerminalOutcome(
                TerminalBranch.SUCCESS,
                Map.of(
                        "output", normalizeChunkText(aiMessage.getContent()),
                        "result_type", "answer"
                ),
                new OutputSchema("answer", 0, Map.of(
                        "output", "",
                        "result_type", "answer",
                        "status", "completed"
                ))
        );
    }

    private TerminalOutcome buildFailureOutcome(String errorMsg) {
        return new TerminalOutcome(
                TerminalBranch.FAILURE,
                Map.of(
                        "output", errorMsg,
                        "result_type", "error"
                ),
                new OutputSchema("final", 0, Map.of(
                        "error", true,
                        "message", errorMsg,
                        "status", "failed"
                ))
        );
    }

    private TerminalOutcome buildInterruptPendingOutcome(String message) {
        return new TerminalOutcome(
                TerminalBranch.INTERRUPT_PENDING,
                Map.of(
                        "output", message,
                        "result_type", "interrupt_pending"
                ),
                null
        );
    }

    private void writeIncrementalAnswerChunk(AgentSessionApi agentSession, int[] chunkIndexRef, String chunkText) {
        if (agentSession == null || chunkText.isEmpty()) {
            return;
        }
        agentSession.writeStream(new OutputSchema("llm_output", chunkIndexRef[0]++, Map.of(
                "output", chunkText,
                "result_type", "answer"
        )));
    }

    private void writeTerminalOutcome(AgentSessionApi agentSession, TerminalOutcome terminalOutcome) {
        if (agentSession == null || terminalOutcome == null || terminalOutcome.streamTerminal() == null) {
            return;
        }
        agentSession.writeStream(terminalOutcome.streamTerminal());
    }
}
