/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Backward-compatible facade for the 0.1.12 workflow agent package.
 *
 * <p>Mirrors Python's {@code WorkflowAgent} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_agent.py}.</p>
 */
public class WorkflowAgent extends com.openjiuwen.core.application.workflow_agent.WorkflowAgent {

    private final WorkflowAgentConfig agentConfig;
    private final AbilityManager abilityManager = new AbilityManager();
    private final AgentCard card;

    public WorkflowAgent(WorkflowAgentConfig agentConfig) {
        super(toLegacyConfig(agentConfig));
        this.agentConfig = agentConfig == null ? WorkflowAgentConfig.builder().build() : agentConfig;
        this.card = toAgentCard(this.agentConfig);
        this.abilityManager.setContextEngine(getContextEngine());
    }

    @Override
    public WorkflowAgentConfig getAgentConfig() {
        return agentConfig;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    @Override
    public com.openjiuwen.core.context.ContextEngine getContextEngine() {
        return (com.openjiuwen.core.context.ContextEngine) super.getContextEngine();
    }

    public AbilityManager get_ability_manager() {
        return abilityManager;
    }

    public AgentCard getCard() {
        return card;
    }

    @Override
    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
        try {
            ControllerOutput output = invoke((Object) inputs, session);
            Map<String, Object> data = directInvokeMapView(output);
            if (data != null) {
                return new MapCompletedStage(output, data);
            }
            return CompletableFuture.completedFuture(output);
        } catch (RuntimeException error) {
            return MapCompletedStage.failed(error);
        }
    }

    private static Map<String, Object> directInvokeMapView(ControllerOutput output) {
        if (output == null) {
            return null;
        }
        Map<String, Object> data = output.getDataAsMap();
        if (data != null) {
            Map<String, Object> interactionView = interactionMapView(data);
            if (interactionView != null) {
                return interactionView;
            }
            return data;
        }
        Object rawData = normalizeCompletedStages(output.getData());
        if (rawData instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            Map<String, Object> interactionView = interactionMapView(result);
            if (interactionView != null) {
                return interactionView;
            }
            return result;
        }
        if (rawData instanceof List<?> list) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("interaction", list);
            return result;
        }
        return null;
    }

    private static Map<String, Object> interactionMapView(Map<String, Object> data) {
        Object workflowOutput = data.get("output");
        if (!(workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput)) {
            return null;
        }
        Object result = normalizeCompletedStages(typedOutput.getResult());
        if (result instanceof OutputSchema outputSchema) {
            return Map.of("interaction", List.of(outputSchema));
        }
        if (result instanceof List<?> list && list.stream().anyMatch(OutputSchema.class::isInstance)) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("interaction", list);
            return view;
        }
        return null;
    }

    public ControllerOutput invoke(Object inputs, AgentSessionApi session) {
        Map<String, Object> inputMap = toInputMap(inputs);
        Object result = super.invoke(inputMap, session).toCompletableFuture().join();
        ControllerOutput output = toControllerOutput(result);
        recordContextMessages(inputMap, output, session);
        return output;
    }

    public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return super.stream(toInputMap(inputs), session, streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes);
    }

    public Iterator<Object> stream(Object inputs, AgentSessionApi session) {
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

    @Override
    public void addWorkflows(List<?> incomingWorkflows) {
        super.addWorkflows(incomingWorkflows);
        Object controller = getController();
        if (controller instanceof com.openjiuwen.core.application.workflow_agent.WorkflowController workflowController) {
            workflowController.setupFromAgent(this);
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

    private static AgentCard toAgentCard(WorkflowAgentConfig source) {
        return new AgentCard(valueOrEmpty(source.getId()), valueOrEmpty(source.getId()),
                valueOrEmpty(source.getDescription()));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private void recordContextMessages(Map<String, Object> inputs, ControllerOutput output, AgentSessionApi session) {
        String sessionId = resolveSessionId(inputs, session);
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        com.openjiuwen.core.context.ModelContext context = getContextEngine().getContext(null, sessionId);
        if (context == null) {
            return;
        }
        Object query = inputs.get("query");
        Object response = extractWorkflowResponse(output);
        if (query != null) {
            addVisibleMessage(context, new UserMessage(String.valueOf(query)));
        }
        if (response != null) {
            addVisibleMessage(context, new AssistantMessage(String.valueOf(response)));
        }
    }

    private static void addVisibleMessage(com.openjiuwen.core.context.ModelContext context,
                                          com.openjiuwen.core.foundation.llm.schema.BaseMessage message) {
        context.addMessages(message).toCompletableFuture().join();
        if (message.getMetadata() != null) {
            message.getMetadata().remove("context_message_id");
        }
    }

    private static String resolveSessionId(Map<String, Object> inputs, AgentSessionApi session) {
        if (session != null && session.getSessionId() != null) {
            return session.getSessionId();
        }
        Object conversationId = inputs.get("conversation_id");
        return conversationId == null ? "default_session" : String.valueOf(conversationId);
    }

    private static Object extractWorkflowResponse(ControllerOutput output) {
        if (output == null || output.getDataAsMap() == null) {
            return null;
        }
        Object workflowOutput = output.getDataAsMap().get("output");
        if (workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput) {
            return extractResponseValue(typedOutput.getResult());
        }
        return null;
    }

    private static Object extractResponseValue(Object result) {
        if (result instanceof Map<?, ?> map) {
            Object response = map.get("response");
            return response != null ? response : result;
        }
        return result;
    }

    private static ControllerOutput toControllerOutput(Object result) {
        result = normalizeCompletedStages(result);
        if (result instanceof ControllerOutput controllerOutput) {
            return controllerOutput;
        }
        if (result instanceof Iterable<?> iterable && !(result instanceof Map<?, ?>)) {
            List<Object> chunks = new java.util.ArrayList<>();
            iterable.forEach(chunks::add);
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), chunks);
        }
        if (result instanceof OutputSchema) {
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), List.of(result));
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), typed);
        }
        return new ControllerOutput(EventType.TASK_COMPLETION.getValue(), result);
    }

    private static Object normalizeCompletedStages(Object value) {
        if (value instanceof CompletionStage<?> stage) {
            return normalizeCompletedStages(stage.toCompletableFuture().join());
        }
        if (value instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            return new com.openjiuwen.core.workflow.WorkflowOutput(
                    normalizeCompletedStages(workflowOutput.getResult()),
                    workflowOutput.getState());
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                normalized.put(String.valueOf(entry.getKey()), normalizeCompletedStages(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Iterable<?> iterable && !(value instanceof String)) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : iterable) {
                normalized.add(normalizeCompletedStages(item));
            }
            return normalized;
        }
        return value;
    }

    private static final class MapCompletedStage extends CompletableFuture<Object> implements Map<String, Object> {
        private final Map<String, Object> delegate = new LinkedHashMap<>();

        private MapCompletedStage() {
        }

        private MapCompletedStage(ControllerOutput output, Map<String, Object> value) {
            delegate.putAll(value);
            complete(output);
        }

        private static MapCompletedStage failed(Throwable error) {
            MapCompletedStage stage = new MapCompletedStage();
            stage.completeExceptionally(error);
            return stage;
        }

        private void throwIfFailed() {
            if (isCompletedExceptionally()) {
                join();
            }
        }

        @Override
        public int size() {
            throwIfFailed();
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            throwIfFailed();
            return delegate.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            throwIfFailed();
            return delegate.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            throwIfFailed();
            return delegate.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            throwIfFailed();
            return delegate.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            throwIfFailed();
            return delegate.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            throwIfFailed();
            return delegate.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ?> map) {
            throwIfFailed();
            delegate.putAll(map);
        }

        @Override
        public void clear() {
            throwIfFailed();
            delegate.clear();
        }

        @Override
        public Set<String> keySet() {
            throwIfFailed();
            return delegate.keySet();
        }

        @Override
        public Collection<Object> values() {
            throwIfFailed();
            return delegate.values();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            throwIfFailed();
            return delegate.entrySet();
        }

        @Override
        public boolean equals(Object other) {
            throwIfFailed();
            return delegate.equals(other);
        }

        @Override
        public int hashCode() {
            throwIfFailed();
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            if (isCompletedExceptionally()) {
                return "MapCompletedStage[failed]";
            }
            return delegate.toString();
        }
    }
}
