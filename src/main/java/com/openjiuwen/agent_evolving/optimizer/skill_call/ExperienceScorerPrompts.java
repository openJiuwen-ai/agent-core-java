/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

/**
 * Prompt templates for ExperienceScorer.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call.experience_scorer} prompt constants.
 */
public final class ExperienceScorerPrompts {

    private ExperienceScorerPrompts() {
        // Utility class
    }

    /**
     * Chinese experience evaluation prompt.
     */
    public static final String EXPERIENCE_EVAL_PROMPT_CN =
            "你是一个经验评估专家。根据对话片段，评估之前展示给 Agent 的经验是否被有效使用。\n\n" +
            "## 展示给 Agent 的经验\n{presented_experiences}\n\n" +
            "## 对话片段（展示经验之后的部分）\n{conversation_snippet}\n\n" +
            "## 评估任务\n对于每条展示的经验，判断：\n" +
            "1. 该经验是否被 Agent 理解和采纳（内容被用于指导后续行为）\n" +
            "2. 该经验是否产生了积极效果（帮助解决了问题或改进了输出）\n" +
            "3. 该经验是否产生了消极效果（导致错误或误导）\n\n" +
            "## 输出格式\n输出 JSON 数组，每条经验一个对象：\n" +
            "[{\"record_id\": \"经验ID\", \"used\": true/false, \"positive\": true/false, \"negative\": true/false, \"reason\": \"简短说明\"}]\n\n" +
            "只输出 JSON，不要其他内容。";

    /**
     * English experience evaluation prompt.
     */
    public static final String EXPERIENCE_EVAL_PROMPT_EN =
            "You are an experience evaluation expert. Based on the conversation snippet, evaluate whether the previously presented experiences were effectively used by the Agent.\n\n" +
            "## Experiences Presented to Agent\n{presented_experiences}\n\n" +
            "## Conversation Snippet (after presenting experiences)\n{conversation_snippet}\n\n" +
            "## Evaluation Task\nFor each presented experience, determine:\n" +
            "1. Whether the experience was understood and adopted by the Agent\n" +
            "2. Whether the experience produced positive effects\n" +
            "3. Whether the experience produced negative effects\n\n" +
            "## Output Format\nOutput a JSON array, one object per experience:\n" +
            "[{\"record_id\": \"experience ID\", \"used\": true/false, \"positive\": true/false, \"negative\": true/false, \"reason\": \"brief explanation\"}]\n\n" +
            "Output only JSON, no other content.";

    /**
     * Get eval prompt by language.
     */
    public static String getEvalPrompt(String language) {
        return "cn".equals(language) ? EXPERIENCE_EVAL_PROMPT_CN : EXPERIENCE_EVAL_PROMPT_EN;
    }

    /**
     * Chinese simplify prompt.
     */
    public static final String SIMPLIFY_PROMPT_CN =
            "你是一个经验库维护专家。根据当前经验的评分和使用情况，生成整理建议。\n\n" +
            "## Skill 名称\n{skill_name}\n\n" +
            "## Skill 摘要\n{skill_summary}\n\n" +
            "## 当前经验列表（按分数排序）\n{scored_experiences}\n\n" +
            "## 整理操作类型\n" +
            "- DELETE: 删除低质量或过时的经验\n" +
            "- MERGE: 合并多条相似经验为一条\n" +
            "- REFINE: 优化单条经验的内容\n" +
            "- KEEP: 保留不变\n\n" +
            "## 规则\n" +
            "1. 删除分数低于 0.4 且使用率为 0 的经验\n" +
            "2. 合并内容高度相似的经验（保留分数最高的作为 primary）\n" +
            "3. 优化内容模糊或格式不规范的经验\n" +
            "4. 保留高质量、高使用率的经验\n\n" +
            "## 输出格式\n输出 JSON 数组：\n" +
            "[{\"action\": \"DELETE|MERGE|REFINE|KEEP\", \"record_id\": \"目标经验ID\", \"reason\": \"操作原因\", \"merge_remove_ids\": [], \"new_content\": \"\"}]\n\n" +
            "只输出 JSON，不要其他内容。";

    /**
     * English simplify prompt.
     */
    public static final String SIMPLIFY_PROMPT_EN =
            "You are an experience library maintenance expert. Based on current experience scores and usage, generate organization suggestions.\n\n" +
            "## Skill Name\n{skill_name}\n\n" +
            "## Skill Summary\n{skill_summary}\n\n" +
            "## Current Experience List (sorted by score)\n{scored_experiences}\n\n" +
            "## Maintenance Actions\n" +
            "- DELETE: Remove low-quality or outdated experiences\n" +
            "- MERGE: Combine multiple similar experiences into one\n" +
            "- REFINE: Optimize content of a single experience\n" +
            "- KEEP: Keep unchanged\n\n" +
            "## Rules\n" +
            "1. Delete experiences with score below 0.4 and zero utilization\n" +
            "2. Merge highly similar experiences\n" +
            "3. Refine experiences with vague content\n" +
            "4. Keep high-quality, high-utilization experiences\n\n" +
            "## Output Format\nOutput a JSON array:\n" +
            "[{\"action\": \"DELETE|MERGE|REFINE|KEEP\", \"record_id\": \"target ID\", \"reason\": \"reason\", \"merge_remove_ids\": [], \"new_content\": \"\"}]\n\n" +
            "Output only JSON, no other content.";

    /**
     * Get simplify prompt by language.
     */
    public static String getSimplifyPrompt(String language) {
        return "cn".equals(language) ? SIMPLIFY_PROMPT_CN : SIMPLIFY_PROMPT_EN;
    }
}