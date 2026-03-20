// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextWindow;
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
import com.openjiuwen.core.operator.Operator;
import com.openjiuwen.core.operator.llm_call.LLMCallOperator;
import com.openjiuwen.core.operator.tool_call.ToolCallOperator;
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
import com.openjiuwen.core.singleagent.skills.SkillUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ReAct paradigm Agent with self-evolving operators.
 *
 * <p>Uses {@link LLMCallOperator} and {@link ToolCallOperator} as evolvable
 * operators, allowing system prompt tuning and tool description updates
 * at runtime.</p>
 */
public class ReActAgentEvolve extends BaseAgent {

    private ReActAgentConfig config;
    private ContextEngine contextEngine;
    private Model llm;
    private LLMCallOperator llmOp;
    private ToolCallOperator toolOp;

    public ReActAgentEvolve(AgentCard card) {
        super(card);
        this.config = createDefaultConfig();
        this.contextEngine = new ContextEngine(config.getContextEngineConfig());
        this.llm = null;
        this.llmOp = null;

        initMemoryScope();

        // SkillUtil init (lazy import pattern from Python)
        setSkillUtil(new SkillUtil(config.getSysOperationId()));

        // ToolCallOperator depends on ability_manager, so init after super()
        this.toolOp = new ToolCallOperator(
                null,
                "react_tool",
                (toolCall, session) -> getAbilityManager().executeAsToolExecutor(toolCall, session),
                getAbilityManager()
        );
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
            this.llmOp = null;
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
            if (config.getModelClientConfig() == null && config.getModelConfigObj() == null) {
                throw new IllegalStateException(
                        "model_client_config is required. Use configureModelClient() to set it.");
            }
            llm = new Model(config.getModelClientConfig(), config.getModelConfigObj());
        }
        return llm;
    }

    /**
     * Callback when LLM operator parameter is updated (for sync with config).
     */
    private void onLlmParameterUpdated(String target, Object value) {
        if ("system_prompt".equals(target)) {
            if (value instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, String>> content = (List<Map<String, String>>) list;
                config.setPromptTemplate(content);
            } else {
                List<Map<String, String>> content = new ArrayList<>();
                content.add(Map.of("role", "system", "content", String.valueOf(value)));
                config.setPromptTemplate(content);
            }
        }
    }

    /**
     * Resolve model name from config.
     */
    private String resolveModelName() {
        if (config.getModelConfigObj() != null
                && config.getModelConfigObj().getModelName() != null
                && !config.getModelConfigObj().getModelName().isEmpty()) {
            return config.getModelConfigObj().getModelName();
        }
        return config.getModelName();
    }

    /**
     * Get LLMCallOperator (lazy initialization with self-evolution support).
     */
    private LLMCallOperator getLlmOp() {
        if (llmOp == null) {
            Model model = getLlm();
            String modelName = resolveModelName();
            List<Map<String, String>> systemPrompt = config.getPromptTemplate() != null
                    ? config.getPromptTemplate() : List.of();

            llmOp = new LLMCallOperator(
                    modelName,
                    model,
                    systemPrompt,
                    "{{query}}",
                    false,
                    true,
                    "react_llm",
                    this::onLlmParameterUpdated
            );
        } else {
            // Sync system prompt from config to operator
            llmOp.updateSystemPrompt(config.getPromptTemplate() != null
                    ? config.getPromptTemplate() : List.of());
        }
        return llmOp;
    }

    /**
     * Get skill messages as system messages.
     */
    private List<SystemMessage> getSkillMessages() {
        if (getSkillUtil() == null || !getSkillUtil().hasSkill()) {
            return List.of();
        }
        return List.of(new SystemMessage(getSkillUtil().getSkillPrompt()));
    }

    /**
     * Return evolvable operator registry.
     */
    public Map<String, Operator> getOperators() {
        Map<String, Operator> ops = new LinkedHashMap<>();
        if (toolOp != null) {
            ops.put(toolOp.getOperatorId(), toolOp);
        }
        try {
            LLMCallOperator op = getLlmOp();
            ops.put(op.getOperatorId(), op);
        } catch (Exception e) {
            // Skip LLM operator if model not configured yet
        }
        return ops;
    }

    /**
     * Prepare model call: build context window and call via operator with rail hooks.
     */
    private AssistantMessage prepareModelCall(
            AgentCallbackContext ctx,
            String userInput,
            ModelContext context,
            List<ToolInfo> tools
    ) {
        ContextWindow contextWindow = context.getContextWindow(
                List.of(),
                tools != null ? tools : null,
                (Integer) null,
                (Integer) null
        );

        List<Object> skillMessages = new ArrayList<>(getSkillMessages());
        List<BaseMessage> historyMessages = contextWindow.getMessages();
        List<Object> allMessages = new ArrayList<>(skillMessages.size() + historyMessages.size());
        allMessages.addAll(skillMessages);
        allMessages.addAll(historyMessages);

        ctx.setInputs(ModelCallInputs.builder()
                .messages(allMessages)
                .tools(contextWindow.getToolList())
                .build());

        return railedModelCall(ctx, userInput, ctx.getSession());
    }

    /**
     * Execute LLM call via Operator with rail hooks.
     */
    private AssistantMessage railedModelCall(
            AgentCallbackContext ctx,
            String userInput,
            Session session
    ) {
        return RailExecutor.execute(
                ctx,
                AgentCallbackEvent.BEFORE_MODEL_CALL,
                AgentCallbackEvent.AFTER_MODEL_CALL,
                AgentCallbackEvent.ON_MODEL_EXCEPTION,
                () -> {
                    LLMCallOperator op = getLlmOp();
                    ModelCallInputs inputs = (ModelCallInputs) ctx.getInputs();

                    Map<String, Object> invokeInputs = new HashMap<>();
                    invokeInputs.put("query", userInput);
                    invokeInputs.put("messages", inputs.getMessages());

                    Map<String, Object> kwargs = new HashMap<>();
                    if (inputs.getTools() != null && !inputs.getTools().isEmpty()) {
                        kwargs.put("tools", inputs.getTools());
                    }

                    AssistantMessage aiMessage = (AssistantMessage) op.invoke(invokeInputs, session, kwargs);
                    inputs.setResponse(aiMessage);
                    return aiMessage;
                }
        );
    }

    /**
     * Execute tool calls and commit tool messages into context.
     */
    private void prepareToolCall(
            AgentCallbackContext ctx,
            List<?> toolCalls,
            ModelContext context
    ) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }

        for (Object tc : toolCalls) {
            Loggers.AGENT.info("Executing tool: " + tc);
        }

        var results = getAbilityManager().execute(ctx, toolCalls, ctx.getSession(), null);

        for (var entry : results) {
            if (entry.toolMessage() != null) {
                context.addMessages(entry.toolMessage());
            }
        }
    }

    /**
     * Initialize model context.
     */
    private ModelContext initContext(Session session) {
        ModelContext context;
        if (config.getContextProcessors() != null) {
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
        String userInput = normalizeUserInput(inputs);
        String conversationId = null;
        if (inputs instanceof Map<?, ?> map) {
            conversationId = map.containsKey("conversation_id")
                    ? String.valueOf(map.get("conversation_id")) : null;
        }

        InvokeInputs invokeInputs = InvokeInputs.builder()
                .query(userInput)
                .conversationId(conversationId)
                .build();

        AgentCallbackContext ctx = AgentCallbackContext.builder()
                .agent(this)
                .inputs(invokeInputs)
                .config(config)
                .session(session)
                .build();
        Object invokeLifecycleInputs = ctx.getInputs();

        // Fire BEFORE_INVOKE
        fireCallbackEvent(AgentCallbackEvent.BEFORE_INVOKE, ctx);

        try {
            // Read query after before_invoke (rail may modify)
            String query = ((InvokeInputs) ctx.getInputs()).getQuery();

            // Get or create model context
            ModelContext context = initContext(session);
            ctx.setContext(context);

            // Add user message to context
            context.addMessages(new UserMessage(query));

            // Get tool info
            List<ToolInfo> tools = getAbilityManager().listToolInfo();

            // ReAct loop
            for (int iteration = 0; iteration < config.getMaxIterations(); iteration++) {
                Loggers.AGENT.info(
                        "ReAct iteration " + (iteration + 1) + "/" + config.getMaxIterations());

                AssistantMessage aiMessage = prepareModelCall(ctx, query, context, tools);

                context.addMessages(AssistantMessage.builder()
                        .content(aiMessage.getContent())
                        .toolCalls(aiMessage.getToolCalls())
                        .build());

                if (aiMessage.getToolCalls() != null && !aiMessage.getToolCalls().isEmpty()) {
                    prepareToolCall(ctx, aiMessage.getToolCalls(), context);
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

            if (agentSession != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("output", finalResult);
                payload.put("result_type", "answer");
                agentSession.writeStream(new OutputSchema("answer", 0, payload));
            }
        } catch (Exception e) {
            Loggers.AGENT.error("ReActAgentEvolve stream error: " + e.getMessage());
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
     * Register a skill.
     */
    public void registerSkill(Object skillPath) {
        if (getSkillUtil() != null) {
            getSkillUtil().registerSkills(skillPath, this);
        }
    }

    private AgentSessionApi toAgentSession(Session session) {
        if (session == null) {
            return null;
        }
        if (session instanceof AgentSessionApi asa) {
            return asa;
        }
        return AgentSessionApi.create(session.getSessionId(), null, getCard());
    }

    private static String normalizeUserInput(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            Object query = map.get("query");
            if (query == null) {
                throw new IllegalArgumentException("Input dict must contain 'query'");
            }
            return String.valueOf(query);
        }
        if (inputs instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("Input must be dict with 'query' or String");
    }

    private static boolean safeEquals(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}
