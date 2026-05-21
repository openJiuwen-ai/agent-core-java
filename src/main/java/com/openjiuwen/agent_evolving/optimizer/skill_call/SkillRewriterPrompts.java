/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.optimizer.skill_call;

/**
 * Prompt templates for SkillRewriter.
 * <p>
 * Mirrors Python's {@code openjiuwen.agent_evolving.optimizer.skill_call.skill_rewriter} prompt constants.
 */
public final class SkillRewriterPrompts {

    private SkillRewriterPrompts() {
        // Utility class
    }

    /**
     * Chinese skill rewrite prompt.
     */
    public static final String SKILL_REWRITE_PROMPT_CN = 
            "你是一个 Skill 文档优化专家。根据当前 SKILL.md 内容和积累的经验记录，重写 SKILL.md 正文，将经验自然融入其中。\n\n" +
            "## 当前 SKILL.md\n\n" +
            "```markdown\n{skill_content}\n```\n\n" +
            "## 有效经验记录（按 section 分组）\n\n{experiences_by_section}\n\n" +
            "## 用户主动描述的优化方向（可选）\n{user_query}\n\n" +
            "## 重写任务\n\n" +
            "将上述经验记录自然融入 SKILL.md 正文，产出一份结构清晰、内容连贯的新版 SKILL.md。\n\n" +
            "### 融入原则\n\n" +
            "1. **知识融合而非简单追加**：经验中的知识点应合并进对应段落的自然描述，而非作为独立条目列出\n" +
            "2. **保留结构**：保留 YAML front-matter 和原有的 section 层级（## Instructions / ## Examples / ## Troubleshooting 等）\n" +
            "3. **可新增 section**：如果经验涉及新主题，可新增 section；但不要删除原有 section\n" +
            "4. **去重与更新**：如果经验与原有内容重复或矛盾，保留更新/更高分版本的信息\n" +
            "5. **语言一致**：输出语言与原 SKILL.md 保持一致\n" +
            "6. **移除索引块**：移除 evolution index block（<!-- evolution-index-start -->...<!-- evolution-index-end -->）\n\n" +
            "### 输出格式\n\n" +
            "只输出重写后的完整 SKILL.md 内容，用 markdown 代码块包裹：\n\n" +
            "```markdown\n---\nname: xxx\ndescription: xxx\n---\n\n# xxx\n\n...（完整内容）...\n```\n\n" +
            "不要输出任何解释文字。";

    /**
     * English skill rewrite prompt.
     */
    public static final String SKILL_REWRITE_PROMPT_EN = 
            "You are a Skill documentation optimization expert. Rewrite the SKILL.md content by integrating the accumulated evolution experiences naturally into the document.\n\n" +
            "## Current SKILL.md\n\n" +
            "```markdown\n{skill_content}\n```\n\n" +
            "## Valid Experience Records (grouped by section)\n\n{experiences_by_section}\n\n" +
            "## User-specified optimization direction (optional)\n{user_query}\n\n" +
            "## Rewrite Task\n\n" +
            "Integrate the above experience records naturally into the SKILL.md body to produce a well-structured, coherent new version.\n\n" +
            "### Integration Principles\n\n" +
            "1. **Knowledge fusion, not simple appending**: Knowledge from experiences should merge into natural descriptions within corresponding paragraphs, not listed as standalone entries\n" +
            "2. **Preserve structure**: Keep YAML front-matter and original section hierarchy (## Instructions / ## Examples / ## Troubleshooting, etc.)\n" +
            "3. **New sections allowed**: You may add new sections for new topics, but do not delete existing sections\n" +
            "4. **Deduplication and updates**: If experiences duplicate or contradict existing content, keep the newer/higher-scored version\n" +
            "5. **Language consistency**: Output language must match the original SKILL.md\n" +
            "6. **Remove index block**: Remove the evolution index block (<!-- evolution-index-start -->...<!-- evolution-index-end -->)\n\n" +
            "### Output Format\n\n" +
            "Output only the complete rewritten SKILL.md content, wrapped in a markdown code block:\n\n" +
            "```markdown\n---\nname: xxx\ndescription: xxx\n---\n\n# xxx\n\n... (full content) ...\n```\n\n" +
            "Do not output any explanatory text.";

    /**
     * Get prompt by language.
     *
     * @param language "cn" or "en"
     * @return Prompt template
     */
    public static String getPrompt(String language) {
        return "cn".equals(language) ? SKILL_REWRITE_PROMPT_CN : SKILL_REWRITE_PROMPT_EN;
    }

    /**
     * Chinese retry prompt.
     */
    public static final String RETRY_PROMPT_CN = 
            "你上次的输出格式不正确。请重新输出重写后的 SKILL.md 内容。\n\n" +
            "要求：\n" +
            "1. 用 ```markdown 和 ``` 包裹完整内容\n" +
            "2. 保留 YAML front-matter（--- 开头部分）\n" +
            "3. 不要输出任何解释文字\n\n" +
            "上次输出预览：\n{broken_preview}\n\n" +
            "请重新输出正确的格式。";

    /**
     * English retry prompt.
     */
    public static final String RETRY_PROMPT_EN = 
            "Your previous output format was incorrect. Please re-output the rewritten SKILL.md content.\n\n" +
            "Requirements:\n" +
            "1. Wrap the full content with ```markdown and ```\n" +
            "2. Preserve YAML front-matter (starting with ---)\n" +
            "3. Do not output any explanatory text\n\n" +
            "Previous output preview:\n{broken_preview}\n\n" +
            "Please output the correct format.";

    /**
     * Get retry prompt by language.
     *
     * @param language "cn" or "en"
     * @return Retry prompt template
     */
    public static String getRetryPrompt(String language) {
        return "cn".equals(language) ? RETRY_PROMPT_CN : RETRY_PROMPT_EN;
    }
}