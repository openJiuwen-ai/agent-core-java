/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Todo checkbox rendering for CLI output.
 *
 * <p>Mirrors Python's {@code openjiuwen.harness.cli.ui.todo_render}.
 *
 * Renders todo items with visual checkboxes and progress summaries:
 * - ☑ task — completed (green)
 * - ◐ task — in_progress (yellow)
 * - ☐ task — pending (dim)
 * - ✓2 ◐1 ☐3 — progress summary
 */
public final class TodoRender {

    /** Status → (icon, Rich style) */
    private static final Map<String, String[]> STATUS_STYLE = new HashMap<>();

    static {
        STATUS_STYLE.put("completed", new String[]{"☑", "green"});
        STATUS_STYLE.put("in_progress", new String[]{"◐", "yellow"});
        STATUS_STYLE.put("pending", new String[]{"☐", "dim"});
        STATUS_STYLE.put("cancelled", new String[]{"☒", "dim strike"});
    }

    private TodoRender() {
    }

    /**
     * Render a single todo item with a checkbox.
     *
     * @param content Task description text.
     * @param status  One of "completed", "in_progress", "pending", "cancelled".
     * @return Rich-formatted string like "[green]☑ task[/green]".
     */
    public static String renderTodoItem(String content, String status) {
        String[] iconStyle = STATUS_STYLE.getOrDefault(status, new String[]{"☐", "dim"});
        String icon = iconStyle[0];
        String style = iconStyle[1];
        return "[" + style + "]" + icon + " " + content + "[/" + style + "]";
    }

    /**
     * Render a list of todo items as checkbox lines.
     *
     * Each line is prefixed with ⎿ for Claude Code style.
     *
     * @param items List of dicts with "content" and "status" keys.
     * @return List of Rich-formatted strings.
     */
    public static List<String> renderTodoList(List<Map<String, Object>> items) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> item : items) {
            String content = (String) item.getOrDefault("content", item.getOrDefault("activeForm", ""));
            String status = (String) item.getOrDefault("status", "pending");
            String checkbox = renderTodoItem(content, status);
            lines.add("  ⎿  " + checkbox);
        }
        return lines;
    }

    /**
     * Render a compact progress summary.
     *
     * @param items List of dicts with "status" keys.
     * @return String like "✓2 ◐1 ☐3".
     */
    public static String renderTodoSummary(List<Map<String, Object>> items) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("completed", 0);
        counts.put("in_progress", 0);
        counts.put("pending", 0);
        counts.put("cancelled", 0);

        for (Map<String, Object> item : items) {
            String status = (String) item.getOrDefault("status", "pending");
            if (counts.containsKey(status)) {
                counts.put(status, counts.get(status) + 1);
            }
        }

        List<String> parts = new ArrayList<>();
        if (counts.get("completed") > 0) {
            parts.add("✓" + counts.get("completed"));
        }
        if (counts.get("in_progress") > 0) {
            parts.add("◐" + counts.get("in_progress"));
        }
        if (counts.get("pending") > 0) {
            parts.add("☐" + counts.get("pending"));
        }
        if (counts.get("cancelled") > 0) {
            parts.add("☒" + counts.get("cancelled"));
        }

        return parts.isEmpty() ? "No tasks" : String.join(" ", parts);
    }

    /**
     * Count items by status.
     *
     * @param items List of todo items.
     * @param status Status to count.
     * @return Number of items with the given status.
     */
    public static long countByStatus(List<Map<String, Object>> items, String status) {
        return items.stream()
            .filter(i -> status.equals(i.get("status")))
            .count();
    }
}