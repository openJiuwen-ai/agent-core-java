/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.memory.config.MemoryScopeConfig;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.EventInputs;
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ReAct paradigm Agent implementation.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.single_agent.agents.react_agent.ReActAgent}.</p>
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
 *   <li>invoke: {"output": "response content", "result_type": "answer|error|interrupt"}</li>
 *   <li>stream: yields OutputSchema objects, including "__interaction__" chunks for workflow interrupts</li>
 * </ul>
 */
public class ReActAgent extends BaseAgent {

    private static final String REACT_INTERRUPT_STATE_KEY = "react_interrupt_state";
    private static final String REACT_WORKFLOW_INTERRUPT_STATE_KEY = "react_workflow_interrupt_state";
    private static final String INTERRUPTED_TOOL_PLACEHOLDER = "[INTERRUPTED - Waiting for user input]";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private Model llm;
    private String warnedKvCacheReleaseProvider;

    /**
     * Create a ReAct agent with the given card metadata.
     *
     * @param card agent metadata card
     */
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

    /**
     * Create the default config used before external configuration is applied.
     *
     * @return default ReAct agent config
     */
    protected ReActAgentConfig createDefaultConfig() {
        return ReActAgentConfig.builder().build();
    }

    /**
     * Apply a new agent configuration.
     *
     * @param configObj config object, expected to be {@link ReActAgentConfig}
     * @return current agent instance
     */
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
                || !safeEquals(oldConfig.getApiBase(), newConfig.getApiBase())
                || !safeEquals(oldConfig.getModelClientConfig(), newConfig.getModelClientConfig())
                || !safeEquals(oldConfig.getModelConfigObj(), newConfig.getModelConfigObj())) {
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
            if (newConfig.getSysOperationId() == null || newConfig.getSysOperationId().isBlank()) {
                setSkillUtil(null);
            }
            lazyInitSkill();
        }

        return this;
    }

    /**
     * Return the current agent configuration.
     *
     * @return current config object
     */
    @Override
    public Object getConfig() {
        return config;
    }

    /**
     * Return the context engine currently used by this agent.
     *
     * @return active context engine
     */
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
     * Inject a pre-built model instance for callers that already own the
     * runtime model, including deterministic tests.
     */
    public void setLlm(Model model) {
        this.llm = model;
    }

    private Map<String, Object> buildContextWindowKwargs(Model model) {
        if (!isKvCacheReleaseEnabled()) {
            return Map.of();
        }
        if (model != null && model.supportsKvCacheRelease()) {
            return Map.of("model", model);
        }
        warnKvCacheReleaseUnsupportedOnce();
        return Map.of();
    }

    private Map<String, Object> buildModelCallKwargs(Session session, Model model) {
        if (!isKvCacheReleaseEnabled() || model == null || !model.supportsKvCacheRelease()) {
            return null;
        }
        Map<String, Object> kwargs = new LinkedHashMap<>();
        if (session != null && session.getSessionId() != null) {
            kwargs.put("session_id", session.getSessionId());
        }
        kwargs.put("enable_cache_sharing", true);
        return kwargs;
    }

    private boolean isKvCacheReleaseEnabled() {
        return config != null
                && config.getContextEngineConfig() != null
                && config.getContextEngineConfig().isEnableKvCacheRelease();
    }

    private void warnKvCacheReleaseUnsupportedOnce() {
        String provider = config != null ? config.getModelProvider() : null;
        String warningKey = provider != null && !provider.isBlank() ? provider : "<unknown>";
        if (warningKey.equals(warnedKvCacheReleaseProvider)) {
            return;
        }
        warnedKvCacheReleaseProvider = warningKey;
        Loggers.AGENT.warning("ContextEngineConfig.enable_kv_cache_release is True but model provider "
                + warningKey + " does not support KV cache release. KV cache release will not take effect.");
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
        Model model = getLlm();
        var contextWindow = context.getContextWindow(
                systemMessages,
                tools != null ? tools : null,
                (Integer) null,
                (Integer) null,
                buildContextWindowKwargs(model)
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
                            null, null, null, null,
                            buildModelCallKwargs(ctx.getSession(), model)
                    );

                    inputs.setResponse(aiMessage);
                    return aiMessage;
                }
        );
    }

    /**
     * Execute tool calls and return structured execution facts.
     */
    private List<AbilityManager.ToolExecutionEntry> executeToolCall(
            AgentCallbackContext ctx,
            List<?> toolCalls,
            Session session,
            ModelContext context
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        for (Object tc : toolCalls) {
            String toolName = null;
            String toolId = null;
            if (tc instanceof ToolCall toolCall) {
                toolName = toolCall.getName();
                toolId = toolCall.getId();
            }
            Loggers.AGENT.info("Executing tool: name=" + toolName + ", id=" + toolId);
        }

        return getAbilityManager().execute(ctx, toolCalls, session, null);
    }

    private Optional<TerminalOutcome> interpretToolExecutionFacts(
            List<AbilityManager.ToolExecutionEntry> toolFacts,
            ModelContext context
    ) {
        return interpretToolExecutionFacts(toolFacts, context, null, null, 0, "");
    }

    private Optional<TerminalOutcome> interpretToolExecutionFacts(
            List<AbilityManager.ToolExecutionEntry> toolFacts,
            ModelContext context,
            AgentSessionApi stateSession,
            AssistantMessage aiMessage,
            int iteration,
            String originalQuery
    ) {
        if (toolFacts == null || toolFacts.isEmpty()) {
            return Optional.empty();
        }

        for (AbilityManager.ToolExecutionEntry entry : toolFacts) {
            if (entry.toolMessage() != null) {
                context.addMessages(entry.toolMessage());
            }

            for (UserMessage multimodalMessage : buildMultimodalToolResultMessages(entry.result())) {
                context.addMessages(multimodalMessage);
            }

            if (entry.classification() == AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE) {
                TerminalOutcome outcome = buildInterruptPendingOutcome("Execution interrupted");
                persistInterruptState(stateSession, iteration, List.of(entry.toolCall()), outcome);
                return Optional.of(buildInterruptPendingOutcome(
                        resolveInterruptMessage(outcome),
                        resolveConversationId(stateSession).orElse(null),
                        resolveInteractionId(List.of(entry.toolCall())).orElse(null)
                ));
            }

            if (entry.classification() == AbilityManager.ToolExecutionClassification.ERROR) {
                return Optional.of(buildFailureOutcome("Tool execution failed"));
            }
        }

        Optional<WorkflowInterruptState> workflowState = buildWorkflowInterruptState(
                toolFacts,
                aiMessage,
                iteration,
                originalQuery,
                resolveConversationId(stateSession).orElse(null)
        );
        if (workflowState.isPresent()) {
            return Optional.of(commitWorkflowInterrupt(workflowState.get(), context, stateSession));
        }

        return Optional.empty();
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

    /**
     * Run the agent in non-streaming mode.
     *
     * @param inputs invoke inputs
     * @param session runtime session, or null to create one automatically
     * @return invoke result payload
     */
    @Override
    public Object invoke(Object inputs, Session session) {
        InvokeInputs invokeInputs = buildInvokeInputs(inputs);
        Session runtimeSession = resolveRuntimeSession(session, invokeInputs, null);
        boolean restoreInterrupt = false;

        // Create shared context for the entire invoke lifecycle
        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(this)
                .inputs(invokeInputs)
                .session(runtimeSession)
                .build();
        populateInvocationExtra(ctx, inputs, false);
        Object invokeLifecycleInputs = ctx.getInputs();

        // Fire BEFORE_INVOKE
        fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        try {
            PreparedExecution prepared = prepareExecution(ctx, runtimeSession, inputs);
            TerminalOutcome terminalOutcome = runSharedLoop(ctx, prepared, runtimeSession, null, inputs);
            restoreInterrupt = captureAndClearInterrupt();
            if (terminalOutcome.failureCause() != null) {
                rethrowInvokeException(terminalOutcome.failureCause());
            }
            invokeInputs.setResult(terminalOutcome.invokeResult());
            return terminalOutcome.invokeResult();

        } finally {
            finalizeExecutionLifecycle(ctx, invokeLifecycleInputs, runtimeSession, null, restoreInterrupt);
        }
    }

    /**
     * Run the agent in streaming mode.
     *
     * @param inputs invoke inputs
     * @param session runtime session, or null to create one automatically
     * @param streamModes enabled stream modes
     * @return iterator over streamed output items
     */
    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        InvokeInputs invokeInputs = buildInvokeInputs(inputs);
        Session runtimeSession = resolveRuntimeSession(session, invokeInputs, streamModes);
        AgentSessionApi agentSession = toAgentSession(runtimeSession, streamModes);
        boolean[] restoreInterruptState = new boolean[] {false};
        agentSession.preRun(inputs);

        startStreamProducer(() -> {
            AgentCallbackContext ctx = null;
            Object invokeLifecycleInputs = null;

            try {
                ctx = AgentCallbackContext.builder()
                        .agent(this)
                        .inputs(invokeInputs)
                        .session(runtimeSession)
                        .build();
                populateInvocationExtra(ctx, inputs, true);
                invokeLifecycleInputs = ctx.getInputs();

                fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

                PreparedExecution prepared = prepareExecution(ctx, runtimeSession, inputs);
                TerminalOutcome terminalOutcome = runSharedLoop(ctx, prepared, runtimeSession, agentSession, inputs);
                restoreInterruptState[0] = captureAndClearInterrupt();
                invokeInputs.setResult(terminalOutcome.invokeResult());
                writeTerminalOutcome(agentSession, terminalOutcome);
            } catch (Exception e) {
                // 该场景仅适合捕获通用异常
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Loggers.AGENT.error("ReActAgent stream error: " + errorMsg);
                TerminalOutcome terminalOutcome = buildFailureOutcome(errorMsg);
                if (invokeInputs != null) {
                    invokeInputs.setResult(terminalOutcome.invokeResult());
                }
                writeTerminalOutcome(agentSession, terminalOutcome);
            } finally {
                boolean restoreInterrupt = restoreInterruptState[0];
                finalizeExecutionLifecycle(ctx, invokeLifecycleInputs, runtimeSession, agentSession, restoreInterrupt);
            }
        });

        return agentSession.streamIterator();
    }

    /**
     * Convert a Session to AgentSessionApi.
     */
    private Session resolveRuntimeSession(Session session, InvokeInputs invokeInputs, List<StreamMode> streamModes) {
        if (session != null) {
            return session;
        }
        return AgentSessionApi.create(normalizeConversationId(invokeInputs), null, getCard(), streamModes);
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

    private void finalizeExecutionLifecycle(
            AgentCallbackContext ctx,
            Object invokeLifecycleInputs,
            Session session,
            AgentSessionApi agentSession,
            boolean restoreInterrupt
    ) {
        try {
            contextEngine.saveContexts(session, null);
        } finally {
            try {
                if (agentSession != null) {
                    agentSession.postRun();
                }
            } finally {
                if (ctx != null && invokeLifecycleInputs instanceof EventInputs eventInputs) {
                    ctx.setInputs(eventInputs);
                    fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
                }
                if (restoreInterrupt) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private boolean captureAndClearInterrupt() {
        if (!Thread.currentThread().isInterrupted()) {
            return false;
        }
        Thread.interrupted();
        return true;
    }

    private Optional<String> normalizeConversationId(InvokeInputs invokeInputs) {
        if (invokeInputs == null) {
            return Optional.empty();
        }
        String conversationId = invokeInputs.getConversationId();
        if (conversationId == null) {
            return Optional.empty();
        }

        String normalized = conversationId.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        if ("null".equalsIgnoreCase(normalized) || "undefined".equalsIgnoreCase(normalized)) {
            return Optional.empty();
        }

        return Optional.of(normalized);
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
            OutputSchema streamTerminal,
            Throwable failureCause
    ) {
    }

    private record InterruptState(
            int iteration,
            String conversationId,
            ToolCall pendingToolCall,
            String interactionId,
            String interruptMessage,
            boolean resumeProducedTerminal
    ) {
    }

    private record WorkflowInterruptEntry(
            ToolCall toolCall,
            List<String> componentIds,
            WorkflowOutput workflowExecutionState,
            InteractiveInput collectedInput
    ) {
        WorkflowInterruptEntry withCollectedInput(InteractiveInput input) {
            return new WorkflowInterruptEntry(toolCall, componentIds, workflowExecutionState, input);
        }
    }

    private record WorkflowInterruptState(
            int iteration,
            String conversationId,
            AssistantMessage aiMessage,
            Map<String, WorkflowInterruptEntry> interruptedWorkflows,
            String pendingWorkflowId,
            String pendingComponentId,
            String originalQuery
    ) {
    }

    private record ResumeContinuation(int nextIteration, TerminalOutcome terminalOutcome) {
    }

    private ResumeContinuation continueInterruptedToolCall(
            AgentCallbackContext ctx,
            ModelContext context,
            Session session,
            AgentSessionApi stateSession,
            InterruptState interruptState,
            InteractiveInput resumeInput
    ) {
        Optional<ToolCall> resumedToolCall = rebuildPendingToolCall(
                interruptState.pendingToolCall(),
                interruptState.interactionId(),
                resolveResumeValue(resumeInput, interruptState.interactionId())
        );
        if (resumedToolCall.isEmpty()) {
            clearInterruptState(stateSession);
            return new ResumeContinuation(0, buildFailureOutcome("missing interrupt state for resume"));
        }

        List<AbilityManager.ToolExecutionEntry> toolFacts = executeToolCall(ctx, List.of(resumedToolCall.get()), session, context);
        Optional<TerminalOutcome> toolOutcome = interpretToolExecutionFacts(toolFacts, context);
        if (toolOutcome.isEmpty()) {
            clearInterruptState(stateSession);
            return new ResumeContinuation(interruptState.iteration() + 1, null);
        }
        if (toolOutcome.get().branch() == TerminalBranch.INTERRUPT_PENDING) {
            persistInterruptState(stateSession, interruptState.iteration(), List.of(resumedToolCall.get()), toolOutcome.get());
            return new ResumeContinuation(
                    interruptState.iteration(),
                    buildInterruptPendingOutcome(
                            resolveInterruptMessage(toolOutcome.get()),
                            interruptState.conversationId(),
                            interruptState.interactionId()
                    )
            );
        }
        clearInterruptState(stateSession);
        return new ResumeContinuation(interruptState.iteration(), toolOutcome.get());
    }

    private ResumeContinuation continueInterruptedWorkflow(
            ModelContext context,
            Session session,
            AgentSessionApi stateSession,
            WorkflowInterruptState interruptState,
            InteractiveInput resumeInput
    ) {
        Map<String, WorkflowInterruptEntry> workflows = new LinkedHashMap<>(interruptState.interruptedWorkflows());
        WorkflowInterruptEntry pendingEntry = workflows.get(interruptState.pendingWorkflowId());
        if (pendingEntry == null) {
            clearWorkflowInterruptState(stateSession);
            return new ResumeContinuation(0, buildFailureOutcome("missing interrupt state for resume"));
        }

        InteractiveInput collectedInput = buildInteractiveInput(resumeInput, List.of(interruptState.pendingComponentId()));
        workflows.put(interruptState.pendingWorkflowId(), pendingEntry.withCollectedInput(collectedInput));

        for (Map.Entry<String, WorkflowInterruptEntry> entry : workflows.entrySet()) {
            if (entry.getValue().collectedInput() == null) {
                WorkflowInterruptState nextState = new WorkflowInterruptState(
                        interruptState.iteration(),
                        interruptState.conversationId(),
                        interruptState.aiMessage(),
                        workflows,
                        entry.getKey(),
                        firstComponentId(entry.getValue().componentIds()).orElse(""),
                        interruptState.originalQuery()
                );
                return new ResumeContinuation(
                        interruptState.iteration(),
                        commitWorkflowInterrupt(nextState, context, stateSession)
                );
            }
        }

        context.addMessages(copyAssistantMessage(interruptState.aiMessage()));

        List<AbilityManager.ToolExecutionEntry> toolFacts = new ArrayList<>();
        for (WorkflowInterruptEntry entry : workflows.values()) {
            ToolCall resumedToolCall = copyToolCall(entry.toolCall());
            toolFacts.add(getAbilityManager().executeWorkflowCall(
                    resumedToolCall,
                    entry.collectedInput(),
                    session,
                    null
            ));
        }

        Optional<TerminalOutcome> terminalOutcome = interpretToolExecutionFacts(
                toolFacts,
                context,
                stateSession,
                interruptState.aiMessage(),
                interruptState.iteration(),
                interruptState.originalQuery()
        );
        if (terminalOutcome.isPresent()) {
            return new ResumeContinuation(interruptState.iteration(), terminalOutcome.get());
        }

        clearWorkflowInterruptState(stateSession);
        return new ResumeContinuation(interruptState.iteration() + 1, null);
    }

    private TerminalOutcome commitWorkflowInterrupt(
            WorkflowInterruptState interruptState,
            ModelContext context,
            AgentSessionApi stateSession
    ) {
        WorkflowInterruptEntry pendingEntry = interruptState.interruptedWorkflows().get(interruptState.pendingWorkflowId());
        if (pendingEntry != null && pendingEntry.toolCall() != null) {
            context.addMessages(ToolMessage.builder()
                    .toolCallId(pendingEntry.toolCall().getId())
                    .content(INTERRUPTED_TOOL_PLACEHOLDER)
                    .build());
        }
        persistWorkflowInterruptState(stateSession, interruptState);
        return buildWorkflowInterruptOutcome(interruptState);
    }

    private void persistInterruptState(
            AgentSessionApi stateSession,
            int iteration,
            List<?> toolCalls,
            TerminalOutcome terminalOutcome
    ) {
        if (stateSession == null) {
            return;
        }
        Optional<ToolCall> pendingToolCall = firstToolCall(toolCalls);
        Optional<String> interactionId = resolveInteractionId(toolCalls);
        if (pendingToolCall.isEmpty() && interactionId.isEmpty()) {
            return;
        }
        Map<String, Object> interruptState = new HashMap<>();
        interruptState.put("iteration", iteration);
        interruptState.put("conversationId", safeString(resolveConversationId(stateSession).orElse(null)));
        interruptState.put("pendingToolCall", pendingToolCall.orElse(null));
        interruptState.put("interactionId", safeString(interactionId.orElse(null)));
        interruptState.put("interruptMessage", resolveInterruptMessage(terminalOutcome));
        interruptState.put("resumeProducedTerminal", false);
        stateSession.updateState(Map.of(REACT_INTERRUPT_STATE_KEY, interruptState));
    }

    private Optional<InterruptState> readInterruptState(AgentSessionApi stateSession) {
        if (stateSession == null) {
            return Optional.empty();
        }
        Object rawState = stateSession.getState("react_interrupt_state");
        if (!(rawState instanceof Map<?, ?> stateMap)) {
            return Optional.empty();
        }
        Object pendingToolCall = stateMap.get("pendingToolCall");
        if (!(pendingToolCall instanceof ToolCall toolCall)) {
            return Optional.empty();
        }
        Object iteration = stateMap.get("iteration");
        String conversationId = stringValue(stateMap.get("conversationId")).orElse(null);
        String interactionId = stringValue(stateMap.get("interactionId")).orElse(null);
        String interruptMessage = stringValue(stateMap.get("interruptMessage")).orElse(null);
        boolean resumeProducedTerminal = Boolean.TRUE.equals(stateMap.get("resumeProducedTerminal"));
        int safeIteration = iteration instanceof Number number ? number.intValue() : 0;
        return Optional.of(new InterruptState(
                safeIteration,
                conversationId,
                toolCall,
                interactionId,
                interruptMessage,
                resumeProducedTerminal
        ));
    }

    private void clearInterruptState(AgentSessionApi stateSession) {
        if (stateSession == null) {
            return;
        }
        Map<String, Object> clearedState = new HashMap<>();
        clearedState.put("react_interrupt_state", null);
        stateSession.updateState(clearedState);
    }

    private void persistWorkflowInterruptState(AgentSessionApi stateSession, WorkflowInterruptState interruptState) {
        if (stateSession == null || interruptState == null) {
            return;
        }
        Map<String, Object> state = new HashMap<>();
        state.put(REACT_WORKFLOW_INTERRUPT_STATE_KEY, interruptState);
        stateSession.updateState(state);
    }

    private Optional<WorkflowInterruptState> readWorkflowInterruptState(AgentSessionApi stateSession) {
        if (stateSession == null) {
            return Optional.empty();
        }
        Object rawState = stateSession.getState(REACT_WORKFLOW_INTERRUPT_STATE_KEY);
        return rawState instanceof WorkflowInterruptState workflowInterruptState
                ? Optional.of(workflowInterruptState)
                : Optional.empty();
    }

    private void clearWorkflowInterruptState(AgentSessionApi stateSession) {
        if (stateSession == null) {
            return;
        }
        Map<String, Object> clearedState = new HashMap<>();
        clearedState.put(REACT_WORKFLOW_INTERRUPT_STATE_KEY, null);
        stateSession.updateState(clearedState);
    }

    private Optional<InteractiveInput> normalizeResumeInput(Object rawInputs) {
        if (rawInputs instanceof InteractiveInput interactiveInput) {
            return Optional.of(interactiveInput);
        }
        if (rawInputs == null || rawInputs instanceof Map<?, ?>) {
            return Optional.empty();
        }
        return Optional.of(new InteractiveInput(rawInputs));
    }

    private Optional<Object> resolveResumeValue(InteractiveInput resumeInput, String interactionId) {
        if (resumeInput == null) {
            return Optional.empty();
        }
        if (interactionId != null && resumeInput.getUserInputs() != null) {
            Object interactionValue = resumeInput.getUserInputs().get(interactionId);
            if (interactionValue != null) {
                return Optional.of(interactionValue);
            }
        }
        if (resumeInput.getRawInputs() != null) {
            return Optional.of(resumeInput.getRawInputs());
        }
        if (resumeInput.getUserInputs() != null && resumeInput.getUserInputs().size() == 1) {
            return Optional.of(resumeInput.getUserInputs().values().iterator().next());
        }
        return Optional.empty();
    }

    private Optional<ToolCall> rebuildPendingToolCall(
            ToolCall pendingToolCall,
            String interactionId,
            Optional<Object> resumeValue
    ) {
        if (pendingToolCall == null || resumeValue.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ToolCall.builder()
                .id(interactionId != null && !interactionId.isBlank() ? interactionId : pendingToolCall.getId())
                .name(pendingToolCall.getName())
                .arguments(serializeResumeArguments(resumeValue.get()))
                .build());
    }

    private String serializeResumeArguments(Object resumeValue) {
        if (resumeValue instanceof String rawValue) {
            return rawValue;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(resumeValue);
        } catch (JsonProcessingException e) {
            Loggers.AGENT.warning("Failed to serialize resume arguments, falling back to string value: {}",
                    e.getMessage());
            return String.valueOf(resumeValue);
        }
    }

    private String resolveInterruptMessage(TerminalOutcome terminalOutcome) {
        if (terminalOutcome == null || terminalOutcome.streamTerminal() == null
                || !(terminalOutcome.streamTerminal().getPayload() instanceof Map<?, ?> payload)) {
            return "Execution interrupted";
        }
        Object message = payload.get("message");
        return message != null ? String.valueOf(message) : "Execution interrupted";
    }

    private Optional<ToolCall> firstToolCall(List<?> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Optional.empty();
        }
        Object first = toolCalls.get(0);
        return first instanceof ToolCall toolCall ? Optional.of(toolCall) : Optional.empty();
    }

    private Optional<String> resolveInteractionId(List<?> toolCalls) {
        return firstToolCall(toolCalls).map(ToolCall::getId);
    }

    private Optional<String> resolveConversationId(Session session) {
        if (session == null || session.getSessionId() == null) {
            return Optional.empty();
        }
        String sessionId = session.getSessionId().trim();
        return sessionId.isEmpty() ? Optional.empty() : Optional.of(sessionId);
    }

    private boolean matchesConversation(AgentSessionApi stateSession, String expectedConversationId) {
        String actualConversationId = resolveConversationId(stateSession).orElse(null);
        if (expectedConversationId == null || expectedConversationId.isBlank()) {
            return actualConversationId == null || actualConversationId.isBlank();
        }
        return expectedConversationId.equals(actualConversationId);
    }

    private String safeString(String value) {
        return value != null ? value : "";
    }

    private Optional<String> stringValue(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? Optional.empty() : Optional.of(stringValue);
    }

    private Optional<WorkflowInterruptState> buildWorkflowInterruptState(
            List<AbilityManager.ToolExecutionEntry> toolFacts,
            AssistantMessage aiMessage,
            int iteration,
            String originalQuery,
            String conversationId
    ) {
        if (aiMessage == null || toolFacts == null || toolFacts.isEmpty()) {
            return Optional.empty();
        }

        Map<String, WorkflowInterruptEntry> interrupted = new LinkedHashMap<>();
        String firstWorkflowId = null;
        String firstComponentId = null;
        for (AbilityManager.ToolExecutionEntry entry : toolFacts) {
            if (!isInterruptedWorkflowResult(entry.result())) {
                continue;
            }

            String workflowId = resolveWorkflowId(entry.toolCall());
            List<String> componentIds = extractComponentIds((WorkflowOutput) entry.result());
            interrupted.put(workflowId, new WorkflowInterruptEntry(
                    copyToolCall(entry.toolCall()),
                    componentIds,
                    (WorkflowOutput) entry.result(),
                    null
            ));
            if (firstWorkflowId == null) {
                firstWorkflowId = workflowId;
                firstComponentId = firstComponentId(componentIds).orElse("");
            }
        }

        if (interrupted.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new WorkflowInterruptState(
                iteration,
                safeString(conversationId),
                copyAssistantMessage(aiMessage),
                interrupted,
                firstWorkflowId,
                safeString(firstComponentId),
                safeString(originalQuery)
        ));
    }

    private boolean isInterruptedWorkflowResult(Object result) {
        return result instanceof WorkflowOutput workflowOutput
                && workflowOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED;
    }

    private List<String> extractComponentIds(WorkflowOutput workflowOutput) {
        List<String> ids = new ArrayList<>();
        if (workflowOutput == null || !(workflowOutput.getResult() instanceof List<?> schemas)) {
            return ids;
        }
        for (Object item : schemas) {
            if (item instanceof OutputSchema schema) {
                schemaInteractionId(schema).ifPresent(ids::add);
            }
        }
        ids.sort(String::compareTo);
        return ids;
    }

    private Optional<String> schemaInteractionId(OutputSchema schema) {
        if (schema == null || !"__interaction__".equals(schema.getType())) {
            return Optional.empty();
        }
        Object payload = schema.getPayload();
        if (payload instanceof InteractionOutput interactionOutput) {
            return stringValue(interactionOutput.getId());
        }
        if (payload instanceof Map<?, ?> payloadMap) {
            Object id = payloadMap.get("id");
            if (id == null) {
                id = payloadMap.get("component_id");
            }
            return stringValue(id);
        }
        return Optional.empty();
    }

    private String resolveWorkflowId(ToolCall toolCall) {
        if (toolCall == null || toolCall.getName() == null) {
            return "";
        }
        Object ability = getAbilityManager().get(toolCall.getName());
        if (ability instanceof WorkflowCard workflowCard) {
            String id = workflowCard.getId();
            if (id != null && !id.isBlank()) {
                return id;
            }
            String name = workflowCard.getName();
            if (name != null && !name.isBlank()) {
                return name;
            }
        }
        return toolCall.getName();
    }

    private Optional<String> firstComponentId(List<String> componentIds) {
        return componentIds == null || componentIds.isEmpty()
                ? Optional.empty()
                : Optional.ofNullable(componentIds.get(0));
    }

    private TerminalOutcome buildWorkflowInterruptOutcome(WorkflowInterruptState interruptState) {
        WorkflowInterruptEntry pendingEntry = interruptState.interruptedWorkflows().get(interruptState.pendingWorkflowId());
        OutputSchema streamSchema = selectInteractionSchema(pendingEntry, interruptState.pendingComponentId())
                .orElseGet(() -> new OutputSchema(
                        "__interaction__",
                        0,
                        new InteractionOutput(interruptState.pendingComponentId(), "Execution interrupted")
                ));

        Map<String, Object> invokePayload = new HashMap<>();
        invokePayload.put("result_type", "interrupt");
        invokePayload.put("workflow_execution_state", pendingEntry != null ? pendingEntry.workflowExecutionState() : null);
        invokePayload.put("component_ids", List.of(safeString(interruptState.pendingComponentId())));
        return new TerminalOutcome(TerminalBranch.INTERRUPT_PENDING, invokePayload, streamSchema, null);
    }

    private Optional<OutputSchema> selectInteractionSchema(WorkflowInterruptEntry entry, String componentId) {
        if (entry == null || entry.workflowExecutionState() == null
                || !(entry.workflowExecutionState().getResult() instanceof List<?> schemas)) {
            return Optional.empty();
        }
        OutputSchema fallback = null;
        for (Object item : schemas) {
            if (!(item instanceof OutputSchema schema) || !"__interaction__".equals(schema.getType())) {
                continue;
            }
            if (fallback == null) {
                fallback = schema;
            }
            if (schemaInteractionId(schema).filter(id -> id.equals(componentId)).isPresent()) {
                return Optional.of(schema);
            }
        }
        return Optional.ofNullable(fallback);
    }

    private InteractiveInput buildInteractiveInput(InteractiveInput userInput, List<String> componentIds) {
        String fallback = extractUserText(userInput);
        InteractiveInput interactiveInput = new InteractiveInput();
        if (componentIds != null && !componentIds.isEmpty()) {
            for (String componentId : componentIds) {
                if (componentId == null || componentId.isBlank()) {
                    continue;
                }
                Object value = null;
                if (userInput != null && userInput.getUserInputs() != null) {
                    value = userInput.getUserInputs().get(componentId);
                    if (value == null && userInput.getUserInputs().size() == 1) {
                        value = userInput.getUserInputs().values().iterator().next();
                    }
                }
                interactiveInput.update(componentId, value != null ? value : fallback);
            }
            return interactiveInput;
        }
        return new InteractiveInput(fallback);
    }

    private String extractUserText(InteractiveInput userInput) {
        if (userInput == null) {
            return "";
        }
        if (userInput.getUserInputs() != null && !userInput.getUserInputs().isEmpty()) {
            Object first = userInput.getUserInputs().values().iterator().next();
            return first != null ? String.valueOf(first) : "";
        }
        Object rawInputs = userInput.getRawInputs();
        return rawInputs != null ? String.valueOf(rawInputs) : "";
    }

    private AssistantMessage copyAssistantMessage(AssistantMessage source) {
        if (source == null) {
            return AssistantMessage.builder().content("").build();
        }
        return AssistantMessage.builder()
                .content(source.getContent())
                .toolCalls(copyToolCalls(source.getToolCalls()))
                .usageMetadata(source.getUsageMetadata())
                .finishReason(source.getFinishReason())
                .parserContent(source.getParserContent())
                .reasoningContent(source.getReasoningContent())
                .build();
    }

    private List<ToolCall> copyToolCalls(List<ToolCall> source) {
        if (source == null) {
            return null;
        }
        List<ToolCall> copies = new ArrayList<>(source.size());
        for (ToolCall toolCall : source) {
            copies.add(copyToolCall(toolCall));
        }
        return copies;
    }

    private ToolCall copyToolCall(ToolCall source) {
        if (source == null) {
            return null;
        }
        return ToolCall.builder()
                .id(source.getId())
                .type(source.getType())
                .name(source.getName())
                .arguments(source.getArguments())
                .index(source.getIndex())
                .build();
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
            int[] chunkIndexRef,
            StringBuilder visibleOutput
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
                            null, null, null, null,
                            buildModelCallKwargs(ctx.getSession(), model)
                    );

                    AssistantMessageChunk mergedChunk = null;
                    while (chunks.hasNext()) {
                        AssistantMessageChunk chunk = chunks.next();
                        if (chunk == null) {
                            continue;
                        }

                        mergedChunk = mergedChunk == null ? chunk : mergedChunk.merge(chunk);
                        writeCanonicalStreamChunk(agentSession, chunkIndexRef, chunk, visibleOutput);
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
            int[] chunkIndexRef,
            StringBuilder visibleOutput
    ) {
        Model model = getLlm();
        var contextWindow = context.getContextWindow(
                systemMessages,
                tools != null ? tools : null,
                (Integer) null,
                (Integer) null,
                buildContextWindowKwargs(model)
        );

        ctx.setInputs(ModelCallInputs.builder()
                .messages(new ArrayList<>(contextWindow.getMessages()))
                .tools(contextWindow.getToolList())
                .build());

        return railedModelStreamCall(ctx, agentSession, chunkIndexRef, visibleOutput);
    }

    private InvokeInputs buildInvokeInputs(Object inputs) {
        if (inputs instanceof InteractiveInput) {
            return InvokeInputs.builder().build();
        }

        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String))) {
            throw new IllegalArgumentException("Input must be dict with 'query', str, or InteractiveInput");
        }

        String query;
        String conversationId = null;
        if (inputs instanceof Map<?, ?> map) {
            Object rawQuery = map.get("query");
            query = rawQuery != null ? String.valueOf(rawQuery) : "";
            Object rawConversationId = map.get("conversation_id");
            if (rawConversationId != null) {
                String candidate = String.valueOf(rawConversationId);
                conversationId = candidate.isBlank() ? null : candidate;
            }
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

    private PreparedExecution prepareExecution(AgentCallbackContext ctx, Session session, Object rawInputs) {
        String userInput = ((InvokeInputs) ctx.getInputs()).getQuery();
        ModelContext context = initContext(session);
        ctx.setContext(context);
        if (userInput != null && !userInput.isEmpty()) {
            context.addMessages(new UserMessage(userInput));
        }

        Object memoryVariables = ctx.getExtra() != null ? ctx.getExtra().get("memory_variables") : null;
        List<BaseMessage> systemMessages = buildSystemMessages(rawInputs, memoryVariables);
        List<ToolInfo> tools = getAbilityManager().listToolInfo();
        return new PreparedExecution((InvokeInputs) ctx.getInputs(), context, systemMessages, tools);
    }

    private List<BaseMessage> buildSystemMessages(Object rawInputs, Object extraRenderFields) {
        Map<String, Object> renderFields = buildRenderFields(rawInputs, extraRenderFields);
        List<BaseMessage> systemMessages = new ArrayList<>();
        if (config.getPromptTemplate() != null) {
            for (Map<String, String> msg : config.getPromptTemplate()) {
                if ("system".equals(msg.get("role"))) {
                    systemMessages.add(new SystemMessage(renderSystemContent(msg.get("content"), renderFields)));
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

    private void populateInvocationExtra(AgentCallbackContext ctx, Object inputs, boolean streaming) {
        Map<String, Object> extra = ctx.getExtra();
        if (extra == null) {
            extra = new HashMap<>();
            ctx.setExtra(extra);
        }
        extra.put("_streaming", streaming);
        if (inputs instanceof Map<?, ?> inputMap) {
            extra.put("user_id", stringOrEmpty(inputMap.get("user_id")));
            extra.put("run_kind", stringOrEmpty(inputMap.get("run_kind")));
            extra.put("run_context", stringOrEmpty(inputMap.get("run_context")));
        }
    }

    private static Map<String, Object> buildRenderFields(Object rawInputs, Object extraRenderFields) {
        Map<String, Object> renderFields = new LinkedHashMap<>();
        if (rawInputs instanceof Map<?, ?> inputMap) {
            for (Map.Entry<?, ?> entry : inputMap.entrySet()) {
                if (entry.getValue() instanceof String value) {
                    renderFields.put(String.valueOf(entry.getKey()), value);
                }
            }
        } else if (rawInputs != null && !(rawInputs instanceof InteractiveInput)) {
            renderFields.put("query", String.valueOf(rawInputs));
        }

        if (extraRenderFields instanceof Map<?, ?> extraMap) {
            for (Map.Entry<?, ?> entry : extraMap.entrySet()) {
                if (entry.getValue() instanceof String value) {
                    renderFields.put(String.valueOf(entry.getKey()), value);
                }
            }
        }
        return renderFields;
    }

    private static String renderSystemContent(String content, Map<String, Object> renderFields) {
        if (content == null || renderFields == null || renderFields.isEmpty()) {
            return content;
        }
        try {
            Object rendered = PromptTemplate.builder()
                    .content(content)
                    .build()
                    .format(renderFields)
                    .getContent();
            return rendered instanceof String text ? text : content;
        } catch (RuntimeException e) {
            Loggers.AGENT.warning("Failed to render system message placeholder: {}", e.getMessage());
            return content;
        }
    }

    private static String stringOrEmpty(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private TerminalOutcome runSharedLoop(
            AgentCallbackContext ctx,
            PreparedExecution prepared,
            Session session,
            AgentSessionApi agentSession,
            Object rawInputs
    ) {
        int[] chunkIndexRef = new int[] {0};
        StringBuilder visibleOutput = new StringBuilder();
        AgentSessionApi stateSession = session instanceof AgentSessionApi agentStateSession ? agentStateSession : null;
        try {
            Optional<WorkflowInterruptState> workflowInterruptState = readWorkflowInterruptState(stateSession);
            Optional<InterruptState> interruptState = workflowInterruptState.isEmpty()
                    ? readInterruptState(stateSession)
                    : Optional.empty();
            if (workflowInterruptState.isEmpty() && interruptState.isEmpty() && rawInputs instanceof InteractiveInput) {
                return buildFailureOutcome("missing interrupt state for resume");
            }

            int startIteration = 0;
            if (workflowInterruptState.isPresent()) {
                WorkflowInterruptState savedWorkflowState = workflowInterruptState.get();
                Optional<InteractiveInput> resumeInput = normalizeResumeInput(rawInputs);
                if (resumeInput.isEmpty()) {
                    return buildWorkflowInterruptOutcome(savedWorkflowState);
                }
                if (!matchesConversation(stateSession, savedWorkflowState.conversationId())) {
                    clearWorkflowInterruptState(stateSession);
                    return buildFailureOutcome("missing interrupt state for resume");
                }

                String resumeText = extractUserText(resumeInput.get());
                if (!resumeText.isBlank()) {
                    prepared.context().addMessages(new UserMessage(resumeText));
                }

                ResumeContinuation continuation = continueInterruptedWorkflow(
                        prepared.context(),
                        session,
                        stateSession,
                        savedWorkflowState,
                        resumeInput.get()
                );
                if (continuation.terminalOutcome() != null) {
                    return continuation.terminalOutcome();
                }
                startIteration = continuation.nextIteration();
            } else if (interruptState.isPresent()) {
                InterruptState savedInterruptState = interruptState.get();
                Optional<InteractiveInput> resumeInput = normalizeResumeInput(rawInputs);
                if (resumeInput.isEmpty()) {
                    return buildInterruptPendingOutcome(
                            savedInterruptState.interruptMessage(),
                            savedInterruptState.conversationId(),
                            savedInterruptState.interactionId()
                    );
                }
                if (!matchesConversation(stateSession, savedInterruptState.conversationId())) {
                    clearInterruptState(stateSession);
                    return buildFailureOutcome("missing interrupt state for resume");
                }

                ResumeContinuation continuation = continueInterruptedToolCall(
                        ctx,
                        prepared.context(),
                        session,
                        stateSession,
                        savedInterruptState,
                        resumeInput.get()
                );
                if (continuation.terminalOutcome() != null) {
                    return continuation.terminalOutcome();
                }
                startIteration = continuation.nextIteration();
            }

            for (int iteration = startIteration; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info("ReAct iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                AssistantMessage aiMessage = agentSession == null
                        ? callModel(ctx, prepared.context(), prepared.systemMessages(), prepared.tools())
                        : callModelStream(
                                ctx,
                                prepared.context(),
                                prepared.systemMessages(),
                                prepared.tools(),
                                agentSession,
                                chunkIndexRef,
                                visibleOutput
                        );

                if (agentSession == null) {
                    visibleOutput.append(normalizeChunkText(aiMessage.getContent()));
                }

                prepared.context().addMessages(AssistantMessage.builder()
                        .content(aiMessage.getContent())
                        .toolCalls(aiMessage.getToolCalls())
                        .build());

                if (hasToolCalls(aiMessage)) {
                    List<AbilityManager.ToolExecutionEntry> toolFacts = executeToolCall(
                            ctx,
                            aiMessage.getToolCalls(),
                            session,
                            prepared.context()
                    );
                    Optional<TerminalOutcome> toolOutcome = interpretToolExecutionFacts(
                            toolFacts,
                            prepared.context(),
                            stateSession,
                            aiMessage,
                            iteration,
                            prepared.invokeInputs().getQuery()
                    );
                    if (toolOutcome.isPresent()) {
                        if (toolOutcome.get().branch() == TerminalBranch.INTERRUPT_PENDING) {
                            return toolOutcome.get();
                        }
                        return toolOutcome.get();
                    }
                } else {
                    return buildSuccessOutcome(visibleOutput.toString());
                }
            }

            return buildFailureOutcome("Max iterations reached without completion");
        } catch (Exception e) {
            // 此处确实只适合捕获基础异常
            Optional<InterruptedException> interruptedException = findInterruptedException(e);
            if (interruptedException.isPresent()) {
                Loggers.AGENT.warn("ReActAgent shared loop interrupted");
                Thread.currentThread().interrupt();
                persistInterruptState(stateSession, config.getMaxIterations(), List.of(),
                        buildInterruptPendingOutcome("Execution interrupted", resolveConversationId(session).orElse(null), null));
                return buildInterruptPendingOutcome("Execution interrupted", resolveConversationId(session).orElse(null), null);
            }
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            Loggers.AGENT.error("ReActAgent shared loop error: " + errorMsg);
            return buildFailureOutcome(errorMsg, e);
        }
    }

    private void rethrowInvokeException(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }

    private void startStreamProducer(Runnable producer) {
        String workerName = "react-agent-stream-" + getCard().getId();
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = createStreamProducerExceptionHandler();
        try {
            Thread.ofVirtual()
                    .name(workerName)
                    .uncaughtExceptionHandler(uncaughtExceptionHandler)
                    .start(producer);
            return;
        } catch (UnsupportedOperationException | NoSuchMethodError ignored) {
            throw new IllegalStateException(
                    "ReActAgent streaming requires virtual threads, but the current runtime does not support them.",
                    ignored
            );
        }
    }

    private Thread.UncaughtExceptionHandler createStreamProducerExceptionHandler() {
        return (thread, throwable) -> Loggers.AGENT.error(
                "ReActAgent stream producer crashed, thread={}, agentId={}",
                thread.getName(),
                getCard().getId(),
                throwable
        );
    }

    private String normalizeChunkText(Object content) {
        return content == null ? "" : String.valueOf(content);
    }

    /**
     * Build extra user messages for multimodal tool outputs.
     *
     * <p>Mirrors Python's {@code ReActAgent._build_multimodal_tool_result_messages}.</p>
     *
     * @param toolResult raw tool execution result
     * @return user messages containing image parts
     */
    static List<UserMessage> buildMultimodalToolResultMessages(Object toolResult) {
        Object data = readBeanValue(toolResult, "getData");
        if (!(data instanceof Map<?, ?> dataMap)) {
            return List.of();
        }

        Object multimodalItems = dataMap.get("multimodal");
        if (!(multimodalItems instanceof List<?> items)) {
            return List.of();
        }

        List<UserMessage> messages = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> itemMap) || !"image".equals(itemMap.get("type"))) {
                continue;
            }
            Object dataUrl = itemMap.get("data_url");
            if (!(dataUrl instanceof String url) || !url.startsWith("data:image/")) {
                continue;
            }

            Object sourcePathValue = itemMap.get("source_path");
            String sourcePath = sourcePathValue != null ? String.valueOf(sourcePathValue) : "unknown image";
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "Image loaded from read_file: " + sourcePath);

            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", url);
            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);

            messages.add(UserMessage.builder()
                    .content(List.of(textPart, imagePart))
                    .build());
        }
        return messages;
    }

    private static Object readBeanValue(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return null;
        }
    }

    private boolean hasToolCalls(AssistantMessage aiMessage) {
        return aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty();
    }

    private Optional<InterruptedException> findInterruptedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InterruptedException interruptedException) {
                return Optional.of(interruptedException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private TerminalOutcome buildSuccessOutcome(String fullVisibleOutput) {
        String output = normalizeChunkText(fullVisibleOutput);
        return new TerminalOutcome(
                TerminalBranch.SUCCESS,
                Map.of(
                        "output", output,
                        "result_type", "answer"
                ),
                new OutputSchema("answer", 0, Map.of(
                        "output", output,
                        "result_type", "answer",
                        "status", "completed"
                )),
                null
        );
    }

    private TerminalOutcome buildFailureOutcome(String errorMsg) {
        return buildFailureOutcome(errorMsg, null);
    }

    private TerminalOutcome buildFailureOutcome(String errorMsg, Throwable failureCause) {
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
                )),
                failureCause
        );
    }

    private TerminalOutcome buildInterruptPendingOutcome(String message) {
        return buildInterruptPendingOutcome(message, null, null);
    }

    private TerminalOutcome buildInterruptPendingOutcome(String message, String conversationId, String interactionId) {
        Map<String, Object> invokePayload = new HashMap<>();
        invokePayload.put("output", message);
        invokePayload.put("result_type", "interrupt_pending");
        if (conversationId != null && !conversationId.isBlank()) {
            invokePayload.put("resume_supported", true);
            invokePayload.put("conversation_id", conversationId);
            if (interactionId != null && !interactionId.isBlank()) {
                invokePayload.put("interaction", Map.of(
                        "id", interactionId,
                        "component_ids", List.of(interactionId)
                ));
            }
        }

        Map<String, Object> streamPayload = new HashMap<>();
        streamPayload.put("message", message);
        streamPayload.put("result_type", "interrupt_pending");
        streamPayload.put("status", "interrupt_pending");
        if (conversationId != null && !conversationId.isBlank()) {
            streamPayload.put("resume_supported", true);
            streamPayload.put("conversation_id", conversationId);
            if (interactionId != null && !interactionId.isBlank()) {
                streamPayload.put("interaction", Map.of(
                        "id", interactionId,
                        "component_ids", List.of(interactionId)
                ));
            }
        }

        return new TerminalOutcome(
                TerminalBranch.INTERRUPT_PENDING,
                invokePayload,
                new OutputSchema("final", 0, streamPayload),
                null
        );
    }

    private void writeCanonicalStreamChunk(
            AgentSessionApi agentSession,
            int[] chunkIndexRef,
            AssistantMessageChunk chunk,
            StringBuilder visibleOutput
    ) {
        if (agentSession == null || chunk == null) {
            return;
        }

        String reasoningText = normalizeChunkText(chunk.getReasoningContent());
        if (!reasoningText.isEmpty()) {
            agentSession.writeStream(new OutputSchema("llm_reasoning", chunkIndexRef[0]++, Map.of(
                    "content", reasoningText,
                    "result_type", "answer"
            )));
        }

        String chunkText = normalizeChunkText(chunk.getContent());
        if (chunkText.isEmpty()) {
            return;
        }
        if (visibleOutput != null) {
            visibleOutput.append(chunkText);
        }

        agentSession.writeStream(new OutputSchema("llm_output", chunkIndexRef[0]++, Map.of(
                "content", chunkText,
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
