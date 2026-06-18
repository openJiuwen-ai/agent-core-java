/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.subagent;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.Map;

/**
 * Single-shot task spawning tool.
 *
 * <p>Mirrors Python's {@code TaskTool} and {@code create_task_tool} in
 * {@code openjiuwen/harness/tools/subagent/task_tool.py}.</p>
 */
public class TaskTool extends AbstractHarnessTool {

    private final TaskRunner taskRunner;

    public TaskTool(TaskRunner taskRunner) {
        super(toolCard("task", "TaskTool", "Run a subagent task."));
        this.taskRunner = taskRunner;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String prompt = requiredString(inputs, "prompt");
        String description = stringValue(inputs == null ? null : inputs.get("description"));
        if (taskRunner == null) {
            return ToolOutput.failure("task runner is not configured");
        }
        return ToolOutput.success(taskRunner.run(description, prompt, inputs == null ? Map.of() : inputs,
                kwargs == null ? Map.of() : kwargs));
    }

    /**
     * Java boundary for Python's subagent task execution in
     * {@code openjiuwen/harness/tools/subagent/task_tool.py}.
     */
    @FunctionalInterface
    public interface TaskRunner {
        Map<String, Object> run(String description, String prompt, Map<String, Object> inputs,
                                Map<String, Object> kwargs) throws Exception;
    }
}
