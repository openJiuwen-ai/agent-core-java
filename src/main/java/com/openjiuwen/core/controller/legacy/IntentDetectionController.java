/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.controller.legacy;

import com.openjiuwen.core.controller.legacy.event.Event;
import com.openjiuwen.core.controller.legacy.task.Task;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Legacy intent-detection controller with task routing support.
 */
public abstract class IntentDetectionController extends BaseController {

    protected final TaskQueue taskQueue = new TaskQueue();

    @Override
    protected Map<String, Object> handleEvent(Event event, Session session) {
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
    }

    protected Map<String, Object> handleNewTask(Event event, Intent intent, Session session) {
        if (intent.getTask() == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        intent.getTask().setStatus(Task.TaskStatus.PENDING);
        return execTask(event.getContent(), intent.getTask(), session);
    }

    protected Map<String, Object> handleResume(Event event, Intent intent, Session session) {
        if (intent.getTask() == null) {
            return Map.of("status", "error", "message", "Task not found in intent");
        }
        return execTask(event.getContent(), intent.getTask(), session);
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
