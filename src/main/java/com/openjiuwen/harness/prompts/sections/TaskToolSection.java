/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Task-tool prompt section builder.
 * <p>
 * Mirrors Python's {@code task_tool} in
 * {@code openjiuwen.harness.prompts.sections.task_tool}.
 */
public final class TaskToolSection {

    private TaskToolSection() {
    }

    private static final String CN = "# 子任务工具\n"
            + "\n"
            + "- 使用 `task_tool` 创建并管理子任务\n"
            + "- 子任务可以并行执行以提高效率\n"
            + "- 合理划分任务粒度，避免过细或过粗\n";

    private static final String EN = "# Sub-task Tool\n"
            + "\n"
            + "- Use `task_tool` to create and manage sub-tasks\n"
            + "- Sub-tasks can be executed in parallel for efficiency\n"
            + "- Divide tasks at reasonable granularity\n";

    private static final Map<String, String> TASK_TOOL = new LinkedHashMap<>();

    static {
        TASK_TOOL.put("cn", CN);
        TASK_TOOL.put("en", EN);
    }

    public static PromptSection build() {
        return new PromptSection(SectionName.TASK_TOOL, TASK_TOOL, 65);
    }
}
