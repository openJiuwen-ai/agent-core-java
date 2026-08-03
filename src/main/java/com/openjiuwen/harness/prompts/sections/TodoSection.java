/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo prompt section helpers.
 *
 * <p>Mirrors Python's {@code todo} helpers in
 * {@code openjiuwen/harness/prompts/sections/todo.py}.</p>
 */
public final class TodoSection {

    public static final String TODO_SYSTEM_PROMPT_CN = """
            ## 任务规划

            使用 todo 工具（todo_create、todo_modify、todo_list）拆解和管理工作。
            对复杂任务保持任务列表最新，完成一个任务后及时更新状态。
            """;

    public static final String TODO_SYSTEM_PROMPT_EN = """
            ## task planning

            Use the todo tools (todo_create, todo_modify, todo_list) to break down and manage work.
            Keep the task list current for complex tasks and update status as tasks finish.
            """;

    public static final String PROGRESS_REMINDER_USER_PROMPT_CN = """
            以下是当前任务规划中所有任务的内容和状态：

            {tasks}

            正在执行的任务为：

            {in_progress_task}

            请查看上述任务进度，确保计划正在正确执行。如果有任务卡住或需要调整，请及时更新。
            """;

    public static final String PROGRESS_REMINDER_USER_PROMPT_EN = """
            The following is the content and status of all tasks in the current task plan:

            {tasks}

            The task currently being executed is:

            {in_progress_task}

            Please review the above task progress to ensure the plan is being executed correctly.
            """;

    private TodoSection() {
    }

    public static String buildTodoSystemPrompt(String language) {
        return "en".equals(language) ? TODO_SYSTEM_PROMPT_EN : TODO_SYSTEM_PROMPT_CN;
    }

    public static String buildProgressReminderUserPrompt(String language, String tasks, String inProgressTask) {
        String template = "en".equals(language) ? PROGRESS_REMINDER_USER_PROMPT_EN : PROGRESS_REMINDER_USER_PROMPT_CN;
        return template
                .replace("{tasks}", tasks == null ? "" : tasks)
                .replace("{in_progress_task}", inProgressTask == null ? "" : inProgressTask);
    }

    public static String buildModelSelectionPrompt(String language, Map<String, ?> modelSelection) {
        if (modelSelection == null || modelSelection.isEmpty()) {
            return "en".equals(language)
                    ? "## Model Selection Note\n\nNo model selection list is configured. do NOT use the selected_model_id field."
                    : "## 模型选择说明\n\n当前未配置可选模型列表。不要使用 selected_model_id 字段。";
        }
        Map<String, ?> ordered = new LinkedHashMap<>(modelSelection);
        return ("en".equals(language) ? "## Model Selection Strategy\n\nAvailable models:\n"
                : "## 模型选择策略\n\n当前可用模型：\n") + ordered;
    }

    public static PromptSection buildTodoSection(String language, Map<String, ?> modelSelection) {
        String resolvedLanguage = "en".equals(language) ? "en" : "cn";
        return new PromptSection(
                SectionName.TODO,
                Map.of(resolvedLanguage, buildTodoSystemPrompt(resolvedLanguage)
                        + "\n"
                        + buildModelSelectionPrompt(resolvedLanguage, modelSelection)),
                90
        );
    }
}
