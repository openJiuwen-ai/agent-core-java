// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
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
import com.openjiuwen.core.singleagent.rail.InvokeInputs;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.rail.RailExecutor;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.HashMap;
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
                null,
                null
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
     * Warn when skill prompt is enabled but read_file tool is missing.
     */
    private void warnMissingSkillReadFileTool() {
        List<ToolInfo> toolInfos = getAbilityManager().listToolInfo();

        boolean hasReadFile = false;
        List<String> existingToolNames = new ArrayList<>();

        for (ToolInfo t : toolInfos) {
            String name = t.getName();
            if (name != null && !name.isEmpty()) {
                existingToolNames.add(name);
                if ("read_file".equals(name)) {
                    hasReadFile = true;
                }
            }
        }

        if (hasReadFile) {
            return;
        }

        Loggers.AGENT.warning(
                "skill prompt requires tool 'read_file' but it is not found in ability_manager. "
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
        if (inputs == null || (!(inputs instanceof Map) && !(inputs instanceof String))) {
            throw new IllegalArgumentException("Input must be Map with 'query' or String");
        }

        // Build typed InvokeInputs
        String query;
        String conversationId = null;
        if (inputs instanceof Map<?, ?> map) {
            query = map.containsKey("query") ? String.valueOf(map.get("query")) : "";
            conversationId = map.containsKey("conversation_id")
                    ? String.valueOf(map.get("conversation_id")) : null;
        } else {
            query = (String) inputs;
        }

        InvokeInputs invokeInputs = InvokeInputs.builder()
                .query(query)
                .conversationId(conversationId)
                .build();

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
            // Extract user_input AFTER before_invoke so rail modifications take effect
            String userInput = ((InvokeInputs) ctx.getInputs()).getQuery();
            if (userInput == null || userInput.isEmpty()) {
                throw new IllegalArgumentException("Input must contain 'query'");
            }

            // Get or create model context
            ModelContext context = initContext(session);
            ctx.setContext(context);

            // Add user message to context
            context.addMessages(new UserMessage(userInput));

            // Build system messages from prompt template
            List<BaseMessage> systemMessages = new ArrayList<>();
            if (config.getPromptTemplate() != null) {
                for (Map<String, String> msg : config.getPromptTemplate()) {
                    if ("system".equals(msg.get("role"))) {
                        systemMessages.add(new SystemMessage(msg.get("content")));
                    }
                }
            }

            // Append skill prompt if available
            if (!systemMessages.isEmpty() && getSkillUtil() != null && getSkillUtil().hasSkill()) {
                warnMissingSkillReadFileTool();
                String skillPrompt = getSkillUtil().getSkillPrompt();
                BaseMessage lastMsg = systemMessages.get(systemMessages.size() - 1);
                lastMsg.setContent((lastMsg.getContent() != null ? lastMsg.getContent() : "") + "\n" + skillPrompt);
            }

            // Get tool info from ability_manager
            List<ToolInfo> tools = getAbilityManager().listToolInfo();

            // ReAct loop
            for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info(
                        "ReAct iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                // Model call (BEFORE/AFTER_MODEL_CALL hooks fire inside callModel)
                AssistantMessage aiMessage = callModel(ctx, context, systemMessages, tools);

                context.addMessages(AssistantMessage.builder()
                        .content(aiMessage.getContent())
                        .toolCalls(aiMessage.getToolCalls())
                        .build());

                if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
                    // Tool execution (BEFORE/AFTER_TOOL_CALL hooks fire inside executeToolCall)
                    executeToolCall(ctx, aiMessage.getToolCalls(), session, context);
                } else {
                    contextEngine.saveContexts(session, null);
                    Map<String, Object> result = new HashMap<>();
                    result.put("output", aiMessage.getContent());
                    result.put("result_type", "answer");
                    invokeInputs.setResult(result);
                    return result;
                }
            }

            // Max iterations reached
            contextEngine.saveContexts(session, null);
            Map<String, Object> result = new HashMap<>();
            result.put("output", "Max iterations reached without completion");
            result.put("result_type", "error");
            invokeInputs.setResult(result);
            return result;

        } finally {
            // Fire AFTER_INVOKE
            ctx.setInputs((com.openjiuwen.core.singleagent.rail.EventInputs) invokeLifecycleInputs);
            fireCallbackEvent(AgentCallbackEvent.AFTER_INVOKE, ctx);
        }
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi agentSession = toAgentSession(session);

        if (agentSession != null) {
            agentSession.preRun(inputs);
        }

        List<Object> results = new ArrayList<>();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> finalResult = (Map<String, Object>) invoke(inputs, session);

            // Write to session stream if available
            if (agentSession != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("output", finalResult);
                payload.put("result_type", "answer");
                agentSession.writeStream(new OutputSchema("answer", 0, payload));
            }
        } catch (Exception e) {
            Loggers.AGENT.error("ReActAgent stream error: " + e.getMessage());
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("output", e.getMessage());
            errorResult.put("result_type", "error");
            if (agentSession != null) {
                agentSession.writeStream(new OutputSchema("error", 0, errorResult));
            }
        } finally {
            if (agentSession != null) {
                contextEngine.saveContexts(session, null);
                agentSession.postRun();
            }
        }

        // Read from stream_iterator
        if (agentSession != null) {
            Iterator<Object> streamIter = agentSession.streamIterator();
            while (streamIter.hasNext()) {
                results.add(streamIter.next());
            }
        }

        return results.iterator();
    }

    /**
     * Convert a Session to AgentSessionApi.
     */
    private AgentSessionApi toAgentSession(Session session) {
        if (session == null) {
            return null;
        }
        if (session instanceof AgentSessionApi asa) {
            return asa;
        }
        return AgentSessionApi.create(session.getSessionId(), null, getCard());
    }

    private static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
