/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.agent_control;

import com.openjiuwen.core.common.exception.FrameworkError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.controller.modules.TaskScheduler;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.controller.schema.TaskStatus;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session tools for async subagent spawning.
 *
 * <p>Mirrors Python's {@code session_tools} module in
 * {@code openjiuwen.harness.tools.agent_control.session_tools}.
 */
public final class SessionTools {

    private static final Logger LOG = LoggerFactory.getLogger(SessionTools.class);

    /** Task type for session spawn. */
    public static final String SESSION_SPAWN_TASK_TYPE = "session_spawn_task";

    private SessionTools() {
    }

    /**
     * Minimal metadata surface shared by the Java session tools.
     */
    public interface SessionTool {
        String getName();
    }

    /**
     * Session task row (business view).
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionTaskRow {
        private String taskId;
        private String subSessionId;
        private String description;
        private String status;
        @Builder.Default
        private String result = "";
        @Builder.Default
        private String error = "";
    }

    /**
     * Session task registry.
     * <p>
     * Mirrors Python's {@code SessionToolkit} class.
     */
    public static class SessionToolkit {
        private final Map<String, SessionTaskRow> rows = new ConcurrentHashMap<>();

        /**
         * Insert or update task as running.
         */
        public void upsertRunning(String taskId, String subSessionId, String description) {
            SessionTaskRow row = SessionTaskRow.builder()
                    .taskId(taskId)
                    .subSessionId(subSessionId)
                    .description(description)
                    .status("running")
                    .build();
            rows.put(taskId, row);
            LOG.debug("[SessionToolkit] upsert_running task_id={}", taskId);
        }

        /**
         * Mark task as completed with result.
         */
        public void markCompleted(String taskId, String result) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("completed");
                row.setResult(result != null ? result : "");
                LOG.debug("[SessionToolkit] mark_completed task_id={}", taskId);
            }
        }

        /**
         * Mark task as failed with error.
         */
        public void markFailed(String taskId, String error) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("error");
                row.setError(error != null ? error : "");
                LOG.debug("[SessionToolkit] mark_failed task_id={}", taskId);
            }
        }

        /**
         * Mark task as canceled.
         */
        public void markCanceled(String taskId) {
            SessionTaskRow row = rows.get(taskId);
            if (row != null) {
                row.setStatus("canceled");
                LOG.debug("[SessionToolkit] mark_canceled task_id={}", taskId);
            }
        }

        /**
         * List all tasks.
         */
        public List<SessionTaskRow> listAll() {
            return new ArrayList<>(rows.values());
        }

        /**
         * Get task by id.
         */
        public SessionTaskRow get(String taskId) {
            return rows.get(taskId);
        }

        /**
         * Clear all tasks.
         */
        public void clear() {
            rows.clear();
            LOG.debug("[SessionToolkit] clear");
        }

        /**
         * Get count of tasks.
         */
        public int size() {
            return rows.size();
        }
    }

    /**
     * Sessions list tool.
     * <p>
     * Mirrors Python's {@code SessionsListTool}.
     */
    public static class SessionsListTool implements SessionTool {
        private static final String NAME = "sessions_list";

        private final SessionToolkit toolkit;
        private final String language;

        public SessionsListTool(SessionToolkit toolkit, String language) {
            this.toolkit = toolkit;
            this.language = language != null ? language : "cn";
        }

        @Override
        public String getName() {
            return NAME;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            List<SessionTaskRow> tasks = toolkit.listAll();
            StringBuilder sb = new StringBuilder();

            for (SessionTaskRow task : tasks) {
                sb.append(String.format(
                        "task_id=%s | description=%s | status=%s | result=%s | error=%s%n",
                        task.getTaskId(),
                        task.getDescription(),
                        task.getStatus(),
                        task.getResult(),
                        task.getError()
                ));
            }

            if (sb.length() == 0) {
                String emptyMsg = "cn".equals(language)
                        ? "\u5f53\u524d\u4f1a\u8bdd\u6ca1\u6709\u540e\u53f0\u5b50\u4efb\u52a1"
                        : "No background tasks for this session";
                sb.append(emptyMsg);
            }

            return ToolOutput.success(sb.toString());
        }
    }

    /**
     * Sessions cancel tool.
     * <p>
     * Mirrors Python's {@code SessionsCancelTool}.
     */
    public static class SessionsCancelTool implements SessionTool {
        private static final String NAME = "sessions_cancel";

        private final Object parentAgent;
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsCancelTool(Object parentAgent, SessionToolkit toolkit, String language) {
            this.parentAgent = parentAgent;
            this.toolkit = toolkit;
            this.language = language != null ? language : "cn";
        }

        @Override
        public String getName() {
            return NAME;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            return invoke((Object) inputs);
        }

        public ToolOutput invoke(Object inputs) {
            Map<String, Object> normalized = requireInputsMap(inputs);
            String taskId = stringValue(normalized.get("task_id"));

            if (taskId.isBlank()) {
                throw frameworkError("task_id is required");
            }

            SessionTaskRow row = toolkit.get(taskId);
            if (row == null) {
                throw frameworkError("Task " + taskId + " not found");
            }

            boolean canceled = cancelScheduledTask(resolveTaskScheduler(parentAgent), taskId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", taskId);
            data.put("status", canceled ? "canceled" : row.getStatus());
            data.put("message", cancelMessage(taskId, canceled, language));

            if (!canceled) {
                return ToolOutput.builder().success(false).data(data).build();
            }

            toolkit.markCanceled(taskId);
            LOG.info("[SessionsCancelTool] Cancelled task_id={}", taskId);
            return ToolOutput.success(data);
        }
    }

    /**
     * Sessions read tool.
     * <p>
     * Mirrors Python's SessionsReadTool compatibility surface. The Python
     * factory does not expose this tool.
     */
    public static class SessionsReadTool implements SessionTool {
        private static final String NAME = "sessions_read";

        private final SessionToolkit toolkit;
        private final String language;

        public SessionsReadTool(SessionToolkit toolkit, String language) {
            this.toolkit = toolkit;
            this.language = language != null ? language : "cn";
        }

        @Override
        public String getName() {
            return NAME;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            String taskId = stringValue(inputs != null ? inputs.get("task_id") : null);

            if (taskId.isBlank()) {
                String errorMsg = "cn".equals(language)
                        ? "\u7f3a\u5c11 task_id \u53c2\u6570"
                        : "Missing required parameter: task_id";
                return ToolOutput.error(errorMsg);
            }

            SessionTaskRow row = toolkit.get(taskId);
            if (row == null) {
                String errorMsg = "cn".equals(language)
                        ? "\u627e\u4e0d\u5230\u4efb\u52a1 " + taskId
                        : "Task not found: " + taskId;
                return ToolOutput.error(errorMsg);
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", row.getTaskId());
            data.put("description", row.getDescription());
            data.put("status", row.getStatus());
            data.put("result", row.getResult());
            data.put("error", row.getError());

            return ToolOutput.success(data);
        }
    }

    /**
     * Sessions spawn tool.
     * <p>
     * Mirrors Python's {@code SessionsSpawnTool}.
     */
    public static class SessionsSpawnTool implements SessionTool {
        private static final String NAME = "sessions_spawn";

        private final Object parentAgent;
        private final SessionToolkit toolkit;
        private final String language;

        public SessionsSpawnTool(Object parentAgent, SessionToolkit toolkit, String language) {
            this.parentAgent = parentAgent;
            this.toolkit = toolkit;
            this.language = language != null ? language : "cn";
        }

        @Override
        public String getName() {
            return NAME;
        }

        public ToolOutput invoke(Map<String, Object> inputs) {
            return invoke((Object) inputs, Map.of());
        }

        public ToolOutput invoke(Object inputs, Object session) {
            return invoke(inputs, Map.of("session", session));
        }

        public ToolOutput invoke(Object inputs, Map<String, Object> kwargs) {
            Object deepConfig = resolveDeepConfig(parentAgent);
            if (deepConfig == null || !readBoolean(
                    deepConfig,
                    "isEnableTaskLoop",
                    "getEnableTaskLoop",
                    "enableTaskLoop",
                    "enable_task_loop"
            )) {
                throw frameworkError("enable_task_loop is required for session spawn");
            }

            Object handler = resolveEventHandler(parentAgent);
            Object taskManager = readMember(handler, "getTaskManager", "taskManager", "task_manager");
            if (handler == null || taskManager == null) {
                throw frameworkError("task loop handler/task_manager not available");
            }

            Map<String, Object> normalized = requireInputsMap(inputs);
            String subagentType = stringValue(normalized.get("subagent_type"));
            if (subagentType.isBlank()) {
                subagentType = "general-purpose";
            }
            String taskDescription = stringValue(normalized.get("task_description"));
            if (taskDescription.isBlank()) {
                taskDescription = stringValue(normalized.get("description"));
            }

            Object parentSession = kwargs != null ? kwargs.get("session") : null;
            if (!(parentSession instanceof Session session)) {
                throw frameworkError("SessionSpawnTool requires a valid session in kwargs");
            }

            String parentSessionId = session.getSessionId();
            String taskId = randomHex(32);
            String subSessionId = parentSessionId + "_sub_" + randomHex(8);

            Task task = new Task(parentSessionId, taskId, SESSION_SPAWN_TASK_TYPE);
            task.setDescription(taskDescription);
            task.setStatus(TaskStatus.SUBMITTED);
            task.setMetadata(Map.of(
                    "subagent_type", subagentType,
                    "task_description", taskDescription,
                    "sub_session_id", subSessionId
            ));
            addTask(taskManager, task);

            toolkit.upsertRunning(taskId, subSessionId, taskDescription);
            LOG.info("[SessionsSpawnTool] Submitted task_id={}, sub_session_id={}, subagent_type={}",
                    taskId, subSessionId, subagentType);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "pending");
            data.put("message", spawnMessage(taskDescription, language));
            return ToolOutput.success(data);
        }
    }

    /**
     * Create session tools for the given toolkit.
     */
    public static List<Object> createSessionTools(Object parentAgent, SessionToolkit toolkit, String language) {
        List<Object> tools = new ArrayList<>();
        tools.add(new SessionsListTool(toolkit, language));
        tools.add(new SessionsSpawnTool(parentAgent, toolkit, language));
        tools.add(new SessionsCancelTool(parentAgent, toolkit, language));
        return tools;
    }

    /**
     * Tool output wrapper.
     */
    @Data
    @Builder
    public static class ToolOutput {
        private boolean success;
        private Object data;
        private String error;

        public static ToolOutput success(Object data) {
            return ToolOutput.builder().success(true).data(data).build();
        }

        public static ToolOutput error(String error) {
            return ToolOutput.builder().success(false).error(error).build();
        }
    }

    private static FrameworkError frameworkError(String message) {
        return new FrameworkError(
                StatusCode.TOOL_EXECUTION_ERROR,
                message,
                null,
                null,
                Map.of("card", "sessions", "reason", message)
        );
    }

    private static Map<String, Object> requireInputsMap(Object inputs) {
        if (!(inputs instanceof Map<?, ?> raw)) {
            String type = inputs == null ? "null" : inputs.getClass().getName();
            throw frameworkError("Invalid inputs type: " + type);
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return normalized;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Object resolveDeepConfig(Object parentAgent) {
        if (parentAgent instanceof DeepAgent deepAgent && deepAgent.getConfig() instanceof DeepAgentConfig config) {
            return config;
        }
        return readMember(parentAgent, "getDeepConfig", "deepConfig", "deep_config");
    }

    private static Object resolveEventHandler(Object parentAgent) {
        return readMember(parentAgent, "getEventHandler", "eventHandler", "event_handler");
    }

    private static Object resolveTaskScheduler(Object parentAgent) {
        Object controller = readMember(parentAgent, "getLoopController", "loopController", "loop_controller");
        if (controller == null) {
            throw frameworkError("loop_controller not available");
        }
        Object scheduler = readMember(controller, "getTaskScheduler", "taskScheduler", "task_scheduler");
        if (scheduler == null) {
            throw frameworkError("task_scheduler not available");
        }
        return scheduler;
    }

    private static boolean cancelScheduledTask(Object scheduler, String taskId) {
        Object result;
        if (scheduler instanceof TaskScheduler taskScheduler) {
            result = taskScheduler.cancelTask(taskId);
        } else {
            result = invokeMethod(scheduler, new String[]{"cancelTask", "cancel_task"}, taskId);
        }
        if (result instanceof CompletionStage<?> stage) {
            result = stage.toCompletableFuture().join();
        }
        return !(result instanceof Boolean bool) || bool;
    }

    private static void addTask(Object taskManager, Task task) {
        invokeMethod(taskManager, new String[]{"addTask", "add_task"}, task);
    }

    private static Object readMember(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (NoSuchMethodException ignored) {
                    // Try fields below.
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access method: " + name, e);
                } catch (InvocationTargetException e) {
                    throw new IllegalStateException("Cannot invoke method: " + name, e.getCause());
                }
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(target);
                } catch (NoSuchFieldException ignored) {
                    // Continue searching.
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot access field: " + name, e);
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static boolean readBoolean(Object target, String... names) {
        Object value = readMember(target, names);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Object invokeMethod(Object target, String[] names, Object... args) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            for (String name : names) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
                        continue;
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, args);
                    } catch (IllegalArgumentException ignored) {
                        // Overload with incompatible parameter types; try the next candidate.
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException("Cannot access method: " + name, e);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException runtimeException) {
                            throw runtimeException;
                        }
                        throw new IllegalStateException("Cannot invoke method: " + name, cause);
                    }
                }
            }
            type = type.getSuperclass();
        }
        throw frameworkError("Required method not available: " + String.join("/", names));
    }

    private static String randomHex(int chars) {
        return UUID.randomUUID().toString().replace("-", "").substring(0, chars);
    }

    private static String cancelMessage(String taskId, boolean canceled, String language) {
        if ("cn".equals(language)) {
            return canceled
                    ? "\u4efb\u52a1 " + taskId + " \u53d6\u6d88\u6210\u529f"
                    : "\u4efb\u52a1 " + taskId + " \u53d6\u6d88\u5931\u8d25";
        }
        return canceled ? "Task " + taskId + " cancel success" : "Task " + taskId + " cancel failed";
    }

    private static String spawnMessage(String taskDescription, String language) {
        if ("cn".equals(language)) {
            return "\u5b50\u4efb\u52a1 " + taskDescription
                    + " \u5df2\u63d0\u4ea4\u540e\u53f0\u6267\u884c\uff0c"
                    + "\u4f60\u53ef\u4ee5\u7ee7\u7eed\u53d1\u9001\u5176\u4ed6\u95ee\u9898";
        }
        return "Task " + taskDescription + " submitted to background, you can continue to send other questions";
    }
}
