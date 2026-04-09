/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Legacy intent-detection controller with task routing support.
 * <p>
 * Supports real-time interruption: cancels running handlers and tasks
 * when a new request arrives for the same conversation.
 */
public abstract class IntentDetectionController extends BaseController {

    private static final Logger LOG = LoggerFactory.getLogger(IntentDetectionController.class);

    protected final TaskQueue taskQueue = new TaskQueue();

    /**
     * Track currently processing handlers (conversationId -> Thread).
     * This tracks at handleEvent level, earlier than TaskQueue.
     */
    private final Map<String, Thread> processingHandlers = new ConcurrentHashMap<>();

    /**
     * Override invoke to support real-time interruption.
     * <p>
     * When a new request arrives:
     * 1. Cancel currently processing handler thread first
     * 2. Cancel any running task in the TaskQueue
     * 3. Then delegate to parent invoke
     * <p>
     * Mirrors Python's {@code IntentDetectionController.invoke()}.
     */
    @Override
    public Map<String, Object> invoke(Map<String, Object> inputs, Session session) {
        String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));

        // Cancel processing handler BEFORE sending message to queue
        Thread oldHandler = processingHandlers.get(conversationId);
        if (oldHandler != null && oldHandler.isAlive()) {
            LOG.info("[IntentDetectionController] New request received, "
                    + "cancelling processing handler for {}", conversationId);
            oldHandler.interrupt();
        }

        // Also check TaskQueue for running tasks
        if (taskQueue.hasRunningTask(conversationId)) {
            LOG.info("[IntentDetectionController] Also cancelling running task "
                    + "for {}", conversationId);
            taskQueue.cancelRunningTask(conversationId);
        }

        return super.invoke(inputs, session);
    }

    @Override
    protected Map<String, Object> handleEvent(Event event, Session session) {
        String conversationId = event.getSource() != null
                ? event.getSource().getConversationId() : "default_session";
        Thread currentThread = Thread.currentThread();

        // Register current handler thread for cancellation tracking
        processingHandlers.put(conversationId, currentThread);
        LOG.debug("[IntentDetectionController] Registered handler for {}", conversationId);

        try {
            Intent intent = intentDetection(event, session);
            if (intent == null) {
                return handleUnknownIntent(event, null, session);
            }

            return switch (intent.getIntentType()) {
                case EXEC_NEW_TASK -> handleNewTask(event, intent, session);
                case RESUME_TASK -> handleResume(event, intent, session);
                case CANCEL_TASK -> handleCancel(event, intent, session);
                case DEFAULT_RESPONSE -> handleDefaultResponse(event, intent, session);
                case UNKNOWN -> handleUnknownIntent(event, intent, session);
            };
        } catch (RuntimeException e) {
            if (Thread.interrupted()) {
                // Handler was cancelled by new request
                LOG.info("[IntentDetectionController] Handler cancelled for {}", conversationId);
                Map<String, Object> cancelled = new LinkedHashMap<>();
                cancelled.put("status", "cancelled");
                cancelled.put("conversation_id", conversationId);
                return cancelled;
            }
            throw e;
        } finally {
            // Unregister handler thread
            processingHandlers.remove(conversationId, currentThread);
            LOG.debug("[IntentDetectionController] Unregistered handler for {}", conversationId);
        }
    }

    protected Map<String, Object> handleNewTask(Event event, Intent intent, Session session) {
        if (intent.getTask() == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        intent.getTask().setStatus(Task.TaskStatus.PENDING);
        return execTask(event.getContent(), intent.getTask(), session);
    }

    /**
     * Handle task resumption: Update input -> Execute.
     * <p>
     * Key: Must create InteractiveInput with new user input to update task parameters.
     * Mirrors Python's {@code IntentDetectionController._handle_resume()}.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> handleResume(Event event, Intent intent, Session session) {
        if (intent.getTask() == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        Task task = intent.getTask();

        // Task status should already be INTERRUPTED
        if (task.getStatus() != Task.TaskStatus.INTERRUPTED) {
            LOG.warn("Resuming task with unexpected status: {}", task.getStatus());
        }

        LOG.info("Handling resume task: task_id={}", task.getTaskId());

        // Get target workflow's interrupted component_id
        String workflowId = task.getInput() != null ? task.getInput().getTargetId() : null;
        String targetComponentId = "questioner"; // Default value

        Object state = session.getState("workflow_controller");
        if (state instanceof Map<?, ?> stateMap && workflowId != null) {
            String stateKey = workflowId.replace('.', '_');
            Object interruptedTasks = ((Map<String, Object>) stateMap).get("interrupted_tasks");
            if (interruptedTasks instanceof Map<?, ?> interruptedMap) {
                Object interruptedInfo = ((Map<String, Object>) interruptedMap).get(stateKey);
                if (interruptedInfo instanceof Map<?, ?> infoMap) {
                    Object compId = ((Map<String, Object>) infoMap).get("component_id");
                    if (compId != null) {
                        targetComponentId = String.valueOf(compId);
                    }
                }
            }
        }

        LOG.info("Target workflow interrupted component_id: {}", targetComponentId);

        // Build InteractiveInput for resume
        InteractiveInput interactiveInput;

        Event.EventContent eventContent = event.getContent();
        if (eventContent != null && eventContent.getInteractiveInput() != null) {
            InteractiveInput providedInput = eventContent.getInteractiveInput();
            LOG.info("Provided InteractiveInput for resume");

            if (providedInput.getUserInputs() != null && !providedInput.getUserInputs().isEmpty()) {
                List<String> providedKeys = List.copyOf(providedInput.getUserInputs().keySet());
                List<String> targetIds = List.of(targetComponentId);

                boolean matches = providedKeys.stream().anyMatch(targetIds::contains);
                if (!matches) {
                    // Mismatch: remap user input value to target component
                    Object userValue = providedInput.getUserInputs().values().iterator().next();
                    LOG.info("Component ID mismatch: provided={}, target={}. Remapping.",
                            providedKeys, targetComponentId);
                    interactiveInput = new InteractiveInput();
                    interactiveInput.update(targetIds.get(0), userValue);
                } else {
                    interactiveInput = providedInput;
                }
            } else {
                interactiveInput = providedInput;
            }
        } else {
            // Create InteractiveInput from user query text
            String queryText = eventContent != null ? eventContent.getQueryText() : "";
            interactiveInput = new InteractiveInput();
            interactiveInput.update(targetComponentId, queryText);
            LOG.info("Created InteractiveInput for resume: component_id={}, query={}",
                    targetComponentId, queryText);
        }

        // Update task input arguments to InteractiveInput
        if (task.getInput() != null) {
            task.getInput().setArguments(interactiveInput);
        }

        return execTask(event.getContent(), task, session);
    }

    protected Map<String, Object> handleCancel(Event event, Intent intent, Session session) {
        if (intent.getTask() == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        intent.getTask().setStatus(Task.TaskStatus.CANCELLED);
        return Map.of("status", "cancelled", "task_id", intent.getTask().getTaskId());
    }

    protected Map<String, Object> handleDefaultResponse(Event event, Intent intent, Session session) {
        String defaultText = intent != null && intent.getMetadata() != null
                ? String.valueOf(intent.getMetadata().getOrDefault("default_response_text", ""))
                : "";
        if (session instanceof AgentSessionApi agentSessionApi) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("response", defaultText);
            payload.put("output", Map.of());
            agentSessionApi.writeStream(new OutputSchema("workflow_final", 0, payload));
        }
        return Map.of(
                "status", "default_response",
                "output", Map.of("answer", defaultText),
                "result_type", "answer"
        );
    }

    protected Map<String, Object> handleUnknownIntent(Event event, Intent intent, Session session) {
        return Map.of("status", "error", "message", "Unknown intent type");
    }

    protected abstract Intent intentDetection(Event event, Session session);

    protected abstract Map<String, Object> execTask(Event.EventContent messageContent, Task task, Session session);

    protected abstract Map<String, Object> interruptTask(Task task, Session session);

    public enum IntentType {
        EXEC_NEW_TASK,
        RESUME_TASK,
        CANCEL_TASK,
        DEFAULT_RESPONSE,
        UNKNOWN
    }

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
    }

    public static class TaskQueue {
        private final Map<String, RunningTaskInfo> runningTasks = new ConcurrentHashMap<>();

        public void registerTask(String conversationId, Task task, Future<?> future, String targetId) {
            runningTasks.put(conversationId, new RunningTaskInfo(task, future, targetId, System.currentTimeMillis()));
        }

        public boolean cancelRunningTask(String conversationId) {
            RunningTaskInfo info = runningTasks.get(conversationId);
            if (info == null || info.getFuture() == null) {
                return false;
            }
            return info.getFuture().cancel(true);
        }

        public void unregisterTask(String conversationId) {
            runningTasks.remove(conversationId);
        }

        public RunningTaskInfo findTask(String conversationId) {
            return runningTasks.get(conversationId);
        }

        public boolean hasRunningTask(String conversationId) {
            return runningTasks.containsKey(conversationId);
        }
    }

    @Data
    @AllArgsConstructor
    public static class RunningTaskInfo {
        private Task task;
        private Future<?> future;
        private String targetId;
        private long startTime;
    }
}
