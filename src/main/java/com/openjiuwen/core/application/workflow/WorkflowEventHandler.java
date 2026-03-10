/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2025. All rights reserved.
 */
package com.openjiuwen.core.application.workflow;

import com.openjiuwen.core.application.schema.DefaultResponse;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.modules.EventHandler;
import com.openjiuwen.core.controller.modules.EventHandlerInput;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Workflow Controller - Implements workflow-specific execution logic.
 *
 * <p>Core responsibilities:
 * <ol>
 *   <li>Intent detection: Select workflow + Check interruption state</li>
 *   <li>Task execution: Execute workflow (new/resume)</li>
 *   <li>Interruption handling: Save interruption state to session.state</li>
 * </ol>
 *
 * <p>Mirrors Python's {@code WorkflowController} in
 * {@code openjiuwen.core.application.workflow_agent}.</p>
 */
public class WorkflowEventHandler extends EventHandler {

    private static final String INTERACTION = "__interaction__";
    private static final String STATE_KEY = "workflow_controller";

    private final WorkflowAgentConfig agentConfig;
    private final ContextEngine appContextEngine;

    public WorkflowEventHandler(WorkflowAgentConfig agentConfig, ContextEngine contextEngine) {
        this.agentConfig = agentConfig;
        this.appContextEngine = contextEngine;
    }

    // ==================== EventHandler Implementation ====================

    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Event event = inputs.getEvent();
        AgentSessionApi session = inputs.getSession();

        try {
            return handleUserInput(event, session);
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Error in workflow handling: {}", e.getMessage());
            throw ErrorHelper.buildError(
                    StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage()
            );
        }
    }

    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task interaction received");
        return null;
    }

    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task completion received");
        return null;
    }

    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task failed received");
        return null;
    }

    // ==================== Core Logic ====================

    /**
     * Handle user input: detect intent, select workflow, execute or resume.
     */
    private Map<String, Object> handleUserInput(Event event, AgentSessionApi session) {
        List<WorkflowSchema> workflows = agentConfig.getWorkflows();
        if (workflows == null || workflows.isEmpty()) {
            throw new IllegalStateException("No workflows configured for agent");
        }

        // Fast path: InteractiveInput with node_id - directly resume workflow
        InteractiveInput interactiveInput = extractInteractiveInput(event);
        if (interactiveInput != null && interactiveInput.getUserInputs() != null
                && !interactiveInput.getUserInputs().isEmpty()) {
            ResumeByNodeResult resumeResult = findInterruptedTaskByNodeId(interactiveInput, session);
            if (resumeResult != null) {
                Loggers.CONTROLLER.info("InteractiveInput detected, directly resuming workflow: {}",
                        resumeResult.workflow.getName());
                setTaskArguments(resumeResult.task, interactiveInput);
                return execTask(event, resumeResult.task, session, resumeResult.workflow);
            }
        }

        // Select workflow
        WorkflowSchema detectedWorkflow;
        if (workflows.size() == 1) {
            detectedWorkflow = workflows.get(0);
            Loggers.CONTROLLER.info("Single workflow mode: using {}", detectedWorkflow.getName());
        } else {
            detectedWorkflow = detectWorkflowViaLlm(event, session);
            if (detectedWorkflow == null) {
                DefaultResponse defaultResponse = agentConfig.getDefaultResponse();
                if (defaultResponse != null && defaultResponse.getText() != null
                        && !defaultResponse.getText().isEmpty()) {
                    Loggers.CONTROLLER.info("Using default response: {}", defaultResponse.getText());
                    Map<String, Object> result = new HashMap<>();
                    result.put("output", defaultResponse.getText());
                    result.put("result_type", "answer");
                    OutputSchema os = new OutputSchema("answer", 0, result);
                    session.writeStream(os);
                    return result;
                }
                detectedWorkflow = workflows.get(0);
            }
            Loggers.CONTROLLER.info("Multi workflow mode: detected {}", detectedWorkflow.getName());
        }

        // Check for interrupted task
        Task interruptedTask = findInterruptedTask(detectedWorkflow, session);
        if (interruptedTask != null) {
            boolean shouldResume = shouldResumeInterruptedTask(interruptedTask, event, session);
            if (shouldResume) {
                Loggers.CONTROLLER.info("Resuming interrupted task for workflow {}", detectedWorkflow.getName());
                // Update task with user input
                setTaskArguments(interruptedTask, buildResumeArguments(interactiveInput, interruptedTask, event, session));
                interruptedTask.setStatus(TaskStatus.INPUT_REQUIRED);
                return execTask(event, interruptedTask, session, detectedWorkflow);
            } else {
                Loggers.CONTROLLER.info(
                        "Returning saved interruption for workflow {}", detectedWorkflow.getName());
                return returnSavedInterruption(detectedWorkflow, session);
            }
        }

        // Create new task
        Loggers.CONTROLLER.info("No interrupted task for workflow {}, creating new task",
                detectedWorkflow.getName());
        Task newTask = createNewTask(event, detectedWorkflow, session);
        return execTask(event, newTask, session, detectedWorkflow);
    }

    // ==================== Task Execution ====================

    /**
     * Execute workflow task.
     */
    private Map<String, Object> execTask(Event event, Task task,
                                          AgentSessionApi session, WorkflowSchema workflowSchema) {
        String workflowId = workflowSchema.getId() + "_" + workflowSchema.getVersion();
        boolean isResume = task.getStatus() == TaskStatus.INPUT_REQUIRED;

        try {
            task.setStatus(TaskStatus.WORKING);

            // Get workflow object
            Object workflow = Runner.resourceMgr().getWorkflow(workflowId);
            if (workflow == null) {
                throw new IllegalStateException("Workflow not found: " + workflowId);
            }

            // Create workflow session
            WorkflowSessionApi workflowSession = session.createWorkflowSession();

            // Prepare inputs
            Object inputs = getTaskArguments(task);
            if (isResume) {
                Loggers.CONTROLLER.info("Resuming workflow: {}", workflowId);
            } else {
                Loggers.CONTROLLER.info("Starting workflow: {}", workflowId);
            }

            // Execute workflow with streaming
            ModelContext context = appContextEngine.createContext(workflowId, session.getInner());
            Iterator<Object> workflowStream = Runner.runWorkflowStreaming(
                    workflow, inputs, workflowSession, context, null);

            List<Object> chunks = new ArrayList<>();
            boolean hasInteraction = false;
            Object finalResult = null;

            while (workflowStream.hasNext()) {
                Object chunk = workflowStream.next();
                if (chunk instanceof OutputSchema os) {
                    if (INTERACTION.equals(os.getType())) {
                        hasInteraction = true;
                        // Don't pass through interaction here
                    } else if ("workflow_final".equals(os.getType())) {
                        finalResult = os.getPayload();
                        session.writeStream(os);
                    } else {
                        session.writeStream(os);
                    }
                } else {
                    session.writeStream(chunk);
                }
                chunks.add(chunk);
            }

            // Add context messages
            if (!chunks.isEmpty()) {
                StringBuilder contentParts = new StringBuilder();
                for (Object chunk : chunks) {
                    if (chunk instanceof OutputSchema os) {
                        if (os.getPayload() instanceof Map<?, ?> payloadMap) {
                            Object response = payloadMap.get("response");
                            if (response != null) {
                                contentParts.append(response);
                            }
                        } else if (os.getPayload() instanceof InteractionOutput io) {
                            if (io.getValue() != null) {
                                contentParts.append(io.getValue());
                            }
                        }
                    }
                }
                context.addMessages(new AssistantMessage(contentParts.toString()));
            }

            // Process result
            if (hasInteraction) {
                // Workflow interrupted
                Loggers.CONTROLLER.info("Workflow interrupted: {}", workflowId);
                task.setStatus(TaskStatus.INPUT_REQUIRED);

                interruptTask(task, session, chunks);

                // Return only first interrupt for streaming
                List<Object> firstInterrupt = getFirstInterrupt(chunks);
                Loggers.CONTROLLER.info("Workflow has {} interrupts, returning first for streaming",
                        countInteractions(chunks));

                // Write interrupted stream data
                for (Object item : firstInterrupt) {
                    session.writeStream(item);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("interaction", firstInterrupt);
                return result;
            } else {
                // Workflow completed
                Loggers.CONTROLLER.info("Workflow completed: {}", workflowId);
                task.setStatus(TaskStatus.COMPLETED);
                clearInterruptedState(task, session, workflowId);

                Map<String, Object> result = new HashMap<>();
                result.put("output", finalResult != null ? finalResult : "");
                result.put("result_type", "answer");
                return result;
            }

        } catch (Exception e) {
            Loggers.CONTROLLER.error("Workflow execution failed: {}, error: {}", workflowId, e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            if (e instanceof BaseError be) {
                throw be;
            }
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR,
                    "error_msg", e.getMessage());
        }
    }

    // ==================== Interruption Handling ====================

    private void interruptTask(Task task, AgentSessionApi session, List<Object> interactionData) {
        String workflowId = task.getMetadata() != null
                ? (String) task.getMetadata().get("workflow_id") : "";
        if (workflowId == null || workflowId.isEmpty()) {
            workflowId = "unknown";
        }

        task.setStatus(TaskStatus.INPUT_REQUIRED);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            state = new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> interruptedTasks = (Map<String, Object>) state.computeIfAbsent(
                "interrupted_tasks", k -> new HashMap<>());

        Object componentId = extractComponentIdFromInteractionData(interactionData);
        Object interactionValue = extractInteractionValueFromInteractionData(interactionData);
        String stateKey = workflowId.replace('.', '_');

        Map<String, Object> taskInfo = new HashMap<>();
        Map<String, Object> taskData = new HashMap<>();
        taskData.put("session_id", task.getSessionId());
        taskData.put("task_id", task.getTaskId());
        taskData.put("task_type", task.getTaskType());
        taskData.put("description", task.getDescription());
        taskData.put("status", task.getStatus().getValue());
        taskData.put("inputs", task.getInputs());
        taskData.put("metadata", task.getMetadata());
        taskData.put("extensions", task.getExtensions());
        taskInfo.put("task", taskData);
        taskInfo.put("component_id", componentId);
        taskInfo.put("last_interaction_value", interactionValue);
        interruptedTasks.put(stateKey, taskInfo);

        // Save state
        session.updateState(Map.of(STATE_KEY, (Object) state));

        Loggers.CONTROLLER.info("Task interrupted: workflow={}, state_key={}, component_id={}",
                workflowId, stateKey, componentId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> returnSavedInterruption(WorkflowSchema workflow, AgentSessionApi session) {
        String workflowId = workflow.getId() + "_" + workflow.getVersion();
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return Map.of("output", "", "result_type", "answer");
        }

        String stateKey = workflowId.replace('.', '_');
        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return Map.of("output", "", "result_type", "answer");
        }

        Map<String, Object> interrupted = (Map<String, Object>) interruptedTasks.get(stateKey);
        if (interrupted == null) {
            return Map.of("output", "", "result_type", "answer");
        }

        Object componentIdObj = interrupted.getOrDefault("component_id", "questioner");
        String componentId;
        if (componentIdObj instanceof List<?> list && !list.isEmpty()) {
            componentId = String.valueOf(list.get(0));
        } else {
            componentId = componentIdObj instanceof String s ? s : "questioner";
        }
        Object lastValue = interrupted.get("last_interaction_value");

        if (lastValue == null) {
            return Map.of("output", "", "result_type", "answer");
        }

        InteractionOutput interactionOutput = new InteractionOutput(componentId, lastValue);
        OutputSchema os = new OutputSchema(INTERACTION, 0, interactionOutput);
        session.writeStream(os);

        Map<String, Object> result = new HashMap<>();
        result.put("interaction", List.of(os));
        return result;
    }

    // ==================== Intent Detection ====================

    private WorkflowSchema detectWorkflowViaLlm(Event event, AgentSessionApi session) {
        // Simple implementation: return first workflow
        // Full LLM-based detection would call the model with workflow descriptions
        List<WorkflowSchema> workflows = agentConfig.getWorkflows();
        if (workflows == null || workflows.isEmpty()) {
            return null;
        }
        Loggers.CONTROLLER.info("Using first workflow as default (LLM detection not yet implemented)");
        return workflows.get(0);
    }

    private boolean shouldResumeInterruptedTask(Task task, Event event, AgentSessionApi session) {
        // If user provides InteractiveInput, always resume
        InteractiveInput interactiveInput = extractInteractiveInput(event);
        if (interactiveInput != null && interactiveInput.getUserInputs() != null
                && !interactiveInput.getUserInputs().isEmpty()) {
            return true;
        }

        // Check last_interaction_value type
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return true;
        }

        String workflowId = task.getMetadata() != null
                ? (String) task.getMetadata().get("workflow_id") : "";
        if (workflowId == null) {
            return true;
        }
        String stateKey = workflowId.replace('.', '_');

        @SuppressWarnings("unchecked")
        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return true;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> interrupted = (Map<String, Object>) interruptedTasks.get(stateKey);
        if (interrupted == null) {
            return true;
        }

        Object lastValue = interrupted.get("last_interaction_value");
        if (lastValue == null) {
            return true;
        }

        // Dict/List = structured data → return interruption again; String = resume
        if (lastValue instanceof Map || lastValue instanceof List) {
            Loggers.CONTROLLER.info("last_interaction_value is structured data, returning interruption again");
            return false;
        }
        return true;
    }

    // ==================== Resumption Helpers ====================

    @SuppressWarnings("unchecked")
    private ResumeByNodeResult findInterruptedTaskByNodeId(InteractiveInput interactiveInput,
                                                           AgentSessionApi session) {
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return null;
        }

        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null || interruptedTasks.isEmpty()) {
            return null;
        }

        List<String> nodeIds = new ArrayList<>(interactiveInput.getUserInputs().keySet());
        if (nodeIds.isEmpty()) {
            return null;
        }

        Loggers.CONTROLLER.info("Looking for interrupted task with node_ids={}", nodeIds);

        for (Map.Entry<String, Object> entry : interruptedTasks.entrySet()) {
            Map<String, Object> taskInfo = (Map<String, Object>) entry.getValue();
            Object componentIdObj = taskInfo.get("component_id");

            boolean matched = false;
            if (componentIdObj instanceof List<?> cidList) {
                matched = nodeIds.stream().anyMatch(nid -> cidList.contains(nid));
            } else if (componentIdObj instanceof String cid) {
                matched = nodeIds.contains(cid);
            }

            if (matched) {
                Map<String, Object> taskData = (Map<String, Object>) taskInfo.get("task");
                Task task = deserializeTask(taskData);

                for (WorkflowSchema ws : agentConfig.getWorkflows()) {
                    String baseId = ws.getId() + "_" + ws.getVersion().replace('.', '_');
                    if (entry.getKey().equals(baseId) || entry.getKey().equals(ws.getId())) {
                        return new ResumeByNodeResult(ws, task);
                    }
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Task findInterruptedTask(WorkflowSchema workflow, AgentSessionApi session) {
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return null;
        }

        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return null;
        }

        String baseIdWithVersion = workflow.getId() + "_" + workflow.getVersion().replace('.', '_');
        String[] possibleIds = {baseIdWithVersion, workflow.getId()};

        for (String key : possibleIds) {
            Map<String, Object> taskInfo = (Map<String, Object>) interruptedTasks.get(key);
            if (taskInfo != null) {
                Loggers.CONTROLLER.info("Found interrupted task for {}", key);
                Map<String, Object> taskData = (Map<String, Object>) taskInfo.get("task");
                return deserializeTask(taskData);
            }
        }
        return null;
    }

    // ==================== Task Creation ====================

    private Task createNewTask(Event event, WorkflowSchema workflow, AgentSessionApi session) {
        String query = getDisplayContent(event);

        // Determine required input key
        Map<String, Object> schema = workflow.getInputParams();
        String requiredKey = getRequiredInputKey(schema);
        if (requiredKey == null) {
            requiredKey = "query";
        }

        Map<String, Object> inputs = new HashMap<>();
        inputs.put(requiredKey, query);

        // Filter inputs
        if (schema != null && !schema.isEmpty()) {
            inputs = filterWorkflowInputs(schema, inputs);
        }

        String workflowId = workflow.getId() + "_" + workflow.getVersion();
        String taskId = "workflow_" + event.getEventId();

        Task task = new Task(
                session.getSessionId(),
                taskId, "workflow"
        );
        task.setDescription(workflow.getName());
        task.setStatus(TaskStatus.SUBMITTED);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("target_name", workflow.getName());
        metadata.put("target_id", workflowId);
        metadata.put("arguments", inputs);
        metadata.put("workflow_id", workflowId);
        task.setMetadata(metadata);

        return task;
    }

    private String getRequiredInputKey(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null || properties.isEmpty()) {
            return null;
        }

        if (properties.containsKey("query")) {
            return "query";
        }

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) schema.get("required");
        if (required != null) {
            for (String key : required) {
                if (properties.containsKey(key)) {
                    return key;
                }
            }
        }

        if (properties.containsKey("input")) {
            return "input";
        }
        return null;
    }

    private Map<String, Object> filterWorkflowInputs(Map<String, Object> schema, Map<String, Object> userData) {
        Map<String, Object> filtered = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.getOrDefault("properties", schema);

        for (Map.Entry<String, Object> entry : userData.entrySet()) {
            if (properties.containsKey(entry.getKey()) || properties.isEmpty()) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    // ==================== Utility Methods ====================

    private String getDisplayContent(Event event) {
        Map<String, Object> inputMap = extractInputMap(event);
        if (!inputMap.isEmpty()) {
            Object query = inputMap.get("query");
            if (query instanceof String s) {
                return s;
            }
            if (query instanceof InteractiveInput interactiveInput) {
                Object rawInputs = interactiveInput.getRawInputs();
                return rawInputs != null ? String.valueOf(rawInputs) : "";
            }
            Object content = inputMap.get("content");
            if (content instanceof String s) {
                return s;
            }
        }
        if (event == null) {
            return "";
        }
        if (event instanceof InputEvent ie) {
            var inputData = ie.getInputData();
            if (inputData != null && !inputData.isEmpty()) {
                Object primaryInput = extractPrimaryInput(ie);
                return primaryInput != null ? primaryInput.toString() : "";
            }
        }
        return "";
    }

    private InteractiveInput extractInteractiveInput(Event event) {
        Map<String, Object> inputMap = extractInputMap(event);
        Object directInteractiveInput = inputMap.get("interactive_input");
        if (directInteractiveInput instanceof InteractiveInput ii) {
            return ii;
        }
        Object query = inputMap.get("query");
        if (query instanceof InteractiveInput ii) {
            return ii;
        }
        if (event != null && event.getMetadata() != null) {
            Object input = event.getMetadata().get("interactive_input");
            if (input instanceof InteractiveInput ii) {
                return ii;
            }
        }
        return null;
    }

    private void clearInterruptedState(Task task, AgentSessionApi session, String workflowId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return;
        }
        String stateKey = workflowId.replace('.', '_');
        if (interruptedTasks.remove(stateKey) != null) {
            session.updateState(Map.of(STATE_KEY, state));
            Loggers.CONTROLLER.info("Cleared interrupted state for workflow: {}", workflowId);
        }
    }

    private Object extractComponentIdFromInteractionData(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return "questioner";
        }

        List<String> componentIds = new ArrayList<>();
        for (Object item : interactionData) {
            if (item instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                Object payload = os.getPayload();
                if (payload instanceof InteractionOutput io && io.getId() != null) {
                    componentIds.add(io.getId());
                }
            }
        }

        if (componentIds.isEmpty()) {
            return "questioner";
        }
        return componentIds.size() == 1 ? componentIds.get(0) : componentIds;
    }

    private Object extractInteractionValueFromInteractionData(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return null;
        }

        for (Object item : interactionData) {
            if (item instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                Object payload = os.getPayload();
                if (payload instanceof InteractionOutput io) {
                    return io.getValue();
                }
            }
        }
        return null;
    }

    private List<Object> getFirstInterrupt(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return List.of();
        }

        boolean firstFound = false;
        List<Object> result = new ArrayList<>();
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                if (!firstFound) {
                    result.add(chunk);
                    firstFound = true;
                }
            } else {
                result.add(chunk);
            }
        }
        return result;
    }

    private int countInteractions(List<Object> interactionData) {
        if (interactionData == null) {
            return 0;
        }
        int count = 0;
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                count++;
            }
        }
        return count;
    }

    private boolean isWorkflowInterrupted(Object result) {
        if (result instanceof WorkflowOutput wo) {
            return wo.getState() == WorkflowExecutionState.INPUT_REQUIRED;
        }
        return false;
    }

    private Task deserializeTask(Map<String, Object> data) {
        if (data == null) {
            return new Task();
        }
        String sessionId = (String) data.getOrDefault("session_id", "");
        String taskId = (String) data.getOrDefault("task_id", "");
        String taskType = (String) data.getOrDefault("task_type", "");
        Task task = new Task(sessionId, taskId, taskType);
        task.setDescription((String) data.get("description"));
        String status = (String) data.get("status");
        if (status != null) {
            task.setStatus(TaskStatus.fromValue(status));
        }
        @SuppressWarnings("unchecked")
        List<Object> inputs = (List<Object>) data.get("inputs");
        task.setInputs(inputs);
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        task.setMetadata(metadata);
        @SuppressWarnings("unchecked")
        Map<String, Object> extensions = (Map<String, Object>) data.get("extensions");
        task.setExtensions(extensions);
        return task;
    }

    private Map<String, Object> extractInputMap(Event event) {
        if (!(event instanceof InputEvent inputEvent)) {
            return Map.of();
        }
        Object primaryInput = extractPrimaryInput(inputEvent);
        if (primaryInput instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return typedMap;
        }
        return Map.of();
    }

    private Object extractPrimaryInput(InputEvent event) {
        if (event == null || event.getInputData() == null || event.getInputData().isEmpty()) {
            return null;
        }
        DataFrame firstInput = event.getInputData().get(0);
        if (firstInput instanceof DataFrame.TextDataFrame textDataFrame) {
            return textDataFrame.text();
        }
        if (firstInput instanceof DataFrame.JsonDataFrame jsonDataFrame) {
            return jsonDataFrame.data();
        }
        return firstInput;
    }

    private void setTaskArguments(Task task, Object arguments) {
        if (task.getMetadata() == null) {
            task.setMetadata(new HashMap<>());
        }
        task.getMetadata().put("arguments", arguments);
    }

    private Object getTaskArguments(Task task) {
        if (task.getMetadata() != null && task.getMetadata().containsKey("arguments")) {
            return task.getMetadata().get("arguments");
        }
        if (task.getInputs() == null || task.getInputs().isEmpty()) {
            return Map.of();
        }
        if (task.getInputs().size() == 1) {
            return task.getInputs().get(0);
        }
        return task.getInputs();
    }

    private Object buildResumeArguments(
            InteractiveInput interactiveInput,
            Task task,
            Event event,
            AgentSessionApi session
    ) {
        if (interactiveInput != null) {
            return interactiveInput;
        }
        String fallbackQuery = getDisplayContent(event);
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null || task.getMetadata() == null) {
            return new InteractiveInput(fallbackQuery);
        }
        String workflowId = (String) task.getMetadata().get("workflow_id");
        if (workflowId == null || workflowId.isEmpty()) {
            return new InteractiveInput(fallbackQuery);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return new InteractiveInput(fallbackQuery);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> interrupted = (Map<String, Object>) interruptedTasks.get(workflowId.replace('.', '_'));
        if (interrupted == null) {
            return new InteractiveInput(fallbackQuery);
        }
        Object componentIdObj = interrupted.get("component_id");
        if (componentIdObj instanceof List<?> list && !list.isEmpty()) {
            InteractiveInput resumedInput = new InteractiveInput();
            for (Object componentId : list) {
                resumedInput.update(String.valueOf(componentId), fallbackQuery);
            }
            return resumedInput;
        }
        if (componentIdObj instanceof String componentId && !componentId.isBlank()) {
            InteractiveInput resumedInput = new InteractiveInput();
            resumedInput.update(componentId, fallbackQuery);
            return resumedInput;
        }
        return new InteractiveInput(fallbackQuery);
    }

    // ==================== Inner Records ====================

    private record ResumeByNodeResult(WorkflowSchema workflow, Task task) {
    }
}
