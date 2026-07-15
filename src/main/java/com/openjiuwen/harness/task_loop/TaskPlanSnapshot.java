/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.task_loop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.harness.tools.TodoItem;

import lombok.Builder;
import lombok.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class TaskPlanSnapshot used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
public class TaskPlanSnapshot {
    private String sessionId;
    private String taskId;
    private int round;
    private boolean isFollowUp;
    private String updatedAt;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<TodoItem> todos = new ArrayList<>();
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> result = new LinkedHashMap<>();
    private UsageMetadata usageMetadata;
    private int tokenUsage;

    /**
     * from.
     * 
     * @param ctx ctx
     * @param todos todos
     * @return the result
     * @since 0.1.7
     */
    public static TaskPlanSnapshot from(TaskIterationContext ctx, List<TodoItem> todos) {
        return TaskPlanSnapshot.builder().sessionId(ctx != null ? ctx.sessionId() : "")
                .taskId(ctx != null ? ctx.taskId() : "").round(ctx != null ? ctx.getRound() : 0)
                .isFollowUp(ctx != null && ctx.isFollowUp()).updatedAt(Instant.now().toString())
                .todos(todos == null ? List.of() : new ArrayList<>(todos))
                .result(ctx != null && ctx.getResult() != null
                        ? new LinkedHashMap<>(ctx.getResult())
                        : new LinkedHashMap<>())
                .usageMetadata(ctx != null ? ctx.resolvedUsageMetadata() : null)
                .tokenUsage(ctx != null ? ctx.tokenUsage() : 0).build();
    }

    /**
     * toMap.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> toMap() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("session_id", sessionId);
        payload.put("task_id", taskId);
        payload.put("round", round);
        payload.put("follow_up", isFollowUp);
        payload.put("updated_at", updatedAt);
        payload.put("todos", todos);
        payload.put("result", result);
        payload.put("token_usage", tokenUsage);
        if (usageMetadata != null) {
            payload.put("usage_metadata", usageMetadata);
        }
        return payload;
    }

    /**
     * toTaskPlan.
     * 
     * @return the result
     * @since 0.1.7
     */
    public TaskPlan toTaskPlan() {
        Object raw = result == null ? null : firstNonNull(result, new String[]{"task_plan", "taskPlan"});
        TaskPlan plan = TaskPlan.fromObject(raw);
        if (plan != null) {
            return plan;
        }
        return TaskPlan.builder().tasks(todos == null ? new ArrayList<>() : new ArrayList<>(todos)).build();
    }

    /**
     * save.
     * 
     * @param path path
     * @throws IOException IOException
     * @since 0.1.7
     */
    public void save(Path path) throws IOException {
        if (path == null) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, JsonUtils.safeJsonDumps(toMap(), "{}"));
    }

    /**
     * load.
     * 
     * @param path path
     * @return the result
     * @throws IOException IOException
     * @since 0.1.7
     */
    public static TaskPlanSnapshot load(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        Map<String, Object> data =
            JsonUtils.getMapper().readValue(Files.readString(path), new TypeReference<Map<String, Object>>() {
            });
        return fromMap(data);
    }

    /**
     * fromMap.
     * 
     * @param data data
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static TaskPlanSnapshot fromMap(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return TaskPlanSnapshot.builder().sessionId(string(firstNonNull(data, new String[]{"session_id", "sessionId"})))
                .taskId(string(firstNonNull(data, new String[]{"task_id", "taskId"})))
                .round(intValue(data.get("round")))
                .isFollowUp(booleanValue(firstNonNull(data, new String[]{"follow_up", "isFollowUp"})))
                .updatedAt(string(firstNonNull(data, new String[]{"updated_at", "updatedAt"})))
                .todos(parseTodos(data.get("todos")))
                .result(data.get("result") instanceof Map<?, ?> map
                        ? new LinkedHashMap<>((Map<String, Object>) map)
                        : new LinkedHashMap<>())
                .usageMetadata(parseUsage(data.get("usage_metadata"))).tokenUsage(intValue(data.get("token_usage")))
                .build();
    }

    /**
     * parseTodos.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static List<TodoItem> parseTodos(Object raw) {
        if (raw instanceof List<?> list) {
            List<TodoItem> parsed = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof TodoItem todoItem) {
                    parsed.add(todoItem);
                } else if (item instanceof Map<?, ?> map) {
                    parsed.add(JsonUtils.getMapper().convertValue(map, TodoItem.class));
                } else {
                    // no-op
                }
            }
            return parsed;
        }
        return new ArrayList<>();
    }

    /**
     * parseUsage.
     * 
     * @param raw raw
     * @return the result
     * @since 0.1.7
     */
    private static UsageMetadata parseUsage(Object raw) {
        if (raw instanceof UsageMetadata usageMetadata) {
            return usageMetadata;
        }
        if (raw instanceof Map<?, ?> map) {
            return JsonUtils.getMapper().convertValue(map, UsageMetadata.class);
        }
        return null;
    }

    /**
     * firstNonNull.
     * 
     * @param source source
     * @param keys keys
     * @return the result
     * @since 0.1.7
     */
    private static Object firstNonNull(Map<String, Object> source, String[] keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * string.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * intValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * booleanValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
