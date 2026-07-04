/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backward-compatible facade for the 0.1.12 workflow agent package.
 *
 * <p>Mirrors Python's {@code WorkflowAgent} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_agent.py}.</p>
 */
public class WorkflowAgent extends com.openjiuwen.core.application.workflow_agent.WorkflowAgent {

    private final WorkflowAgentConfig agentConfig;

    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(toLegacyConfig(agentConfig));
        this.agentConfig = agentConfig == null ? WorkflowAgentConfig.builder().build() : agentConfig;
    }

    @Override
    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    public ControllerOutput invoke(Object inputs, Session session) {
        Object result = super.invoke(toInputMap(inputs), null).toCompletableFuture().join();
        return toControllerOutput(result);
    }

    public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
        return super.stream(toInputMap(inputs), null, streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes);
    }

    public Iterator<Object> stream(Object inputs, Session session) {
        return stream(inputs, session, List.of(StreamMode.OUTPUT));
    }

    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate == null ? List.of() : promptTemplate);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addPrompt(List promptTemplate) {
        if (promptTemplate == null || promptTemplate.isEmpty()) {
            return;
        }
        for (Object item : promptTemplate) {
            if (item instanceof Map<?, ?> map) {
                Map<String, String> prompt = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    prompt.put(String.valueOf(entry.getKey()),
                            entry.getValue() == null ? null : String.valueOf(entry.getValue()));
                }
                agentConfig.getPromptTemplate().add(prompt);
            }
        }
    }

    private static com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig toLegacyConfig(
            WorkflowAgentConfig source) {
        WorkflowAgentConfig effective = source == null ? WorkflowAgentConfig.builder().build() : source;
        com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig target =
                new com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig();
        target.setId(effective.getId());
        target.setVersion(effective.getVersion());
        target.setDescription(effective.getDescription());
        target.setModel(effective.getModel());
        target.setControllerType(effective.getControllerType());
        target.setTools(effective.getTools());
        target.setConstrain(effective.getConstrain());
        target.setWorkflows(effective.getWorkflows().stream()
                .map(WorkflowAgent::toLegacyWorkflowSchema)
                .toList());
        target.setStartWorkflow(toLegacyWorkflowSchema(effective.getStartWorkflow()));
        target.setEndWorkflow(toLegacyWorkflowSchema(effective.getEndWorkflow()));
        target.setGlobalVariables(effective.getGlobalVariables());
        target.setGlobalParams(effective.getGlobalParams());
        target.setDefaultResponse(toControllerDefaultResponse(effective.getDefaultResponse()));
        return target;
    }

    private static com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema toLegacyWorkflowSchema(
            WorkflowSchema source) {
        if (source == null) {
            return new com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema();
        }
        return com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema.builder()
                .id(source.getId())
                .name(source.getName())
                .version(source.getVersion())
                .description(source.getDescription())
                .inputs(source.getInputParams() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(
                        source.getInputParams()))
                .build();
    }

    private static com.openjiuwen.core.controller.ControllerConfig.DefaultResponse toControllerDefaultResponse(
            DefaultResponse source) {
        if (source == null) {
            return new com.openjiuwen.core.controller.ControllerConfig.DefaultResponse();
        }
        return new com.openjiuwen.core.controller.ControllerConfig.DefaultResponse(source.getType(), source.getText());
    }

    private static Map<String, Object> toInputMap(Object inputs) {
        if (inputs instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typed;
        }
        return Map.of("input", inputs);
    }

    private static ControllerOutput toControllerOutput(Object result) {
        if (result instanceof ControllerOutput controllerOutput) {
            return controllerOutput;
        }
        return new ControllerOutput(EventType.TASK_COMPLETION, List.of());
    }
}
