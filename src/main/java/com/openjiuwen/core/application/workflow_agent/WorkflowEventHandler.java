/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.constants.TaskType;
import com.openjiuwen.core.common.schema.BaseCard;
import com.openjiuwen.core.common.utils.MessageUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.ControllerConfig;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Intent;
import com.openjiuwen.core.controller.schema.IntentType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.workflow.WorkflowCard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Handles workflow input events for the new controller framework.
 *
 * <p>Mirrors Python's {@code WorkflowEventHandler} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.</p>
 */
public class WorkflowEventHandler extends EventHandler {

    private static final Logger LOGGER = Logger.getLogger(WorkflowEventHandler.class.getName());
    private static final String WORKFLOW_CONTROLLER_STATE = "workflow_controller";
    private static final String INTERRUPTED_TASKS = "interrupted_tasks";
    private static final String QUESTIONER = "questioner";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private IntentDetector intentDetector;

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Event event = inputs.getEvent();
        AgentSessionApi session = inputs.getSession();

        DetectResult result = detectIntent(event, session);
        Intent intent = result.intent();

        Object displayContent = extractDisplayValue(event);
        addUserMessage(displayContent, session);

        IntentType intentType = intent.getIntentType();
        if (intentType == IntentType.CREATE_TASK) {
            routeNewTask(result, session);
        } else if (intentType == IntentType.SUPPLEMENT_TASK || intentType == IntentType.RESUME_TASK) {
            routeResume(event, result, session);
        } else if (intentType == IntentType.CANCEL_TASK) {
            routeCancel(session);
        } else if (intentType == IntentType.UNKNOWN_TASK) {
            routeDefaultResponse(result, session);
        } else {
            LOGGER.warning("Unknown intent type: " + intentType);
        }
        return Map.of("status", "success");
    }

    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        return Map.of("status", "success");
    }

    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        return Map.of("status", "success");
    }

    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        return Map.of("status", "success");
    }

    void setIntentDetector(IntentDetector intentDetector) {
        this.intentDetector = intentDetector;
    }

    private List<WorkflowCard> getWorkflows() {
        List<WorkflowCard> workflows = new ArrayList<>();
        Object manager = abilityManager;
        if (manager instanceof AbilityManager typedManager) {
            for (Object item : typedManager.list()) {
                if (item instanceof WorkflowCard workflowCard) {
                    workflows.add(workflowCard);
                }
            }
            return workflows;
        }
        if (manager == null) {
            return workflows;
        }
        try {
            Method listMethod = manager.getClass().getMethod("list");
            Object result = listMethod.invoke(manager);
            if (result instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof WorkflowCard workflowCard) {
                        workflows.add(workflowCard);
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            LOGGER.fine("Ability manager does not expose list()");
        }
        return workflows;
    }

    private static String workflowStateKey(String workflowId) {
        return workflowId == null ? "" : workflowId.replace(".", "_");
    }

    private DetectResult detectIntent(Event event, AgentSessionApi session) {
        ControllerConfig controllerConfig = getControllerConfig();
        List<WorkflowCard> workflows = getWorkflows();
        if (workflows.isEmpty()) {
            throw new IllegalArgumentException("No workflows configured");
        }

        InteractiveInput providedInteractiveInput = extractInteractiveInput(event, session);
        if (providedInteractiveInput != null && !providedInteractiveInput.getUserInputs().isEmpty()) {
            Optional<WorkflowMatch> resumeResult = findInterruptedTaskByNodeId(providedInteractiveInput, session);
            if (resumeResult.isPresent()) {
                WorkflowMatch match = resumeResult.get();
                Intent intent = makeIntent(
                        IntentType.SUPPLEMENT_TASK,
                        event,
                        match.taskData().targetId(),
                        null,
                        "interactive_input",
                        null,
                        null
                );
                return new DetectResult(intent, match.workflow(), match.taskData());
            }
        }

        WorkflowCard detectedWorkflow;
        if (workflows.size() == 1) {
            detectedWorkflow = workflows.get(0);
        } else {
            detectedWorkflow = detectWorkflowViaLlm(event, session);
            if (detectedWorkflow == null) {
                String defaultText = defaultResponseText(controllerConfig);
                Intent intent = makeIntent(
                        IntentType.UNKNOWN_TASK,
                        event,
                        null,
                        null,
                        null,
                        defaultText,
                        Map.of("default_response_text", defaultText)
                );
                return new DetectResult(intent, null, null);
            }
        }

        Optional<TaskData> interrupted = findInterruptedTask(detectedWorkflow, session);
        if (interrupted.isPresent()) {
            TaskData taskData = interrupted.get();
            boolean shouldResume = shouldResume(taskData, event, session);
            Intent intent = makeIntent(
                    IntentType.SUPPLEMENT_TASK,
                    event,
                    taskData.targetId(),
                    null,
                    shouldResume ? "resume" : "return_interruption",
                    null,
                    shouldResume ? null : Map.of("return_interruption", true)
            );
            return new DetectResult(intent, detectedWorkflow, taskData);
        }

        TaskData taskData = buildNewTaskData(event, detectedWorkflow);
        Intent intent = makeIntent(
                IntentType.CREATE_TASK,
                event,
                null,
                detectedWorkflow.getName(),
                null,
                null,
                null
        );
        return new DetectResult(intent, detectedWorkflow, taskData);
    }

    private WorkflowCard detectWorkflowViaLlm(Event event, AgentSessionApi session) {
        ControllerConfig controllerConfig = getControllerConfig();
        List<WorkflowCard> workflows = getWorkflows();
        try {
            ensureIntentDetectionInitialized(session);
            if (intentDetector == null) {
                return workflows.get(0);
            }
            List<TaskResult> detectedTasks = intentDetector.processMessage(event);
            if (detectedTasks == null || detectedTasks.isEmpty()) {
                if (!defaultResponseText(controllerConfig).isEmpty()) {
                    return null;
                }
                return workflows.get(0);
            }

            String workflowName = detectedTasks.get(0).input().targetName();
            for (WorkflowCard workflow : workflows) {
                if (Objects.equals(workflow.getName(), workflowName)) {
                    return workflow;
                }
            }
            LOGGER.warning("Workflow '" + workflowName + "' not found, using first");
            return workflows.get(0);
        } catch (RuntimeException error) {
            LOGGER.warning("Intent detection failed: " + error.getMessage() + ", using first workflow");
            return workflows.get(0);
        }
    }

    private void ensureIntentDetectionInitialized(AgentSessionApi session) {
        ControllerConfig controllerConfig = getControllerConfig();
        if (!controllerConfig.isEnableIntentRecognition()) {
            return;
        }
        if (intentDetector != null) {
            intentDetector.setSession(session);
            return;
        }
        List<WorkflowCard> workflows = getWorkflows();
        List<String> categoryList = new ArrayList<>();
        for (WorkflowCard workflow : workflows) {
            categoryList.add(!workflow.getDescription().isEmpty() ? workflow.getDescription() : workflow.getName());
        }
        IntentDetectionConfig intentConfig = new IntentDetectionConfig(
                String.join("\n", categoryList.stream().map(value -> "- " + value).toList()),
                categoryList,
                true,
                true,
                100,
                "category0",
                List.of()
        );
        intentDetector = new IntentDetector(intentConfig, controllerConfig, contextEngine, session, abilityManager);
    }

    private void routeNewTask(DetectResult result, AgentSessionApi session) {
        WorkflowCard workflow = result.workflow();
        TaskData taskData = result.taskData();
        if (workflow == null || taskData == null) {
            LOGGER.severe("routeNewTask: missing workflow or task data");
            return;
        }

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("agent_id", schedulerCardId());
        extensions.put("workflow_id", taskData.targetId());
        extensions.put("workflow_version", workflow.getVersion());
        extensions.put("resume_mode", "new");
        extensions.put("interactive_input", null);
        extensions.put("filtered_inputs", taskData.arguments());

        Task task = new Task(session.getSessionId(), randomWorkflowTaskId(), TaskType.WORKFLOW.getValue());
        task.setDescription(taskData.targetName());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setExtensions(extensions);
        requireTaskManager().addTask(task);
        LOGGER.info("routeNewTask: added task " + task.getTaskId() + ", workflow=" + taskData.targetId());
    }

    private void routeResume(Event event, DetectResult result, AgentSessionApi session) {
        Intent intent = result.intent();
        if (intent.getMetadata() != null && Boolean.TRUE.equals(intent.getMetadata().get("return_interruption"))) {
            handleReturnInterruption(result, session);
            return;
        }

        WorkflowCard workflow = result.workflow();
        TaskData taskData = result.taskData();
        if (workflow == null || taskData == null) {
            LOGGER.severe("routeResume: missing workflow or task data");
            return;
        }

        InteractiveInput interactiveInput = buildInteractiveInput(event, taskData.targetId(), session);

        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("workflow_id", taskData.targetId());
        extensions.put("workflow_version", workflow.getVersion());
        extensions.put("resume_mode", "resume");
        extensions.put("interactive_input", interactiveInput);
        extensions.put("filtered_inputs", null);

        Task task = new Task(session.getSessionId(), randomWorkflowTaskId(), TaskType.WORKFLOW.getValue());
        task.setDescription(taskData.targetName());
        task.setStatus(TaskStatus.SUBMITTED);
        task.setExtensions(extensions);
        requireTaskManager().addTask(task);
        LOGGER.info("routeResume: added resume task " + task.getTaskId() + ", workflow=" + taskData.targetId());
    }

    private void routeCancel(AgentSessionApi session) {
        LOGGER.info("routeCancel: cancelling tasks");
        for (TaskStatus status : List.of(TaskStatus.SUBMITTED, TaskStatus.WORKING, TaskStatus.INPUT_REQUIRED)) {
            List<Task> tasks = requireTaskManager().getTask(
                    TaskFilter.builder()
                            .sessionId(session.getSessionId())
                            .status(status)
                            .build()
            );
            for (Task task : tasks) {
                requireTaskManager().updateTaskStatus(task.getTaskId(), TaskStatus.CANCELED);
            }
        }
    }

    private void routeDefaultResponse(DetectResult result, AgentSessionApi session) {
        Map<String, Object> metadata = result.intent().getMetadata();
        String defaultText = metadata == null ? "" : Objects.toString(metadata.getOrDefault("default_response_text", ""), "");
        LOGGER.info("routeDefaultResponse: " + defaultText);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("response", defaultText);
        payload.put("output", Map.of());
        payload.put("status", "default_response");
        session.writeStream(new OutputSchema("workflow_final", 0, payload));

        addAiMessage(defaultText, session);
        sendCompletionSignal(session);
    }

    private void handleReturnInterruption(DetectResult result, AgentSessionApi session) {
        TaskData taskData = result.taskData();
        if (taskData == null) {
            return;
        }

        Map<String, Object> state = stateMap(session);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        Map<String, Object> interruptedInfo = mapValue(interruptedTasks.get(workflowStateKey(taskData.targetId())));
        if (interruptedInfo.isEmpty()) {
            LOGGER.warning("handleReturnInterruption: no interrupted task info");
            return;
        }

        String componentId = firstNonBlankComponentId(interruptedInfo.get("component_id"), QUESTIONER);
        Object lastValue = interruptedInfo.get("last_interaction_value");
        if (lastValue == null) {
            LOGGER.warning("handleReturnInterruption: no last_interaction_value");
            return;
        }

        InteractionOutput interactionOutput = new InteractionOutput(componentId, lastValue);
        session.writeStream(new OutputSchema(Constant.INTERACTION, 0, interactionOutput));
        sendCompletionSignal(session);
        LOGGER.info("handleReturnInterruption: returned interruption for " + taskData.targetId());
    }

    private void sendCompletionSignal(AgentSessionApi session) {
        ControllerOutputChunk chunk = new ControllerOutputChunk(
                0,
                ControllerOutputPayload.allTasksProcessed("All tasks have been successfully processed"),
                true
        );
        session.writeStream(chunk);
    }

    private InteractiveInput buildInteractiveInput(Event event, String workflowId, AgentSessionApi session) {
        Object targetComponentId = getComponentId(workflowId, session);
        List<String> targetIds = normalizeComponentIds(targetComponentId);

        InteractiveInput provided = extractInteractiveInput(event, null);
        if (provided != null) {
            if (!provided.getUserInputs().isEmpty()) {
                boolean matches = false;
                for (String key : provided.getUserInputs().keySet()) {
                    if (targetIds.contains(key)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    Object userValue = provided.getUserInputs().values().iterator().next();
                    InteractiveInput remapped = new InteractiveInput();
                    remapped.update(targetIds.get(0), userValue);
                    return remapped;
                }
            }
            return provided;
        }

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update(targetIds.get(0), Objects.toString(extractDisplayValue(event), ""));
        return interactiveInput;
    }

    private Object getComponentId(String workflowId, AgentSessionApi session) {
        Map<String, Object> state = stateMap(session);
        if (state.isEmpty()) {
            return QUESTIONER;
        }
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        Map<String, Object> info = mapValue(interruptedTasks.get(workflowStateKey(workflowId)));
        if (info.isEmpty()) {
            return QUESTIONER;
        }
        Object componentId = info.get("component_id");
        if (componentId instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object value : list) {
                String text = Objects.toString(value, "");
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values.isEmpty() ? QUESTIONER : values.size() == 1 ? values.get(0) : values;
        }
        String text = Objects.toString(componentId, "");
        return text.isBlank() ? QUESTIONER : text;
    }

    private Optional<WorkflowMatch> findInterruptedTaskByNodeId(InteractiveInput interactiveInput, AgentSessionApi session) {
        Map<String, Object> state = stateMap(session);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        if (interruptedTasks.isEmpty() || interactiveInput == null || interactiveInput.getUserInputs().isEmpty()) {
            return Optional.empty();
        }

        List<String> nodeIds = new ArrayList<>(interactiveInput.getUserInputs().keySet());
        List<WorkflowCard> workflows = getWorkflows();
        for (Map.Entry<String, Object> entry : interruptedTasks.entrySet()) {
            Map<String, Object> taskInfo = mapValue(entry.getValue());
            Object componentId = taskInfo.get("component_id");
            boolean matched = componentId instanceof List<?> componentIds
                    ? componentIds.stream().map(String::valueOf).anyMatch(nodeIds::contains)
                    : nodeIds.contains(String.valueOf(componentId));
            if (!matched) {
                continue;
            }

            Map<String, Object> taskExtensions = taskExtensions(taskInfo.get("task"));
            for (WorkflowCard workflow : workflows) {
                if (Objects.equals(entry.getKey(), workflowStateKey(workflow.getId()))) {
                    TaskData taskData = new TaskData(
                            Objects.toString(taskExtensions.getOrDefault("workflow_id", workflow.getId()), workflow.getId()),
                            workflow.getName(),
                            Objects.toString(taskExtensions.getOrDefault("workflow_version", workflow.getVersion()), workflow.getVersion()),
                            Objects.toString(taskExtensions.getOrDefault("agent_id", ""), ""),
                            mapValue(taskExtensions.get("filtered_inputs"))
                    );
                    return Optional.of(new WorkflowMatch(workflow, taskData));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<TaskData> findInterruptedTask(WorkflowCard workflow, AgentSessionApi session) {
        Map<String, Object> state = stateMap(session);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        if (workflow == null || interruptedTasks.isEmpty()) {
            return Optional.empty();
        }

        for (String workflowId : List.of(workflowStateKey(workflow.getId()), workflow.getId())) {
            if (!interruptedTasks.containsKey(workflowId)) {
                continue;
            }
            Map<String, Object> taskInfo = mapValue(interruptedTasks.get(workflowId));
            Map<String, Object> taskExtensions = taskExtensions(taskInfo.get("task"));
            return Optional.of(new TaskData(
                    Objects.toString(taskExtensions.getOrDefault("workflow_id", workflowId), workflowId),
                    "",
                    Objects.toString(taskExtensions.getOrDefault("workflow_version", ""), ""),
                    Objects.toString(taskExtensions.getOrDefault("agent_id", ""), ""),
                    mapValue(taskExtensions.get("filtered_inputs"))
            ));
        }
        return Optional.empty();
    }

    private boolean shouldResume(TaskData taskData, Event event, AgentSessionApi session) {
        InteractiveInput providedInteractiveInput = extractInteractiveInput(event, session);
        if (providedInteractiveInput != null && !providedInteractiveInput.getUserInputs().isEmpty()) {
            return true;
        }

        Map<String, Object> state = stateMap(session);
        if (state.isEmpty()) {
            return true;
        }
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        Map<String, Object> info = mapValue(interruptedTasks.get(workflowStateKey(taskData.targetId())));
        if (info.isEmpty()) {
            return true;
        }
        Object lastValue = info.get("last_interaction_value");
        if (lastValue == null) {
            return true;
        }
        return !(lastValue instanceof Map<?, ?> || lastValue instanceof List<?>);
    }

    private TaskData buildNewTaskData(Event event, WorkflowCard workflow) {
        Object query = extractDisplayValue(event);
        Map<String, Object> schema = mapValue(workflow.getInputParams());
        String requiredKey = getRequiredInputKey(schema).orElse("query");

        Map<String, Object> userData = new LinkedHashMap<>();
        userData.put(requiredKey, query);
        Map<String, Object> extensions = mapValue(event == null ? null : event.getMetadata().get("extensions"));
        userData.putAll(extensions);

        Map<String, Object> filteredInputs = filterWorkflowInputs(schema, userData);
        return new TaskData(workflow.getId(), workflow.getName(), workflow.getVersion(), "", filteredInputs);
    }

    private static Optional<String> getRequiredInputKey(Map<String, Object> schema) {
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
                String keyText = String.valueOf(key);
                if (properties.containsKey(keyText)) {
                    return Optional.of(keyText);
                }
            }
        }
        return properties.containsKey("input") ? Optional.of("input") : Optional.empty();
    }

    private static Map<String, Object> filterWorkflowInputs(Map<String, Object> schema, Map<String, Object> userData) {
        Map<String, Object> properties = mapValue(schema == null ? null : schema.get("properties"));
        if (properties.isEmpty() && schema != null && !schema.isEmpty() && isSimplifiedSchema(schema)) {
            properties = schema;
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : userData.entrySet()) {
            if (properties.isEmpty() || properties.containsKey(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static boolean isSimplifiedSchema(Map<String, Object> schema) {
        for (Object value : schema.values()) {
            if (mapValue(value).containsKey("type")) {
                return true;
            }
        }
        return false;
    }

    private Object extractDisplayValue(Event event) {
        if (!(event instanceof InputEvent inputEvent) || inputEvent.getInputData().isEmpty()) {
            return "";
        }
        DataFrame firstData = inputEvent.getInputData().get(0);
        if (firstData instanceof DataFrame.JsonDataFrame jsonDataFrame) {
            Object query = jsonDataFrame.data().getOrDefault("query", "");
            if (query instanceof String text) {
                return text;
            }
            if (query instanceof InteractiveInput interactiveInput) {
                Iterator<Object> values = interactiveInput.getUserInputs().values().iterator();
                return values.hasNext() ? values.next() : "";
            }
        }
        return "";
    }

    private InteractiveInput extractInteractiveInput(Event event, AgentSessionApi session) {
        if (event != null && event.getMetadata() != null) {
            Object interactiveInput = event.getMetadata().get("interactive_input");
            if (interactiveInput instanceof InteractiveInput typedInteractiveInput) {
                return typedInteractiveInput;
            }
        }

        if (event instanceof InputEvent inputEvent && !inputEvent.getInputData().isEmpty()) {
            DataFrame firstData = inputEvent.getInputData().get(0);
            if (firstData instanceof DataFrame.JsonDataFrame jsonDataFrame) {
                Object inputQuery = jsonDataFrame.data().get("query");
                if (inputQuery instanceof InteractiveInput interactiveInput) {
                    return interactiveInput;
                }
                if (inputQuery instanceof String text && session != null) {
                    String componentId = recoverComponentIdFromSession(session, getWorkflows());
                    if (!componentId.isEmpty()) {
                        InteractiveInput interactiveInput = new InteractiveInput();
                        interactiveInput.update(componentId, text);
                        return interactiveInput;
                    }
                }
            }
        }
        return null;
    }

    private static String recoverComponentIdFromSession(AgentSessionApi session, List<WorkflowCard> workflows) {
        Map<String, Object> state = stateMap(session);
        if (state.isEmpty()) {
            return "";
        }
        Map<String, Object> interruptedInfo = mapValue(state.get(INTERRUPTED_TASKS));
        if (workflows.size() == 1 && !interruptedInfo.isEmpty()) {
            Object first = interruptedInfo.values().iterator().next();
            return Objects.toString(mapValue(first).getOrDefault("component_id", ""), "");
        }
        return "";
    }

    private Intent makeIntent(IntentType intentType, Event event, String targetTaskId,
                              String targetTaskDescription, String supplementaryInfo,
                              String clarificationPrompt, Map<String, Object> metadata) {
        return new Intent(
                intentType,
                event,
                targetTaskId,
                targetTaskDescription,
                null,
                supplementaryInfo,
                null,
                1.0,
                metadata,
                clarificationPrompt
        );
    }

    private ControllerConfig getControllerConfig() {
        return config == null ? new ControllerConfig() : config;
    }

    private static String defaultResponseText(ControllerConfig controllerConfig) {
        ControllerConfig.DefaultResponse response = controllerConfig.getDefaultResponse();
        return response == null || response.getText() == null ? "" : response.getText();
    }

    private com.openjiuwen.core.controller.modules.TaskManager requireTaskManager() {
        if (taskManager == null) {
            throw new IllegalStateException("TaskManager is required");
        }
        return taskManager;
    }

    private String schedulerCardId() {
        if (taskScheduler == null) {
            return "";
        }
        try {
            Field cardField = taskScheduler.getClass().getDeclaredField("card");
            cardField.setAccessible(true);
            Object card = cardField.get(taskScheduler);
            if (card instanceof BaseCard baseCard) {
                return Objects.toString(baseCard.getId(), "");
            }
        } catch (ReflectiveOperationException ignored) {
            LOGGER.fine("TaskScheduler card is unavailable");
        }
        return "";
    }

    private void addUserMessage(Object content, AgentSessionApi session) {
        if (contextEngine == null || session == null) {
            return;
        }
        MessageUtils.addUserMessage(
                content,
                new ContextEngineMessagePort(contextEngine),
                new SessionMessagePort(session.getSessionId())
        ).toCompletableFuture().join();
    }

    private void addAiMessage(String text, AgentSessionApi session) {
        if (contextEngine == null || session == null) {
            return;
        }
        MessageUtils.addAiMessage(
                new AssistantMessage(text),
                new ContextEngineMessagePort(contextEngine),
                new SessionMessagePort(session.getSessionId())
        ).toCompletableFuture().join();
    }

    private static String randomWorkflowTaskId() {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder builder = new StringBuilder("wf_");
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private static List<String> normalizeComponentIds(Object componentId) {
        if (componentId instanceof List<?> list) {
            List<String> values = new ArrayList<>();
            for (Object value : list) {
                String text = Objects.toString(value, "");
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
            return values.isEmpty() ? List.of(QUESTIONER) : values;
        }
        String text = Objects.toString(componentId, "");
        return text.isBlank() ? List.of(QUESTIONER) : List.of(text);
    }

    private static String firstNonBlankComponentId(Object componentId, String fallback) {
        if (componentId instanceof List<?> list) {
            for (Object value : list) {
                String text = Objects.toString(value, "");
                if (!text.isBlank()) {
                    return text;
                }
            }
            return fallback;
        }
        String text = Objects.toString(componentId, "");
        return text.isBlank() ? fallback : text;
    }

    private static Map<String, Object> stateMap(AgentSessionApi session) {
        return mapValue(session == null ? null : session.getState(WORKFLOW_CONTROLLER_STATE));
    }

    private static Map<String, Object> taskExtensions(Object taskObject) {
        if (taskObject instanceof Task task) {
            return mapValue(task.getExtensions());
        }
        return mapValue(mapValue(taskObject).get("extensions"));
    }

    private static Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private record SessionMessagePort(String sessionId) implements MessageUtils.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }

    private record ContextEngineMessagePort(ContextEngine engine) implements MessageUtils.ContextEnginePort {
        @Override
        public MessageUtils.AgentContextPort getContext(String sessionId) {
            return new ModelContextMessagePort(engine.getContext(ContextEngine.DEFAULT_CONTEXT_ID, sessionId));
        }

        @Override
        public MessageUtils.AgentContextPort getContext(String contextId, String sessionId) {
            return new ModelContextMessagePort(engine.getContext(contextId, sessionId));
        }
    }

    private record ModelContextMessagePort(ModelContext context) implements MessageUtils.AgentContextPort {
        @Override
        public List<BaseMessage> getMessages() {
            return context == null ? List.of() : context.getMessages(null, true);
        }

        @Override
        public List<BaseMessage> getMessages(int size) {
            return context == null ? List.of() : context.getMessages(size, true);
        }

        @Override
        public java.util.concurrent.CompletionStage<Void> addMessages(BaseMessage message) {
            if (context == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            }
            return context.addMessages(message).thenApply(ignored -> null);
        }
    }

    /**
     * Mirrors Python's {@code _DetectResult} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    private record DetectResult(Intent intent, WorkflowCard workflow, TaskData taskData) {
    }

    /**
     * Mirrors Python's task-data dict used by {@code WorkflowEventHandler} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    private record TaskData(String targetId, String targetName, String workflowVersion,
                            String agentId, Map<String, Object> arguments) {
    }

    /**
     * Mirrors Python's interrupted workflow lookup tuple in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    private record WorkflowMatch(WorkflowCard workflow, TaskData taskData) {
    }

    /**
     * Mirrors Python's {@code IntentDetectionConstants} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    public static final class IntentDetectionConstants {
        public static final String USER_PROMPT = "user_prompt";
        public static final String CATEGORY_LIST = "category_list";
        public static final String DEFAULT_CLASS = "category0";
        public static final String ENABLE_HISTORY = "enable_history";
        public static final String ENABLE_INPUT = "enable_input";
        public static final String EXAMPLE_CONTENT = "example_content";
        public static final String CHAT_HISTORY_MAX_TURN = "chat_history_max_turn";
        public static final String CHAT_HISTORY = "chat_history";
        public static final String INPUT = "input";

        private IntentDetectionConstants() {
        }
    }

    /**
     * Mirrors Python's {@code IntentDetectionConfig} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    public record IntentDetectionConfig(String categoryInfo, List<String> categoryList,
                                        boolean enableHistory, boolean enableInput,
                                        int chatHistoryMaxTurn, String defaultClass,
                                        List<String> exampleContent) {
        public IntentDetectionConfig {
            categoryInfo = categoryInfo == null ? "" : categoryInfo;
            categoryList = categoryList == null ? List.of() : List.copyOf(categoryList);
            defaultClass = defaultClass == null ? IntentDetectionConstants.DEFAULT_CLASS : defaultClass;
            exampleContent = exampleContent == null ? List.of() : List.copyOf(exampleContent);
        }
    }

    /**
     * Mirrors Python's {@code _TaskInput} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    public record TaskInput(String targetId, String targetName, Object arguments) {
        public TaskInput {
            targetId = targetId == null ? "" : targetId;
            targetName = targetName == null ? "" : targetName;
        }
    }

    /**
     * Mirrors Python's {@code _TaskResult} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    public record TaskResult(String taskId, TaskType taskType, TaskInput input) {
    }

    /**
     * Mirrors Python's {@code IntentDetector} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_event_handler.py}.
     */
    public static class IntentDetector {
        private IntentDetectionConfig intentConfig;
        private ControllerConfig controllerConfig;
        private ContextEngine contextEngine;
        private AgentSessionApi session;
        private Object abilityManager;

        public IntentDetector() {
            this(null, null, null, null, null);
        }

        public IntentDetector(IntentDetectionConfig intentConfig, ControllerConfig controllerConfig,
                              ContextEngine contextEngine, AgentSessionApi session, Object abilityManager) {
            this.intentConfig = intentConfig;
            this.controllerConfig = controllerConfig;
            this.contextEngine = contextEngine;
            this.session = session;
            this.abilityManager = abilityManager;
        }

        public void setSession(AgentSessionApi session) {
            this.session = session;
        }

        public List<TaskResult> processMessage(Event event) {
            String llmOutput = invokeLlmGetOutput(prepareDetectionMessages(event));
            String intentId = parseIntentFromOutput(llmOutput);
            return generateTasksFromIntent(intentId);
        }

        public List<TaskResult> generateTasksFromIntent(String intentId) {
            if (intentId == null || IntentDetectionConstants.DEFAULT_CLASS.equals(intentId)) {
                return List.of();
            }
            String sessionId = session == null ? "" : session.getSessionId();
            String taskId = sessionId + "_intent_" + intentId + "_" + randomWorkflowTaskId().substring(3, 11);
            List<WorkflowCard> workflows = workflowsFromAbilityManager();
            if (workflows.isEmpty()) {
                return List.of(new TaskResult(
                        taskId,
                        TaskType.WORKFLOW,
                        new TaskInput(intentId, intentId, null)
                ));
            }
            for (WorkflowCard workflow : workflows) {
                if (Objects.equals(workflow.getId(), intentId)) {
                    return List.of(new TaskResult(
                            taskId,
                            TaskType.WORKFLOW,
                            new TaskInput(workflow.getId(), workflow.getName(), null)
                    ));
                }
            }
            return List.of();
        }

        private List<WorkflowCard> workflowsFromAbilityManager() {
            if (abilityManager instanceof AbilityManager typedManager) {
                List<WorkflowCard> workflows = new ArrayList<>();
                for (Object item : typedManager.list()) {
                    if (item instanceof WorkflowCard workflowCard) {
                        workflows.add(workflowCard);
                    }
                }
                return workflows;
            }
            return List.of();
        }

        private List<BaseMessage> prepareDetectionMessages(Event event) {
            IntentDetectionConfig effectiveConfig = intentConfig == null
                    ? new IntentDetectionConfig("", List.of(), true, true, 100,
                    IntentDetectionConstants.DEFAULT_CLASS, List.of())
                    : intentConfig;
            StringBuilder categoryText = new StringBuilder("category0: unknown intent");
            for (int i = 0; i < effectiveConfig.categoryList().size(); i++) {
                categoryText.append('\n')
                        .append("category")
                        .append(i + 1)
                        .append(": ")
                        .append(effectiveConfig.categoryList().get(i));
            }

            String systemPrompt = "You are an intent classification assistant. "
                    + "Return JSON only in the form {\"result\": number}. "
                    + "Use 0 when the input is unclear.\n"
                    + categoryText;
            String userPrompt = "Current input: " + extractQuery(event);
            return List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
        }

        private String invokeLlmGetOutput(List<BaseMessage> messages) {
            ControllerConfig effectiveConfig = controllerConfig == null ? new ControllerConfig() : controllerConfig;
            Object modelObject = Runner.resourceMgr()
                    .getModel(effectiveConfig.getIntentLlmId(), session)
                    .toCompletableFuture()
                    .join();
            if (modelObject instanceof Model model) {
                AssistantMessage message = model.invoke(messages).toCompletableFuture().join();
                return message == null ? "" : message.getContentAsString().strip();
            }
            try {
                Method invokeMethod = modelObject.getClass().getMethod("invoke", List.class);
                Object output = invokeMethod.invoke(modelObject, messages);
                Object resolved = output instanceof java.util.concurrent.CompletionStage<?> stage
                        ? stage.toCompletableFuture().join()
                        : output;
                if (resolved instanceof AssistantMessage assistantMessage) {
                    return assistantMessage.getContentAsString().strip();
                }
                return Objects.toString(resolved, "").strip();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("intent model does not expose invoke(List<BaseMessage>)", error);
            }
        }

        private String parseIntentFromOutput(String llmOutput) {
            try {
                String cleaned = cleanJsonFence(llmOutput);
                Map<String, Object> output = JSON.readValue(cleaned, new TypeReference<>() {
                });
                int classNumber = Integer.parseInt(Objects.toString(output.getOrDefault("result", ""), ""));
                IntentDetectionConfig effectiveConfig = intentConfig == null
                        ? new IntentDetectionConfig("", List.of(), true, true, 100,
                        IntentDetectionConstants.DEFAULT_CLASS, List.of())
                        : intentConfig;
                List<String> categories = effectiveConfig.categoryList();
                if (classNumber <= 0 || classNumber > categories.size()) {
                    return IntentDetectionConstants.DEFAULT_CLASS;
                }
                String categoryName = categories.get(classNumber - 1);
                List<WorkflowCard> workflows = workflowsFromAbilityManager();
                if (workflows.isEmpty()) {
                    return categoryName;
                }
                for (WorkflowCard workflow : workflows) {
                    String workflowLabel = !workflow.getDescription().isEmpty()
                            ? workflow.getDescription()
                            : workflow.getName();
                    if (Objects.equals(workflowLabel, categoryName)) {
                        return workflow.getId();
                    }
                }
            } catch (RuntimeException | java.io.IOException error) {
                return IntentDetectionConstants.DEFAULT_CLASS;
            }
            return IntentDetectionConstants.DEFAULT_CLASS;
        }

        private static String cleanJsonFence(String text) {
            String cleaned = Objects.toString(text, "").strip();
            cleaned = cleaned.replaceFirst("(?is)^```json\\s*", "");
            cleaned = cleaned.replaceFirst("(?is)^'''json\\s*", "");
            cleaned = cleaned.replaceFirst("(?is)\\s*```$", "");
            cleaned = cleaned.replaceFirst("(?is)\\s*'''$", "");
            return cleaned.strip();
        }

        private static String extractQuery(Event event) {
            if (!(event instanceof InputEvent inputEvent) || inputEvent.getInputData().isEmpty()) {
                return "";
            }
            DataFrame firstData = inputEvent.getInputData().get(0);
            if (firstData instanceof DataFrame.JsonDataFrame jsonDataFrame) {
                Object query = jsonDataFrame.data().get("query");
                return query instanceof String text ? text : "";
            }
            return "";
        }

        public IntentDetectionConfig getIntentConfig() {
            return intentConfig;
        }

        public ControllerConfig getControllerConfig() {
            return controllerConfig;
        }

        public ContextEngine getContextEngine() {
            return contextEngine;
        }
    }
}
