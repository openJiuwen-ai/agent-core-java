/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.prompts.sections.TodoSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Synchronizes todo planning prompts and task progress reminders.
 *
 * <p>Mirrors Python's {@code TaskPlanningRail} in
 * {@code openjiuwen/harness/rails/task_planning_rail.py}.</p>
 */
public class TaskPlanningRail extends DeepAgentRail {

    private final boolean enableProgressRepeat;
    private final int listToolCallInterval;
    private final Map<String, Object> modelSelection = new LinkedHashMap<>();
    private int toolCallCount;

    public TaskPlanningRail() {
        this(true, 5, Map.of());
    }

    public TaskPlanningRail(boolean enableProgressRepeat, int listToolCallInterval, Map<String, Object> modelSelection) {
        setPriority(45);
        this.enableProgressRepeat = enableProgressRepeat;
        this.listToolCallInterval = listToolCallInterval <= 0 ? 5 : listToolCallInterval;
        if (modelSelection != null) {
            this.modelSelection.putAll(modelSelection);
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        String language = String.valueOf(ctx.getValues().getOrDefault("language", "cn"));
        ctx.put("todo_section", TodoSection.buildTodoSection(language, modelSelection));
    }

    @Override
    public void afterToolCall(CallbackContext ctx) {
        toolCallCount += 1;
        if (enableProgressRepeat && toolCallCount % listToolCallInterval == 0) {
            ctx.put("should_repeat_progress", true);
        }
    }

    public int getToolCallCount() {
        return toolCallCount;
    }
}
