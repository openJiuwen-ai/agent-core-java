/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.unit;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CLI todo rendering.
 * <p>
 * Mirrors Python's {@code test_todo_render} in
 * {@code tests.cli.unit.test_todo_render}.
 */
class TodoRenderTest {

    private String renderTodoItem(String content, String status) {
        return switch (status) {
            case "completed" -> "[green]☑ " + content;
            case "in_progress" -> "[yellow]◐ " + content;
            case "cancelled" -> "[dim]☒ " + content;
            default -> "[dim]☐ " + content;
        };
    }

    private List<String> renderTodoList(List<Map<String, Object>> items) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String content = (String) item.getOrDefault("content",
                    item.getOrDefault("activeForm", ""));
            String status = (String) item.getOrDefault("status", "pending");
            lines.add("⎿ " + renderTodoItem(content, status));
        }
        return lines;
    }

    private String renderTodoSummary(List<Map<String, Object>> items) {
        if (items.isEmpty()) return "No tasks";
        long completed = items.stream()
                .filter(i -> "completed".equals(i.get("status"))).count();
        long inProgress = items.stream()
                .filter(i -> "in_progress".equals(i.get("status"))).count();
        long pending = items.stream()
                .filter(i -> "pending".equals(i.get("status"))).count();

        StringBuilder sb = new StringBuilder();
        if (completed > 0) sb.append("✓").append(completed).append(" ");
        if (inProgress > 0) sb.append("◐").append(inProgress).append(" ");
        if (pending > 0) sb.append("☐").append(pending);
        return sb.toString().trim();
    }

    private List<Map<String, Object>> parseTodoResult(String result) {
        if (result == null || result.isEmpty()) return null;

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            if (result.trim().startsWith("[")) {
                return mapper.readValue(result,
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            }
            if (result.trim().startsWith("{")) {
                var node = mapper.readTree(result);
                if (node.has("items")) {
                    return mapper.convertValue(node.get("items"),
                            new com.fasterxml.jackson.core.type.TypeReference<>() {});
                }
            }
        } catch (Exception ignored) {
        }

        List<Map<String, Object>> items = new ArrayList<>();
        Pattern taskPattern = Pattern.compile("\\[([>√ ])]\\s+\\[(\\S+)]\\s+(.+)");
        Matcher matcher = taskPattern.matcher(result);
        while (matcher.find()) {
            String marker = matcher.group(1);
            String id = matcher.group(2);
            String content = matcher.group(3).trim();
            String status = switch (marker) {
                case "√" -> "completed";
                case ">" -> "in_progress";
                default -> "pending";
            };
            items.add(Map.of("id", id, "content", content, "status", status));
        }

        Pattern createPattern = Pattern.compile("\\[([> ])]\\s+task_id:\\s*(\\S+)\\s*,\\s*content:\\s*(.+)");
        Matcher createMatcher = createPattern.matcher(result);
        while (createMatcher.find()) {
            String marker = createMatcher.group(1);
            String id = createMatcher.group(2);
            String content = createMatcher.group(3).trim();
            String status = ">".equals(marker) ? "in_progress" : "pending";
            items.add(Map.of("id", id, "content", content, "status", status));
        }

        return items.isEmpty() ? null : items;
    }

    @Test
    void completedShowsCheckmark() {
        String result = renderTodoItem("任务A", "completed");
        assertTrue(result.contains("☑"));
        assertTrue(result.contains("green"));
        assertTrue(result.contains("任务A"));
    }

    @Test
    void inProgressShowsHalf() {
        String result = renderTodoItem("任务B", "in_progress");
        assertTrue(result.contains("◐"));
        assertTrue(result.contains("yellow"));
    }

    @Test
    void pendingShowsEmpty() {
        String result = renderTodoItem("任务C", "pending");
        assertTrue(result.contains("☐"));
        assertTrue(result.contains("dim"));
    }

    @Test
    void cancelledShowsCrossed() {
        String result = renderTodoItem("任务D", "cancelled");
        assertTrue(result.contains("☒"));
    }

    @Test
    void unknownStatusDefaultsToPending() {
        String result = renderTodoItem("任务E", "unknown_status");
        assertTrue(result.contains("☐"));
    }

    @Test
    void mixedStatusList() {
        List<Map<String, Object>> items = List.of(
                Map.of("content", "完成的", "status", "completed"),
                Map.of("content", "进行中", "status", "in_progress"),
                Map.of("content", "待处理", "status", "pending")
        );
        List<String> lines = renderTodoList(items);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("☑"));
        assertTrue(lines.get(1).contains("◐"));
        assertTrue(lines.get(2).contains("☐"));
        for (String line : lines) {
            assertTrue(line.contains("⎿"));
        }
    }

    @Test
    void emptyListReturnsEmptyLines() {
        assertEquals(List.of(), renderTodoList(List.of()));
    }

    @Test
    void activeFormFallback() {
        List<Map<String, Object>> items = List.of(
                Map.of("activeForm", "进行中的活动", "status", "in_progress")
        );
        List<String> lines = renderTodoList(items);
        assertTrue(lines.get(0).contains("进行中的活动"));
    }

    @Test
    void mixedSummary() {
        List<Map<String, Object>> items = List.of(
                Map.of("status", "completed"),
                Map.of("status", "completed"),
                Map.of("status", "in_progress"),
                Map.of("status", "pending")
        );
        String result = renderTodoSummary(items);
        assertTrue(result.contains("✓2"));
        assertTrue(result.contains("◐1"));
        assertTrue(result.contains("☐1"));
    }

    @Test
    void emptySummary() {
        assertEquals("No tasks", renderTodoSummary(List.of()));
    }

    @Test
    void allCompleted() {
        List<Map<String, Object>> items = List.of(
                Map.of("status", "completed"),
                Map.of("status", "completed")
        );
        String result = renderTodoSummary(items);
        assertTrue(result.contains("✓2"));
        assertFalse(result.contains("◐"));
        assertFalse(result.contains("☐"));
    }

    @Test
    void parseJsonList() {
        String result = "[{\"content\": \"task1\", \"status\": \"pending\"}]";
        List<Map<String, Object>> items = parseTodoResult(result);
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("task1", items.get(0).get("content"));
    }

    @Test
    void parseJsonDictWithItems() {
        String result = "{\"items\": [{\"content\": \"a\", \"status\": \"completed\"}]}";
        List<Map<String, Object>> items = parseTodoResult(result);
        assertNotNull(items);
        assertEquals(1, items.size());
    }

    @Test
    void parseEmptyString() {
        assertNull(parseTodoResult(""));
    }

    @Test
    void parsePlainText() {
        assertNull(parseTodoResult("just some text"));
    }

    @Test
    void applyTodoModifyArgsFromJson() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        String json = "{\"action\":\"delete\",\"ids\":[\"a\"]}";
        Map<String, Object> args = mapper.readValue(json,
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
        assertEquals("delete", args.get("action"));
    }

    @Test
    void updateRewritesCachedItems() {
        List<Map<String, Object>> items = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("id", "a", "content", "Task A", "status", "in_progress")),
                new LinkedHashMap<>(Map.of("id", "b", "content", "Task B", "status", "pending"))
        ));

        for (Map<String, Object> item : items) {
            if ("a".equals(item.get("id"))) {
                item.put("status", "completed");
            }
        }

        assertEquals("completed", items.get(0).get("status"));
        assertEquals("pending", items.get(1).get("status"));
    }
}
