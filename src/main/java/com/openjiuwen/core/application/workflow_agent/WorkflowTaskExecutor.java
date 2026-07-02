/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.utils.MessageUtils;
import com.openjiuwen.core.context_engine.ContextEngine;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.controller.modules.TaskExecutor;
import com.openjiuwen.core.controller.modules.TaskExecutorDependencies;
import com.openjiuwen.core.controller.modules.TaskFilter;
import com.openjiuwen.core.controller.schema.ControllerOutputChunk;
import com.openjiuwen.core.controller.schema.ControllerOutputPayload;
import com.openjiuwen.core.controller.schema.DataFrame;
import com.openjiuwen.core.controller.schema.EventType;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Executes workflow tasks inside the new controller framework.
 *
 * <p>Mirrors Python's {@code WorkflowTaskExecutor} in
 * {@code openjiuwen/core/application/workflow_agent/workflow_task_executor.py}.</p>
 */
public class WorkflowTaskExecutor extends TaskExecutor {

    private static final Logger LOGGER = Logger.getLogger(WorkflowTaskExecutor.class.getName());
    private static final String WORKFLOW_CONTROLLER_STATE = "workflow_controller";
    private static final String INTERRUPTED_TASKS = "interrupted_tasks";
    private static final ObjectMapper JSON = new ObjectMapper();

    public WorkflowTaskExecutor(TaskExecutorDependencies dependencies) {
        super(dependencies);
    }

    @Override
    public Iterator<ControllerOutputChunk> executeAbility(String taskId, AgentSessionApi session) {
        List<Task> tasks = taskManager.getTask(TaskFilter.byTaskId(taskId));
        if (tasks.isEmpty()) {
            LOGGER.severe("WorkflowTaskExecutor: task not found: " + taskId);
            return List.<ControllerOutputChunk>of().iterator();
        }

        Task task = tasks.get(0);
        Map<String, Object> extensions = mapValue(task.getExtensions());
        String resumeMode = Objects.toString(extensions.getOrDefault("resume_mode", "new"), "new");
        String workflowId = Objects.toString(extensions.getOrDefault("workflow_id", ""), "");
        Object inputs = "resume".equals(resumeMode)
                ? extensions.get("interactive_input")
                : valueOrEmptyMap(extensions.get("filtered_inputs"));

        return runWorkflowAndCollect(task, session, workflowId, inputs).iterator();
    }

    @Override
    public PauseCheckResult canPause(String taskId, AgentSessionApi session) {
        return new PauseCheckResult(false, "Workflow tasks do not support pause");
    }

    @Override
    public boolean pause(String taskId, AgentSessionApi session) {
        LOGGER.warning("WorkflowTaskExecutor does not support pause");
        return false;
    }

    @Override
    public CancelCheckResult canCancel(String taskId, AgentSessionApi session) {
        return new CancelCheckResult(true, "");
    }

    @Override
    public boolean cancel(String taskId, AgentSessionApi session) {
        taskManager.removeTask(TaskFilter.byTaskId(taskId));
        return true;
    }

    private List<ControllerOutputChunk> runWorkflowAndCollect(
            Task task, AgentSessionApi session, String workflowId, Object inputs) {
        Object workflow = findWorkflow(workflowId, session, "");
        if (workflow == null) {
            return List.of();
        }

        Object workflowSession = createWorkflowSession(session);
        ModelContext context = contextEngine == null ? null : contextEngine.createContext(workflowId, session);
        Iterator<?> workflowStream = runWorkflowStreaming(workflow, inputs, workflowSession, context);
        StreamCollectResult collected = collectStreamChunks(workflowStream, session);

        if (collected.hasInteraction()) {
            return handleInterruption(task, session, workflowId, collected);
        }
        return handleCompletion(task, session, workflowId, collected);
    }

    protected Iterator<?> runWorkflowStreaming(Object workflow, Object inputs, Object workflowSession, ModelContext context) {
        return Runner.runWorkflowStreaming(
                workflow,
                inputs,
                workflowSession,
                context,
                List.of(StreamMode.OUTPUT),
                null
        ).toCompletableFuture().join();
    }

    private StreamCollectResult collectStreamChunks(Iterator<?> workflowStream, AgentSessionApi session) {
        StreamCollectResult result = new StreamCollectResult();
        while (workflowStream != null && workflowStream.hasNext()) {
            Object rawChunk = workflowStream.next();
            Object chunk = rawChunk instanceof WorkflowChunk workflowChunk ? workflowChunk : rawChunk;
            if (chunk instanceof OutputSchema outputSchema) {
                if (Constant.INTERACTION.equals(outputSchema.getType())) {
                    result.setHasInteraction(true);
                } else if ("workflow_final".equals(outputSchema.getType())) {
                    result.setFinalResult(outputSchema.getPayload());
                    session.writeStream(outputSchema);
                } else {
                    session.writeStream(outputSchema);
                }
            } else if (chunk instanceof CustomSchema customSchema) {
                writeCustomStream(session, customSchema);
            } else {
                session.writeStream(chunk);
            }
            result.getChunks().add(chunk);
        }
        return result;
    }

    private List<ControllerOutputChunk> handleInterruption(
            Task task, AgentSessionApi session, String workflowId, StreamCollectResult collected) {
        saveInterruptState(task, session, collected.getChunks());

        WorkflowOutput workflowOutput = null;
        OutputSchema firstInteraction = findFirstInteraction(collected.getChunks());
        if (firstInteraction != null) {
            session.writeStream(firstInteraction);
            workflowOutput = new WorkflowOutput(firstInteraction, WorkflowExecutionState.INPUT_REQUIRED);
        }

        Object interactionValue = extractInteractionValue(collected.getChunks());
        if (interactionValue != null) {
            addAiMessage(String.valueOf(interactionValue), session);
        }

        return List.of(buildOutputChunk(EventType.TASK_INTERACTION, workflowId, task.getTaskId(), workflowOutput));
    }

    private List<ControllerOutputChunk> handleCompletion(
            Task task, AgentSessionApi session, String workflowId, StreamCollectResult collected) {
        clearInterruptState(task, session);

        WorkflowOutput workflowOutput = null;
        if (collected.getFinalResult() != null) {
            addAiMessage(stringifyResult(collected.getFinalResult()), session);
            workflowOutput = new WorkflowOutput(collected.getFinalResult(), WorkflowExecutionState.COMPLETED);
        }

        return List.of(buildOutputChunk(EventType.TASK_COMPLETION, workflowId, task.getTaskId(), workflowOutput));
    }

    private static ControllerOutputChunk buildOutputChunk(
            EventType eventType, String workflowId, String taskId, Object result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workflow_id", workflowId);
        data.put("task_id", taskId);
        data.put("result", result);
        return new ControllerOutputChunk(
                0,
                new ControllerOutputPayload(eventType, List.of(new DataFrame.JsonDataFrame(data)))
        );
    }

    private void saveInterruptState(Task task, AgentSessionApi session, List<Object> interactionData) {
        Map<String, Object> extensions = mapValue(task.getExtensions());
        String workflowId = Objects.toString(extensions.getOrDefault("workflow_id", ""), "");
        String stateKey = workflowStateKey(workflowId);

        Map<String, Object> state = stateMap(session);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));

        Object componentId = extractComponentIds(interactionData);
        Object interactionValue = extractInteractionValue(interactionData);

        Map<String, Object> taskState = new LinkedHashMap<>();
        taskState.put("task", task.toMap());
        taskState.put("component_id", componentId);
        taskState.put("last_interaction_value", interactionValue);
        interruptedTasks.put(stateKey, taskState);
        state.put(INTERRUPTED_TASKS, interruptedTasks);

        extensions.put("component_id", componentId);
        task.setExtensions(extensions);
        taskManager.updateTask(task);

        flushSessionState(session, state);
    }

    private void clearInterruptState(Task task, AgentSessionApi session) {
        Map<String, Object> extensions = mapValue(task.getExtensions());
        String workflowId = Objects.toString(extensions.getOrDefault("workflow_id", ""), "");
        String stateKey = workflowStateKey(workflowId);

        Map<String, Object> state = stateMap(session);
        Map<String, Object> interruptedTasks = mapValue(state.get(INTERRUPTED_TASKS));
        if (interruptedTasks.remove(stateKey) != null) {
            state.put(INTERRUPTED_TASKS, interruptedTasks);
            flushSessionState(session, state);
        }
    }

    private static void flushSessionState(AgentSessionApi session, Map<String, Object> state) {
        Map<String, Object> clearUpdate = new LinkedHashMap<>();
        clearUpdate.put(WORKFLOW_CONTROLLER_STATE, null);
        session.updateState(clearUpdate);
        session.updateState(Map.of(WORKFLOW_CONTROLLER_STATE, state));
    }

    protected Object findWorkflow(String workflowId, AgentSessionApi session, String agentId) {
        try {
            Object workflow = Runner.resourceMgr().getWorkflow(workflowId, session).toCompletableFuture().join();
            if (workflow != null) {
                return workflow;
            }
        } catch (RuntimeException error) {
            LOGGER.warning("Failed to find workflow " + workflowId + ": " + error.getMessage());
        }
        LOGGER.severe("Workflow not found: " + workflowId);
        return null;
    }

    protected Object createWorkflowSession(AgentSessionApi session) {
        try {
            Method method = session.getClass().getMethod("createWorkflowSession");
            return method.invoke(session);
        } catch (IllegalAccessException | NoSuchMethodException ignored) {
            return session;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    static OutputSchema findFirstInteraction(List<Object> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        for (Object chunk : chunks) {
            if (chunk instanceof OutputSchema outputSchema && Constant.INTERACTION.equals(outputSchema.getType())) {
                return outputSchema;
            }
        }
        return null;
    }

    static Object extractComponentIds(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return "";
        }
        List<String> componentIds = new ArrayList<>();
        for (Object item : interactionData) {
            if (item instanceof OutputSchema outputSchema && Constant.INTERACTION.equals(outputSchema.getType())) {
                Object componentId = payloadField(outputSchema.getPayload(), "id");
                if (componentId != null) {
                    componentIds.add(String.valueOf(componentId));
                }
            }
        }
        if (componentIds.isEmpty()) {
            return "";
        }
        return componentIds.size() == 1 ? componentIds.get(0) : componentIds;
    }

    static Object extractInteractionValue(List<Object> interactionData) {
        if (interactionData == null || interactionData.isEmpty()) {
            return null;
        }
        for (Object item : interactionData) {
            if (item instanceof OutputSchema outputSchema && Constant.INTERACTION.equals(outputSchema.getType())) {
                Object value = payloadField(outputSchema.getPayload(), "value");
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object payloadField(Object payload, String fieldName) {
        if (payload instanceof Map<?, ?> map) {
            return map.get(fieldName);
        }
        if (payload instanceof InteractionOutput interactionOutput) {
            return "id".equals(fieldName) ? interactionOutput.getId() : interactionOutput.getValue();
        }
        if (payload == null) {
            return null;
        }
        try {
            Method getter = payload.getClass().getMethod(
                    "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1));
            return getter.invoke(payload);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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

    private static void writeCustomStream(AgentSessionApi session, CustomSchema customSchema) {
        try {
            Method method = session.getClass().getMethod("writeCustomStream", Object.class);
            method.invoke(session, customSchema);
        } catch (IllegalAccessException | NoSuchMethodException ignored) {
            session.writeStream(customSchema);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(cause);
        }
    }

    private static Object valueOrEmptyMap(Object value) {
        return value == null ? Map.of() : value;
    }

    private static Map<String, Object> stateMap(AgentSessionApi session) {
        return mapValue(session == null ? null : session.getState(WORKFLOW_CONTROLLER_STATE));
    }

    private static String workflowStateKey(String workflowId) {
        return workflowId == null ? "" : workflowId.replace(".", "_");
    }

    private static String stringifyResult(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            try {
                return JSON.writeValueAsString(value);
            } catch (JsonProcessingException ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
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
     * Mirrors Python's {@code _StreamCollectResult} in
     * {@code openjiuwen/core/application/workflow_agent/workflow_task_executor.py}.
     */
    private static class StreamCollectResult {
        private final List<Object> chunks = new ArrayList<>();
        private boolean hasInteraction;
        private Object finalResult;

        List<Object> getChunks() {
            return chunks;
        }

        boolean hasInteraction() {
            return hasInteraction;
        }

        void setHasInteraction(boolean hasInteraction) {
            this.hasInteraction = hasInteraction;
        }

        Object getFinalResult() {
            return finalResult;
        }

        void setFinalResult(Object finalResult) {
            this.finalResult = finalResult;
        }
    }
}
