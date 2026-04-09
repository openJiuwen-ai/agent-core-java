  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.application.llm;

import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.PluginSchema;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;

/**
 * LLM Agent - ReAct style Agent based on ControllerAgent.
 *
 * <p>Core features:
 * <ol>
 *   <li>Inherits ControllerAgent, holds LlmController (via EventHandler)</li>
 *   <li>Supports LLM reasoning to generate task plans</li>
 *   <li>Supports multi-round conversations and task execution</li>
 *   <li>Optionally writes conversation messages to long-term memory</li>
 * </ol>
 *
 * <p>Mirrors Python's {@code LLMAgent} in {@code openjiuwen.core.application.llm_agent}.
 */
public class LlmAgent extends ControllerAgent {

    private final LlmAgentConfig agentConfig;
    private final LongTermMemory longTermMemoryInstance;
    private final boolean enableMemory;

    /**
     * Create LlmAgent with the given configuration.
     *
     * @param agentConfig the LLM agent configuration
     */
    public LlmAgent(LlmAgentConfig agentConfig) {
        super(buildAgentCard(agentConfig), new Controller(), buildControllerConfig(agentConfig),
                buildContextEngineConfig(agentConfig));
        if (agentConfig.getControllerType() != null
                && agentConfig.getControllerType() != ControllerType.REACT_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "LlmAgent requires REACT_CONTROLLER, got " + agentConfig.getControllerType()
            );
        }
        this.agentConfig = agentConfig;
        this.longTermMemoryInstance = LongTermMemory.getInstance();

        String memoryScopeId = agentConfig.getMemoryScopeId();
        this.enableMemory = memoryScopeId != null && !memoryScopeId.isEmpty()
                && (agentConfig.getAgentMemoryConfig().isEnableLongTermMem()
                || !agentConfig.getAgentMemoryConfig().getMemVariables().isEmpty());

        // Set up the LlmEventHandler on the controller
        LlmEventHandler eventHandler = new LlmEventHandler(agentConfig, getContextEngine());
        getController().setEventHandler(eventHandler);
    }

    @Override
    public ControllerOutput invoke(Object inputs, Session session) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }

        ControllerOutput result;
        try {
            result = super.invoke(inputs, effectiveSession);
        } finally {
            if (managedSession != null) {
                managedSession.postRun();
            }
        }

        if (enableMemory && inputs instanceof Map<?, ?> inputMap) {
            writeMessagesToMemoryAsync(inputMap, result);
        }

        return result;
    }

    @Override
    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs, streamModes) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }

        Iterator<Object> streamIter = super.stream(inputs, effectiveSession, streamModes);
        List<String> answerOutputs = new ArrayList<>();

        return new Iterator<>() {
            private boolean finalized;

            @Override
            public boolean hasNext() {
                boolean hasNext = streamIter.hasNext();
                if (!hasNext) {
                    finalizeStream();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                try {
                    Object item = streamIter.next();
                    String answer = extractAnswerOutput(item);
                    if (!answer.isEmpty()) {
                        answerOutputs.add(answer);
                    }
                    return item;
                } catch (NoSuchElementException e) {
                    finalizeStream();
                    throw e;
                }
            }

            private void finalizeStream() {
                if (finalized) {
                    return;
                }
                finalized = true;
                if (enableMemory && inputs instanceof Map<?, ?> inputMap) {
                    writeMessagesToMemoryAsync(inputMap, String.join("", answerOutputs));
                }
                if (managedSession != null) {
                    managedSession.postRun();
                }
            }
        };
    }

    /**
     * Set prompt template and propagate to controller.
     *
     * @param promptTemplate new prompt template
     */
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate);
        Controller ctrl = getController();
        if (ctrl.getEventHandler() instanceof LlmEventHandler llmHandler) {
            llmHandler.setPromptTemplate(promptTemplate);
        }
    }

    /**
     * Append prompt template entries, mirroring the legacy BaseAgent API.
     */
    public void addPrompt(List<Map<String, String>> promptTemplate) {
        if (promptTemplate == null || promptTemplate.isEmpty()) {
            return;
        }
        List<Map<String, String>> merged = agentConfig.getPromptTemplate() == null
                ? new ArrayList<>()
                : new ArrayList<>(agentConfig.getPromptTemplate());
        merged.addAll(promptTemplate);
        setPromptTemplate(merged);
    }

    public LlmAgentConfig getAgentConfig() {
        return agentConfig;
    }

    /**
     * Backward-compatible factory mirroring Python's {@code create_llm_agent_config(...)}.
     */
    public static LlmAgentConfig createLlmAgentConfig(
            String agentId,
            String agentVersion,
            String description,
            List<com.openjiuwen.core.application.schema.WorkflowSchema> workflows,
            List<com.openjiuwen.core.application.schema.PluginSchema> plugins,
            ModelConfig model,
            List<Map<String, String>> promptTemplate,
            List<String> tools
    ) {
        return LlmAgentConfig.builder()
                .id(agentId)
                .version(agentVersion)
                .description(description)
                .workflows(workflows != null ? workflows : List.of())
                .plugins(plugins != null ? plugins : List.of())
                .model(model)
                .promptTemplate(promptTemplate != null ? promptTemplate : List.of())
                .tools(tools != null ? tools : List.of())
                .build();
    }

    /**
     * Backward-compatible factory mirroring Python's {@code create_llm_agent(...)}.
     */
    public static LlmAgent createLlmAgent(
            LlmAgentConfig agentConfig,
            List<Workflow> workflows,
            List<Tool> tools
    ) {
        LlmAgent agent = new LlmAgent(agentConfig);
        String tag = agentConfig != null ? agentConfig.getId() : null;

        if (workflows != null) {
            for (Workflow workflow : workflows) {
                if (workflow == null || workflow.getCard() == null) {
                    continue;
                }
                registerWorkflowSchema(agentConfig, workflow.getCard());
                agent.getAbilityManager().add(workflow.getCard());
                WorkflowCard card = workflow.getCard();
                String workflowResourceId = WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion());
                WorkflowCard resourceCard = WorkflowCard.builder()
                        .id(workflowResourceId)
                        .name(card.getName())
                        .version(card.getVersion())
                        .description(card.getDescription())
                        .inputParams(card.getInputParams())
                        .build();
                Runner.resourceMgr().addWorkflow(resourceCard, () -> workflow, tag);
            }
        }

        if (tools != null) {
            for (Tool tool : tools) {
                if (tool == null || tool.getCard() == null) {
                    continue;
                }
                registerPluginSchema(agentConfig, tool.getCard());
                agent.getAbilityManager().add(tool.getCard());
                Runner.resourceMgr().addTool(tool, tag);
            }
        }

        return agent;
    }

    private static void registerWorkflowSchema(LlmAgentConfig agentConfig, WorkflowCard card) {
        if (agentConfig == null || card == null) {
            return;
        }
        List<WorkflowSchema> workflows = agentConfig.getWorkflows();
        if (workflows == null) {
            workflows = new ArrayList<>();
        } else {
            workflows = new ArrayList<>(workflows);
        }
        agentConfig.setWorkflows(workflows);
        boolean exists = workflows.stream()
                .anyMatch(schema -> Objects.equals(schema.getId(), card.getId())
                        && Objects.equals(schema.getVersion(), card.getVersion()));
        if (exists) {
            return;
        }
        workflows.add(WorkflowSchema.builder()
                .id(Objects.requireNonNullElse(card.getId(), ""))
                .name(Objects.requireNonNullElse(card.getName(), ""))
                .version(Objects.requireNonNullElse(card.getVersion(), ""))
                .description(Objects.requireNonNullElse(card.getDescription(), ""))
                .inputParams(copyWorkflowInputs(card.getInputParams()))
                .build());
    }

    private static void registerPluginSchema(LlmAgentConfig agentConfig, com.openjiuwen.core.foundation.tool.ToolCard card) {
        if (agentConfig == null || card == null) {
            return;
        }
        List<PluginSchema> plugins = agentConfig.getPlugins();
        if (plugins == null) {
            plugins = new ArrayList<>();
        } else {
            plugins = new ArrayList<>(plugins);
        }
        agentConfig.setPlugins(plugins);
        boolean exists = plugins.stream()
                .anyMatch(schema -> Objects.equals(schema.getId(), card.getId())
                        || Objects.equals(schema.getName(), card.getName()));
        if (exists) {
            return;
        }
        plugins.add(PluginSchema.builder()
                .id(Objects.requireNonNullElse(card.getId(), ""))
                .pluginId(Objects.requireNonNullElse(card.getId(), ""))
                .name(Objects.requireNonNullElse(card.getName(), ""))
                .description(Objects.requireNonNullElse(card.getDescription(), ""))
                .inputs(card.getInputParams() != null
                        ? new LinkedHashMap<>(card.getInputParams())
                        : new LinkedHashMap<>())
                .build());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copyWorkflowInputs(Object inputParams) {
        if (inputParams instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    // ==================== Private Helpers ====================

    private static AgentCard buildAgentCard(LlmAgentConfig config) {
        return AgentCard.builder()
                .id(config.getId())
                .name(Objects.requireNonNullElse(config.getId(), ""))
                .description(Objects.requireNonNullElse(config.getDescription(), ""))
                .build();
    }

    private static ControllerConfig buildControllerConfig(LlmAgentConfig config) {
        ControllerConfig cc = new ControllerConfig();
        cc.setMaxConcurrentTasks(1);
        return cc;
    }

    private static ContextEngineConfig buildContextEngineConfig(LlmAgentConfig config) {
        if (config.getContextEngineConfig() != null) {
            return config.getContextEngineConfig();
        }
        int maxRounds = config.getContextWindowLimit();
        return ContextEngineConfig.builder()
                .maxContextMessageNum(maxRounds * 2)
                .defaultWindowRoundNum(maxRounds)
                .build();
    }

    private AgentSessionApi createManagedSession(Object inputs) {
        return createManagedSession(inputs, null);
    }

    private AgentSessionApi createManagedSession(Object inputs, List<StreamMode> streamModes) {
        String sessionId = "default_session";
        if (inputs instanceof Map<?, ?> inputMap) {
            Object conversationId = inputMap.get("conversation_id");
            if (conversationId instanceof String s && !s.isBlank()) {
                sessionId = s;
            }
        }
        return AgentSessionApi.create(sessionId, null, getCard(), streamModes);
    }

    private void writeMessagesToMemoryAsync(Map<?, ?> inputs, Object result) {
        CompletableFuture.runAsync(() -> {
            try {
                writeMessagesToMemory(inputs, result);
            } catch (Exception e) {
                Loggers.AGENT.error("Add memory failed: {}", e.getMessage());
            }
        });
    }

    private void writeMessagesToMemory(Map<?, ?> inputs, Object result) {
        Object userIdObj = inputs.get("user_id");
        if (userIdObj == null) {
            return;
        }
        String userId = userIdObj.toString();
        String sessionId = inputs.containsKey("conversation_id")
                ? inputs.get("conversation_id").toString() : "default_session";

        List<BaseMessage> messageList = new ArrayList<>();

        // Add user message
        Object queryObj = inputs.get("query");
        if (queryObj instanceof String query && !query.isEmpty()) {
            messageList.add(new UserMessage(query));
        }

        // Add AI response message
        AssistantMessage assistantMessage = convertResponseToMessage(result);
        if (assistantMessage != null && assistantMessage.getContentAsString() != null
                && !assistantMessage.getContentAsString().isEmpty()) {
            messageList.add(assistantMessage);
        }

        if (!messageList.isEmpty()) {
            longTermMemoryInstance.addMessages(
                    messageList, agentConfig.getAgentMemoryConfig(),
                    userId, agentConfig.getMemoryScopeId(), sessionId
            );
        }
    }

    private static String extractAnswerOutput(Object result) {
        if (result instanceof OutputSchema output) {
            Object payload = output.getPayload();
            if (payload instanceof Map<?, ?> payloadMap) {
                if ("answer".equals(payloadMap.get("result_type"))
                        && payloadMap.get("output") instanceof String outputStr) {
                    return outputStr;
                }
            }
        }
        return "";
    }

    private static AssistantMessage convertResponseToMessage(Object result) {
        if (result instanceof OutputSchema output
                && "answer".equals(output.getType())
                && output.getPayload() instanceof Map<?, ?> payload) {
            Object response = payload.get("output");
            if (response instanceof String s && !s.isEmpty()) {
                return new AssistantMessage(s);
            }
        } else if (result instanceof Map<?, ?> map
                && "answer".equals(map.get("result_type"))
                && map.get("output") instanceof String s && !s.isEmpty()) {
            return new AssistantMessage(s);
        } else if (result instanceof String s && !s.isEmpty()) {
            return new AssistantMessage(s);
        } else if (result instanceof ControllerOutput co) {
            List<?> chunks = co.getDataAsChunks();
            if (chunks != null) {
                for (Object chunk : chunks) {
                    AssistantMessage msg = convertResponseToMessage(chunk);
                    if (msg != null) {
                        return msg;
                    }
                }
            }
        }
        return null;
    }
}
