/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.common.utils.MessageUtils;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.controller.legacy.task.TaskStatus;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Intent detection controller with task routing and interruption handling.
 *
 * <p>Mirrors Python's {@code IntentDetectionController} in
 * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.</p>
 */
public abstract class IntentDetectionController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(IntentDetectionController.class);

    protected final TaskQueue taskQueue = new TaskQueue();

    private final Map<String, Thread> processingHandlers = new ConcurrentHashMap<>();

    private Object session;

    protected IntentDetectionController() {
        super();
    }

    protected IntentDetectionController(Object config, ContextEngine contextEngine) {
        super(config, contextEngine);
    }

    protected IntentDetectionController(Object config, ContextEngine contextEngine, Object session) {
        super(config, contextEngine);
        this.session = session;
    }

    /**
     * Override invoke to support Python's real-time interruption behavior.
     *
     * @param inputs input dictionary containing query and conversation_id
     * @param session session context
     * @return processing result
     */
    @Override
    public Map<String, Object> invoke(Map<String, Object> inputs, Object session) {
        Map<String, Object> safeInputs = inputs == null ? Map.of() : inputs;
        String conversationId = String.valueOf(
                safeInputs.getOrDefault("conversation_id", "default_session")
        );

        Thread oldHandler = processingHandlers.get(conversationId);
        if (oldHandler != null && oldHandler.isAlive()) {
            LOG.info("[IntentDetectionController] New request received, "
                    + "cancelling processing handler for {}", conversationId);
            oldHandler.interrupt();
        }

        if (taskQueue.hasRunningTask(conversationId)) {
            LOG.info("[IntentDetectionController] Also cancelling workflow task for {}", conversationId);
            taskQueue.cancelRunningTask(conversationId);
        }

        this.session = session;
        return super.invoke(safeInputs, session);
    }

    /**
     * Standard message flow: intent detection, user-message persistence, and route dispatch.
     *
     * @param event event object
     * @param session session context
     * @return processing result
     */
    @Override
    protected Map<String, Object> handleEvent(Event event, Object session) {
        String conversationId = conversationId(event);
        Thread currentThread = Thread.currentThread();
        processingHandlers.put(conversationId, currentThread);
        LOG.debug("[IntentDetectionController] Registered handler for {}", conversationId);

        try {
            Intent intent = intentDetection(event, session);
            addUserMessage(event, session);
            if (intent == null || intent.getIntentType() == null) {
                return handleUnknownIntent(event, intent, session);
            }

            return switch (intent.getIntentType()) {
                case EXEC_NEW_TASK -> handleNewTask(event, intent, session);
                case RESUME_TASK -> handleResume(event, intent, session);
                case CANCEL_TASK -> handleCancel(event, intent, session);
                case DEFAULT_RESPONSE -> handleDefaultResponse(event, intent, session);
                case UNKNOWN -> handleUnknownIntent(event, intent, session);
            };
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                LOG.info("[IntentDetectionController] Handler cancelled for {}", conversationId);
                Map<String, Object> cancelled = new LinkedHashMap<>();
                cancelled.put("status", "cancelled");
                cancelled.put("conversation_id", conversationId);
                return cancelled;
            }
            throw e;
        } finally {
            processingHandlers.remove(conversationId, currentThread);
            LOG.debug("[IntentDetectionController] Unregistered handler for {}", conversationId);
        }
    }

    protected Map<String, Object> handleNewTask(Event event, Intent intent, Object session) {
        Task task = intent.getTask();
        if (task == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        task.setStatus(TaskStatus.PENDING);
        LOG.info("Handling new task: task_id={}", task.getTaskId());
        return execTask(event == null ? null : event.getContent(), task, session);
    }

    /**
     * Handle task resumption by remapping the user's input to the interrupted component.
     *
     * @param event event object
     * @param intent detected intent
     * @param session session context
     * @return execution result
     */
    protected Map<String, Object> handleResume(Event event, Intent intent, Object session) {
        Task task = intent.getTask();
        if (task == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        if (task.getStatus() != TaskStatus.INTERRUPTED) {
            LOG.warn("Resuming task with unexpected status: {}", task.getStatus());
        }

        String workflowId = task.getInput() == null ? "" : task.getInput().getTargetId();
        Object targetComponentValue = interruptedComponentId(session, workflowId);
        List<String> targetIds = normalizeTargetIds(targetComponentValue);

        Event.EventContent content = event == null ? null : event.getContent();
        InteractiveInput interactiveInput = resolveInteractiveInput(content, targetIds);
        if (task.getInput() != null) {
            task.getInput().setArguments(interactiveInput);
        }

        return execTask(content, task, session);
    }

    protected Map<String, Object> handleCancel(Event event, Intent intent, Object session) {
        Task task = intent.getTask();
        if (task == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        task.setStatus(TaskStatus.CANCELLED);
        LOG.info("Handling cancel task: task_id={}", task.getTaskId());
        return Map.of("status", "cancelled", "task_id", task.getTaskId());
    }

    protected Map<String, Object> handleDefaultResponse(Event event, Intent intent, Object session) {
        String defaultText = "";
        if (intent != null && intent.getMetadata() != null) {
            defaultText = String.valueOf(intent.getMetadata().getOrDefault("default_response_text", ""));
        }
        LOG.info("Returning default response: {}", defaultText);

        Map<String, Object> finalPayload = new LinkedHashMap<>();
        finalPayload.put("response", defaultText);
        finalPayload.put("output", Map.of());
        if (session instanceof AgentSessionApi agentSessionApi) {
            agentSessionApi.writeStream(new OutputSchema("workflow_final", 0, finalPayload));
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("answer", defaultText);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "default_response");
        result.put("output", output);
        result.put("result_type", "answer");
        return result;
    }

    protected Map<String, Object> handleUnknownIntent(Event event, Intent intent, Object session) {
        LOG.warn("Unknown intent type: {}", intent == null ? null : intent.getIntentType());
        return Map.of(
                "status", "error",
                "message", "Unknown intent type: " + (intent == null ? null : intent.getIntentType())
        );
    }

    protected abstract Intent intentDetection(Event event, Object session);

    protected abstract Map<String, Object> execTask(Event.EventContent messageContent, Task task, Object session);

    protected abstract Map<String, Object> interruptTask(Task task, Object session);

    private void addUserMessage(Event event, Object session) {
        if (event == null || contextEngine == null || session == null) {
            return;
        }
        ModelContext context = contextEngine.getContext(
                ContextEngine.DEFAULT_CONTEXT_ID,
                resolveSessionId(session)
        );
        if (context == null) {
            return;
        }
        MessageUtils.addUserMessage(
                event.getDisplayContent(),
                new ContextEngineMessagePort(contextEngine),
                new SessionMessagePort(resolveSessionId(session))
        ).toCompletableFuture().join();
    }

    private static String conversationId(Event event) {
        if (event == null || event.getSource() == null
                || event.getSource().getConversationId() == null) {
            return "default_session";
        }
        return event.getSource().getConversationId();
    }

    @SuppressWarnings("unchecked")
    private static Object interruptedComponentId(Object session, String workflowId) {
        Object state = null;
        if (session instanceof AgentSessionApi agentSessionApi) {
            state = agentSessionApi.getState("workflow_controller");
        } else {
            state = invoke(session, "getState", "workflow_controller");
            if (state == null) {
                state = invoke(session, "get_state", "workflow_controller");
            }
        }
        Object targetComponentId = "questioner";
        if (state instanceof Map<?, ?> stateMap && workflowId != null) {
            String stateKey = workflowId.replace('.', '_');
            Object interruptedTasks = ((Map<String, Object>) stateMap).get("interrupted_tasks");
            if (interruptedTasks instanceof Map<?, ?> interruptedTasksMap) {
                Object interruptedInfo = ((Map<String, Object>) interruptedTasksMap).get(stateKey);
                if (interruptedInfo instanceof Map<?, ?> interruptedInfoMap) {
                    Object componentId = ((Map<String, Object>) interruptedInfoMap).get("component_id");
                    if (componentId != null) {
                        targetComponentId = componentId;
                    }
                }
            }
        }
        return targetComponentId;
    }

    private static InteractiveInput resolveInteractiveInput(Event.EventContent content, List<String> targetIds) {
        if (content != null && content.getInteractiveInput() != null) {
            InteractiveInput providedInput = content.getInteractiveInput();
            Map<String, Object> userInputs = providedInput.getUserInputs();
            if (userInputs != null && !userInputs.isEmpty()) {
                boolean matches = userInputs.keySet().stream().anyMatch(targetIds::contains);
                if (!matches) {
                    Object userValue = userInputs.values().iterator().next();
                    InteractiveInput remappedInput = new InteractiveInput();
                    remappedInput.update(targetIds.get(0), userValue);
                    return remappedInput;
                }
            }
            return providedInput;
        }

        InteractiveInput interactiveInput = new InteractiveInput();
        String queryText = content == null ? "" : content.getQueryText();
        interactiveInput.update(targetIds.get(0), queryText);
        return interactiveInput;
    }

    private static List<String> normalizeTargetIds(Object value) {
        if (value instanceof Iterable<?> values) {
            List<String> result = new ArrayList<>();
            for (Object item : values) {
                result.add(String.valueOf(item));
            }
            return result.isEmpty() ? List.of("questioner") : result;
        }
        return List.of(value == null ? "questioner" : String.valueOf(value));
    }

    private static String resolveSessionId(Object session) {
        if (session instanceof AgentSessionApi agentSessionApi) {
            return agentSessionApi.getSessionId();
        }
        Object value = invoke(session, "getSessionId");
        if (value == null) {
            value = invoke(session, "get_session_id");
        }
        return value == null ? ContextEngine.DEFAULT_SESSION_ID : String.valueOf(value);
    }

    private static Object invoke(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }
        try {
            Class<?>[] types = new Class<?>[arguments.length];
            for (int index = 0; index < arguments.length; index++) {
                types[index] = arguments[index] == null ? Object.class : arguments[index].getClass();
            }
            Method method = findCompatibleMethod(target.getClass(), methodName, arguments.length);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findCompatibleMethod(Class<?> type, String methodName, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(methodName)
                    && method.getParameterCount() == parameterCount) {
                return method;
            }
        }
        throw new NoSuchMethodException(methodName);
    }

    /**
     * Mirrors Python's {@code IntentType} in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.
     */
    public enum IntentType {
        EXEC_NEW_TASK("exec_new_task"),
        RESUME_TASK("resume_task"),
        CANCEL_TASK("cancel_task"),
        DEFAULT_RESPONSE("default_response"),
        UNKNOWN("unknown");

        private final String value;

        IntentType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * Mirrors Python's {@code Intent} in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Intent {
        @Builder.Default
        private IntentType intentType = IntentType.UNKNOWN;

        private Task task;

        private Object workflow;

        @Builder.Default
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public void setMetadata(Map<String, Object> metadata) {
            this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
        }
    }

    /**
     * Mirrors Python's {@code RunningTaskInfo} in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunningTaskInfo {
        private Task task;
        private Future<?> future;
        private String targetId;
        private Instant startTime;
    }

    /**
     * Mirrors Python's {@code TaskQueue} in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.
     */
    public static class TaskQueue {
        private final Map<String, RunningTaskInfo> runningTasks = new ConcurrentHashMap<>();

        public void registerTask(String conversationId, Task task, Future<?> future, String targetId) {
            runningTasks.put(
                    conversationId,
                    new RunningTaskInfo(task, future, targetId, Instant.now())
            );
            LOG.info("TaskQueue: Registered task for {}, target={}", conversationId, targetId);
        }

        public boolean cancelRunningTask(String conversationId) {
            RunningTaskInfo info = runningTasks.get(conversationId);
            if (info == null) {
                return false;
            }
            Future<?> future = info.getFuture();
            if (future != null && !future.isDone()) {
                LOG.info("TaskQueue: Cancelling task for {}, target={}", conversationId, info.getTargetId());
                return future.cancel(true);
            }
            return false;
        }

        public void unregisterTask(String conversationId) {
            if (runningTasks.remove(conversationId) != null) {
                LOG.info("TaskQueue: Unregistered task for {}", conversationId);
            }
        }

        public RunningTaskInfo findTask(String conversationId) {
            return runningTasks.get(conversationId);
        }

        public boolean hasRunningTask(String conversationId) {
            return runningTasks.containsKey(conversationId);
        }
    }

    /**
     * Session adapter used by message history persistence.
     *
     * <p>Mirrors Python's session dependency in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.</p>
     */
    private record SessionMessagePort(String sessionId) implements MessageUtils.SessionPort {
        @Override
        public String getSessionId() {
            return sessionId;
        }
    }

    /**
     * Context-engine adapter used by message history persistence.
     *
     * <p>Mirrors Python's context-engine dependency in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.</p>
     */
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

    /**
     * Model-context adapter used by message history persistence.
     *
     * <p>Mirrors Python's agent context dependency in
     * {@code openjiuwen/core/controller/legacy/intent_detection_controller.py}.</p>
     */
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

    protected Object getSession() {
        return session;
    }

    protected Map<String, Thread> getProcessingHandlers() {
        return processingHandlers;
    }
}
