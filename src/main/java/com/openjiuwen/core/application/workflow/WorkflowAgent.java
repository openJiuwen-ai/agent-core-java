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

import java.util.Iterator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Workflow-based Agent - Executes predefined workflows with multi-workflow controller.
 *
 * <p>Implemented using ControllerAgent with WorkflowEventHandler for
 * workflow-specific execution logic including intent detection and
 * interruption handling.</p>
 *
 * <p>Mirrors Python's {@code WorkflowAgent} in {@code openjiuwen.core.application.workflow_agent}.</p>
 */
public class WorkflowAgent extends ControllerAgent {

    private static final String CALL_MODE_STATE_KEY = "__workflow_agent_call_mode";

    private final WorkflowAgentConfig agentConfig;

    /**
     * Create WorkflowAgent with the given configuration.
     *
     * @param agentConfig the workflow agent configuration
     */
    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(buildAgentCard(agentConfig), new Controller(), buildControllerConfig(),
                buildContextEngineConfig(agentConfig));
        if (agentConfig.getControllerType() != null
                && agentConfig.getControllerType() != ControllerType.WORKFLOW_CONTROLLER) {
            throw new UnsupportedOperationException(
                    "WorkflowAgent requires WORKFLOW_CONTROLLER, got " + agentConfig.getControllerType()
            );
        }
        this.agentConfig = agentConfig;

        // Set up the WorkflowEventHandler on the controller
        WorkflowEventHandler eventHandler = new WorkflowEventHandler(agentConfig, getContextEngine());
        getController().setEventHandler(eventHandler);
    }

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

    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    /**
     * Set prompt template and keep it on the application config for Python compatibility.
     */
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate != null ? promptTemplate : new ArrayList<>());
    }

    /**
     * Append prompt template entries, mirroring Python's {@code add_prompt()}.
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
     * Add workflows to this agent (update config and resource manager).
     * Mirrors Python's {@code BaseAgent.add_workflows()}.
     */
    @SuppressWarnings("unchecked")
    public void addWorkflows(List<Workflow> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return;
        }
        String agentId = getCard() != null ? getCard().getId() : null;
        boolean canRegisterWorkflowResource = agentId != null && !agentId.isBlank();
        for (Workflow workflow : workflows) {
            WorkflowCard card = workflow.getCard();
            getAbilityManager().add(card);
            agentConfig.getWorkflows().add(WorkflowSchema.builder()
                    .id(card.getId())
                    .name(card.getName())
                    .version(card.getVersion())
                    .description(card.getDescription())
                    .inputParams(card.getInputParams() instanceof Map
                            ? (Map<String, Object>) card.getInputParams() : Map.of())
                    .build());
            String workflowResourceId = WorkflowUtils.generateWorkflowKey(card.getId(), card.getVersion());
            WorkflowCard resourceCard = WorkflowCard.builder()
                    .id(workflowResourceId)
                    .name(card.getName())
                    .version(card.getVersion())
                    .description(card.getDescription())
                    .inputParams(card.getInputParams())
                    .build();
            if (canRegisterWorkflowResource) {
                Runner.resourceMgr().addWorkflow(resourceCard, () -> workflow, agentId);
            }
        }
    }

    // ==================== Private Helpers ====================

    private static AgentCard buildAgentCard(WorkflowAgentConfig config) {
        return AgentCard.builder()
                .id(config.getId())
                .name(config.getId())
                .description(config.getDescription())
                .build();
    }

    private static ControllerConfig buildControllerConfig() {
        ControllerConfig cc = new ControllerConfig();
        cc.setMaxConcurrentTasks(1);
        return cc;
    }

    private static ContextEngineConfig buildContextEngineConfig(WorkflowAgentConfig config) {
        if (config.getContextEngineConfig() != null) {
            return config.getContextEngineConfig();
        }
        int maxRounds = config.getConstrain() != null ? config.getConstrain().getReservedMaxChatRounds() : 10;
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

    private ControllerOutput normalizeInvokeOutput(ControllerOutput result) {
        if (result == null) {
            return null;
        }
        List<Object> outputs = flattenControllerOutputs(result.getData());
        if (outputs.isEmpty()) {
            return result;
        }

        List<Object> interactionOutputs = outputs.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .filter(os -> "__interaction__".equals(os.getType()))
                .map(Object.class::cast)
                .toList();
        if (!interactionOutputs.isEmpty()) {
            return new ControllerOutput(result.getType(), interactionOutputs);
        }

        OutputSchema finalAnswer = null;
        for (int i = outputs.size() - 1; i >= 0; i--) {
            Object output = outputs.get(i);
            if (output instanceof OutputSchema os
                    && ("workflow_final".equals(os.getType()) || "answer".equals(os.getType()))) {
                finalAnswer = os;
                break;
            }
        }
        if (finalAnswer == null) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("output", new WorkflowOutput(null, WorkflowExecutionState.COMPLETED));
            normalized.put("result_type", "answer");
            return new ControllerOutput(result.getType(), normalized);
        }

        Object payload = finalAnswer.getPayload();
        if ("answer".equals(finalAnswer.getType()) && payload instanceof Map<?, ?> payloadMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedPayload = (Map<String, Object>) payloadMap;
            return new ControllerOutput(result.getType(), typedPayload);
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("output", new WorkflowOutput(payload, WorkflowExecutionState.COMPLETED));
        normalized.put("result_type", "answer");
        return new ControllerOutput(result.getType(), normalized);
    }

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

    private void setCallMode(Session session, String mode) {
        if (session instanceof AgentSessionApi agentSession) {
            agentSession.updateState(Map.of(CALL_MODE_STATE_KEY, mode));
        }
    }

    private void clearCallMode(Session session) {
        if (session instanceof AgentSessionApi agentSession) {
            Map<String, Object> stateUpdate = new LinkedHashMap<>();
            stateUpdate.put(CALL_MODE_STATE_KEY, null);
            agentSession.updateState(stateUpdate);
        }
    }
}
