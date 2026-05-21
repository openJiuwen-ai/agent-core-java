/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skills prompt section builder.
 * <p>
 * Mirrors Python's {@code skills} in
 * {@code openjiuwen.harness.prompts.sections.skills}.
 */
public final class SkillsSection {

    private SkillsSection() {
    }

    // List-skill system prompt (bilingual)
    private static final String LIST_SKILL_CN =
            "\n你是一个技能选择器。\n"
            + "你的任务是为给定的用户任务选择最相关的技能。\n"
            + "仅返回一个 JSON 对象。\n"
            + "输出格式：\n"
            + "{\n"
            + "  \"skills\": [\"skill_name_1\", \"skill_name_2\"]\n"
            + "}\n";

    private static final String LIST_SKILL_EN =
            "\nYou are a list_skill selector.\n"
            + "Your task is to select the most relevant skills for the given user task.\n"
            + "Return a JSON object only.\n"
            + "Output format:\n"
            + "{\n"
            + "  \"skills\": [\"skill_name_1\", \"skill_name_2\"]\n"
            + "}\n";

    // All-mode header / instruction (bilingual)
    private static final String ALL_MODE_HEADER_CN =
            "# 技能\n\n"
            + "执行前先用 read_file 阅读相关 SKILL.md。\n\n"
            + "可用技能：\n";

    private static final String ALL_MODE_HEADER_EN =
            "# Skills\n\n"
            + "Read the relevant SKILL.md using read_file before execution.\n\n"
            + "Available skills:\n";

    private static final String ALL_MODE_INSTRUCTION_CN =
            "\n选择最相关的技能，先阅读其 SKILL.md 再执行。";

    private static final String ALL_MODE_INSTRUCTION_EN =
            "\nSelect the most relevant skill by reading its SKILL.md first.";

    // Auto-list mode prompt (bilingual)
    private static final String AUTO_LIST_CN =
            "# 技能\n\n"
            + "需要时先调用 list_skill 查看可用技能，再用 read_file 读取相关 SKILL.md 后执行。\n"
            + "需要时使用 code 执行 Python 或 JavaScript，使用 bash 执行 shell 命令。\n";

    private static final String AUTO_LIST_EN =
            "# Skills\n\n"
            + "When needed, call list_skill first to see available skills, then read the relevant SKILL.md with read_file before execution.\n"
            + "Use code for Python or JavaScript snippets when needed, and use bash for shell commands.\n";

    // No-skill fallback prompt (bilingual)
    private static final String NO_SKILL_CN =
            "# 技能\n\n"
            + "当前任务没有选择任何技能。如有技能信息可用，请用 read_file 阅读相关 SKILL.md。\n";

    private static final String NO_SKILL_EN =
            "# Skills\n\n"
            + "No skill was selected for this task. When skill information is available, read the relevant SKILL.md using read_file.\n";

    private static final Map<String, String> LIST_SKILL_PROMPT = new LinkedHashMap<>();
    private static final Map<String, String> AUTO_LIST_PROMPT = new LinkedHashMap<>();
    private static final Map<String, String> NO_SKILL_PROMPT = new LinkedHashMap<>();
    private static final Map<String, String> ALL_MODE_HEADER = new LinkedHashMap<>();
    private static final Map<String, String> ALL_MODE_INSTRUCTION = new LinkedHashMap<>();

    static {
        LIST_SKILL_PROMPT.put("cn", LIST_SKILL_CN);
        LIST_SKILL_PROMPT.put("en", LIST_SKILL_EN);
        AUTO_LIST_PROMPT.put("cn", AUTO_LIST_CN);
        AUTO_LIST_PROMPT.put("en", AUTO_LIST_EN);
        NO_SKILL_PROMPT.put("cn", NO_SKILL_CN);
        NO_SKILL_PROMPT.put("en", NO_SKILL_EN);
        ALL_MODE_HEADER.put("cn", ALL_MODE_HEADER_CN);
        ALL_MODE_HEADER.put("en", ALL_MODE_HEADER_EN);
        ALL_MODE_INSTRUCTION.put("cn", ALL_MODE_INSTRUCTION_CN);
        ALL_MODE_INSTRUCTION.put("en", ALL_MODE_INSTRUCTION_EN);
    }

    /**
     * Build one rendered skill line.
     *
     * @param index      skill index
     * @param skillName  skill name
     * @param description skill description
     * @param skillMdPath skill md path (optional)
     * @return rendered skill line
     */
    public static String buildSkillLine(int index, String skillName, String description, String skillMdPath) {
        StringBuilder sb = new StringBuilder();
        sb.append(index).append(". ").append(skillName).append(": ").append(description);
        if (skillMdPath != null && !skillMdPath.isEmpty()) {
            sb.append("\n   Path: ").append(skillMdPath);
        }
        return sb.toString();
    }

    /**
     * Build prompt for all mode.
     *
     * @param skillLines pre-rendered skill lines
     * @param language   language code
     * @return prompt text
     */
    public static String buildAllModeSkillPrompt(String skillLines, String language) {
        String text = (skillLines != null) ? skillLines.trim() : "";
        if (text.isEmpty()) {
            return NO_SKILL_PROMPT.getOrDefault(language, NO_SKILL_CN);
        }
        String header = ALL_MODE_HEADER.getOrDefault(language, ALL_MODE_HEADER_CN);
        String instruction = ALL_MODE_INSTRUCTION.getOrDefault(language, ALL_MODE_INSTRUCTION_CN);
        return header + text + instruction;
    }

    /**
     * Build prompt for auto_list mode.
     *
     * @param language language code
     * @return prompt text
     */
    public static String buildAutoListModeSkillPrompt(String language) {
        return AUTO_LIST_PROMPT.getOrDefault(language, AUTO_LIST_CN);
    }

    /**
     * Get the list_skill system prompt for the given language.
     *
     * @param language language code
     * @return list_skill system prompt
     */
    public static String getListSkillSystemPrompt(String language) {
        return LIST_SKILL_PROMPT.getOrDefault(language, LIST_SKILL_CN);
    }

    /**
     * Build a skills prompt section.
     *
     * @param skillLines pre-rendered skill lines (only used in 'all' mode)
     * @param language   language code
     * @param mode       'all' or 'auto_list'
     * @return PromptSection instance
     */
    public static PromptSection build(String skillLines, String language, String mode) {
        String content;
        if ("all".equals(mode)) {
            content = buildAllModeSkillPrompt(skillLines, language);
        } else if ("auto_list".equals(mode)) {
            content = buildAutoListModeSkillPrompt(language);
        } else {
            return null;
        }

        Map<String, String> contentMap = new LinkedHashMap<>();
        contentMap.put(language, content);
        return new PromptSection(SectionName.SKILLS, contentMap, 40);
    }

    /** Build with defaults (auto_list, cn). */
    public static PromptSection build() {
        return build(null, "cn", "auto_list");
    }
}