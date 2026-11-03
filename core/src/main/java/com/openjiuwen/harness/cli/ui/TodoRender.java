/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Todo checkbox rendering for CLI output.
 * <p>
 * Mirrors Python's module in
 * {@code openjiuwen/harness/cli/ui/todo_render.py}.
 */
public final class TodoRender {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String[]> STATUS_STYLE = Map.of(
            "completed", new String[]{"\u2612", "green"},
            "in_progress", new String[]{"\u25fc", "yellow"},
            "pending", new String[]{"\u2610", "dim"},
            "cancelled", new String[]{"\u2715", "dim strike"}
    );
    private static final Map<String, String> SDK_ICON_TO_STATUS = Map.of(
            "[>]", "in_progress",
            "[ ]", "pending",
            "[\u221a]", "completed",
            "[\u00d7]", "cancelled"
    );

    private static final Pattern CREATE_PATTERN = Pattern.compile(
            "\\[([>\\u221a\\u00d7 ])]\\s+task_id:\\s*\\S+\\s*,\\s*content:\\s*(.+)",
            Pattern.MULTILINE
    );
    private static final Pattern SECTION_PATTERN = Pattern.compile(
            "^\\[([>\\u221a\\u00d7 ])]\\s+(?:In Progress|Pending|Completed|Cancelled)",
            Pattern.MULTILINE
    );
    private static final Pattern ITEM_PATTERN = Pattern.compile("^\\s+\\[[\\w-]+]\\s+(.+)");

    private TodoRender() {
    }

    public static String renderTodoItem(String content, String status) {
        String[] iconStyle = STATUS_STYLE.getOrDefault(status, STATUS_STYLE.get("pending"));
        String icon = iconStyle[0];
        String style = iconStyle[1];
        return "[" + style + "]" + icon + " " + content + "[/" + style + "]";
    }

    public static List<String> renderTodoList(List<Map<String, Object>> items) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String content = String.valueOf(item.getOrDefault("content", item.getOrDefault("activeForm", "")));
            String status = String.valueOf(item.getOrDefault("status", "pending"));
            lines.add("  \u23bf " + renderTodoItem(content, status));
        }
        return lines;
    }

    public static String renderTodoSummary(List<Map<String, Object>> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("completed", 0);
        counts.put("in_progress", 0);
        counts.put("pending", 0);
        counts.put("cancelled", 0);
        for (Map<String, Object> item : items) {
            String status = String.valueOf(item.getOrDefault("status", "pending"));
            if (counts.containsKey(status)) {
                counts.put(status, counts.get(status) + 1);
            }
        }
        List<String> parts = new ArrayList<>();
        if (counts.get("completed") > 0) {
            parts.add("\u2713" + counts.get("completed"));
        }
        if (counts.get("in_progress") > 0) {
            parts.add("\u25fc" + counts.get("in_progress"));
        }
        if (counts.get("pending") > 0) {
            parts.add("\u2610" + counts.get("pending"));
        }
        return parts.isEmpty() ? "No tasks" : String.join(" ", parts);
    }

    public static List<Map<String, Object>> parseTodoResult(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return null;
        }
        try {
            Object data = MAPPER.readValue(toolResult, new TypeReference<Object>() {
            });
            List<Map<String, Object>> structured = extractStructuredItems(data);
            if (structured != null) {
                return structured;
            }
        } catch (Exception ignored) {
            // Fall through to SDK-style text parsing.
        }
        return parseTodoText(cleanPythonReprSuffix(toolResult));
    }

    public static Map<String, Object> parseTodoToolArgs(Object toolArgs) {
        if (toolArgs instanceof Map<?, ?> rawMap) {
            return toStringKeyMap(rawMap);
        }
        if (toolArgs instanceof String text) {
            try {
                Object data = MAPPER.readValue(text, new TypeReference<Object>() {
                });
                if (data instanceof Map<?, ?> rawMap) {
                    return toStringKeyMap(rawMap);
                }
            } catch (Exception ignored) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    public static List<Map<String, Object>> applyTodoModifyArgs(List<Map<String, Object>> items, Object toolArgs) {
        Map<String, Object> args = parseTodoToolArgs(toolArgs);
        Object actionObject = args.get("action");
        if (!(actionObject instanceof String action) || action.isBlank()) {
            return null;
        }

        List<Map<String, Object>> currentItems = new ArrayList<>();
        for (Map<String, Object> item : items) {
            currentItems.add(new LinkedHashMap<>(item));
        }

        return switch (action) {
            case "update" -> applyUpdate(currentItems, args.get("todos"));
            case "delete" -> applyDelete(currentItems, args.get("ids"));
            case "cancel" -> applyCancel(currentItems, args.get("ids"));
            case "append" -> applyAppend(currentItems, args.get("todos"));
            case "insert_after" -> applyInsert(currentItems, args.get("todo_data"), true);
            case "insert_before" -> applyInsert(currentItems, args.get("todo_data"), false);
            default -> null;
        };
    }

    private static List<Map<String, Object>> extractStructuredItems(Object data) {
        if (data instanceof List<?> list) {
            return normalizeList(list);
        }
        if (data instanceof Map<?, ?> rawMap) {
            for (String key : List.of("items", "tasks", "todos", "result")) {
                Object value = rawMap.get(key);
                if (value instanceof List<?> list) {
                    return normalizeList(list);
                }
            }
            if (rawMap.containsKey("content") || rawMap.containsKey("status")) {
                return List.of(toStringKeyMap(rawMap));
            }
        }
        return null;
    }

    private static List<Map<String, Object>> normalizeList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawMap) {
                result.add(toStringKeyMap(rawMap));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> parseTodoText(String toolResult) {
        String normalized = toolResult.replace("\\n", "\n");
        List<Map<String, Object>> items = new ArrayList<>();

        Matcher createMatcher = CREATE_PATTERN.matcher(normalized);
        while (createMatcher.find()) {
            items.add(todoItem(createMatcher.group(2).trim(), statusFor(createMatcher.group(1))));
        }
        if (!items.isEmpty()) {
            return items;
        }

        String currentStatus = "pending";
        for (String line : normalized.split("\\R")) {
            Matcher sectionMatcher = SECTION_PATTERN.matcher(line);
            if (sectionMatcher.find()) {
                currentStatus = statusFor(sectionMatcher.group(1));
                continue;
            }
            Matcher itemMatcher = ITEM_PATTERN.matcher(line);
            if (itemMatcher.find()) {
                items.add(todoItem(itemMatcher.group(1).trim(), currentStatus));
            }
        }
        return items.isEmpty() ? null : items;
    }

    private static String cleanPythonReprSuffix(String toolResult) {
        String cleaned = toolResult.stripTrailing();
        for (String suffix : List.of("'}", "\"}", "}", "'", "\"")) {
            if (cleaned.endsWith(suffix)) {
                return cleaned.substring(0, cleaned.length() - suffix.length());
            }
        }
        return cleaned;
    }

    private static Map<String, Object> todoItem(String content, String status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("content", content);
        item.put("status", status);
        return item;
    }

    private static String statusFor(String marker) {
        return SDK_ICON_TO_STATUS.getOrDefault("[" + marker + "]", "pending");
    }

    private static List<Map<String, Object>> applyUpdate(List<Map<String, Object>> currentItems, Object updatesObject) {
        if (!(updatesObject instanceof List<?> updates)) {
            return null;
        }
        Map<Object, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> item : currentItems) {
            Object id = item.get("id");
            if (id != null) {
                byId.put(id, item);
            }
        }
        if (byId.isEmpty()) {
            return null;
        }
        for (Object updateObject : updates) {
            if (!(updateObject instanceof Map<?, ?> rawUpdate)) {
                continue;
            }
            Object id = rawUpdate.get("id");
            Map<String, Object> item = byId.get(id);
            if (item == null) {
                continue;
            }
            item.putAll(toStringKeyMap(rawUpdate));
            syncDisplayContent(item);
        }
        return currentItems;
    }

    private static List<Map<String, Object>> applyDelete(List<Map<String, Object>> currentItems, Object idsObject) {
        if (!(idsObject instanceof List<?> ids)) {
            return null;
        }
        Set<?> deleteIds = Set.copyOf(ids);
        return currentItems.stream().filter(item -> !deleteIds.contains(item.get("id"))).toList();
    }

    private static List<Map<String, Object>> applyCancel(List<Map<String, Object>> currentItems, Object idsObject) {
        if (!(idsObject instanceof List<?> ids)) {
            return null;
        }
        Set<?> cancelIds = Set.copyOf(ids);
        for (Map<String, Object> item : currentItems) {
            if (cancelIds.contains(item.get("id"))) {
                item.put("status", "cancelled");
                syncDisplayContent(item);
            }
        }
        return currentItems;
    }

    private static List<Map<String, Object>> applyAppend(List<Map<String, Object>> currentItems, Object todosObject) {
        if (!(todosObject instanceof List<?> todos)) {
            return null;
        }
        for (Object todo : todos) {
            if (todo instanceof Map<?, ?> rawMap) {
                currentItems.add(normalizeTodoItem(rawMap));
            }
        }
        return currentItems;
    }

    private static List<Map<String, Object>> applyInsert(List<Map<String, Object>> currentItems, Object todoDataObject, boolean after) {
        if (!(todoDataObject instanceof List<?> todoData) || todoData.size() != 2) {
            return null;
        }
        Object targetId = todoData.get(0);
        Object insertTodosObject = todoData.get(1);
        if (!(targetId instanceof String) || !(insertTodosObject instanceof List<?> insertTodos)) {
            return null;
        }
        int targetIndex = -1;
        for (int index = 0; index < currentItems.size(); index++) {
            if (Objects.equals(targetId, currentItems.get(index).get("id"))) {
                targetIndex = index;
                break;
            }
        }
        if (targetIndex < 0) {
            return null;
        }
        List<Map<String, Object>> newItems = new ArrayList<>();
        for (Object todo : insertTodos) {
            if (todo instanceof Map<?, ?> rawMap) {
                newItems.add(normalizeTodoItem(rawMap));
            }
        }
        int insertIndex = after ? targetIndex + 1 : targetIndex;
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(currentItems.subList(0, insertIndex));
        result.addAll(newItems);
        result.addAll(currentItems.subList(insertIndex, currentItems.size()));
        return result;
    }

    private static Map<String, Object> normalizeTodoItem(Map<?, ?> rawMap) {
        Map<String, Object> normalized = toStringKeyMap(rawMap);
        syncDisplayContent(normalized);
        return normalized;
    }

    private static void syncDisplayContent(Map<String, Object> item) {
        Object status = item.getOrDefault("status", "pending");
        Object activeForm = item.get("activeForm");
        if ("in_progress".equals(status) && activeForm instanceof String text && !text.isBlank()) {
            item.put("content", text);
        }
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }
}
