/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.session.tracer.TracerDecorator;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Workflow Controller - Implements workflow-specific execution logic.
 * <p>
 * Core responsibilities:
 * <ol>
 * <li>Intent detection: Select workflow + Check interruption state</li>
 * <li>Task execution: Execute workflow (new/resume)</li>
 * <li>Interruption handling: Save interruption state to session.state</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code WorkflowController} in
 * {@code openjiuwen.core.application.workflow_agent}.
 * </p>
 * 
 * @since 0.1.7
 */
public class WorkflowEventHandler extends EventHandler {
    private static final String INTERACTION = "__interaction__";
    private static final String STATE_KEY = "workflow_controller";
    private static final String CALL_MODE_STATE_KEY = "__workflow_agent_call_mode";
    private static final long CANCEL_GRACE_TIMEOUT_SECONDS = 5L;

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_INTENT_CLASS = "分类0";
    private static final String INTENT_SYSTEM_PROMPT = """
            你是一个意图分类助手，擅长判断用户的输入属于哪个分类。
            当用户输入没有明确意图或者你无法判断用户输入意图时请选择 {{default_class}}。
            以下是给定的意图分类列表：
            {{category_list}}
            {{example_content}}
            请根据上述要求判断用户输入意图分类，输出要求如下：
            直接以JSON格式输出分类ID，不进行任何解释。JSON格式如下：
            {"result": int}""";
    private static final String INTENT_USER_PROMPT = """
            用户与助手的对话历史：
            {{chat_history}}
            当前输入：
            {{input}}""";

    private final WorkflowAgentConfig agentConfig;
    private final ContextEngine appContextEngine;
    private final Map<String, RunningWorkflow> runningWorkflows = new ConcurrentHashMap<>();

    /**
     * WorkflowEventHandler.
     * 
     * @param agentConfig agentConfig
     * @param contextEngine contextEngine
     * @since 0.1.7
     */
    public WorkflowEventHandler(WorkflowAgentConfig agentConfig, ContextEngine contextEngine) {
        this.agentConfig = agentConfig;
        this.appContextEngine = contextEngine;
    }

    // ==================== EventHandler Implementation ====================

    /**
     * handleInput.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> handleInput(EventHandlerInput inputs) {
        Event event = inputs.getEvent();
        AgentSessionApi session = inputs.getSession();

        try {
            return handleUserInput(event, session);
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            BaseError nested = findNestedBaseError(e);
            if (nested != null) {
                throw nested;
            }
            Loggers.CONTROLLER.error("Error in workflow handling: {}", e.getMessage());
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg", e.getMessage());
        }
    }

    /**
     * handleTaskInteraction.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> handleTaskInteraction(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task interaction received");
        return null;
    }

    /**
     * handleTaskCompletion.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> handleTaskCompletion(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task completion received");
        return null;
    }

    /**
     * handleTaskFailed.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> handleTaskFailed(EventHandlerInput inputs) {
        Loggers.CONTROLLER.info("Workflow task failed received");
        return null;
    }

    // ==================== Core Logic ====================

    /**
     * Handle user input: detect intent, select workflow, execute or resume.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> handleUserInput(Event event, AgentSessionApi session) {
        InteractiveInput interactiveInput = extractInteractiveInput(event);
        WorkflowIntent intent = intentDetection(event, session);
        if (intent == null) {
            return Map.of("output", "", "result_type", "answer");
        }
        addUserMessageToAgentContext(session, getDisplayContent(event));

        return switch (intent.intentType()) {
            case DEFAULT_RESPONSE -> {
                String defaultText = String.valueOf(intent.metadata().getOrDefault("default_response_text", ""));
                Loggers.CONTROLLER.info("Using default response: {}", defaultText);
                Map<String, Object> finalPayload = new LinkedHashMap<>();
                finalPayload.put("response", defaultText);
                finalPayload.put("output", Map.of());
                session.writeStream(new OutputSchema("workflow_final", 0, finalPayload));
                addAssistantMessageToAgentContext(session, defaultText);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "default_response");
                result.put("output", Map.of("answer", defaultText));
                result.put("result_type", "answer");
                yield result;
            }
            case RESUME_TASK -> {
                WorkflowSchema workflow = intent.workflow();
                Task task = intent.task();
                if (Boolean.TRUE.equals(intent.metadata().get("return_interruption"))) {
                    Loggers.CONTROLLER.info("Returning saved interruption for workflow {}", workflow.getName());
                    yield returnSavedInterruption(workflow, session);
                }
                Loggers.CONTROLLER.info("Resuming interrupted task for workflow {}", workflow.getName());
                setTaskArguments(task, buildResumeArguments(interactiveInput, task, event, session));
                task.setStatus(TaskStatus.INPUT_REQUIRED);
                yield execTask(event, task, session, workflow);
            }
            case EXEC_NEW_TASK -> {
                Loggers.CONTROLLER.info("No interrupted task for workflow {}, creating new task",
                        intent.workflow().getName());
                yield execTask(event, intent.task(), session, intent.workflow());
            }
        };
    }

    WorkflowIntent intentDetection(Event event, AgentSessionApi session) {
        List<WorkflowSchema> workflows = uniqueWorkflows(agentConfig.getWorkflows());
        if (workflows == null || workflows.isEmpty()) {
            throw new IllegalStateException("No workflows configured for agent");
        }

        InteractiveInput interactiveInput = extractInteractiveInput(event);
        if (interactiveInput != null && interactiveInput.getUserInputs() != null
                && !interactiveInput.getUserInputs().isEmpty()) {
            ResumeByNodeResult resumeResult = findInterruptedTaskByNodeId(interactiveInput, session);
            if (resumeResult != null) {
                Loggers.CONTROLLER.info("InteractiveInput detected, directly resuming workflow: {}",
                        resumeResult.workflow.getName());
                return new WorkflowIntent(WorkflowIntent.Type.RESUME_TASK, resumeResult.task, resumeResult.workflow,
                        Map.of());
            }
        }

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
                    return new WorkflowIntent(WorkflowIntent.Type.DEFAULT_RESPONSE, null, null,
                            Map.of("default_response_text", defaultResponse.getText()));
                }
                detectedWorkflow = workflows.get(0);
            }
            Loggers.CONTROLLER.info("Multi workflow mode: detected {}", detectedWorkflow.getName());
        }

        Task interruptedTask = findInterruptedTask(detectedWorkflow, session);
        if (interruptedTask != null) {
            boolean shouldResume = shouldResumeInterruptedTask(interruptedTask, event, session);
            if (shouldResume) {
                return new WorkflowIntent(WorkflowIntent.Type.RESUME_TASK, interruptedTask, detectedWorkflow, Map.of());
            }
            return new WorkflowIntent(WorkflowIntent.Type.RESUME_TASK, interruptedTask, detectedWorkflow,
                    Map.of("return_interruption", true));
        }

        Task newTask = createNewTask(event, detectedWorkflow, session);
        return new WorkflowIntent(WorkflowIntent.Type.EXEC_NEW_TASK, newTask, detectedWorkflow, Map.of());
    }

    // ==================== Task Execution ====================

    /**
     * Execute workflow task.
     *
     * @param event Event
     * @param task Task
     * @param session AgentSessionApi
     * @param workflowSchema WorkflowSchema
     * @return Map<String, Object>
     */
    Map<String, Object> execTask(Event event, Task task, AgentSessionApi session, WorkflowSchema workflowSchema) {
        String workflowId = workflowSchema.getId() + "_" + workflowSchema.getVersion();
        String conversationId = session.getSessionId();
        RunningWorkflow runningWorkflow = registerWorkflow(conversationId);
        return executeRegisteredTask(task, session, workflowId, conversationId, runningWorkflow);
    }

    /**
     * Execute a workflow after registering it as the active request for its conversation.
     *
     * @param task workflow task
     * @param session agent session
     * @param workflowId workflow ID
     * @param conversationId conversation ID
     * @param runningWorkflow registered execution
     * @return workflow result
     * @since 0.1.7
     */
    private Map<String, Object> executeRegisteredTask(Task task, AgentSessionApi session, String workflowId,
            String conversationId, RunningWorkflow runningWorkflow) {
        try {
            if (agentConfig.getId() == null || agentConfig.getId().isBlank()) {
                throw new IllegalStateException("Workflow not found: " + workflowId);
            }

            // Get workflow object
            Object workflow = Runner.resourceMgr().getWorkflow(workflowId);
            if (workflow == null) {
                throw new IllegalStateException("Workflow not found: " + workflowId);
            }

            boolean isResume = task.getStatus() == TaskStatus.INPUT_REQUIRED;
            if (isResume) {
                Loggers.CONTROLLER.info("Resuming workflow: {}", workflowId);
            } else {
                Loggers.CONTROLLER.info("Starting workflow: {}", workflowId);
            }
            task.setStatus(TaskStatus.WORKING);

            // Execute workflow with streaming
            WorkflowSessionApi workflowSession = session.createWorkflowSession();
            Object inputs = getTaskArguments(task);
            ModelContext context = appContextEngine.createContext(workflowId, session.getInner());
            Iterator<WorkflowChunk> workflowStream = Runner.runWorkflowStreaming(workflow, inputs, workflowSession,
                    context, resolveWorkflowStreamModes(session));

            List<Object> chunks = new ArrayList<>();
            boolean hasInteraction = false;
            Object finalResult = null;

            while (workflowStream.hasNext()) {
                WorkflowChunk chunk = workflowStream.next();
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
                } else if (chunk instanceof CustomSchema customSchema) {
                    session.writeCustomStream(customSchema.getProperties());
                } else if (chunk instanceof TraceSchema traceSchema) {
                    session.writeTraceStream(traceSchema);
                } else {
                    session.writeStream(chunk);
                }
                chunks.add(chunk);
            }

            addAssistantMessageToAgentContext(session, buildAssistantContent(chunks));

            // Process result
            if (hasInteraction) {
                // Workflow interrupted
                Loggers.CONTROLLER.info("Workflow interrupted: {}", workflowId);
                task.setStatus(TaskStatus.INPUT_REQUIRED);

                interruptTask(task, session, chunks);

                // Return only first interrupt for streaming
                List<Object> interruptChunks = getInteractionChunks(chunks);
                List<Object> firstInterrupt = getFirstInterrupt(chunks);
                Loggers.CONTROLLER.info("Workflow has {} interrupts, returning first for streaming",
                        countInteractions(chunks));

                List<Object> streamedInterrupts = isInvokeCall(session) ? interruptChunks : firstInterrupt;
                for (Object item : streamedInterrupts) {
                    if (item instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                        session.writeStream(os);
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("interaction", interruptChunks);
                return result;
            } else {
                // Workflow completed
                Loggers.CONTROLLER.info("Workflow completed: {}", workflowId);
                task.setStatus(TaskStatus.COMPLETED);
                clearInterruptedState(task, session, workflowId);

                Map<String, Object> result = new HashMap<>();
                result.put("output",
                        new WorkflowOutput(finalResult != null ? finalResult : "", WorkflowExecutionState.COMPLETED));
                result.put("result_type", "answer");
                return result;
            }
        } catch (Exception e) {
            if (runningWorkflow.isCancelled()) {
                Thread.interrupted();
                task.setStatus(TaskStatus.CANCELED);
                return writeCancellationResult(session, task, workflowId);
            }
            Loggers.CONTROLLER.error("Workflow execution failed: {}, error: {}", workflowId, e.getMessage());
            task.setStatus(TaskStatus.FAILED);
            if (e instanceof BaseError be) {
                throw be;
            }
            BaseError nested = findNestedBaseError(e);
            if (nested != null) {
                throw nested;
            }
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg", e.getMessage());
        } finally {
            runningWorkflow.complete();
            runningWorkflows.remove(conversationId, runningWorkflow);
        }
    }

    /**
     * Register the workflow currently serving a conversation and cancel its predecessor.
     *
     * @param conversationId conversation ID
     * @return registered workflow execution
     * @since 0.1.7
     */
    private RunningWorkflow registerWorkflow(String conversationId) {
        RunningWorkflow current = new RunningWorkflow(Thread.currentThread());
        RunningWorkflow previous = runningWorkflows.put(conversationId, current);
        if (previous != null) {
            previous.cancelAndAwait();
        }
        return current;
    }

    /**
     * Write the terminal marker expected when a workflow is superseded by a new request.
     *
     * @param session agent session
     * @param task cancelled task
     * @param workflowId workflow ID
     * @return cancellation result
     * @since 0.1.7
     */
    private static Map<String, Object> writeCancellationResult(AgentSessionApi session, Task task,
            String workflowId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "cancelled");
        result.put("conversation_id", session.getSessionId());
        result.put("task_id", task.getTaskId());
        result.put("workflow_id", workflowId);
        session.writeStream(new OutputSchema("cancelled", 0, result));
        Loggers.CONTROLLER.info("Workflow cancelled: workflow={}, conversation={}", workflowId,
                session.getSessionId());
        return result;
    }

    /**
     * Tracks one workflow handler serving a conversation.
     *
     * @since 0.1.7
     */
    private static final class RunningWorkflow {
        private final Thread owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final CountDownLatch completion = new CountDownLatch(1);

        private RunningWorkflow(Thread owner) {
            this.owner = owner;
        }

        private void cancelAndAwait() {
            if (owner == Thread.currentThread()) {
                return;
            }
            if (cancelled.compareAndSet(false, true)) {
                owner.interrupt();
            }
            boolean interrupted = Thread.interrupted();
            try {
                if (!completion.await(CANCEL_GRACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    Loggers.CONTROLLER.warning("Timed out waiting for cancelled workflow handler to stop");
                }
            } catch (InterruptedException e) {
                interrupted = true;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private boolean isCancelled() {
            return cancelled.get();
        }

        private void complete() {
            completion.countDown();
        }
    }

    // ==================== Interruption Handling ====================

    void interruptTask(Task task, AgentSessionApi session, List<Object> interactionData) {
        String workflowId = task.getMetadata() != null ? (String) task.getMetadata().get("workflow_id") : "";
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
        Map<String, Object> interruptedTasks =
            (Map<String, Object>) state.computeIfAbsent("interrupted_tasks", k -> new HashMap<>());

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

        replaceControllerState(session, state);

        Loggers.CONTROLLER.info("Task interrupted: workflow={}, state_key={}, component_id={}", workflowId, stateKey,
                componentId);
    }

    @SuppressWarnings("unchecked")
    /**
     * returnSavedInterruption.
     * 
     * @param workflow workflow
     * @param session session
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * detectWorkflowViaLlm.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private WorkflowSchema detectWorkflowViaLlm(Event event, AgentSessionApi session) {
        List<WorkflowSchema> workflows = uniqueWorkflows(agentConfig.getWorkflows());
        if (workflows == null || workflows.isEmpty()) {
            return null;
        }
        if (agentConfig.getModel() == null || agentConfig.getModel().modelInfo() == null) {
            Loggers.CONTROLLER.warning("No model configured for workflow intent detection, using first workflow");
            return workflows.get(0);
        }

        try {
            String currentInput = getDisplayContent(event);
            List<BaseMessage> messages = buildIntentDetectionMessages(workflows, currentInput, session);
            var modelInfo = agentConfig.getModel().modelInfo();
            Loggers.CONTROLLER.info(
                    "Intent detection LLM params: {\"model\":\"{}\",\"temperature\":{},\"top_p\":{},\"timeout\":{}}",
                    modelInfo.getModelName(), modelInfo.getTemperature(), modelInfo.getTopP(),
                    (double) modelInfo.getTimeout());
            Loggers.CONTROLLER.info("Intent detection messages: {}", messages);

            Model model = TracerDecorator.decorateModelWithTrace(getModel(), session);
            AssistantMessage llmOutput = model.invoke(messages, null, null, null, modelInfo.getModelName(), null, null,
                    null, null, null);
            WorkflowSchema detectedWorkflow = mapWorkflowFromIntentOutput(llmOutput, workflows);
            if (detectedWorkflow != null) {
                return detectedWorkflow;
            }
            if (hasDefaultResponseText()) {
                Loggers.CONTROLLER.info("Intent detection returned default class, using configured default response");
                return null;
            }
            Loggers.CONTROLLER.warning("Intent detection returned no workflow, using first workflow");
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Intent detection failed: {}, using first workflow", e.getMessage());
        }
        return workflows.get(0);
    }

    /**
     * shouldResumeInterruptedTask.
     * 
     * @param task task
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
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

        String workflowId = task.getMetadata() != null ? (String) task.getMetadata().get("workflow_id") : "";
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
    /**
     * findInterruptedTaskByNodeId.
     * 
     * @param interactiveInput interactiveInput
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private ResumeByNodeResult findInterruptedTaskByNodeId(InteractiveInput interactiveInput, AgentSessionApi session) {
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
    /**
     * findInterruptedTask.
     * 
     * @param workflow workflow
     * @param session session
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * createNewTask.
     * 
     * @param event event
     * @param workflow workflow
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Task createNewTask(Event event, WorkflowSchema workflow, AgentSessionApi session) {
        Map<String, Object> rawInputs = new HashMap<>(extractInputMap(event));
        rawInputs.remove("conversation_id");

        // Determine required input key
        Map<String, Object> schema = workflow.getInputParams();
        String requiredKey = getRequiredInputKey(schema);
        if (requiredKey == null) {
            requiredKey = "query";
        }

        Map<String, Object> inputs = new HashMap<>(rawInputs);
        if (!inputs.containsKey(requiredKey)) {
            if ("query".equals(requiredKey)) {
                inputs.put(requiredKey, "");
            } else {
                inputs.put(requiredKey, getDisplayContent(event));
            }
        }

        // Filter inputs
        if (schema != null && !schema.isEmpty()) {
            inputs = filterWorkflowInputs(schema, inputs);
        }

        String workflowId = workflow.getId() + "_" + workflow.getVersion();
        String taskId = "workflow_" + event.getEventId();

        Task task = new Task(session.getSessionId(), taskId, "workflow");
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

    /**
     * getRequiredInputKey.
     * 
     * @param schema schema
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * resolveWorkflowStreamModes.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<StreamMode> resolveWorkflowStreamModes(AgentSessionApi session) {
        if (session == null || session.getInner() == null || session.getInner().streamWriterManager() == null) {
            return java.util.Collections.emptyList();
        }
        List<StreamMode> streamModes = session.getInner().streamWriterManager().getEnabledModes();
        return streamModes.isEmpty() ? null : streamModes;
    }

    /**
     * findNestedBaseError.
     * 
     * @param throwable throwable
     * @return the result
     * @since 0.1.7
     */
    private BaseError findNestedBaseError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BaseError baseError) {
                return baseError;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * filterWorkflowInputs.
     * 
     * @param schema schema
     * @param userData userData
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * getDisplayContent.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
    private String getDisplayContent(Event event) {
        Map<String, Object> inputMap = extractInputMap(event);
        if (!inputMap.isEmpty()) {
            Object query = inputMap.get("query");
            if (query instanceof String s) {
                return s;
            }
            if (query instanceof InteractiveInput interactiveInput) {
                Object rawInputs = interactiveInput.getRawInputs();
                if (rawInputs != null) {
                    return String.valueOf(rawInputs);
                }
                if (interactiveInput.getUserInputs() != null && !interactiveInput.getUserInputs().isEmpty()) {
                    Object firstValue = interactiveInput.getUserInputs().values().iterator().next();
                    return firstValue != null ? String.valueOf(firstValue) : "";
                }
                return "";
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

    /**
     * extractInteractiveInput.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * clearInterruptedState.
     * 
     * @param task task
     * @param session session
     * @param workflowId workflowId
     * @since 0.1.7
     */
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
            replaceControllerState(session, state);
            Loggers.CONTROLLER.info("Cleared interrupted state for workflow: {}", workflowId);
        }
    }

    /**
     * replaceControllerState.
     * 
     * @param session session
     * @param state state
     * @since 0.1.7
     */
    private void replaceControllerState(AgentSessionApi session, Map<String, Object> state) {
        Map<String, Object> clearState = new HashMap<>();
        clearState.put(STATE_KEY, null);
        session.updateState(clearState);

        Map<String, Object> updatedState = new HashMap<>();
        updatedState.put(STATE_KEY, state);
        session.updateState(updatedState);
    }

    /**
     * extractComponentIdFromInteractionData.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractInteractionValueFromInteractionData.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * getFirstInterrupt.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
    private List<Object> getFirstInterrupt(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return List.of();
        }

        List<Object> result = new ArrayList<>();
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                result.add(chunk);
                break;
            }
        }
        return result;
    }

    /**
     * getInteractionChunks.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
    private List<Object> getInteractionChunks(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return List.of();
        }
        List<Object> result = new ArrayList<>();
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * countInteractions.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * isWorkflowInterrupted.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private boolean isWorkflowInterrupted(Object result) {
        if (result instanceof WorkflowOutput wo) {
            return wo.getState() == WorkflowExecutionState.INPUT_REQUIRED;
        }
        return false;
    }

    /**
     * hasDefaultResponseText.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean hasDefaultResponseText() {
        DefaultResponse defaultResponse = agentConfig.getDefaultResponse();
        return defaultResponse != null && defaultResponse.getText() != null && !defaultResponse.getText().isEmpty();
    }

    /**
     * isInvokeCall.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private boolean isInvokeCall(AgentSessionApi session) {
        Object callMode = session.getState(CALL_MODE_STATE_KEY);
        return "invoke".equals(callMode);
    }

    /**
     * getModel.
     * 
     * @return the result
     * @since 0.1.7
     */
    private Model getModel() {
        if (agentConfig.getModel() == null || agentConfig.getModel().modelInfo() == null) {
            throw new IllegalStateException("Model configuration is required");
        }

        var modelInfo = agentConfig.getModel().modelInfo();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider(agentConfig.getModel().modelProvider()).apiKey(modelInfo.getApiKey())
                .apiBase(modelInfo.getApiBase()).timeout(modelInfo.getTimeout()).verifySsl(modelInfo.isVerifySsl())
                .sslCert(modelInfo.getSslCert()).headers(modelInfo.getHeaders()).build();

        ModelRequestConfig requestConfig = ModelRequestConfig.builder().modelName(modelInfo.getModelName())
                .temperature(modelInfo.getTemperature()).topP(modelInfo.getTopP())
                .extraFields(modelInfo.getExtraFields() != null
                        ? new java.util.LinkedHashMap<>(modelInfo.getExtraFields())
                        : new java.util.LinkedHashMap<>())
                .build();

        return new Model(clientConfig, requestConfig);
    }

    /**
     * buildIntentDetectionMessages.
     * 
     * @param workflows workflows
     * @param currentInput currentInput
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<BaseMessage> buildIntentDetectionMessages(List<WorkflowSchema> workflows, String currentInput,
            AgentSessionApi session) {
        String categoryList = buildIntentCategoryList(workflows);
        String chatHistory = buildIntentChatHistory(session, 100);
        String exampleContent = "";

        String systemPrompt = INTENT_SYSTEM_PROMPT.replace("{{default_class}}", DEFAULT_INTENT_CLASS)
                .replace("{{category_list}}", categoryList).replace("{{example_content}}", exampleContent);
        String userPrompt = INTENT_USER_PROMPT.replace("{{chat_history}}", chatHistory).replace("{{input}}",
                currentInput != null ? currentInput : "");

        return List.of(BaseMessage.builder().role("system").content(systemPrompt).build(),
                BaseMessage.builder().role("user").content(userPrompt).build());
    }

    /**
     * buildIntentCategoryList.
     * 
     * @param workflows workflows
     * @return the result
     * @since 0.1.7
     */
    private String buildIntentCategoryList(List<WorkflowSchema> workflows) {
        StringBuilder builder = new StringBuilder();
        builder.append(DEFAULT_INTENT_CLASS).append("：意图不明");
        for (int i = 0; i < workflows.size(); i++) {
            WorkflowSchema workflow = workflows.get(i);
            String categoryName = workflow.getDescription() != null && !workflow.getDescription().isEmpty()
                    ? workflow.getDescription()
                    : workflow.getName();
            builder.append("\n分类").append(i + 1).append("：").append(categoryName);
        }
        return builder.toString();
    }

    /**
     * buildIntentChatHistory.
     * 
     * @param session session
     * @param maxRounds maxRounds
     * @return the result
     * @since 0.1.7
     */
    private String buildIntentChatHistory(AgentSessionApi session, int maxRounds) {
        ModelContext agentContext = getOrCreateAgentContext(session);
        List<BaseMessage> messages = agentContext.getMessages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int limit = Math.min(messages.size(), maxRounds * 2);
        StringBuilder builder = new StringBuilder();
        for (BaseMessage message : messages.subList(messages.size() - limit, messages.size())) {
            String roleName = "assistant".equals(message.getRole()) ? "助手" : "用户";
            builder.append(roleName).append(": ").append(message.getContentAsString()).append("\n");
        }
        return builder.toString();
    }

    /**
     * mapWorkflowFromIntentOutput.
     * 
     * @param llmOutput llmOutput
     * @param workflows workflows
     * @return the result
     * @since 0.1.7
     */
    private WorkflowSchema mapWorkflowFromIntentOutput(AssistantMessage llmOutput, List<WorkflowSchema> workflows) {
        Integer categoryIndex = parseIntentCategoryIndex(llmOutput != null ? llmOutput.getContentAsString() : "");
        if (categoryIndex == null || categoryIndex <= 0 || categoryIndex > workflows.size()) {
            return null;
        }
        String detectedCategory = workflows.get(categoryIndex - 1).getDescription();
        if (detectedCategory == null || detectedCategory.isEmpty()) {
            detectedCategory = workflows.get(categoryIndex - 1).getName();
        }
        for (WorkflowSchema workflow : workflows) {
            String categoryName = workflow.getDescription() != null && !workflow.getDescription().isEmpty()
                    ? workflow.getDescription()
                    : workflow.getName();
            if (detectedCategory.equals(categoryName)) {
                return workflow;
            }
        }
        return null;
    }

    /**
     * parseIntentCategoryIndex.
     * 
     * @param llmOutput llmOutput
     * @return the result
     * @since 0.1.7
     */
    private Integer parseIntentCategoryIndex(String llmOutput) {
        if (llmOutput == null || llmOutput.isBlank()) {
            return null;
        }
        String cleaned = llmOutput.strip().replaceAll("(?is)^```json\\s*", "").replaceAll("(?is)^```\\s*", "")
                .replaceAll("(?is)```\\s*$", "");
        try {
            JsonNode root = OBJECT_MAPPER.readTree(cleaned);
            if (root.has("result") && root.get("result").canConvertToInt()) {
                return root.get("result").asInt();
            }
        } catch (Exception ignored) {
            // Fallback to a lightweight number search below.
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"result\"\\s*:\\s*(\\d+)").matcher(cleaned);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    /**
     * buildAssistantContent.
     * 
     * @param chunks chunks
     * @return the result
     * @since 0.1.7
     */
    private String buildAssistantContent(List<Object> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        StringBuilder contentParts = new StringBuilder();
        for (Object chunk : chunks) {
            if (!(chunk instanceof OutputSchema os)) {
                continue;
            }
            if (os.getPayload() instanceof Map<?, ?> payloadMap) {
                Object response = payloadMap.get("response");
                if (response != null) {
                    contentParts.append(response);
                }
            } else if (os.getPayload() instanceof InteractionOutput io && io.getValue() != null) {
                contentParts.append(io.getValue());
            } else {
                // no-op
            }
        }
        return contentParts.toString();
    }

    /**
     * uniqueWorkflows.
     * 
     * @param workflows workflows
     * @return the result
     * @since 0.1.7
     */
    private List<WorkflowSchema> uniqueWorkflows(List<WorkflowSchema> workflows) {
        if (workflows == null || workflows.isEmpty()) {
            return List.of();
        }
        Map<String, WorkflowSchema> unique = new LinkedHashMap<>();
        for (WorkflowSchema workflow : workflows) {
            if (workflow == null) {
                continue;
            }
            String key = workflow.getId() + "_" + workflow.getVersion();
            unique.putIfAbsent(key, workflow);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * addUserMessageToAgentContext.
     * 
     * @param session session
     * @param content content
     * @since 0.1.7
     */
    private void addUserMessageToAgentContext(AgentSessionApi session, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        addMessageToContext(getOrCreateAgentContext(session), new UserMessage(content), true);
    }

    /**
     * addAssistantMessageToAgentContext.
     * 
     * @param session session
     * @param content content
     * @since 0.1.7
     */
    private void addAssistantMessageToAgentContext(AgentSessionApi session, String content) {
        if (content == null) {
            return;
        }
        addMessageToContext(getOrCreateAgentContext(session), new AssistantMessage(content), true);
    }

    /**
     * getOrCreateAgentContext.
     * 
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private ModelContext getOrCreateAgentContext(AgentSessionApi session) {
        ModelContext context = appContextEngine.getContext(null, session.getSessionId());
        if (context != null) {
            return context;
        }
        return appContextEngine.createContext(null, session.getInner());
    }

    /**
     * addMessageToContext.
     * 
     * @param context context
     * @param message message
     * @param deduplicate deduplicate
     * @since 0.1.7
     */
    private void addMessageToContext(ModelContext context, BaseMessage message, boolean deduplicate) {
        if (context == null || message == null) {
            return;
        }
        if (deduplicate) {
            List<BaseMessage> lastMessage = context.getMessages(1, true);
            if (!lastMessage.isEmpty()) {
                BaseMessage previous = lastMessage.get(0);
                if (message.getRole().equals(previous.getRole())
                        && message.getContentAsString().equals(previous.getContentAsString())) {
                    return;
                }
            }
        }
        context.addMessages(message);
    }

    /**
     * deserializeTask.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractInputMap.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * extractPrimaryInput.
     * 
     * @param event event
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * setTaskArguments.
     * 
     * @param task task
     * @param arguments arguments
     * @since 0.1.7
     */
    private void setTaskArguments(Task task, Object arguments) {
        if (task.getMetadata() == null) {
            task.setMetadata(new HashMap<>());
        }
        task.getMetadata().put("arguments", arguments);
    }

    /**
     * getTaskArguments.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * buildResumeArguments.
     * 
     * @param interactiveInput interactiveInput
     * @param task task
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Object buildResumeArguments(InteractiveInput interactiveInput, Task task, Event event,
            AgentSessionApi session) {
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

    /**
     * ResumeByNodeResult.
     * 
     * @param workflow workflow
     * @param task task
     * @since 0.1.7
     */
    private record ResumeByNodeResult(WorkflowSchema workflow, Task task) {
    }
}
