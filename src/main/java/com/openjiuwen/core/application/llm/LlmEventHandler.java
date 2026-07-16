/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapType;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.PluginSchema;
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
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.memory.LongTermMemory;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.AbilityManager;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM Controller - ReAct style event handler based on EventHandler.
 * <p>
 * Core responsibilities:
 * <ol>
 * <li>Receive user input and invoke LLM reasoning to generate tasks</li>
 * <li>Execute tasks (plugin/workflow)</li>
 * <li>After task completion, invoke LLM reasoning again to decide next step</li>
 * <li>Loop until problem solved or max iteration reached</li>
 * </ol>
 * <p>
 * Mirrors Python's {@code LLMController} in {@code openjiuwen.core.application.llm_agent}.
 * 
 * @since 0.1.7
 */
public class LlmEventHandler extends EventHandler {
    private static final String INTERACTION = "__interaction__";
    private static final String LLM_OUTPUT = "llm_output";
    private static final String STATE_KEY = "llm_controller";

    /**
     * ObjectMapper.
     * 
     * @since 0.1.7
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * OBJECT_MAPPER.getTypeFactory.
     * 
     * @since 0.1.7
     */
    private static final MapType STRING_OBJECT_MAP_TYPE =
        OBJECT_MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);

    private final LlmAgentConfig agentConfig;
    private final ContextEngine appContextEngine;
    private final boolean enableLongTermMem;
    private final boolean enableSummaryMemory;
    private final boolean enableMemVariables;
    private final boolean enableMemory;
    private final LongTermMemory longTermMemoryInstance;

    /**
     * LlmEventHandler.
     * 
     * @param agentConfig agentConfig
     * @param contextEngine contextEngine
     * @since 0.1.7
     */
    public LlmEventHandler(LlmAgentConfig agentConfig, ContextEngine contextEngine) {
        this.agentConfig = agentConfig;
        this.appContextEngine = contextEngine;
        this.enableLongTermMem = agentConfig.getAgentMemoryConfig().isEnableLongTermMem();
        this.enableSummaryMemory = agentConfig.getAgentMemoryConfig().isEnableSummaryMemory();
        this.enableMemVariables = !agentConfig.getAgentMemoryConfig().getMemVariables().isEmpty();
        String memoryScopeId = agentConfig.getMemoryScopeId();
        boolean isEnableFragmentMemory = agentConfig.getAgentMemoryConfig().isMemoryTypeEnabled("user_profile")
                || agentConfig.getAgentMemoryConfig().isMemoryTypeEnabled("semantic_memory")
                || agentConfig.getAgentMemoryConfig().isMemoryTypeEnabled("episodic_memory");
        this.enableMemory = memoryScopeId != null && !memoryScopeId.isEmpty()
                && (enableLongTermMem || isEnableFragmentMemory || enableSummaryMemory || enableMemVariables);
        this.longTermMemoryInstance = LongTermMemory.getInstance();
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
            Loggers.CONTROLLER.error("Error in handling message: {}", e.getMessage());
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
        Loggers.CONTROLLER.info("Task interaction received");
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
        Loggers.CONTROLLER.info("Task completion received");
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
        Loggers.CONTROLLER.info("Task failed received");
        return null;
    }

    /**
     * Set prompt template on the config.
     * 
     * @param promptTemplate promptTemplate
     * @since 0.1.7
     */
    public void setPromptTemplate(List<Map<String, String>> promptTemplate) {
        agentConfig.setPromptTemplate(promptTemplate);
    }

    // ==================== Core Logic ====================

    /**
     * Handle user input - ReAct core: LLM reasoning to generate plan.
     * <p>
     * Process:
     * <ol>
     * <li>Add user message to conversation history</li>
     * <li>Call LLM reasoning to generate task plan</li>
     * <li>Execute tasks in ReAct loop</li>
     * </ol>
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> handleUserInput(Event event, AgentSessionApi session) {
        String displayContent = getDisplayContent(event);

        // Add user message to context
        ModelContext context = appContextEngine.createContext(null, session.getInner());
        context.addMessages(new UserMessage(displayContent));

        // Check for InteractiveInput fast path (resume from interruption)
        InteractiveInput interactiveInput = extractInteractiveInput(event);
        if (interactiveInput != null && interactiveInput.getUserInputs() != null
                && !interactiveInput.getUserInputs().isEmpty()) {
            ResumeResult resume = findInterruptedTaskByNodeId(interactiveInput, session);
            if (resume != null) {
                Loggers.CONTROLLER.info(
                        "Resuming interrupted workflow from InteractiveInput, "
                                + "remaining tasks: {}, saved_iteration: {}",
                        resume.remainingTasks.size(), resume.savedIteration);

                context.addMessages(resume.aiMessage);

                // Update first task with user input
                Task interruptedTask = resume.remainingTasks.get(0);
                setTaskArguments(interruptedTask, interactiveInput);
                interruptedTask.setStatus(TaskStatus.INPUT_REQUIRED);

                int initialIteration = resume.savedIteration != null ? resume.savedIteration + 1 : 1;
                return executeReactLoop(resume.remainingTasks, session, initialIteration, resume.aiMessage, context);
            }
            Loggers.CONTROLLER.warning(
                    "Given Interactive input, but no interrupted task found, falling through to normal LLM detection");
        }

        // Normal path: Call LLM to generate plans
        LlmPlanResult planResult = generatePlanFromLlm(event, session, context);

        if (planResult.tasks.isEmpty()) {
            Loggers.CONTROLLER.info("ReAct Iteration: 1 end, No task is generated");
            Map<String, Object> finalResult = sendFinalStream(planResult.llmOutput.getContentAsString(), session);
            return unwrapResult(finalResult);
        }

        // Check for workflow task resumption
        int initialIteration = 1;
        Task workflowTask = resolveWorkflowFromTasks(planResult.tasks);
        if (workflowTask != null) {
            ResumeResult resume = findInterruptedTask(workflowTask, session);
            if (resume != null) {
                Loggers.CONTROLLER.info("Resuming interrupted workflow task: {}, last iteration: {}",
                        workflowTask.getDescription(), resume.savedIteration);

                if (planResult.llmOutput.getToolCalls() != null && !planResult.llmOutput.getToolCalls().isEmpty()) {
                    String newToolCallId = planResult.llmOutput.getToolCalls().get(0).getId();
                    String oldTaskId = resume.remainingTasks.get(0).getTaskId();
                    resume.remainingTasks.get(0).setTaskId(newToolCallId);
                    Loggers.CONTROLLER.info("Updated task_id from {} to {} for resume", oldTaskId, newToolCallId);
                }

                Task interrupted = resume.remainingTasks.get(0);
                if (interactiveInput != null) {
                    setTaskArguments(interrupted, interactiveInput);
                } else {
                    String query = getDisplayContent(event);
                    setTaskArguments(interrupted, buildResumeInteractiveInput(query, resume.componentIds));
                }
                interrupted.setStatus(TaskStatus.INPUT_REQUIRED);

                int resumeIteration = resume.savedIteration != null ? resume.savedIteration + 1 : 1;
                return executeReactLoop(resume.remainingTasks, session, resumeIteration, resume.aiMessage, context);
            }
        }

        return executeReactLoop(planResult.tasks, session, initialIteration, planResult.llmOutput, context);
    }

    /**
     * Execute ReAct loop with explicit iteration - NO RECURSION.
     * 
     * @param tasks tasks
     * @param session session
     * @param initialIteration initialIteration
     * @param aiMessage aiMessage
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> executeReactLoop(List<Task> tasks, AgentSessionApi session, int initialIteration,
            AssistantMessage aiMessage, ModelContext context) {
        int maxIteration = agentConfig.getConstrain().getMaxIteration();
        int iteration = initialIteration;
        List<Task> currentTasks = tasks;
        AssistantMessage currentAiMessage = aiMessage;
        TaskExecutionResult lastResult = null;

        while (!currentTasks.isEmpty() && iteration <= maxIteration) {
            Loggers.CONTROLLER.info("ReAct Iteration: {} / {}", iteration, maxIteration);

            for (int idx = 0; idx < currentTasks.size(); idx++) {
                Task task = currentTasks.get(idx);
                Loggers.CONTROLLER.info("Executing task {}, type: {}, index: {}/{}", task.getTaskId(),
                        task.getTaskType(), idx + 1, currentTasks.size());

                lastResult = executeTask(task, session, context);

                // Handle interruption
                if (lastResult.status == TaskStatus.INPUT_REQUIRED) {
                    Loggers.CONTROLLER.info("Task interrupted at index {}, stopping ReAct loop", idx + 1);
                    List<Task> remainingTasks = new ArrayList<>(currentTasks.subList(idx, currentTasks.size()));

                    TaskInterruptionState state = new TaskInterruptionState(task, session, currentAiMessage,
                            remainingTasks, lastResult.output, iteration);
                    return handleTaskInterrupted(state);
                }

                // Handle error
                if (lastResult.status == TaskStatus.FAILED) {
                    Loggers.CONTROLLER.error("Task failed: {}", lastResult.errorMessage);
                    handleTaskError(lastResult.errorMessage, session, task, context);
                    continue;
                }

                // Task completed
                if (lastResult.status == TaskStatus.COMPLETED) {
                    postTaskCompletion(task, lastResult, session, context);
                }
            }

            // All tasks completed, generate next plan
            Loggers.CONTROLLER.info("All tasks completed, generating next plan");
            Task lastTask = currentTasks.get(currentTasks.size() - 1);

            LlmPlanResult nextPlan = generatePlanFromLlm(null, session, context);

            if (nextPlan.tasks.isEmpty()) {
                Loggers.CONTROLLER.info("No new tasks generated, ReAct loop completed");
                Map<String, Object> finalResult = sendFinalStream(nextPlan.llmOutput.getContentAsString(), session);
                return unwrapResult(finalResult);
            }

            currentTasks = nextPlan.tasks;
            currentAiMessage = nextPlan.llmOutput;
            iteration++;
        }

        Loggers.CONTROLLER.warning("Exceeded max iteration {}, stopping ReAct loop", maxIteration);
        Map<String, Object> result = sendFinalStream("Maximum iteration reached", session);
        return unwrapResult(result);
    }

    // ==================== Task Execution ====================

    /**
     * executeTask.
     * 
     * @param task task
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private TaskExecutionResult executeTask(Task task, AgentSessionApi session, ModelContext context) {
        try {
            String taskType = task.getTaskType();
            if ("workflow".equals(taskType)) {
                return executeWorkflowTask(task, session, context);
            } else if ("plugin".equals(taskType)) {
                return executePluginTask(task, session, context);
            } else {
                Loggers.CONTROLLER.warning("Unknown task type: {}", taskType);
                throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                        "Unknown task type: " + taskType);
            }
        } catch (BaseError e) {
            if (e.getCode() == StatusCode.AGENT_TOOL_NOT_FOUND.getCode()) {
                throw e;
            }
            Loggers.CONTROLLER.error("Error executing task {}: {}", task.getTaskId(), e.getMessage());
            return new TaskExecutionResult(TaskStatus.FAILED, null, e.getMessage(), null);
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Error executing task {}: {}", task.getTaskId(), e.getMessage());
            return new TaskExecutionResult(TaskStatus.FAILED, null, e.getMessage(), null);
        }
    }

    /**
     * executeWorkflowTask.
     * 
     * @param task task
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private TaskExecutionResult executeWorkflowTask(Task task, AgentSessionApi session, ModelContext context) {
        String workflowId = getWorkflowIdFromTask(task);
        if (workflowId == null || workflowId.isBlank()) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND, "error_msg",
                    "workflow '" + task.getDescription() + "' is not registered");
        }
        Object workflow = Runner.resourceMgr().getWorkflow(workflowId);
        if (workflow == null) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND, "error_msg",
                    "workflow '" + task.getDescription() + "' is not registered");
        }

        boolean isResume = task.getStatus() == TaskStatus.INPUT_REQUIRED;
        Loggers.CONTROLLER.info("Executing workflow: {}, is_resume={}", workflowId, isResume);
        task.setStatus(TaskStatus.WORKING);

        Object inputs = getTaskArguments(task);
        Object result = Runner.runWorkflow(workflow, inputs, session, context);

        boolean isInterrupted = isWorkflowInterrupted(result);
        Loggers.CONTROLLER.info("Workflow result: interrupted={}", isInterrupted);

        if (isInterrupted) {
            List<Object> streamData = prepareWorkflowStreamData(result);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("workflow_id", workflowId);
            return new TaskExecutionResult(TaskStatus.INPUT_REQUIRED, streamData, null, metadata);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("output", result);
            payload.put("result_type", "answer");
            List<Object> streamData = List.of(new OutputSchema("workflow_final", 0, payload));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("workflow_id", workflowId);
            return new TaskExecutionResult(TaskStatus.COMPLETED, streamData, null, metadata);
        }
    }

    /**
     * executePluginTask.
     * 
     * @param task task
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private TaskExecutionResult executePluginTask(Task task, AgentSessionApi session, ModelContext context) {
        String toolName = task.getDescription();
        String toolId = findPluginIdByName(toolName);
        String resolvedToolId = toolId != null ? toolId : toolName;

        Object tool = Runner.resourceMgr().getTool(resolvedToolId);
        if (tool == null) {
            tool = Runner.resourceMgr().getTool(toolName);
        }
        if (tool == null && toolId != null && !toolId.equals(toolName)) {
            tool = Runner.resourceMgr().getTool(toolId, agentConfig.getId(), null);
        }
        boolean abilityHasTool = false;
        if (abilityManager instanceof AbilityManager manager) {
            abilityHasTool = manager.listToolInfo().stream().anyMatch(info -> toolName.equals(info.getName()));
        }
        if (tool == null && !abilityHasTool) {
            throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND, "error_msg",
                    "tool '" + toolName + "' is not registered");
        }

        Loggers.CONTROLLER.info("Executing plugin: {}", toolName);

        AbilityManager.ToolExecutionEntry executionEntry;
        if (abilityManager instanceof AbilityManager manager) {
            executionEntry = manager.executeSingleToolCall(
                    ToolCall.builder().id(task.getTaskId()).name(toolName)
                            .arguments(serializeTaskArguments(getTaskArguments(task))).build(),
                    session, agentConfig.getId());
        } else {
            Map<String, Object> toolArgs = castArguments(getTaskArguments(task));
            Object toolResult;
            try {
                toolResult = ((com.openjiuwen.core.foundation.tool.Tool) tool).invoke(toolArgs, Map.of());
            } catch (Exception e) {
                throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg",
                        "Tool execution error: " + e.getMessage());
            }
            executionEntry = new AbilityManager.ToolExecutionEntry(toolResult,
                    new ToolMessage(String.valueOf(toolResult), task.getTaskId()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("output", executionEntry.result());
        payload.put("result_type", "answer");
        List<Object> streamData = List.of(new OutputSchema("plugin_final", 0, payload));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("tool_name", toolName);
        metadata.put("tool_message", executionEntry.toolMessage());
        return new TaskExecutionResult(TaskStatus.COMPLETED, streamData, null, metadata);
    }

    // ==================== Interruption Handling ====================

    /**
     * handleTaskInterrupted.
     * 
     * @param interruptionState interruptionState
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> handleTaskInterrupted(TaskInterruptionState interruptionState) {
        interruptTask(interruptionState);

        // Add mock tool message to context
        ToolMessage mockToolMsg =
            new ToolMessage("[INTERRUPTED - Waiting for user input]", interruptionState.getTask().getTaskId());
        Loggers.CONTROLLER.info("Task interrupted, saved state for later resumption");

        List<Object> firstInterrupt = getFirstInterrupt(interruptionState.getInteractionData());

        // Write interrupted stream data
        writeMessageStreamData(firstInterrupt, interruptionState.getSession());

        return unwrapResult(firstInterrupt);
    }

    @SuppressWarnings("unchecked")
    /**
     * interruptTask.
     * 
     * @param interruptionState interruptionState
     * @since 0.1.7
     */
    private void interruptTask(TaskInterruptionState interruptionState) {
        Task task = interruptionState.getTask();
        String workflowId = ensureWorkflowId(task);
        task.setStatus(TaskStatus.INPUT_REQUIRED);

        List<String> componentIds = extractComponentIdsFromInteractionData(interruptionState.getInteractionData());
        String stateKey = workflowId.replace('.', '_');

        Map<String, Object> taskInfo = new HashMap<>();
        taskInfo.put("ai_message", interruptionState.getAiMessage().toApiFormat());
        taskInfo.put("remaining_tasks", serializeTaskList(interruptionState.getRemainingTasks()));
        taskInfo.put("component_ids", componentIds);
        taskInfo.put("iteration", interruptionState.getCurrentIteration());

        Map<String, Object> update = new HashMap<>();
        update.put(STATE_KEY + ".interrupted_tasks." + stateKey, taskInfo);
        interruptionState.getSession().updateState(update);

        Loggers.CONTROLLER.info("Task interrupted: workflow={}, state_key={}, remaining_tasks={}", workflowId, stateKey,
                interruptionState.getRemainingTasks().size());
    }

    /**
     * handleTaskError.
     * 
     * @param errorMsg errorMsg
     * @param session session
     * @param task task
     * @param context context
     * @since 0.1.7
     */
    private void handleTaskError(String errorMsg, AgentSessionApi session, Task task, ModelContext context) {
        Loggers.CONTROLLER.error("Task execution error: {}", errorMsg);

        ToolMessage errorToolMsg = new ToolMessage("[FAILED - " + errorMsg + "]", task.getTaskId());
        context.addMessages(errorToolMsg);

        sendErrorStream(errorMsg, session);
    }

    /**
     * postTaskCompletion.
     * 
     * @param task task
     * @param result result
     * @param session session
     * @param context context
     * @since 0.1.7
     */
    private void postTaskCompletion(Task task, TaskExecutionResult result, AgentSessionApi session,
            ModelContext context) {
        if (result.output != null && !result.output.isEmpty() && result.output.get(0) instanceof OutputSchema os) {
            String type = os.getType();
            if ("plugin_final".equals(type) || "workflow_final".equals(type)) {
                Loggers.CONTROLLER.info("payload={}", toPythonLiteral(os.getPayload()));
            }
        }
        if (result.metadata != null && result.metadata.get("tool_message") instanceof ToolMessage toolMessage) {
            context.addMessages(toolMessage);
            Loggers.CONTROLLER.info("Added tool_message for completed task: {}", task.getTaskId());
        }

        if (result.output != null && !result.output.isEmpty()) {
            Object firstOutput = result.output.get(0);
            if (firstOutput instanceof OutputSchema os
                    && !(result.metadata != null && result.metadata.get("tool_message") instanceof ToolMessage)) {
                String type = os.getType();
                if ("plugin_final".equals(type) || "workflow_final".equals(type)) {
                    String content = extractToolMessageContent(os.getPayload());
                    ToolMessage toolMsg = new ToolMessage(content, task.getTaskId());
                    context.addMessages(toolMsg);
                    Loggers.CONTROLLER.info("Added tool_message for completed task: {}", task.getTaskId());
                }
            }
        }

        // Clear interrupted state
        String workflowId = result.metadata != null ? (String) result.metadata.get("workflow_id") : null;
        if (workflowId != null) {
            clearInterruptedState(task, session, workflowId);
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * extractToolMessageContent.
     * 
     * @param payload payload
     * @return the result
     * @since 0.1.7
     */
    private String extractToolMessageContent(Object payload) {
        Object toolResult = payload;
        if (payload instanceof Map<?, ?> payloadMap) {
            toolResult = payloadMap.containsKey("output") ? payloadMap.get("output") : "";
        }
        if (toolResult instanceof WorkflowOutput workflowOutput) {
            toolResult = workflowOutput.getResult();
        }
        if (toolResult == null) {
            return "";
        }
        if (toolResult instanceof Map<?, ?> || toolResult instanceof List<?>) {
            try {
                return OBJECT_MAPPER.writeValueAsString(toolResult);
            } catch (JsonProcessingException ignored) {
                return toolResult.toString();
            }
        }
        return toolResult.toString();
    }

    /**
     * toPythonLiteral.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private String toPythonLiteral(Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String s) {
            return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(toPythonLiteral(String.valueOf(entry.getKey()))).append(": ")
                        .append(toPythonLiteral(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(toPythonLiteral(list.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "'" + value.toString().replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    // ==================== LLM Plan Generation ====================

    /**
     * generatePlanFromLlm.
     * 
     * @param event event
     * @param session session
     * @param context context
     * @return the result
     * @since 0.1.7
     */
    private LlmPlanResult generatePlanFromLlm(Event event, AgentSessionApi session, ModelContext context) {
        if (agentConfig.getWorkflows() != null) {
            for (WorkflowSchema ws : agentConfig.getWorkflows()) {
                if (ws.getName() == null || ws.getName().isBlank()) {
                    throw ErrorHelper.buildError(StatusCode.AGENT_TOOL_NOT_FOUND, "error_msg",
                            "workflow '" + ws.getId() + "' is not registered");
                }
            }
        }
        List<ToolInfo> tools = new ArrayList<>();
        if (abilityManager != null) {
            try {
                var method = abilityManager.getClass().getMethod("listToolInfo");
                @SuppressWarnings("unchecked")
                List<ToolInfo> toolInfos = (List<ToolInfo>) method.invoke(abilityManager);
                tools = toolInfos;
            } catch (Exception e) {
                Loggers.CONTROLLER.warning("Failed to get tool infos: {}", e.getMessage());
            }
        }
        Loggers.CONTROLLER.info("Loaded {} Tool(s) for generating plans", tools.size());

        // Build system messages from prompt template
        List<BaseMessage> systemMessages = new ArrayList<>();
        if (agentConfig.getPromptTemplate() != null) {
            for (Map<String, String> msg : agentConfig.getPromptTemplate()) {
                if ("system".equals(msg.get("role"))) {
                    systemMessages.add(new SystemMessage(msg.get("content")));
                }
            }
        }

        // Get memory keywords if enabled
        Map<String, String> keywords = getSystemPromptKeywords(event, session);
        // Apply keywords to system prompt
        if (!keywords.isEmpty() && !systemMessages.isEmpty()) {
            BaseMessage lastSystem = systemMessages.get(systemMessages.size() - 1);
            StringBuilder extra = new StringBuilder();
            for (Map.Entry<String, String> entry : keywords.entrySet()) {
                extra.append("\n").append(entry.getKey()).append(": ").append(entry.getValue());
            }
            lastSystem.setContent(lastSystem.getContentAsString() + extra.toString());
        }

        try {
            Model model = getModel();
            AssistantMessage llmOutput = model.invoke(context.getMessages(), tools.isEmpty() ? null : tools, null, null,
                    agentConfig.getModel().modelInfo().getModelName(), null, null, null, null, null);

            // Parse tool calls into tasks
            List<Task> tasks = parseLlmOutputToTasks(llmOutput, session);

            // Add LLM output to context
            context.addMessages(AssistantMessage.builder().content(llmOutput.getContent())
                    .toolCalls(llmOutput.getToolCalls()).build());

            return new LlmPlanResult(tasks, llmOutput);
        } catch (Exception e) {
            Loggers.CONTROLLER.error("Failed to invoke model: {}", e.getMessage());
            if (e instanceof BaseError be) {
                throw be;
            }
            throw ErrorHelper.buildError(StatusCode.AGENT_CONTROLLER_RUNTIME_ERROR, "error_msg", e.getMessage());
        }
    }

    /**
     * parseLlmOutputToTasks.
     * 
     * @param llmOutput llmOutput
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private List<Task> parseLlmOutputToTasks(AssistantMessage llmOutput, AgentSessionApi session) {
        List<Task> tasks = new ArrayList<>();
        if (llmOutput.getToolCalls() == null || llmOutput.getToolCalls().isEmpty()) {
            return tasks;
        }

        for (ToolCall toolCall : llmOutput.getToolCalls()) {
            String targetName = toolCall.getName();
            String taskType = resolveTaskType(targetName);
            String targetId = resolveTargetId(targetName);

            Task task = new Task(session.getSessionId(), toolCall.getId(), taskType);
            task.setDescription(targetName);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("target_id", targetId);
            metadata.put("target_name", targetName);
            metadata.put("arguments", parseToolArguments(toolCall.getArguments()));
            task.setMetadata(metadata);
            tasks.add(task);
        }
        return tasks;
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
    private ResumeResult findInterruptedTaskByNodeId(InteractiveInput interactiveInput, AgentSessionApi session) {
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
            List<String> componentIds = (List<String>) taskInfo.get("component_ids");
            if (componentIds == null) {
                componentIds = List.of();
            }

            boolean matched = nodeIds.stream().anyMatch(componentIds::contains);
            if (matched) {
                Loggers.CONTROLLER.info("Found matching interrupted task: key={}", entry.getKey());

                Map<String, Object> aiMessageData = (Map<String, Object>) taskInfo.get("ai_message");
                AssistantMessage aiMessage = reconstructAssistantMessage(aiMessageData);
                List<Task> remainingTasks =
                    deserializeTaskList((List<Map<String, Object>>) taskInfo.get("remaining_tasks"));
                Integer savedIteration = (Integer) taskInfo.get("iteration");

                return new ResumeResult(aiMessage, remainingTasks, savedIteration, componentIds);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    /**
     * findInterruptedTask.
     * 
     * @param workflowTask workflowTask
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private ResumeResult findInterruptedTask(Task workflowTask, AgentSessionApi session) {
        Map<String, Object> state = (Map<String, Object>) session.getState(STATE_KEY);
        if (state == null) {
            return null;
        }

        Map<String, Object> interruptedTasks = (Map<String, Object>) state.get("interrupted_tasks");
        if (interruptedTasks == null) {
            return null;
        }

        String workflowId = getWorkflowIdFromSchema(workflowTask.getDescription());
        if (workflowId == null) {
            return null;
        }

        String stateKey = workflowId.replace('.', '_');
        Map<String, Object> taskInfo = (Map<String, Object>) interruptedTasks.get(stateKey);
        if (taskInfo == null) {
            return null;
        }

        Loggers.CONTROLLER.info("Found interrupted task for {} (key: {})", workflowTask.getDescription(), stateKey);
        Map<String, Object> aiMessageData = (Map<String, Object>) taskInfo.get("ai_message");
        AssistantMessage aiMessage = reconstructAssistantMessage(aiMessageData);
        List<Task> remainingTasks = deserializeTaskList((List<Map<String, Object>>) taskInfo.get("remaining_tasks"));
        Integer savedIteration = (Integer) taskInfo.get("iteration");
        List<String> componentIds = (List<String>) taskInfo.getOrDefault("component_ids", List.of());

        return new ResumeResult(aiMessage, remainingTasks, savedIteration, componentIds);
    }

    // ==================== Stream Helpers ====================

    /**
     * sendFinalStream.
     * 
     * @param content content
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> sendFinalStream(String content, AgentSessionApi session) {
        Loggers.CONTROLLER.info("sendFinalStream called with content length={}, content='{}'",
                content != null ? content.length() : -1, content);
        writeLlmOutputChunks(content, session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("output", content);
        payload.put("result_type", "answer");
        OutputSchema finalStream = new OutputSchema("answer", 0, payload);
        session.writeStream(finalStream);
        return payload;
    }

    /**
     * writeLlmOutputChunks.
     * 
     * @param content content
     * @param session session
     * @since 0.1.7
     */
    private void writeLlmOutputChunks(String content, AgentSessionApi session) {
        if (content == null || content.isEmpty()) {
            Loggers.CONTROLLER.warning("writeLlmOutputChunks called with null/empty content");
            return;
        }
        int index = 0;
        int offset = 0;
        while (offset < content.length()) {
            int codePoint = content.codePointAt(offset);
            String chunk = new String(Character.toChars(codePoint));
            Map<String, Object> payload = new HashMap<>();
            payload.put("output", chunk);
            payload.put("result_type", "answer");
            session.writeStream(new OutputSchema(LLM_OUTPUT, index, payload));
            Loggers.CONTROLLER.info("Wrote llm_output chunk index={}, char='{}'", index, chunk);
            index++;
            offset += Character.charCount(codePoint);
        }
        Loggers.CONTROLLER.info("writeLlmOutputChunks completed, total chunks={}", index);
    }

    /**
     * sendErrorStream.
     * 
     * @param errorMsg errorMsg
     * @param session session
     * @since 0.1.7
     */
    private void sendErrorStream(String errorMsg, AgentSessionApi session) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("error", true);
        payload.put("message", errorMsg);
        payload.put("status", "failed");
        OutputSchema errorStream = new OutputSchema("final", 0, payload);
        session.writeStream(errorStream);
    }

    /**
     * writeMessageStreamData.
     * 
     * @param streamData streamData
     * @param session session
     * @since 0.1.7
     */
    private void writeMessageStreamData(List<Object> streamData, AgentSessionApi session) {
        if (streamData == null) {
            return;
        }
        for (Object item : streamData) {
            session.writeStream(item);
        }
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
                Map<String, Object> userInputs = interactiveInput.getUserInputs();
                if (userInputs != null && !userInputs.isEmpty()) {
                    return String.valueOf(userInputs.values().iterator().next());
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
     * resolveTaskType.
     * 
     * @param targetName targetName
     * @return the result
     * @since 0.1.7
     */
    private String resolveTaskType(String targetName) {
        // Check if target matches a workflow name
        if (agentConfig.getWorkflows() != null) {
            for (WorkflowSchema ws : agentConfig.getWorkflows()) {
                if (ws.getName().equals(targetName)) {
                    return "workflow";
                }
            }
        }
        return "plugin";
    }

    /**
     * resolveTargetId.
     * 
     * @param targetName targetName
     * @return the result
     * @since 0.1.7
     */
    private String resolveTargetId(String targetName) {
        if (agentConfig.getWorkflows() != null) {
            for (WorkflowSchema ws : agentConfig.getWorkflows()) {
                if (ws.getName().equals(targetName)) {
                    return ws.getId() + "_" + ws.getVersion();
                }
            }
        }
        return findPluginIdByName(targetName);
    }

    /**
     * getWorkflowIdFromTask.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private String getWorkflowIdFromTask(Task task) {
        if (task.getMetadata() != null) {
            Object targetId = task.getMetadata().get("target_id");
            if (targetId instanceof String s && !s.isEmpty()) {
                return s;
            }
        }
        // Fallback: construct from schema
        String targetName = task.getDescription();
        return getWorkflowIdFromSchema(targetName);
    }

    /**
     * getWorkflowIdFromSchema.
     * 
     * @param workflowName workflowName
     * @return the result
     * @since 0.1.7
     */
    private String getWorkflowIdFromSchema(String workflowName) {
        if (agentConfig.getWorkflows() != null) {
            for (WorkflowSchema ws : agentConfig.getWorkflows()) {
                if (ws.getName().equals(workflowName)) {
                    return ws.getId() + "_" + ws.getVersion();
                }
            }
        }
        return null;
    }

    /**
     * ensureWorkflowId.
     * 
     * @param task task
     * @return the result
     * @since 0.1.7
     */
    private String ensureWorkflowId(Task task) {
        String id = getWorkflowIdFromTask(task);
        if (id != null) {
            return id;
        }
        return "unknown";
    }

    /**
     * findPluginIdByName.
     * 
     * @param toolName toolName
     * @return the result
     * @since 0.1.7
     */
    private String findPluginIdByName(String toolName) {
        if (agentConfig.getPlugins() != null) {
            for (PluginSchema ps : agentConfig.getPlugins()) {
                if (toolName.equals(ps.getName())) {
                    return ps.getId();
                }
            }
        }
        return null;
    }

    /**
     * resolveWorkflowFromTasks.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    private Task resolveWorkflowFromTasks(List<Task> tasks) {
        for (Task task : tasks) {
            if ("workflow".equals(task.getTaskType())) {
                return task;
            }
        }
        return null;
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
     * prepareWorkflowStreamData.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private List<Object> prepareWorkflowStreamData(Object result) {
        if (result instanceof WorkflowOutput wo && isWorkflowInterrupted(result)) {
            Object res = wo.getResult();
            if (res instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("output", result);
        payload.put("result_type", "answer");
        return List.of(new OutputSchema("workflow_final", 0, payload));
    }

    @SuppressWarnings("unchecked")
    /**
     * clearInterruptedState.
     * 
     * @param task task
     * @param session session
     * @param workflowId workflowId
     * @since 0.1.7
     */
    private void clearInterruptedState(Task task, AgentSessionApi session, String workflowId) {
        String stateKey = workflowId.replace('.', '_');

        Map<String, Object> beforeState = (Map<String, Object>) session.getState(STATE_KEY);
        Map<String, Object> beforeTasks =
            beforeState != null ? (Map<String, Object>) beforeState.get("interrupted_tasks") : null;
        Loggers.CONTROLLER.info("Before clear: interrupted_tasks keys={}",
                beforeTasks != null ? beforeTasks.keySet() : "null");

        Map<String, Object> update = new HashMap<>();
        update.put(STATE_KEY + ".interrupted_tasks." + stateKey, null);
        session.updateState(update);

        Map<String, Object> afterState = (Map<String, Object>) session.getState(STATE_KEY);
        Map<String, Object> afterTasks =
            afterState != null ? (Map<String, Object>) afterState.get("interrupted_tasks") : null;
        Loggers.CONTROLLER.info("After clear: interrupted_tasks keys={}",
                afterTasks != null ? afterTasks.keySet() : "null");

        Loggers.CONTROLLER.info("Cleared interrupted state for workflow: {}", workflowId);
    }

    /**
     * extractComponentIdsFromInteractionData.
     * 
     * @param interactionData interactionData
     * @return the result
     * @since 0.1.7
     */
    private List<String> extractComponentIdsFromInteractionData(List<Object> interactionData) {
        List<String> componentIds = new ArrayList<>();
        if (interactionData == null || interactionData.isEmpty()) {
            componentIds.add("questioner");
            return componentIds;
        }

        for (Object item : interactionData) {
            if (item instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                Object payload = os.getPayload();
                if (payload != null) {
                    if (payload instanceof Map<?, ?> map) {
                        Object id = map.get("id");
                        if (id instanceof String s && !s.isBlank()) {
                            componentIds.add(s);
                            continue;
                        }
                    }
                    try {
                        var method = payload.getClass().getMethod("getId");
                        Object id = method.invoke(payload);
                        if (id instanceof String s && !s.isBlank()) {
                            componentIds.add(s);
                        }
                    } catch (Exception e) {
                        Loggers.CONTROLLER.warning("Failed to extract component_id: {}", e.getMessage());
                    }
                }
            }
        }

        if (componentIds.isEmpty()) {
            componentIds.add("questioner");
        }
        return componentIds;
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

        boolean firstFound = false;
        List<Object> result = new ArrayList<>();
        for (Object chunk : interactionData) {
            if (chunk instanceof OutputSchema os && INTERACTION.equals(os.getType())) {
                if (!firstFound) {
                    result.add(chunk);
                    firstFound = true;
                }
                // Skip additional interrupts
            } else {
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

    @SuppressWarnings("unchecked")
    /**
     * unwrapResult.
     * 
     * @param result result
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> unwrapResult(Object result) {
        if (result instanceof List<?> list) {
            if (list.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("output", "");
                empty.put("result_type", "answer");
                return empty;
            }
            Object first = list.get(0);
            if (first instanceof OutputSchema os) {
                if (INTERACTION.equals(os.getType())) {
                    Map<String, Object> r = new HashMap<>();
                    r.put("interaction", list);
                    return r;
                }
                if (list.size() == 1 && os.getPayload() instanceof Map<?, ?> payload) {
                    return new HashMap<>((Map<String, Object>) payload);
                }
            }
            Map<String, Object> r = new HashMap<>();
            r.put("output", result);
            r.put("result_type", "answer");
            return r;
        }

        if (result instanceof OutputSchema os) {
            if (INTERACTION.equals(os.getType())) {
                Map<String, Object> r = new HashMap<>();
                r.put("interaction", List.of(os));
                return r;
            }
            if (os.getPayload() instanceof Map<?, ?> payload) {
                return new HashMap<>((Map<String, Object>) payload);
            }
            Map<String, Object> r = new HashMap<>();
            r.put("output", os.getPayload());
            r.put("result_type", "answer");
            return r;
        }

        if (result instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }

        Map<String, Object> r = new HashMap<>();
        r.put("output", result);
        r.put("result_type", "answer");
        return r;
    }

    /**
     * getSystemPromptKeywords.
     * 
     * @param event event
     * @param session session
     * @return the result
     * @since 0.1.7
     */
    private Map<String, String> getSystemPromptKeywords(Event event, AgentSessionApi session) {
        Map<String, String> result = new HashMap<>();
        if (!enableMemory) {
            return result;
        }
        // Memory keyword retrieval would be implemented here
        return result;
    }

    /**
     * buildInteractiveInputExtensions.
     * 
     * @param input input
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> buildInteractiveInputExtensions(InteractiveInput input) {
        Map<String, Object> extensions = new HashMap<>();
        extensions.put("interactive_input", input);
        return extensions;
    }

    @SuppressWarnings("unchecked")
    /**
     * reconstructAssistantMessage.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private AssistantMessage reconstructAssistantMessage(Map<String, Object> data) {
        if (data == null) {
            return new AssistantMessage("");
        }
        String content = (String) data.getOrDefault("content", "");
        List<ToolCall> toolCalls = null;

        Object rawToolCalls = data.get("tool_calls");
        if (rawToolCalls instanceof List<?> tcList && !tcList.isEmpty()) {
            toolCalls = AssistantMessage.convertOpenAiToolCalls((List<Map<String, Object>>) rawToolCalls);
        }

        return AssistantMessage.builder().content(content).toolCalls(toolCalls).build();
    }

    /**
     * serializeTaskList.
     * 
     * @param tasks tasks
     * @return the result
     * @since 0.1.7
     */
    private List<Map<String, Object>> serializeTaskList(List<Task> tasks) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Task task : tasks) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("session_id", task.getSessionId());
            map.put("task_id", task.getTaskId());
            map.put("task_type", task.getTaskType());
            map.put("description", task.getDescription());
            map.put("status", task.getStatus() != null ? task.getStatus().getValue() : null);
            map.put("inputs", task.getInputs());
            map.put("metadata", task.getMetadata());
            map.put("extensions", task.getExtensions());
            result.add(map);
        }
        return result;
    }

    /**
     * deserializeTaskList.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    private List<Task> deserializeTaskList(List<Map<String, Object>> data) {
        List<Task> tasks = new ArrayList<>();
        if (data == null) {
            return tasks;
        }
        for (Map<String, Object> map : data) {
            String sessionId = (String) map.getOrDefault("session_id", "");
            String taskId = (String) map.getOrDefault("task_id", "");
            String taskType = (String) map.getOrDefault("task_type", "");
            Task task = new Task(sessionId, taskId, taskType);
            task.setDescription((String) map.get("description"));
            String statusStr = (String) map.get("status");
            if (statusStr != null) {
                task.setStatus(TaskStatus.fromValue(statusStr));
            }
            @SuppressWarnings("unchecked")
            List<Object> inputs = (List<Object>) map.get("inputs");
            task.setInputs(inputs);
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
            task.setMetadata(metadata);
            @SuppressWarnings("unchecked")
            Map<String, Object> extensions = (Map<String, Object>) map.get("extensions");
            task.setExtensions(extensions);
            tasks.add(task);
        }
        return tasks;
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
            Object arguments = task.getMetadata().get("arguments");
            if (arguments instanceof String rawArguments) {
                return parseToolArguments(rawArguments);
            }
            return arguments;
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
     * parseToolArguments.
     * 
     * @param rawArguments rawArguments
     * @return the result
     * @since 0.1.7
     */
    private Object parseToolArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(rawArguments, STRING_OBJECT_MAP_TYPE);
        } catch (JsonProcessingException e) {
            Loggers.CONTROLLER.warning("Failed to parse tool arguments as json, keeping raw string: {}",
                    e.getMessage());
            return rawArguments;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * castArguments.
     * 
     * @param arguments arguments
     * @return the result
     * @since 0.1.7
     */
    private Map<String, Object> castArguments(Object arguments) {
        if (arguments instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return Map.of();
    }

    /**
     * serializeTaskArguments.
     * 
     * @param arguments arguments
     * @return the result
     * @since 0.1.7
     */
    private String serializeTaskArguments(Object arguments) {
        if (arguments == null) {
            return "{}";
        }
        if (arguments instanceof String rawArguments) {
            return rawArguments;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            Loggers.CONTROLLER.warning("Failed to serialize task arguments, falling back to empty object: {}",
                    e.getMessage());
            return "{}";
        }
    }

    /**
     * buildResumeInteractiveInput.
     * 
     * @param query query
     * @param componentIds componentIds
     * @return the result
     * @since 0.1.7
     */
    private InteractiveInput buildResumeInteractiveInput(String query, List<String> componentIds) {
        if (componentIds != null && !componentIds.isEmpty()) {
            InteractiveInput interactiveInput = new InteractiveInput();
            for (String componentId : componentIds) {
                interactiveInput.update(componentId, query);
            }
            return interactiveInput;
        }
        return new InteractiveInput(query);
    }

    // ==================== Inner Records ====================

    /**
     * LlmPlanResult.
     * 
     * @param tasks tasks
     * @param llmOutput llmOutput
     * @since 0.1.7
     */
    private record LlmPlanResult(List<Task> tasks, AssistantMessage llmOutput) {
    }

    /**
     * TaskExecutionResult.
     * 
     * @param status status
     * @param output output
     * @param errorMessage errorMessage
     * @param metadata metadata
     * @since 0.1.7
     */
    private record TaskExecutionResult(TaskStatus status, List<Object> output, String errorMessage,
            Map<String, Object> metadata) {
    }

    /**
     * ResumeResult.
     * 
     * @param aiMessage aiMessage
     * @param remainingTasks remainingTasks
     * @param savedIteration savedIteration
     * @param componentIds componentIds
     * @since 0.1.7
     */
    private record ResumeResult(AssistantMessage aiMessage, List<Task> remainingTasks, Integer savedIteration,
            List<String> componentIds) {
    }
}
