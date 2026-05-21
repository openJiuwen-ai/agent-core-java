/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todo prompt section builder.
 * <p>
 * Mirrors Python's {@code todo} in
 * {@code openjiuwen.harness.prompts.sections.todo}.
 */
public final class TodoSection {

    private TodoSection() {
    }

    // TodoTool system prompt - for system message injection
    private static final String CN_SYSTEM =
            "\n使用 todo 工具（todo_create、todo_modify、todo_list）拆解和管理工作。这些工具用于跟踪进度、组织复杂任务，确保所有需求都被完成。\n\n"
            + "**何时创建任务列表 — 以下情况立即调用 todo_create：**\n"
            + "- 用户明确要求使用待办清单，或提供了多个待完成事项\n"
            + "- 任务需要 3 个或更多步骤\n"
            + "- 任务具有规划性质（多步骤实现、功能开发等）\n\n"
            + "**识别到规划需求后，在开始执行前立即调用 todo_create。**\n\n"
            + "**任务管理规则：**\n"
            + "- 实时更新状态：任务状态变化时立即调用 todo_modify\n"
            + "- 同一时间只能有一个任务处于 in_progress，完成后再开始下一个\n"
            + "- 批量更新：将多个状态变更合并为一次 todo_modify 调用\n"
            + "- 不再需要的任务用 todo_modify 标记为 cancelled\n"
            + "- 可通过调用 todo_list 了解当前任务规划进展\n\n"
            + "**将任务标记为已完成前：**\n"
            + "- 必须仔细验证工作已全部完成（如运行测试用例）\n"
            + "- 以下情况绝对不能标记为已完成：部分实现、测试失败、存在未解决的错误等\n"
            + "- 标记完成后，检查实现过程中是否发现新的后续任务，及时通过 todo_modify 追加\n";

    private static final String EN_SYSTEM =
            "\nUse the todo tools (todo_create, todo_modify, todo_list) to break down and manage your work. These tools help track progress, organize complex tasks, and ensure all requirements are completed.\n\n"
            + "**When to create a task list — call todo_create immediately when:**\n"
            + "- User explicitly requests a todo list or provides multiple items to complete\n"
            + "- Task requires 3 or more distinct steps\n"
            + "- Task has planning nature (multi-step implementation, feature development, etc.)\n\n"
            + "Identify the planning need and call todo_create BEFORE starting execution.\n\n"
            + "**Task management rules:**\n"
            + "- Update status in real-time: call todo_modify the moment a task status changes\n"
            + "- Only one task can be in_progress at a time; complete it before starting the next\n"
            + "- Batch updates: consolidate multiple status changes into a single todo_modify call\n"
            + "- Cancel tasks that are no longer needed\n"
            + "- Can understand the current task planning progress by calling todo_list.\n\n"
            + "**Before marking a task completed:**\n"
            + "- Verify the work is fully done (e.g., run tests to confirm)\n"
            + "- Never mark completed if: partially implemented, tests failing, unresolved errors\n"
            + "- After completing, check if new follow-up tasks were discovered and append them via todo_modify\n";

    // Progress reminder user prompt - for user message injection
    private static final String CN_PROGRESS_REMINDER =
            "\n以下是当前任务规划中所有任务的内容和状态：\n\n"
            + "{tasks}\n\n"
            + "正在执行的任务为：\n\n"
            + "{in_progress_task}\n\n"
            + "请查看上述任务进度，确保计划正在正确执行。如果有任务卡住或需要调整，请及时更新\n";

    private static final String EN_PROGRESS_REMINDER =
            "\nThe following is the content and status of all tasks in the current task plan:\n\n"
            + "{tasks}\n\n"
            + "The task currently being executed is:\n\n"
            + "{in_progress_task}\n\n"
            + "Please review the above task progress to ensure the plan is being executed correctly.\n"
            + "If any tasks are stuck or need adjustment, please update them promptly\n";

    // Model selection prompt - appended when configured
    private static final String CN_MODEL_SELECTION =
            "\n## 模型选择策略\n\n"
            + "当前可用模型：\n"
            + "{model_list}\n\n"
            + "每个模型 ID 由用户配置，对应一个具体的模型实例及其描述。描述说明了该模型的能力特点和适用场景，\n"
            + "是你选择模型的主要依据。目标是：在保证任务质量的前提下，为每个子任务选择最合适的模型，控制整体 token 成本。\n\n"
            + "### 选择原则\n"
            + "创建子任务时，阅读每个模型的描述，根据任务复杂度为 selected_model_id 字段选择合适的模型 ID：\n"
            + "- 描述中标注\"适合简单任务\"、\"成本低\"、\"速度快\"等的模型 → 用于翻译、摘要、格式转换等无需深度推理的任务\n"
            + "- 描述中标注\"适合复杂任务\"、\"推理能力强\"、\"效果好\"等的模型 → 用于代码生成、逻辑分析、策略规划等任务\n"
            + "- 不填则使用 Agent 默认模型\n\n"
            + "### 执行质量保障\n"
            + "若某个子任务执行结果质量不佳（输出不准确、逻辑错误、未达到预期目标），\n"
            + "应通过 todo_modify 工具将该任务的 selected_model_id 修改为描述更强的模型 ID，然后重新执行该任务。\n"
            + "不要在低质量结果上继续推进后续依赖任务。\n";

    private static final String EN_MODEL_SELECTION =
            "\n## Model Selection Strategy\n\n"
            + "Available models:\n"
            + "{model_list}\n\n"
            + "Each model ID is configured by the user and maps to a specific model instance with a description.\n"
            + "The description explains the model's capability and best-fit scenarios — use it as the primary basis\n"
            + "for selection. The goal is to pick the most appropriate model for each subtask to maintain quality\n"
            + "while keeping overall token cost in check.\n\n"
            + "### Selection Principles\n"
            + "When creating subtasks, read each model's description and assign an appropriate model ID to selected_model_id:\n"
            + "- Models described as \"suitable for simple tasks\", \"low cost\", \"fast\" → use for translation, summarization,\n"
            + "  format conversion, and other tasks that don't require deep reasoning\n"
            + "- Models described as \"suitable for complex tasks\", \"strong reasoning\", \"high quality\" → use for code\n"
            + "  generation, logical analysis, strategic planning, etc.\n"
            + "- Omit to use the agent's default model\n\n"
            + "### Quality Assurance\n"
            + "If a subtask produces poor results (inaccurate output, logical errors, unmet objectives),\n"
            + "use todo_modify to update that task's selected_model_id to a model with a stronger description,\n"
            + "then re-execute the task. Do not proceed with downstream tasks that depend on low-quality results.\n";

    private static final String CN_NO_MODEL_SELECTION =
            "\n## 模型选择说明\n\n"
            + "当前未配置可选模型列表。创建和更新任务时，**不要使用 selected_model_id 字段**。\n"
            + "所有任务将使用 Agent 默认模型执行。\n";

    private static final String EN_NO_MODEL_SELECTION =
            "\n## Model Selection Note\n\n"
            + "No model selection list is configured. When creating or updating tasks, **do NOT use the selected_model_id field**.\n"
            + "All tasks will be executed using the Agent's default model.\n";

    private static final Map<String, String> SYSTEM_PROMPT = new LinkedHashMap<>();
    private static final Map<String, String> PROGRESS_REMINDER = new LinkedHashMap<>();
    private static final Map<String, String> MODEL_SELECTION_PROMPT = new LinkedHashMap<>();
    private static final Map<String, String> NO_MODEL_SELECTION_PROMPT = new LinkedHashMap<>();

    static {
        SYSTEM_PROMPT.put("cn", CN_SYSTEM);
        SYSTEM_PROMPT.put("en", EN_SYSTEM);
        PROGRESS_REMINDER.put("cn", CN_PROGRESS_REMINDER);
        PROGRESS_REMINDER.put("en", EN_PROGRESS_REMINDER);
        MODEL_SELECTION_PROMPT.put("cn", CN_MODEL_SELECTION);
        MODEL_SELECTION_PROMPT.put("en", EN_MODEL_SELECTION);
        NO_MODEL_SELECTION_PROMPT.put("cn", CN_NO_MODEL_SELECTION);
        NO_MODEL_SELECTION_PROMPT.put("en", EN_NO_MODEL_SELECTION);
    }

    /**
     * Get the todo system prompt for the given language.
     *
     * @param language language code
     * @return todo system prompt text
     */
    public static String buildTodoSystemPrompt(String language) {
        return SYSTEM_PROMPT.getOrDefault(language, CN_SYSTEM);
    }

    /**
     * Build the progress reminder user prompt.
     *
     * @param language         language code
     * @param tasks            all tasks currently planned
     * @param inProgressTask   the task with status in_progress
     * @return progress reminder user prompt text
     */
    public static String buildProgressReminderUserPrompt(String language, String tasks, String inProgressTask) {
        String template = PROGRESS_REMINDER.getOrDefault(language, CN_PROGRESS_REMINDER);
        return template.replace("{tasks}", tasks).replace("{in_progress_task}", inProgressTask);
    }

    /**
     * Build the model selection guidance prompt.
     *
     * @param language   language code
     * @param modelList  formatted model list string
     * @return model selection prompt string
     */
    public static String buildModelSelectionPrompt(String language, String modelList) {
        if (modelList == null || modelList.isEmpty()) {
            return NO_MODEL_SELECTION_PROMPT.getOrDefault(language, CN_NO_MODEL_SELECTION);
        }
        String template = MODEL_SELECTION_PROMPT.getOrDefault(language, CN_MODEL_SELECTION);
        return template.replace("{model_list}", modelList);
    }

    /**
     * Build a todo prompt section.
     *
     * @param language   language code
     * @param modelList  optional formatted model list string
     * @return PromptSection instance
     */
    public static PromptSection build(String language, String modelList) {
        String content = buildTodoSystemPrompt(language);
        String modelContent = buildModelSelectionPrompt(language, modelList);
        content = content + modelContent;

        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection(SectionName.TODO, contentMap, 90);
    }

    /** Build with defaults (cn, no model selection). */
    public static PromptSection build() {
        return build("cn", null);
    }
}