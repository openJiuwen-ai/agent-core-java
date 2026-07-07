/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseModelInfo;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowKeys;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Workflow-specific controller logic for workflow selection and interruption state.
 * <p>
 * Mirrors Python's {@code WorkflowController} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
 */
public class WorkflowController {

    private static final Logger LOGGER = Logger.getLogger(WorkflowController.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATE_KEY = "workflow_controller";
    private static final String INTERRUPTED_TASKS = "interrupted_tasks";

    private AgentConfig agentConfig;
    private Object contextEngine;
    private SessionPort session;
    private WorkflowDetector workflowDetector;
    private List<Object> workflows = new ArrayList<>();
    private Object sourceAgent;

    public WorkflowController() {
        this(null, null, null);
    }

    public WorkflowController(AgentConfig config, Object contextEngine, SessionPort session) {
        this.agentConfig = config;
        this.contextEngine = contextEngine;
        this.session = session;
    }

    public void setupFromAgent(ControllerAgentPort agent) {
        this.agentConfig = agent == null ? null : agent.config();
        this.contextEngine = agent == null ? null : agent.contextEngine();
        this.session = agent == null ? null : agent.session();
    }

    public void setupFromAgent(Object agent) {
        if (agent == null) {
            return;
        }
        this.sourceAgent = agent;
        this.agentConfig = toControllerConfig(readProperty(agent, "getAgentConfig"));
        if (this.agentConfig == null) {
            this.agentConfig = toControllerConfig(readAgentConfigFromWrapper(agent));
        }
        this.contextEngine = readProperty(agent, "getContextEngine");
        this.workflows = readWorkflows(agent);
        fillWorkflowSchemasFromRuntime();
    }

    public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi activeSession) {
        try {
            refreshConfigFromSourceAgent();
            if (agentConfig == null || isBlank(agentConfig.getId())) {
                throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                        "error_msg", "Workflow not found");
            }
            Map<String, Object> effectiveInputs = inputs == null ? Map.of() : inputs;
            SessionPort sessionPort = sessionPort(activeSession);
            Event event = eventFromInputs(effectiveInputs);
            Intent intent = intentDetection(event, sessionPort);
            Object resumeOutput = handleResume(event, intent, sessionPort);
            if (resumeOutput != null) {
                return CompletableFuture.completedFuture(resumeOutput);
            }
            if (intent.getIntentType() == IntentType.DEFAULT_RESPONSE) {
                return CompletableFuture.completedFuture(Map.of(
                        "result_type", "answer",
                        "output", intent.getMetadata().getOrDefault("default_response_text", "")
                ));
            }
            Workflow workflow = workflowFor(intent.getWorkflow());
            Object workflowInputs = workflowInputsFor(event, intent, effectiveInputs, workflow, sessionPort);
            com.openjiuwen.core.workflow.WorkflowOutput output =
                    workflow.invoke(workflowInputs, workflowSession(activeSession), null);
            if (isWorkflowInterrupted(output)) {
                Object interactionData = output.getResult();
                List<?> interactions = interactionData instanceof List<?> list ? list : List.of(interactionData);
                interruptTask(intent.getTask(), sessionPort, interactions);
                return CompletableFuture.completedFuture(getFirstInterrupt(interactions));
            }
            clearInterruptedState(intent.getTask(), sessionPort);
            return CompletableFuture.completedFuture(Map.of(
                    "result_type", "answer",
                    "output", output
            ));
        } catch (RuntimeException error) {
            CompletableFuture<Object> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }
    }

    private Event eventFromInputs(Map<String, Object> inputs) {
        Object query = inputs.get("query");
        InteractiveInput interactiveInput = interactiveInput(query);
        Map<String, Object> extensions = new LinkedHashMap<>(inputs);
        extensions.remove("query");
        return Event.builder()
                .eventId("workflow_" + Integer.toHexString(System.identityHashCode(inputs)))
                .content(EventContent.builder()
                        .query(interactiveInput == null ? stringValue(query) : "")
                        .extensions(extensions)
                        .interactiveInput(interactiveInput)
                        .build())
                .build();
    }

    private InteractiveInput interactiveInput(Object query) {
        if (!(query instanceof com.openjiuwen.core.session.interaction.InteractiveInput source)) {
            return null;
        }
        Map<String, Object> userInputs = source.getUserInputs() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source.getUserInputs());
        return InteractiveInput.builder().userInputs(userInputs).build();
    }

    private Workflow workflowFor(WorkflowSchema schema) {
        if (schema == null) {
            return firstWorkflow();
        }
        for (Object item : sourceAgent == null ? workflows : readWorkflows(sourceAgent)) {
            Object value = item instanceof java.util.function.Supplier<?> supplier ? supplier.get() : item;
            if (value instanceof Workflow workflow
                    && workflow.getCard() != null
                    && Objects.equals(workflow.getCard().getId(), schema.getId())) {
                return workflow;
            }
        }
        Workflow resolved = resolveWorkflowFromResourceManager(schema);
        return resolved == null ? firstWorkflow() : resolved;
    }

    private Object workflowInputsFor(Event event, Intent intent, Map<String, Object> originalInputs,
                                     Workflow workflow, SessionPort activeSession) {
        Object originalQuery = originalInputs.get("query");
        if (event.getContent() != null
                && event.getContent().getInteractiveInput() != null
                && originalQuery instanceof com.openjiuwen.core.session.interaction.InteractiveInput) {
            return originalQuery;
        }
        Task task = intent.getTask();
        if (intent.getIntentType() == IntentType.RESUME_TASK && originalQuery != null) {
            com.openjiuwen.core.session.interaction.InteractiveInput interactiveInput =
                    new com.openjiuwen.core.session.interaction.InteractiveInput();
            interactiveInput.update(String.valueOf(getInterruptedComponentId(task, activeSession)), originalQuery);
            return interactiveInput;
        }
        Object arguments = task == null || task.getInput() == null ? null : task.getInput().getArguments();
        if (arguments instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                typed.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return typed;
        }
        return filterWorkflowInputs(originalInputs, workflow);
    }

    private SessionPort sessionPort(AgentSessionApi activeSession) {
        return new SessionPort() {
            @Override
            public String getSessionId() {
                return activeSession == null ? "" : activeSession.getSessionId();
            }

            @Override
            public Object getState(String key) {
                return activeSession == null ? null : activeSession.getState(key);
            }

            @Override
            public void updateState(Map<String, Object> update) {
                if (activeSession != null) {
                    activeSession.updateState(update);
                }
            }
        };
    }

    public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi activeSession,
                                   List<StreamMode> streamModes) {
        Workflow workflow = firstWorkflow();
        Map<String, Object> effectiveInputs = inputs == null ? Map.of() : inputs;
        validateRequiredAgentInputs(effectiveInputs, workflow);
        Iterator<? extends Object> iterator = workflow.stream(
                effectiveInputs,
                workflowSession(activeSession),
                null,
                streamModes == null ? List.of(StreamMode.OUTPUT) : streamModes);
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Object next() {
                return iterator.next();
            }
        };
    }

    private Map<String, Object> filterWorkflowInputs(Map<String, Object> inputs, Workflow workflow) {
        if (inputs == null || inputs.isEmpty() || workflow == null || workflow.getCard() == null) {
            return inputs == null ? Map.of() : inputs;
        }
        Object schema = workflow.getCard().getInputParams();
        if (!(schema instanceof Map<?, ?> schemaMap) || !(schemaMap.get("properties") instanceof Map<?, ?> props)
                || props.isEmpty()) {
            return inputs;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        List<String> requiredKeys = stringList(schemaMap.get("required"));
        for (Object rawKey : props.keySet()) {
            String key = String.valueOf(rawKey);
            if (inputs.containsKey(key)) {
                filtered.put(key, inputs.get(key));
            } else if (requiredKeys.contains(key) && isStringProperty(props.get(rawKey))) {
                filtered.put(key, "");
            }
        }
        return filtered;
    }

    private boolean isStringProperty(Object property) {
        return property instanceof Map<?, ?> map && "string".equals(map.get("type"));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof Iterable<?> iterable)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            result.add(String.valueOf(item));
        }
        return result;
    }

    private Object workflowSession(AgentSessionApi activeSession) {
        if (activeSession == null) {
            return WorkflowSessionApi.create(null, null, null);
        }
        try {
            Method method = activeSession.getClass().getMethod("createWorkflowSession");
            Object value = method.invoke(activeSession);
            if (value != null) {
                return value;
            }
        } catch (ReflectiveOperationException ignored) {
            // Older session facades expose only AgentSessionApi; fall back to a standalone workflow session.
        }
        return WorkflowSessionApi.create(null, activeSession.getSessionId(), null);
    }

    public void setWorkflowDetector(WorkflowDetector workflowDetector) {
        this.workflowDetector = workflowDetector;
    }

    public AgentConfig getAgentConfig() {
        return agentConfig;
    }

    public Object getContextEngine() {
        return contextEngine;
    }

    public SessionPort getSession() {
        return session;
    }

    public Intent intentDetection(Event event, SessionPort activeSession) {
        List<WorkflowSchema> workflows = agentConfig == null || agentConfig.getWorkflows() == null
                ? List.of()
                : agentConfig.getWorkflows();
        if (workflows.isEmpty()) {
            throw new IllegalArgumentException("No workflows configured for single_agent");
        }

        InteractiveInput interactiveInput = event == null || event.getContent() == null
                ? null
                : event.getContent().getInteractiveInput();
        if (interactiveInput != null && !interactiveInput.getUserInputs().isEmpty()) {
            Optional<WorkflowTaskMatch> match = findInterruptedTaskByNodeId(interactiveInput, activeSession);
            if (match.isPresent()) {
                return Intent.builder()
                        .intentType(IntentType.RESUME_TASK)
                        .task(match.get().getTask())
                        .workflow(match.get().getWorkflow())
                        .build();
            }
        }

        WorkflowSchema detectedWorkflow;
        if (workflows.size() == 1) {
            detectedWorkflow = workflows.get(0);
        } else {
            detectedWorkflow = detectWorkflowViaLlm(event, activeSession);
            if (detectedWorkflow == null) {
                String defaultText = agentConfig.getDefaultResponse() == null
                        ? ""
                        : agentConfig.getDefaultResponse().getText();
                return Intent.builder()
                        .intentType(IntentType.DEFAULT_RESPONSE)
                        .metadata(Map.of("default_response_text", defaultText))
                        .build();
            }
        }

        Task interruptedTask = findInterruptedTask(detectedWorkflow, activeSession).orElse(null);
        if (interruptedTask != null) {
            boolean shouldResume = shouldResumeInterruptedTask(interruptedTask, event, activeSession);
            Intent.IntentBuilder builder = Intent.builder()
                    .intentType(IntentType.RESUME_TASK)
                    .task(interruptedTask)
                    .workflow(detectedWorkflow);
            if (!shouldResume) {
                builder.metadata(Map.of("return_interruption", true));
            }
            return builder.build();
        }

        return Intent.builder()
                .intentType(IntentType.EXEC_NEW_TASK)
                .task(createNewTask(event, detectedWorkflow))
                .workflow(detectedWorkflow)
                .build();
    }

    public Object handleResume(Event event, Intent intent, SessionPort activeSession) {
        if (intent != null
                && intent.getMetadata() != null
                && Boolean.TRUE.equals(intent.getMetadata().get("return_interruption"))) {
            Task task = intent.getTask();
            Map<String, Object> state = stateMap(activeSession);
            Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
            Map<String, Object> interruptedInfo = mapValue(
                    interruptedTasks.get(stateKey(task.getInput().getTargetId()))
            );
            Object lastInteractionValue = interruptedInfo.get("last_interaction_value");
            if (lastInteractionValue == null) {
                return null;
            }
            Object componentId = interruptedInfo.getOrDefault("component_id", "questioner");
            return List.of(new OutputSchema(
                    Constant.INTERACTION,
                    0,
                    new InteractionOutput(componentId, lastInteractionValue)
            ));
        }
        return null;
    }

    public Map<String, Object> interruptTask(Task task, SessionPort activeSession, List<?> interactionData) {
        String workflowId = task.getInput().getTargetId();
        task.setStatus(TaskStatus.INTERRUPTED);

        Map<String, Object> state = stateMap(activeSession);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        Object componentId = extractComponentIdFromInteractionData(interactionData);
        Object interactionValue = extractInteractionValueFromInteractionData(interactionData);
        Map<String, Object> taskState = new LinkedHashMap<>();
        taskState.put("task", taskToMap(task));
        taskState.put("component_id", componentId);
        taskState.put("last_interaction_value", interactionValue);
        interruptedTasks.put(stateKey(workflowId), taskState);
        state.put(INTERRUPTED_TASKS, interruptedTasks);

        activeSession.updateState(stateUpdate(null));
        activeSession.updateState(Map.of(STATE_KEY, state));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "interrupted");
        result.put("task_id", task.getTaskId());
        result.put("workflow_id", workflowId);
        result.put("message", "Task interrupted, waiting for subsequent input");
        return result;
    }

    WorkflowSchema detectWorkflowViaLlm(Event event, SessionPort activeSession) {
        List<WorkflowSchema> workflows = agentConfig.getWorkflows();
        try {
            if (workflowDetector == null) {
                WorkflowSchema detected = detectWorkflowWithAgentModel(event, workflows);
                if (detected != null) {
                    return detected;
                }
                LOGGER.warning("No intent detection configured, using first workflow");
                return workflows.get(0);
            }
            List<Task> detectedTasks = workflowDetector.processMessage(event);
            if (detectedTasks == null || detectedTasks.isEmpty()) {
                DefaultResponse defaultResponse = agentConfig.getDefaultResponse();
                if (defaultResponse != null && !isBlank(defaultResponse.getText())) {
                    return null;
                }
                return workflows.get(0);
            }
            String workflowName = detectedTasks.get(0).getInput().getTargetName();
            for (WorkflowSchema workflow : workflows) {
                if (Objects.equals(workflow.getName(), workflowName)) {
                    return workflow;
                }
            }
            return workflows.get(0);
        } catch (RuntimeException e) {
            LOGGER.warning("Intent detection failed: " + e.getMessage() + ", using first workflow");
            return workflows.get(0);
        }
    }

    private WorkflowSchema detectWorkflowWithAgentModel(Event event, List<WorkflowSchema> candidates) {
        ModelConfig modelConfig = agentModelConfig();
        if (modelConfig == null || modelConfig.getModelInfo() == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        BaseModelInfo info = modelConfig.getModelInfo();
        Map<String, Object> extraFields = info.getExtraFields() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(info.getExtraFields());
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(modelConfig.getModelProvider())
                .apiKey(info.getApiKey())
                .apiBase(info.getApiBase())
                .timeout(info.getTimeout())
                .customHeaders(info.getCustomHeaders())
                .verifySsl(false)
                .maxRetries(3)
                .build();
        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(info.getModelName())
                .temperature(info.getTemperature())
                .topP(info.getTopP())
                .user(stringOrNull(extraFields.get("user")))
                .seed(integerOrNull(extraFields.get("seed")))
                .maxTokens(integerOrNull(extraFields.get("max_tokens")))
                .extraFields(extraFields)
                .build();
        Model model = new Model(clientConfig, requestConfig);
        AssistantMessage message = model.invoke(intentDetectionMessages(event, candidates)).toCompletableFuture().join();
        int selected = parseIntentClass(message == null ? "" : message.getContentAsString());
        return selected <= 0 || selected > candidates.size() ? null : candidates.get(selected - 1);
    }

    private ModelConfig agentModelConfig() {
        Object rawConfig = sourceAgent == null ? null : readProperty(sourceAgent, "getAgentConfig");
        Object model = rawConfig == null ? null : readProperty(rawConfig, "getModel");
        return model instanceof ModelConfig modelConfig ? modelConfig : null;
    }

    private List<BaseMessage> intentDetectionMessages(Event event, List<WorkflowSchema> candidates) {
        StringBuilder categoryText = new StringBuilder("category0: unknown intent");
        for (int i = 0; i < candidates.size(); i++) {
            WorkflowSchema workflow = candidates.get(i);
            String label = !isBlank(workflow.getDescription()) ? workflow.getDescription() : workflow.getName();
            categoryText.append('\n')
                    .append("category")
                    .append(i + 1)
                    .append(": ")
                    .append(label);
        }
        String systemPrompt = "You are an intent classification assistant. "
                + "Return JSON only in the form {\"result\": number}. "
                + "Use 0 when the input is unclear.\n"
                + categoryText;
        String query = event == null || event.getContent() == null ? "" : event.getContent().getQuery();
        return List.of(new SystemMessage(systemPrompt), new UserMessage("Current input: " + query));
    }

    private int parseIntentClass(String output) {
        try {
            String cleaned = Objects.toString(output, "").strip();
            cleaned = cleaned.replaceFirst("(?is)^```json\\s*", "");
            cleaned = cleaned.replaceFirst("(?is)\\s*```$", "");
            Map<String, Object> parsed = MAPPER.readValue(cleaned, new TypeReference<>() {
            });
            return Integer.parseInt(Objects.toString(parsed.getOrDefault("result", "0")));
        } catch (RuntimeException | java.io.IOException error) {
            return 0;
        }
    }

    boolean shouldResumeInterruptedTask(Task task, Event event, SessionPort activeSession) {
        InteractiveInput interactiveInput = event == null || event.getContent() == null
                ? null
                : event.getContent().getInteractiveInput();
        if (interactiveInput != null && !interactiveInput.getUserInputs().isEmpty()) {
            return true;
        }

        Map<String, Object> state = stateMap(activeSession);
        if (state.isEmpty()) {
            return true;
        }
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        Map<String, Object> interruptedInfo = mapValue(
                interruptedTasks.get(stateKey(task.getInput().getTargetId()))
        );
        if (interruptedInfo.isEmpty()) {
            return true;
        }
        Object lastInteractionValue = interruptedInfo.get("last_interaction_value");
        if (lastInteractionValue == null) {
            return true;
        }
        return !(lastInteractionValue instanceof Map<?, ?> || lastInteractionValue instanceof List<?>);
    }

    Optional<WorkflowTaskMatch> findInterruptedTaskByNodeId(InteractiveInput interactiveInput, SessionPort activeSession) {
        Map<String, Object> state = stateMap(activeSession);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        if (interruptedTasks.isEmpty() || interactiveInput == null || interactiveInput.getUserInputs().isEmpty()) {
            return Optional.empty();
        }
        List<String> nodeIds = new ArrayList<>(interactiveInput.getUserInputs().keySet());
        for (Map.Entry<String, Object> entry : interruptedTasks.entrySet()) {
            Map<String, Object> taskInfo = mapValue(entry.getValue());
            Object componentId = taskInfo.get("component_id");
            boolean matched = componentId instanceof List<?> list
                    ? list.stream().map(String::valueOf).anyMatch(nodeIds::contains)
                    : nodeIds.contains(String.valueOf(componentId));
            if (!matched) {
                continue;
            }
            Task task = taskFromObject(taskInfo.get("task"));
            for (WorkflowSchema workflow : workflows()) {
                String baseId = workflow.getId() + "_" + workflow.getVersion().replace(".", "_");
                if (Objects.equals(entry.getKey(), baseId) || Objects.equals(entry.getKey(), workflow.getId())) {
                    return Optional.of(new WorkflowTaskMatch(workflow, task));
                }
            }
        }
        return Optional.empty();
    }

    Optional<Task> findInterruptedTask(WorkflowSchema workflow, SessionPort activeSession) {
        Map<String, Object> state = stateMap(activeSession);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        if (workflow == null || interruptedTasks.isEmpty()) {
            return Optional.empty();
        }
        List<String> possibleIds = List.of(
                workflow.getId() + "_" + workflow.getVersion().replace(".", "_"),
                workflow.getId()
        );
        for (String workflowId : possibleIds) {
            if (interruptedTasks.containsKey(workflowId)) {
                Map<String, Object> taskInfo = mapValue(interruptedTasks.get(workflowId));
                return Optional.of(taskFromObject(taskInfo.get("task")));
            }
        }
        return Optional.empty();
    }

    Task createNewTask(Event event, WorkflowSchema workflow) {
        String query = event == null || event.getContent() == null ? "" : event.getContent().getQuery();
        Map<String, Object> schema = workflow.getInputs() == null ? Map.of() : workflow.getInputs();
        String requiredKey = getRequiredInputKey(schema).orElse("query");

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put(requiredKey, query);
        if (event != null && event.getContent() != null && event.getContent().getExtensions() != null) {
            userData.putAll(event.getContent().getExtensions());
        }
        Map<String, Object> filteredInputs = filterWorkflowInputs(schema, userData);

        Task task = new Task();
        task.setTaskId("workflow_" + (event == null ? "" : event.getEventId()));
        task.setTaskType(TaskType.WORKFLOW);
        task.setStatus(TaskStatus.PENDING);
        task.setInput(new TaskInput(
                workflow.getId() + "_" + workflow.getVersion(),
                workflow.getName(),
                filteredInputs
        ));
        return task;
    }

    Optional<String> getRequiredInputKey(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> properties = mapValue(schema.get("properties"));
        if (properties.isEmpty()) {
            return Optional.empty();
        }
        if (properties.containsKey("query")) {
            return Optional.of("query");
        }
        Object required = schema.get("required");
        if (required instanceof List<?> requiredList) {
            for (Object key : requiredList) {
                String keyString = String.valueOf(key);
                if (properties.containsKey(keyString)) {
                    return Optional.of(keyString);
                }
            }
        }
        if (properties.containsKey("input")) {
            return Optional.of("input");
        }
        return Optional.empty();
    }

    Map<String, Object> filterWorkflowInputs(Map<String, Object> schema, Map<String, Object> userData) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        Map<String, Object> properties = mapValue(schema == null ? null : schema.get("properties"));
        if (properties.isEmpty() && schema != null && !schema.isEmpty() && isSimplifiedSchema(schema)) {
            properties = schema;
        }
        for (Map.Entry<String, Object> entry : userData.entrySet()) {
            if (properties.isEmpty() || properties.containsKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    Object getInterruptedComponentId(Task task, SessionPort activeSession) {
        Map<String, Object> interruptedInfo = interruptedInfo(task, activeSession);
        return interruptedInfo.getOrDefault("component_id", "questioner");
    }

    void clearInterruptedState(Task task, SessionPort activeSession) {
        Map<String, Object> state = stateMap(activeSession);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        String key = stateKey(task.getInput().getTargetId());
        if (interruptedTasks.containsKey(key)) {
            interruptedTasks.remove(key);
            state.put(INTERRUPTED_TASKS, interruptedTasks);
            activeSession.updateState(stateUpdate(null));
            activeSession.updateState(Map.of(STATE_KEY, state));
        }
    }

    Object extractComponentIdFromInteractionData(List<?> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return "questioner";
        }
        List<String> componentIds = new ArrayList<>();
        for (Object item : interactionData) {
            if (item instanceof OutputSchema output && Constant.INTERACTION.equals(output.getType())) {
                Object id = payloadField(output.getPayload(), "id");
                if (id != null) {
                    componentIds.add(String.valueOf(id));
                }
            }
        }
        if (componentIds.isEmpty()) {
            return "questioner";
        }
        return componentIds.size() == 1 ? componentIds.get(0) : componentIds;
    }

    Object extractInteractionValueFromInteractionData(List<?> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return null;
        }
        for (Object item : interactionData) {
            if (item instanceof OutputSchema output && Constant.INTERACTION.equals(output.getType())) {
                Object value = payloadField(output.getPayload(), "value");
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    List<Object> getFirstInterrupt(List<?> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        boolean found = false;
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema output && Constant.INTERACTION.equals(output.getType())) {
                if (!found) {
                    result.add(output);
                    found = true;
                }
            }
        }
        return result;
    }

    int countInteractions(List<?> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema output && Constant.INTERACTION.equals(output.getType())) {
                count++;
            }
        }
        return count;
    }

    boolean isWorkflowInterrupted(com.openjiuwen.core.workflow.WorkflowOutput result) {
        return result != null
                && result.getState() == com.openjiuwen.core.workflow.WorkflowExecutionState.INPUT_REQUIRED;
    }

    private List<WorkflowSchema> workflows() {
        return agentConfig == null || agentConfig.getWorkflows() == null ? List.of() : agentConfig.getWorkflows();
    }

    private Workflow firstWorkflow() {
        refreshConfigFromSourceAgent();
        if (agentConfig == null || isBlank(agentConfig.getId())) {
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", "Workflow not found");
        }
        List<Object> currentWorkflows = sourceAgent == null ? workflows : readWorkflows(sourceAgent);
        if (currentWorkflows.isEmpty()) {
            currentWorkflows = workflows;
        }
        for (Object item : currentWorkflows) {
            Object value = item;
            if (value instanceof java.util.function.Supplier<?> supplier) {
                value = supplier.get();
            }
            if (value instanceof Workflow workflow) {
                return workflow;
            }
        }
        for (WorkflowSchema schema : workflows()) {
            Workflow workflow = resolveWorkflowFromResourceManager(schema);
            if (workflow != null) {
                return workflow;
            }
        }
        throw new IllegalArgumentException("No workflows configured for single_agent");
    }

    private void validateRequiredAgentInputs(Map<String, Object> inputs, Workflow workflow) {
        if (inputs.containsKey("query")) {
            return;
        }
        throw ErrorHelper.buildError(StatusCode.WORKFLOW_COMPONENT_EXECUTION_ERROR,
                "ability", "INVOKE",
                "comp", "start",
                "reason", "query is required",
                "workflow", workflow == null || workflow.getCard() == null ? "" : workflow.getCard().getId());
    }

    private Workflow resolveWorkflowFromResourceManager(WorkflowSchema schema) {
        if (schema == null || isBlank(schema.getId())) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (!isBlank(schema.getVersion())) {
            candidates.add(WorkflowKeys.generateWorkflowKey(schema.getId(), schema.getVersion()));
        }
        candidates.add(schema.getId());
        for (String workflowId : candidates) {
            try {
                Object resolved = Runner.resourceMgr().getWorkflow(workflowId, null).toCompletableFuture().join();
                if (resolved instanceof Workflow workflow) {
                    return workflow;
                }
            } catch (RuntimeException ignored) {
                // Missing resources are expected when a schema only describes an unavailable workflow.
            }
        }
        return null;
    }

    private void refreshConfigFromSourceAgent() {
        if (sourceAgent == null) {
            return;
        }
        AgentConfig refreshed = toControllerConfig(readProperty(sourceAgent, "getAgentConfig"));
        if (refreshed == null) {
            refreshed = toControllerConfig(readAgentConfigFromWrapper(sourceAgent));
        }
        if (refreshed != null) {
            this.agentConfig = refreshed;
            fillWorkflowSchemasFromRuntime();
        }
    }

    private void fillWorkflowSchemasFromRuntime() {
        if (agentConfig == null || (agentConfig.getWorkflows() != null && !agentConfig.getWorkflows().isEmpty())) {
            return;
        }
        List<WorkflowSchema> schemas = new ArrayList<>();
        for (Object item : workflows) {
            Object value = item instanceof java.util.function.Supplier<?> supplier ? supplier.get() : item;
            if (value instanceof Workflow workflow && workflow.getCard() != null) {
                schemas.add(toWorkflowSchema(workflow.getCard()));
            }
        }
        agentConfig.setWorkflows(schemas);
    }

    private Map<String, Object> interruptedInfo(Task task, SessionPort activeSession) {
        Map<String, Object> state = stateMap(activeSession);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        return mapValue(interruptedTasks.get(stateKey(task.getInput().getTargetId())));
    }

    private Map<String, Object> stateMap(SessionPort activeSession) {
        if (activeSession == null) {
            return new LinkedHashMap<>();
        }
        return mapValue(activeSession.getState(STATE_KEY));
    }

    private static String stateKey(String workflowId) {
        return workflowId == null ? "" : workflowId.replace(".", "_");
    }

    private static Map<String, Object> stateUpdate(Object value) {
        Map<String, Object> update = new LinkedHashMap<>();
        update.put(STATE_KEY, value);
        return update;
    }

    private static boolean isSimplifiedSchema(Map<String, Object> schema) {
        for (Object value : schema.values()) {
            Map<String, Object> valueMap = mapValue(value);
            if (valueMap.containsKey("type")) {
                return true;
            }
        }
        return false;
    }

    private static Task taskFromObject(Object value) {
        return MAPPER.convertValue(value, Task.class);
    }

    private static Map<String, Object> taskToMap(Task task) {
        return MAPPER.convertValue(task, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static Object payloadField(Object payload, String fieldName) {
        if (payload instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        if (payload instanceof InteractionOutput interactionOutput) {
            return "id".equals(fieldName) ? interactionOutput.getId() : interactionOutput.getValue();
        }
        try {
            Method getter = payload.getClass().getMethod("get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            return getter.invoke(payload);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static AgentConfig toControllerConfig(Object rawConfig) {
        if (rawConfig == null) {
            return null;
        }
        if (rawConfig instanceof AgentConfig config) {
            return config;
        }
        AgentConfig config = new AgentConfig();
        config.setId(stringValue(readProperty(rawConfig, "getId")));
        config.setWorkflows(toWorkflowSchemas(readProperty(rawConfig, "getWorkflows")));
        Object defaultResponse = readProperty(rawConfig, "getDefaultResponse");
        if (defaultResponse != null) {
            config.setDefaultResponse(DefaultResponse.builder()
                    .text(stringValue(readProperty(defaultResponse, "getText")))
                    .build());
        }
        return config;
    }

    private static List<WorkflowSchema> toWorkflowSchemas(Object rawWorkflows) {
        if (!(rawWorkflows instanceof Iterable<?> iterable)) {
            return new ArrayList<>();
        }
        List<WorkflowSchema> result = new ArrayList<>();
        for (Object item : iterable) {
            WorkflowSchema workflowSchema = toWorkflowSchema(item);
            if (workflowSchema != null) {
                result.add(workflowSchema);
            }
        }
        return result;
    }

    private static WorkflowSchema toWorkflowSchema(Object item) {
        if (item instanceof WorkflowSchema workflowSchema) {
            return workflowSchema;
        }
        if (item == null) {
            return null;
        }
        Object inputs = readProperty(item, "getInputs");
        if (inputs == null) {
            inputs = readProperty(item, "getInputParams");
        }
        return WorkflowSchema.builder()
                .id(stringValue(readProperty(item, "getId")))
                .name(stringValue(readProperty(item, "getName")))
                .description(stringValue(readProperty(item, "getDescription")))
                .version(stringValue(readProperty(item, "getVersion")))
                .inputs(stringObjectMap(inputs))
                .build();
    }

    private static Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static List<Object> readWorkflows(Object agent) {
        Object rawWorkflows = readProperty(agent, "getWorkflows");
        if (rawWorkflows instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            for (Object item : iterable) {
                result.add(item);
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static Object readAgentConfigFromWrapper(Object agent) {
        Object wrapper = readProperty(agent, "getConfigWrapper");
        return wrapper == null ? null : readProperty(wrapper, "getAgentConfig");
    }

    private static Object readProperty(Object target, String methodName) {
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

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringOrNull(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer integerOrNull(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.valueOf(String.valueOf(value));
    }

    /**
     * Mirrors Python's controller agent setup contract in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    public interface ControllerAgentPort {
        AgentConfig config();

        Object contextEngine();

        SessionPort session();
    }

    /**
     * Mirrors Python's session methods used by {@code WorkflowController} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    public interface SessionPort {
        String getSessionId();

        Object getState(String key);

        void updateState(Map<String, Object> update);
    }

    /**
     * Mirrors Python's intent detector process-message boundary in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    public interface WorkflowDetector {
        List<Task> processMessage(Event event);
    }

    /**
     * Mirrors Python's {@code AgentConfig} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentConfig {
        @Builder.Default
        private String id = "";
        @Builder.Default
        private List<WorkflowSchema> workflows = new ArrayList<>();
        private DefaultResponse defaultResponse;
    }

    /**
     * Mirrors Python's default-response config usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DefaultResponse {
        @Builder.Default
        private String text = "";
    }

    /**
     * Mirrors Python's {@code Event} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Event {
        @Builder.Default
        private String eventId = "";
        @Builder.Default
        private EventContent content = new EventContent();
    }

    /**
     * Mirrors Python's {@code EventContent} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventContent {
        @Builder.Default
        private String query = "";
        @Builder.Default
        private Map<String, Object> extensions = new LinkedHashMap<>();
        private InteractiveInput interactiveInput;

        public String getQuery() {
            return query == null ? "" : query;
        }
    }

    /**
     * Mirrors Python's interactive input object usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractiveInput {
        @Builder.Default
        private Map<String, Object> userInputs = new LinkedHashMap<>();
    }

    /**
     * Mirrors Python's {@code IntentType} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    public enum IntentType {
        EXEC_NEW_TASK("ExecNewTask"),
        RESUME_TASK("ResumeTask"),
        DEFAULT_RESPONSE("DefaultResponse");

        private final String value;

        IntentType(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    /**
     * Mirrors Python's {@code Intent} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Intent {
        private IntentType intentType;
        private Task task;
        private WorkflowSchema workflow;
        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<>();
    }

    /**
     * Mirrors Python's workflow/task resume tuple in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @AllArgsConstructor
    public static class WorkflowTaskMatch {
        private WorkflowSchema workflow;
        private Task task;
    }

    /**
     * Mirrors Python's {@code InteractionOutput} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InteractionOutput {
        private Object id;
        private Object value;
    }

    /**
     * Mirrors Python's {@code WorkflowOutput} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowOutput {
        private Object result;
        private WorkflowExecutionState state;
    }

    /**
     * Mirrors Python's {@code WorkflowExecutionState} usage in
     * {@code openjiuwen/core/application/workflow_agent/workflow_controller.py}.
     */
    public enum WorkflowExecutionState {
        INPUT_REQUIRED,
        COMPLETED
    }
}
