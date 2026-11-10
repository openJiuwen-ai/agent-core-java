/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 在通用参数校验前转换待办修改工具的旧参数，模型只看到统一的 todos/id 结构。
 *
 * @since 0.1.16
 */
public final class TodoModifyFunction extends LocalFunction {
    private static final Set<String> UPDATE_FIELDS = Set.of("content", "activeForm", "active_form", "description",
            "status", "priority", "selected_model_id", "depends_on", "result_summary", "meta_data");

    /**
     * 创建待办修改工具，保留父类的参数校验和会话上下文处理。
     *
     * @param card 统一参数结构的工具元数据
     * @param function 执行待办修改的函数
     * @since 0.1.16
     */
    public TodoModifyFunction(ToolCard card, Function<Map<String, Object>, Object> function) {
        super(card, function);
    }

    /** {@inheritDoc} */
    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return super.invoke(normalizeInputs(inputs), kwargs);
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        return super.stream(normalizeInputs(inputs), kwargs);
    }

    private static Map<String, Object> normalizeInputs(Map<String, Object> inputs) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (inputs != null) {
            normalized.putAll(inputs);
        }
        if (normalized.containsKey("updates")) {
            Object legacyUpdates = normalized.remove("updates");
            Object action = normalized.get("action");
            if (action == null || (action instanceof String value && value.isBlank())) {
                normalized.put("action", "update");
            }
            if (!normalized.containsKey("todos")) {
                normalized.put("todos", normalizeLegacyUpdates(legacyUpdates));
            }
        }
        if ("update".equals(normalized.get("action"))) {
            validateUpdates(normalized.get("todos"));
        }
        return normalized;
    }

    private static List<Map<String, Object>> normalizeLegacyUpdates(Object rawUpdates) {
        if (!(rawUpdates instanceof List<?> updates)) {
            throw new IllegalArgumentException("todo_modify updates must be a non-empty list");
        }
        List<Map<String, Object>> normalized = new ArrayList<>(updates.size());
        for (Object value : updates) {
            if (!(value instanceof Map<?, ?> update)) {
                throw new IllegalArgumentException("todo_modify updates items must be objects");
            }
            Map<String, Object> item = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : update.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("todo_modify update field names must be strings");
                }
                item.put(key, entry.getValue());
            }
            if (item.containsKey("task_id")) {
                item.put("id", item.remove("task_id"));
            }
            normalized.add(item);
        }
        return normalized;
    }

    private static void validateUpdates(Object rawTodos) {
        if (!(rawTodos instanceof List<?> todos) || todos.isEmpty()) {
            throw new IllegalArgumentException("todo_modify update requires a non-empty todos list");
        }
        for (Object value : todos) {
            if (!(value instanceof Map<?, ?> todo)) {
                throw new IllegalArgumentException("todo_modify todos items must be objects");
            }
            if (!(todo.get("id") instanceof String id) || id.isBlank()) {
                throw new IllegalArgumentException("todo_modify update requires todos[].id");
            }
            if (todo.containsKey("task_id")) {
                throw new IllegalArgumentException("todo_modify todos uses id, not task_id");
            }
            if (todo.keySet().stream().noneMatch(UPDATE_FIELDS::contains)) {
                throw new IllegalArgumentException("todo_modify update requires fields to change inside each todo");
            }
        }
    }
}
