/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskInput;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    boolean isWorkflowInterrupted(WorkflowOutput result) {
        return result != null && result.getState() == WorkflowExecutionState.INPUT_REQUIRED;
    }

    private List<WorkflowSchema> workflows() {
        return agentConfig == null || agentConfig.getWorkflows() == null ? List.of() : agentConfig.getWorkflows();
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
