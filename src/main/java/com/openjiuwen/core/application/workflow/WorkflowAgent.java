/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.controller.schema.ControllerOutput;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.TraceWorkflowSpan;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
            Map<String, Object> inputMap = toInputMap(inputs);
            logLegacyModelParams();
            InvocationResult invocation = invokeController(inputMap, session);
            recordContextMessages(inputMap, invocation.output(), invocation.session());
            Map<String, Object> data = directInvokeMapView(invocation.output());
            if (data != null) {
                return new MapCompletedStage(invocation.output(), data);
            }
            return CompletableFuture.completedFuture(invocation.output());
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
        logLegacyModelParams();
        InvocationResult invocation = invokeController(inputMap, session);
        recordContextMessages(inputMap, invocation.output(), invocation.session());
        return invocation.output();
    }

    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        Map<String, Object> inputMap = toInputMap(inputs);
        if (agentConfig.getModel() == null) {
            return super.stream(inputMap, session, streamModes);
        }
        logLegacyModelParams();
        InvocationResult invocation = invokeController(inputMap, session);
        recordContextMessages(inputMap, invocation.output(), invocation.session());
        List<Object> chunks = streamChunksView(invocation);
        return chunks.iterator();
    }

    public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
        return stream(toInputMap(inputs), session, streamModes);
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

    private void logLegacyModelParams() {
        if (agentConfig.getModel() == null || agentConfig.getModel().getModelInfo() == null) {
            return;
        }
        var info = agentConfig.getModel().getModelInfo();
        Loggers.LLM.info("WorkflowAgent model params {\"temperature\":" + decimalText(info.getTemperature())
                + ",\"top_p\":" + info.getTopP()
                + ",\"timeout\":" + decimalText(info.getTimeout())
                + "}");
    }

    private static String decimalText(Number value) {
        return value == null ? "null" : String.format(java.util.Locale.ROOT, "%.1f", value.doubleValue());
    }

    private void recordContextMessages(Map<String, Object> inputs, ControllerOutput output, AgentSessionApi session) {
        String sessionId = resolveSessionId(inputs, session);
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        com.openjiuwen.core.context.ModelContext context = contextFor(null, session);
        if (context == null) {
            return;
        }
        Object query = visibleQuery(inputs.get("query"));
        Object agentResponse = extractAgentContextResponse(output);
        Object workflowResponse = extractWorkflowContextResponse(output);
        if (query != null) {
            addVisibleMessage(context, new UserMessage(String.valueOf(query)));
        }
        if (agentResponse != null) {
            addVisibleMessage(context, new AssistantMessage(String.valueOf(agentResponse)));
        }
        pruneVisibleMessages(context);
        recordWorkflowContextMessages(session, query, workflowResponse);
    }

    private static Object visibleQuery(Object query) {
        if (query instanceof com.openjiuwen.core.session.interaction.InteractiveInput interactiveInput
                && interactiveInput.getUserInputs() != null
                && !interactiveInput.getUserInputs().isEmpty()) {
            return interactiveInput.getUserInputs().values().iterator().next();
        }
        return query;
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

    private static Object extractAgentContextResponse(ControllerOutput output) {
        if (output == null) {
            return null;
        }
        Object data = output.getData();
        if (data instanceof List<?> list) {
            return list.stream()
                    .map(WorkflowAgent::interactionResponse)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        if (output.getDataAsMap() == null) {
            return null;
        }
        Object workflowOutput = output.getDataAsMap().get("output");
        if (workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput) {
            if (typedOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
                return extractResponseValue(typedOutput.getResult());
            }
            return completedAgentResponse(typedOutput.getResult());
        }
        return null;
    }

    private static Object completedAgentResponse(Object result) {
        if (result instanceof Map<?, ?> map && map.containsKey("response")) {
            return map.get("response");
        }
        return "";
    }

    private void recordWorkflowContextMessages(AgentSessionApi session, Object query, Object response) {
        if (session == null) {
            return;
        }
        String workflowId = currentWorkflowId(session);
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        com.openjiuwen.core.context.ModelContext workflowContext = contextFor(workflowId, session);
        if (workflowContext == null) {
            return;
        }
        if (query != null) {
            addVisibleMessage(workflowContext, new UserMessage(String.valueOf(query)));
        }
        if (response != null) {
            addVisibleMessage(workflowContext, new AssistantMessage(String.valueOf(response)));
        }
        pruneVisibleMessages(workflowContext);
    }

    private com.openjiuwen.core.context.ModelContext contextFor(String contextId, AgentSessionApi session) {
        com.openjiuwen.core.context.ModelContext context =
                getContextEngine().getContext(contextId, session.getSessionId());
        return context == null ? getContextEngine().createContext(contextId, session) : context;
    }

    private static Object extractWorkflowContextResponse(ControllerOutput output) {
        if (output == null) {
            return null;
        }
        Object data = output.getData();
        if (data instanceof List<?> list) {
            return list.stream()
                    .map(WorkflowAgent::interactionResponse)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        Map<String, Object> dataMap = output.getDataAsMap();
        if (dataMap == null) {
            return null;
        }
        Object workflowOutput = dataMap.get("output");
        if (workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput) {
            if (typedOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
                return extractResponseValue(typedOutput.getResult());
            }
            return compactJson(typedOutput.getResult());
        }
        return null;
    }

    private static String currentWorkflowId(AgentSessionApi session) {
        Object state = session.getState("workflow_controller");
        if (!(state instanceof Map<?, ?> stateMap)) {
            return null;
        }
        Object current = stateMap.get("current_workflow_id");
        if (current != null) {
            return String.valueOf(current);
        }
        Object interrupted = stateMap.get("interrupted_tasks");
        if (!(interrupted instanceof Map<?, ?> interruptedTasks) || interruptedTasks.isEmpty()) {
            return null;
        }
        Object first = interruptedTasks.values().iterator().next();
        if (!(first instanceof Map<?, ?> info)) {
            return null;
        }
        Object task = info.get("task");
        if (!(task instanceof Map<?, ?> taskMap)) {
            return null;
        }
        Object input = taskMap.get("input");
        if (!(input instanceof Map<?, ?> inputMap)) {
            return null;
        }
        Object targetId = inputMap.get("targetId");
        return targetId == null ? null : String.valueOf(targetId);
    }

    private void pruneVisibleMessages(com.openjiuwen.core.context.ModelContext context) {
        int maxRounds = agentConfig == null || agentConfig.getConstrain() == null
                ? 0
                : agentConfig.getConstrain().getReservedMaxChatRounds();
        if (maxRounds <= 0) {
            return;
        }
        List<com.openjiuwen.core.foundation.llm.schema.BaseMessage> messages = context.getMessages();
        int maxMessages = maxRounds * 2;
        if (messages.size() <= maxMessages) {
            return;
        }
        context.setMessages(new ArrayList<>(messages.subList(messages.size() - maxMessages, messages.size())));
    }

    private static String compactJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            return String.valueOf(value);
        }
    }

    private static Object interactionResponse(Object item) {
        if (!(item instanceof OutputSchema outputSchema)
                || !com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(outputSchema.getType())) {
            return null;
        }
        return interactionPayloadField(outputSchema.getPayload(), "value");
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

    private InvocationResult invokeController(Map<String, Object> inputMap, AgentSessionApi session) {
        AgentSession createdSession = null;
        AgentSessionApi activeSession = session;
        if (activeSession == null) {
            createdSession = createCompatibilitySession(inputMap);
            activeSession = createdSession;
        }
        activeSession.updateState(Map.of("__workflow_interaction_outputs__", new ArrayList<>()));
        Map<String, Object> submittedInteractions = submittedInteractionInputs(inputMap, activeSession);
        AgentSession captureSession = activeSession instanceof AgentSession agentSession ? agentSession : null;
        Object result = super.invoke(inputMap, activeSession).toCompletableFuture().join();
        ControllerOutput output = toControllerOutput(result);
        List<Object> streamChunks = captureSession == null ? List.of() : closeAndCollect(captureSession);
        output = legacyInvokeOutputView(output);
        List<Object> interactionSource = streamChunks.isEmpty() ? outputDataChunks(output) : streamChunks;
        List<Object> interactions = interactionChunks(interactionSource, activeSession);
        if (!streamChunks.isEmpty() && !interactions.isEmpty()) {
            List<Object> mergedStreamChunks = new ArrayList<>(streamChunks);
            for (Object interaction : interactions) {
                if (!mergedStreamChunks.contains(interaction)) {
                    mergedStreamChunks.add(interaction);
                }
            }
            streamChunks = mergedStreamChunks;
        }
        if (!interactions.isEmpty() && shouldReturnInteractions(output)) {
            output = new ControllerOutput(EventType.TASK_COMPLETION.getValue(), interactions);
        }
        if (createdSession != null) {
            getContextEngine().saveContexts(createdSession);
            createdSession.commit();
        }
        return new InvocationResult(output, streamChunks, activeSession, submittedInteractions);
    }

    private static boolean shouldReturnInteractions(ControllerOutput output) {
        if (output == null) {
            return true;
        }
        Map<String, Object> dataMap = output.getDataAsMap();
        if (dataMap == null) {
            return true;
        }
        Object workflowOutput = dataMap.get("output");
        if (workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput) {
            return typedOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED;
        }
        return true;
    }

    private static ControllerOutput legacyInvokeOutputView(ControllerOutput output) {
        if (output == null) {
            return null;
        }
        Object data = output.getData();
        if (data instanceof List<?> list) {
            Object finalPayload = workflowFinalPayload(list);
            if (finalPayload != null) {
                Map<String, Object> normalizedData = new LinkedHashMap<>();
                normalizedData.put("result_type", "answer");
                normalizedData.put("output", new com.openjiuwen.core.workflow.WorkflowOutput(
                        finalPayload,
                        WorkflowExecutionState.COMPLETED));
                return new ControllerOutput(output.getType(), normalizedData);
            }
        }
        Map<String, Object> dataMap = output.getDataAsMap();
        if (dataMap == null) {
            return output;
        }
        Object workflowOutput = dataMap.get("output");
        if (!(workflowOutput instanceof com.openjiuwen.core.workflow.WorkflowOutput typedOutput)) {
            return output;
        }
        Object normalizedResult = legacyInvokeWorkflowResult(typedOutput.getResult());
        if (normalizedResult == typedOutput.getResult()) {
            return output;
        }
        Map<String, Object> normalizedData = new LinkedHashMap<>(dataMap);
        normalizedData.put("output", new com.openjiuwen.core.workflow.WorkflowOutput(
                normalizedResult,
                typedOutput.getState()));
        return new ControllerOutput(output.getType(), normalizedData);
    }

    private static Object workflowFinalPayload(List<?> list) {
        boolean hasInteraction = list.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .anyMatch(schema -> com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(schema.getType()));
        if (hasInteraction) {
            return null;
        }
        for (int index = list.size() - 1; index >= 0; index--) {
            Object item = normalizeCompletedStages(list.get(index));
            if (item instanceof OutputSchema schema && "workflow_final".equals(schema.getType())) {
                return normalizeWorkflowFinalPayload(schema.getPayload());
            }
        }
        return null;
    }

    private static Object legacyInvokeWorkflowResult(Object result) {
        Object normalized = normalizeCompletedStages(result);
        if (!(normalized instanceof List<?> list) || list.isEmpty()
                || list.stream().noneMatch(OutputSchema.class::isInstance)) {
            return result;
        }
        List<OutputSchema> schemas = list.stream()
                .filter(OutputSchema.class::isInstance)
                .map(OutputSchema.class::cast)
                .toList();
        if (schemas.stream().anyMatch(schema -> com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(
                schema.getType()))) {
            return result;
        }
        for (int index = schemas.size() - 1; index >= 0; index--) {
            OutputSchema schema = schemas.get(index);
            if ("workflow_final".equals(schema.getType())) {
                return normalizeWorkflowFinalPayload(schema.getPayload());
            }
        }
        return null;
    }

    private static Object normalizeWorkflowFinalPayload(Object payload) {
        Object normalized = normalizeCompletedStages(payload);
        if (normalized instanceof Map<?, ?> map && map.containsKey("output")) {
            return normalizeCompletedStages(map.get("output"));
        }
        return normalized;
    }

    private AgentSession createCompatibilitySession(Map<String, Object> inputMap) {
        String sessionId = String.valueOf(inputMap.getOrDefault("conversation_id", "default_session"));
        AgentSession session = AgentSession.createAgentSession(sessionId, null, card);
        session.preRun(Map.of("inputs", inputMap));
        return session;
    }

    private static List<Object> closeAndCollect(AgentSession session) {
        session.closeStream();
        List<Object> result = new ArrayList<>();
        Iterator<Object> iterator = session.streamIterator();
        while (iterator.hasNext()) {
            result.add(normalizeCompletedStages(iterator.next()));
        }
        return result;
    }

    private static List<Object> streamChunksView(InvocationResult invocation) {
        if (!invocation.streamChunks().isEmpty()) {
            List<Object> result = new ArrayList<>(invocation.streamChunks());
            appendOutputDataChunks(result, invocation.output());
            appendWorkflowOutputChunks(result, invocation.output());
            appendMissingComponentInteractionTraces(result, invocation.submittedInteractions(), invocation.session());
            appendMissingEndComponentTrace(result, invocation.session());
            appendMissingRootWorkflowTrace(result, invocation.session());
            return result;
        }
        ControllerOutput output = invocation.output();
        Object data = output.getData();
        if (data instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(normalizeCompletedStages(item));
            }
            appendMissingComponentInteractionTraces(result, invocation.submittedInteractions(), invocation.session());
            appendMissingEndComponentTrace(result, invocation.session());
            appendMissingRootWorkflowTrace(result, invocation.session());
            return result;
        }
        Map<String, Object> dataMap = output.getDataAsMap();
        if (dataMap != null && dataMap.get("output") instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            List<Object> result = workflowOutputChunks(workflowOutput);
            appendMissingComponentInteractionTraces(result, invocation.submittedInteractions(), invocation.session());
            appendMissingEndComponentTrace(result, invocation.session());
            appendMissingRootWorkflowTrace(result, invocation.session());
            return result;
        }
        if (data instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            List<Object> result = workflowOutputChunks(workflowOutput);
            appendMissingComponentInteractionTraces(result, invocation.submittedInteractions(), invocation.session());
            appendMissingEndComponentTrace(result, invocation.session());
            appendMissingRootWorkflowTrace(result, invocation.session());
            return result;
        }
        List<Object> result = new ArrayList<>();
        result.add(new OutputSchema("workflow_final", 0, normalizeCompletedStages(data)));
        appendMissingComponentInteractionTraces(result, invocation.submittedInteractions(), invocation.session());
        appendMissingEndComponentTrace(result, invocation.session());
        appendMissingRootWorkflowTrace(result, invocation.session());
        return result;
    }

    private static void appendWorkflowOutputChunks(List<Object> result, ControllerOutput output) {
        if (output == null) {
            return;
        }
        List<Object> outputChunks = List.of();
        Map<String, Object> dataMap = output.getDataAsMap();
        if (dataMap != null && dataMap.get("output") instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            outputChunks = workflowOutputChunks(workflowOutput);
        } else if (output.getData() instanceof com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
            outputChunks = workflowOutputChunks(workflowOutput);
        }
        for (Object chunk : outputChunks) {
            if (!result.contains(chunk)) {
                result.add(chunk);
            }
        }
    }

    private static void appendOutputDataChunks(List<Object> result, ControllerOutput output) {
        if (output == null || !(output.getData() instanceof List<?> list)) {
            return;
        }
        for (Object item : list) {
            Object normalized = normalizeCompletedStages(item);
            if (!result.contains(normalized)) {
                result.add(normalized);
            }
        }
    }

    private static List<Object> outputDataChunks(ControllerOutput output) {
        if (output == null) {
            return List.of();
        }
        Object data = output.getData();
        if (data instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(normalizeCompletedStages(item));
            }
            return result;
        }
        if (data instanceof OutputSchema) {
            return List.of(data);
        }
        return List.of();
    }

    private static List<Object> workflowOutputChunks(com.openjiuwen.core.workflow.WorkflowOutput workflowOutput) {
        Object result = normalizeCompletedStages(workflowOutput.getResult());
        if (workflowOutput.getState() == WorkflowExecutionState.INPUT_REQUIRED) {
            if (result instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return result == null ? List.of() : List.of(result);
        }
        return new ArrayList<>(List.of(new OutputSchema("workflow_final", 0, result)));
    }

    private static void appendMissingRootWorkflowTrace(List<Object> result, AgentSessionApi session) {
        Object finalPayload = lastWorkflowFinalPayload(result);
        if (finalPayload == null) {
            return;
        }
        String workflowId = workflowTraceId(result, session);
        if (workflowId == null || workflowId.isBlank()
                || hasRootWorkflowTraceWithOutputs(result, workflowId, finalPayload)) {
            return;
        }
        TraceWorkflowSpan span = new TraceWorkflowSpan();
        span.setInvokeId(workflowId);
        span.setWorkflowId(workflowId);
        span.setOutputs(finalPayload);
        span.setStatus("success");
        addTraceBeforeTrailingWorkflowFinal(result, new TraceSchema("tracer_workflow", span));
    }

    private static void appendMissingEndComponentTrace(List<Object> result, AgentSessionApi session) {
        Object finalPayload = lastWorkflowFinalPayload(result);
        if (finalPayload == null || hasComponentOutputTrace(result, "end", finalPayload)) {
            return;
        }
        TraceWorkflowSpan span = new TraceWorkflowSpan();
        span.setInvokeId("end");
        span.setComponentId("end");
        span.setComponentName("end");
        span.setComponentType("End");
        span.setWorkflowId(workflowTraceId(result, session));
        span.setOutputs(finalPayload);
        span.setStatus("success");
        addTraceBeforeTrailingWorkflowFinal(result, new TraceSchema("tracer_workflow", span));
    }

    private static void appendMissingComponentInteractionTraces(List<Object> result,
                                                               Map<String, Object> submittedInteractions,
                                                               AgentSessionApi session) {
        if (submittedInteractions == null || submittedInteractions.isEmpty()) {
            return;
        }
        String workflowId = workflowTraceId(result, session);
        for (Map.Entry<String, Object> entry : submittedInteractions.entrySet()) {
            String componentId = entry.getKey();
            Object interactiveInput = normalizeCompletedStages(entry.getValue());
            if (componentId == null || componentId.isBlank()
                    || hasComponentInteractiveTrace(result, componentId, interactiveInput)) {
                continue;
            }
            TraceWorkflowSpan span = new TraceWorkflowSpan();
            span.setInvokeId(componentId);
            span.setComponentId(componentId);
            span.setComponentName(componentId);
            span.setComponentType("InteractiveNode");
            span.setWorkflowId(workflowId);
            span.setInteractiveInputs(interactiveInput);
            span.setStatus("success");
            addTraceBeforeTerminalInteraction(result, new TraceSchema("tracer_workflow", span));
        }
    }

    private static void addTraceBeforeTerminalInteraction(List<Object> result, TraceSchema trace) {
        int insertIndex = result.size();
        if (!result.isEmpty()
                && result.get(result.size() - 1) instanceof OutputSchema schema
                && com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(schema.getType())) {
            insertIndex = result.size() - 1;
        }
        result.add(insertIndex, trace);
    }

    private static void addTraceBeforeTrailingWorkflowFinal(List<Object> result, TraceSchema trace) {
        int insertIndex = result.size();
        if (!result.isEmpty()
                && result.get(result.size() - 1) instanceof OutputSchema schema
                && "workflow_final".equals(schema.getType())) {
            insertIndex = result.size() - 1;
        }
        result.add(insertIndex, trace);
    }

    private static boolean hasComponentInteractiveTrace(List<Object> chunks, String componentId,
                                                        Object interactiveInput) {
        for (Object chunk : chunks) {
            if (!(chunk instanceof TraceSchema traceSchema)
                    || !(traceSchema.getPayload() instanceof TraceWorkflowSpan span)
                    || !componentId.equals(span.getComponentId())
                    || !Objects.equals(normalizeCompletedStages(span.getInteractiveInputs()), interactiveInput)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean hasComponentOutputTrace(List<Object> chunks, String componentId, Object outputs) {
        for (Object chunk : chunks) {
            if (!(chunk instanceof TraceSchema traceSchema)
                    || !(traceSchema.getPayload() instanceof TraceWorkflowSpan span)
                    || !componentId.equals(span.getComponentId())
                    || !Objects.equals(normalizeCompletedStages(span.getOutputs()), outputs)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static Map<String, Object> submittedInteractionInputs(Map<String, Object> inputMap,
                                                                  AgentSessionApi session) {
        Object value = inputMap.get("input");
        if (value == null) {
            value = inputMap.get("query");
        }
        if (value instanceof InteractiveInput interactiveInput) {
            if (interactiveInput.getUserInputs() != null && !interactiveInput.getUserInputs().isEmpty()) {
                return new LinkedHashMap<>(interactiveInput.getUserInputs());
            }
            if (interactiveInput.getRawInputs() != null) {
                return interactionForInterruptedComponent(session, interactiveInput.getRawInputs());
            }
            return Map.of();
        }
        if (value != null) {
            return interactionForInterruptedComponent(session, value);
        }
        return Map.of();
    }

    private static Map<String, Object> interactionForInterruptedComponent(AgentSessionApi session, Object value) {
        String componentId = firstInterruptedComponentId(session);
        if (componentId == null || componentId.isBlank()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(componentId, value);
        return result;
    }

    private static String firstInterruptedComponentId(AgentSessionApi session) {
        if (session == null) {
            return null;
        }
        Object state = session.getState("workflow_controller");
        if (!(state instanceof Map<?, ?> stateMap)) {
            return null;
        }
        Object interrupted = stateMap.get("interrupted_tasks");
        if (!(interrupted instanceof Map<?, ?> interruptedTasks) || interruptedTasks.isEmpty()) {
            return null;
        }
        Object first = interruptedTasks.values().iterator().next();
        if (!(first instanceof Map<?, ?> info)) {
            return null;
        }
        Object componentId = info.get("component_id");
        if (componentId instanceof Iterable<?> ids) {
            Iterator<?> iterator = ids.iterator();
            return iterator.hasNext() ? String.valueOf(iterator.next()) : null;
        }
        return componentId == null ? null : String.valueOf(componentId);
    }

    private static Object lastWorkflowFinalPayload(List<Object> chunks) {
        for (int index = chunks.size() - 1; index >= 0; index--) {
            Object chunk = chunks.get(index);
            if (chunk instanceof OutputSchema schema && "workflow_final".equals(schema.getType())) {
                return normalizeCompletedStages(schema.getPayload());
            }
        }
        return null;
    }

    private static boolean hasRootWorkflowTraceWithOutputs(List<Object> chunks, String workflowId,
                                                           Object finalPayload) {
        for (Object chunk : chunks) {
            if (!(chunk instanceof TraceSchema traceSchema)
                    || !(traceSchema.getPayload() instanceof TraceWorkflowSpan span)
                    || !workflowId.equals(span.getInvokeId())
                    || (span.getParentNodeId() != null && !span.getParentNodeId().isEmpty())) {
                continue;
            }
            if (Objects.equals(normalizeCompletedStages(span.getOutputs()), finalPayload)) {
                return true;
            }
        }
        return false;
    }

    private static String workflowTraceId(List<Object> chunks, AgentSessionApi session) {
        for (Object chunk : chunks) {
            if (chunk instanceof TraceSchema traceSchema
                    && traceSchema.getPayload() instanceof TraceWorkflowSpan span
                    && span.getWorkflowId() != null
                    && !span.getWorkflowId().isBlank()) {
                return normalizeWorkflowTraceId(span.getWorkflowId());
            }
        }
        return normalizeWorkflowTraceId(session == null ? null : currentWorkflowId(session));
    }

    private static String normalizeWorkflowTraceId(String workflowId) {
        if (workflowId == null || workflowId.isBlank()) {
            return null;
        }
        return workflowId.replaceFirst("_[0-9]+(?:[._][0-9]+)*$", "");
    }

    private static List<Object> interactionChunks(List<Object> chunks, AgentSessionApi session) {
        List<Object> result = new ArrayList<>();
        Set<String> seenIds = new java.util.LinkedHashSet<>();
        if (chunks != null) {
            for (Object chunk : chunks) {
                if (chunk instanceof OutputSchema outputSchema
                        && com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(outputSchema.getType())) {
                    result.add(outputSchema);
                    Object id = interactionPayloadField(outputSchema.getPayload(), "id");
                    if (id != null) {
                        seenIds.add(String.valueOf(id));
                    }
                }
            }
        }
        appendRememberedInteractionOutputs(result, seenIds, session);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void appendInterruptedComponentIds(List<Object> result, Set<String> seenIds, AgentSessionApi session) {
        if (session == null) {
            return;
        }
        Object state = session.getState("workflow_controller");
        if (!(state instanceof Map<?, ?> stateMap)) {
            return;
        }
        Object interrupted = stateMap.get("interrupted_tasks");
        if (!(interrupted instanceof Map<?, ?> interruptedTasks)) {
            return;
        }
        for (Object value : interruptedTasks.values()) {
            if (!(value instanceof Map<?, ?> taskInfo)) {
                continue;
            }
            Object componentId = taskInfo.get("component_id");
            if (componentId instanceof Iterable<?> ids) {
                for (Object id : ids) {
                    appendInterruptedComponentId(result, seenIds, id);
                }
            } else {
                appendInterruptedComponentId(result, seenIds, componentId);
            }
        }
    }

    private static void appendRememberedInteractionOutputs(List<Object> result, Set<String> seenIds,
                                                           AgentSessionApi session) {
        Object remembered = session.getState("__workflow_interaction_outputs__");
        if (!(remembered instanceof Iterable<?> iterable)) {
            return;
        }
        for (Object item : iterable) {
            if (!(item instanceof OutputSchema outputSchema)
                    || !com.openjiuwen.core.common.constants.Constant.INTERACTION.equals(outputSchema.getType())) {
                continue;
            }
            Object id = interactionPayloadField(outputSchema.getPayload(), "id");
            if (id == null || seenIds.contains(String.valueOf(id))) {
                continue;
            }
            seenIds.add(String.valueOf(id));
            result.add(outputSchema);
        }
    }

    private static void appendInterruptedComponentId(List<Object> result, Set<String> seenIds, Object id) {
        if (id == null) {
            return;
        }
        String text = String.valueOf(id);
        if (text.isEmpty() || seenIds.contains(text)) {
            return;
        }
        seenIds.add(text);
        result.add(new OutputSchema(
                com.openjiuwen.core.common.constants.Constant.INTERACTION,
                0,
                new InteractionOutput(text, null)
        ));
    }

    private static Object interactionPayloadField(Object payload, String fieldName) {
        if (payload instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        try {
            String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            return payload == null ? null : payload.getClass().getMethod(getter).invoke(payload);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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

    private record InvocationResult(ControllerOutput output, List<Object> streamChunks, AgentSessionApi session,
                                    Map<String, Object> submittedInteractions) {
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
