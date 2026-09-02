/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.Controller;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.Result;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.ControllerAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Workflow-based Agent - Executes predefined workflows with multi-workflow controller.
 * Implemented using ControllerAgent with WorkflowEventHandler for
 * workflow-specific execution logic including intent detection and
 * interruption handling.
 * 
 * @since 0.1.7
 */
public class WorkflowAgent extends ControllerAgent {
    private static final String CALL_MODE_STATE_KEY = "__workflow_agent_call_mode";

    private final WorkflowAgentConfig agentConfig;

    private final Map<String, Workflow> registeredWorkflowProviders = new LinkedHashMap<>();

    /**
     * Create WorkflowAgent with the given configuration.
     * 
     * @param agentConfig the workflow agent configuration
     * @since 0.1.7
     */
    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(buildAgentCard(agentConfig), new Controller(), buildControllerConfig(),
                buildContextEngineConfig(agentConfig));
        if (agentConfig.getControllerType() != null
                && agentConfig.getControllerType() != ControllerType.WORKFLOW_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "WorkflowAgent requires WORKFLOW_CONTROLLER, got " + agentConfig.getControllerType());
        }
        this.agentConfig = agentConfig;

        // Set up the WorkflowEventHandler on the controller
        WorkflowEventHandler eventHandler = new WorkflowEventHandler(agentConfig, getContextEngine());
        getController().setEventHandler(eventHandler);
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
    public ControllerOutput invoke(Object inputs, Session session) {
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }
        setCallMode(effectiveSession, "invoke");
        try {
            return normalizeInvokeOutput(super.invoke(inputs, effectiveSession));
        } finally {
            clearCallMode(effectiveSession);
            if (managedSession != null) {
                managedSession.postRun();
            }
        }
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
        AgentSessionApi managedSession = session == null ? createManagedSession(inputs, streamModes) : null;
        Session effectiveSession = managedSession != null ? managedSession : session;

        if (managedSession != null) {
            managedSession.preRun(inputs);
        }
        setCallMode(effectiveSession, "stream");
        Iterator<Object> delegate = super.stream(inputs, effectiveSession, streamModes);
        return new Iterator<>() {
            private boolean finalized;
            @Override
            public boolean hasNext() {
                boolean hasNext = delegate.hasNext();
                if (!hasNext) {
                    finalizeStream();
                }
                return hasNext;
            }

            @Override
            public Object next() {
                try {
                    return delegate.next();
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
                clearCallMode(effectiveSession);
                if (managedSession != null) {
                    managedSession.postRun();
                }
            }
        };
    }

    /**
     * getAgentConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    /**
     * Set prompt template and keep it on the application config for Python compatibility.
     * 
     * @param promptTemplate promptTemplate
     * @since 0.1.7
     */
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate != null ? promptTemplate : new ArrayList<>());
    }

    /**
     * Append prompt template entries, mirroring Python's {@code add_prompt()}.
     * 
     * @param promptTemplate promptTemplate
     * @since 0.1.7
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

    /**
     * Add tools to this agent (update config, ability manager, and resource manager).
     * Mirrors Python's {@code BaseAgent.add_tools()} behavior used by workflow-agent tests.
     * 
     * @param tools tools
     * @since 0.1.7
     */
    public void addTools(List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        for (Tool tool : tools) {
            if (tool == null || tool.getCard() == null) {
                continue;
            }
            getAbilityManager().add(tool.getCard());
            Runner.resourceMgr().addTool(tool, getCard().getId());
            if (agentConfig.getTools() != null && !agentConfig.getTools().contains(tool.getCard().getName())) {
                agentConfig.getTools().add(tool.getCard().getName());
            }
        }
    }

    /**
     * addWorkflows.
     * 
     * @param workflows workflows
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public void addWorkflows(List<Workflow> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        Map<String, WorkflowSchema> configuredWorkflowSchemas = new LinkedHashMap<>();
        for (WorkflowSchema schema : agentConfig.getWorkflows()) {
            String workflowResourceId = WorkflowUtils.generateWorkflowKey(schema.getId(), schema.getVersion());
            configuredWorkflowSchemas.putIfAbsent(workflowResourceId, schema);
        }
        Map<String, Workflow> uniqueWorkflows = new LinkedHashMap<>();
        for (Workflow workflow : workflows) {
            WorkflowCard card = workflow.getCard();
            String workflowResourceId = WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion());
            uniqueWorkflows.putIfAbsent(workflowResourceId, workflow);
        }
        String agentId = getCard() != null ? getCard().getId() : null;
        boolean canRegisterWorkflowResource = agentId != null && !agentId.isBlank();
        for (Map.Entry<String, Workflow> entry : uniqueWorkflows.entrySet()) {
            String workflowResourceId = entry.getKey();
            Workflow workflow = entry.getValue();
            if (registeredWorkflowProviders.containsKey(workflowResourceId)) {
                continue;
            }
            WorkflowCard card = workflow.getCard();
            if (canRegisterWorkflowResource) {
                WorkflowCard resourceCard =
                    WorkflowCard.builder().id(workflowResourceId).name(card.getName()).version(card.getVersion())
                            .description(card.getDescription()).inputParams(card.getInputParams()).build();
                Result<WorkflowCard> registration =
                        Runner.resourceMgr().addWorkflow(resourceCard, () -> workflow, agentId);
                if (registration.isError()) {
                    continue;
                }
            }
            getAbilityManager().add(card);
            if (!configuredWorkflowSchemas.containsKey(workflowResourceId)) {
                WorkflowSchema schema =
                        WorkflowSchema.builder().id(card.getId()).name(card.getName()).version(card.getVersion())
                                .description(card.getDescription())
                                .inputParams(card.getInputParams() instanceof Map
                                        ? (Map<String, Object>) card.getInputParams()
                                        : Map.of())
                                .build();
                agentConfig.getWorkflows().add(schema);
                configuredWorkflowSchemas.put(workflowResourceId, schema);
            }
            registeredWorkflowProviders.put(workflowResourceId, workflow);
        }
    }

    // ==================== Private Helpers ====================

    /**
     * buildAgentCard.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static AgentCard buildAgentCard(WorkflowAgentConfig config) {
        return AgentCard.builder().id(config.getId()).name(config.getId()).description(config.getDescription()).build();
    }

    /**
     * buildControllerConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    private static ControllerConfig buildControllerConfig() {
        ControllerConfig cc = new ControllerConfig();
        cc.setMaxConcurrentTasks(1);
        return cc;
    }

    /**
     * buildContextEngineConfig.
     * 
     * @param config config
     * @return the result
     * @since 0.1.7
     */
    private static ContextEngineConfig buildContextEngineConfig(WorkflowAgentConfig config) {
        if (config.getContextEngineConfig() != null) {
            return config.getContextEngineConfig();
        }
        int maxRounds = config.getConstrain() != null ? config.getConstrain().getReservedMaxChatRounds() : 10;
        return ContextEngineConfig.builder().maxContextMessageNum(maxRounds * 2).defaultWindowRoundNum(maxRounds)
                .build();
    }

    /**
     * createManagedSession.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private AgentSessionApi createManagedSession(Object inputs) {
        return createManagedSession(inputs, null);
    }

    /**
     * createManagedSession.
     * 
     * @param inputs inputs
     * @param streamModes streamModes
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * normalizeInvokeOutput.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private ControllerOutput normalizeInvokeOutput(ControllerOutput result) {
        if (result == null) {
            return null;
        }
        List<Object> outputs = flattenControllerOutputs(result.getData());
        if (outputs.isEmpty()) {
            return result;
        }

        OutputSchema terminalOutput = null;
        for (int i = outputs.size() - 1; i >= 0; i--) {
            Object output = outputs.get(i);
            if (output instanceof OutputSchema outputSchema && isTerminalOutput(outputSchema)) {
                terminalOutput = outputSchema;
                break;
            }
        }
        if (terminalOutput == null) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("output", new WorkflowOutput(null, WorkflowExecutionState.COMPLETED));
            normalized.put("result_type", "answer");
            return new ControllerOutput(result.getType(), normalized);
        }

        if ("cancelled".equals(terminalOutput.getType())
                && terminalOutput.getPayload() instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cancellation = (Map<String, Object>) payloadMap;
            return new ControllerOutput(result.getType(), cancellation);
        }
        if ("__interaction__".equals(terminalOutput.getType())) {
            int terminalIndex = terminalOutput.getIndex();
            List<Object> interactionOutputs = outputs.stream()
                    .filter(OutputSchema.class::isInstance)
                    .map(OutputSchema.class::cast)
                    .filter(output -> "__interaction__".equals(output.getType())
                            && output.getIndex() == terminalIndex)
                    .map(Object.class::cast)
                    .toList();
            return new ControllerOutput(result.getType(), interactionOutputs);
        }

        Object payload = terminalOutput.getPayload();
        if ("answer".equals(terminalOutput.getType()) && payload instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> answer = (Map<String, Object>) payloadMap;
            return new ControllerOutput(result.getType(), answer);
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("output", new WorkflowOutput(payload, WorkflowExecutionState.COMPLETED));
        normalized.put("result_type", "answer");
        return new ControllerOutput(result.getType(), normalized);
    }

    private static boolean isTerminalOutput(OutputSchema output) {
        return "cancelled".equals(output.getType())
                || "__interaction__".equals(output.getType())
                || "workflow_final".equals(output.getType())
                || "answer".equals(output.getType());
    }

    /**
     * flattenControllerOutputs.
     * 
     * @param rawData rawData
     * @return the result
     * @since 0.1.7
     */
    private List<Object> flattenControllerOutputs(Object rawData) {
        if (!(rawData instanceof List<?> rawList)) {
            return List.of();
        }
        List<Object> flattened = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof ControllerOutputChunk chunk) {
                Object payload = chunk.getPayload();
                if (payload != null) {
                    flattened.add(payload);
                }
            } else {
                flattened.add(item);
            }
        }
        return flattened;
    }

    /**
     * setCallMode.
     * 
     * @param session session
     * @param mode mode
     * @since 0.1.7
     */
    private void setCallMode(Session session, String mode) {
        if (session instanceof AgentSessionApi agentSession) {
            agentSession.updateState(Map.of(CALL_MODE_STATE_KEY, mode));
        }
    }

    /**
     * clearCallMode.
     * 
     * @param session session
     * @since 0.1.7
     */
    private void clearCallMode(Session session) {
        if (session instanceof AgentSessionApi agentSession) {
            Map<String, Object> stateUpdate = new LinkedHashMap<>();
            stateUpdate.put(CALL_MODE_STATE_KEY, null);
            agentSession.updateState(stateUpdate);
        }
    }
}
