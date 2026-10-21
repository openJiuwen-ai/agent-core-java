/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.singleagent.prompts.PromptSection;

import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Skill prompt section helpers.
 *
 * <p>Mirrors Python's {@code skills} in {@code openjiuwen/harness/prompts/sections/skills.py}.
 */
public final class SkillsSection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 40;

    private static final String LIST_SKILL_CN =
            "你是一个技能选择器。\n"
            + "你的任务是为给定的用户任务选择最相关的技能。\n"
            + "仅返回一个 JSON 对象。\n"
            + "输出格式：\n"
            + "{\n"
            + "  \"skills\": [\"skill_name_1\", \"skill_name_2\"]\n"
            + "}\n";

    private static final String LIST_SKILL_EN =
            "You are a list_skill selector.\n"
            + "Your task is to select the most relevant skills for the given user task.\n"
            + "Return a JSON object only.\n"
            + "Output format:\n"
            + "{\n"
            + "  \"skills\": [\"skill_name_1\", \"skill_name_2\"]\n"
            + "}\n";

    private static final String ALL_MODE_HEADER_CN =
            "# 技能\n"
            + "\n"
            + "执行前先用 skill_tool 阅读相关 SKILL.md。\n"
            + "\n"
            + "可用技能：\n";

    private static final String ALL_MODE_HEADER_EN =
            "# Skills\n"
            + "\n"
            + "Read the relevant SKILL.md using skill_tool before execution.\n"
            + "\n"
            + "Available skills:\n";

    private static final String ALL_MODE_INSTRUCTION_CN = "\n选择最相关的技能，先阅读其 SKILL.md 再执行。";
    private static final String ALL_MODE_INSTRUCTION_EN =
            "\nSelect the most relevant skill by reading its SKILL.md with skill_tool first.";

    private static final String AUTO_LIST_CN =
            "# 技能\n"
            + "\n"
            + "需要时先调用 list_skill 查看可用技能，再用 skill_tool 读取相关 SKILL.md 后执行。\n"
            + "需要时使用 code 执行 Python 或 JavaScript；执行 shell 命令时，根据运行环境信息选择合适的 shell\n"
            + "（Windows 按 Git Bash/PowerShell 可用性选择，Linux/macOS 通常使用 bash/sh）。\n";

    private static final String AUTO_LIST_EN =
            "# Skills\n"
            + "\n"
            + "When needed, call list_skill first to see available skills,\n"
            + "then read the relevant SKILL.md with skill_tool before execution.\n"
            + "Use code for Python or JavaScript snippets when needed.\n"
            + "For shell commands, choose the shell according to the runtime environment information\n"
            + "(Windows depends on Git Bash/PowerShell availability; Linux/macOS usually use bash/sh).\n";

    private static final String NO_SKILL_CN =
            "# 技能\n"
            + "\n"
            + "当前任务没有选择任何技能。如有技能信息可用，请用 skill_tool 阅读相关 SKILL.md。\n";

    private static final String NO_SKILL_EN =
            "# Skills\n"
            + "\n"
            + "No skill was selected for this task. When skill information is available, read the relevant "
            + "SKILL.md using skill_tool.\n";

    private SkillsSection() {}

    /**
     * Builds a one-line Skill summary with an optional SKILL.md path.
     *
     * @param index display order of the Skill
     * @param skillName name shown to the model
     * @param description short Skill summary
     * @param skillMdPath optional path to the main Skill document
     * @return formatted Skill line
     */
    public static String buildSkillLine(int index, String skillName, String description, String skillMdPath) {
        return buildSkillLine(index, skillName, description, skillMdPath, Map.of());
    }

    /**
     * Builds a one-line Skill summary with optional metadata and path.
     *
     * @param index display order of the Skill
     * @param skillName name shown to the model
     * @param description short Skill summary
     * @param skillMdPath optional path to the main Skill document
     * @param metadata optional id, source, and future metadata values
     * @return formatted Skill line
     */
    public static String buildSkillLine(
            int index,
            String skillName,
            String description,
            String skillMdPath,
            Map<String, Object> metadata) {
        StringBuilder builder =
                new StringBuilder()
                        .append(index)
                        .append(". ")
                        .append(skillName)
                        .append(": ")
                        .append(description);
        metadataValue(metadata, "id").ifPresent(id -> builder.append("\n   ID: ").append(id));
        metadataValue(metadata, "source").ifPresent(source -> builder.append("\n   Source: ").append(source));
        if (skillMdPath != null && !skillMdPath.isEmpty()) {
            builder.append("\n   Path: ").append(skillMdPath);
        }
        return builder.toString();
    }

    private static Optional<String> metadataValue(Map<String, Object> metadata, String key) {
        Object value = metadata == null ? null : metadata.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? Optional.empty()
                : Optional.of(String.valueOf(value));
    }

    /**
     * Joins non-empty Skill lines into the all-mode prompt body.
     *
     * @param lines ordered Skill lines to join
     * @return joined Skill body, or an empty string when none are usable
     */
    public static String buildSkillLines(Iterable<String> lines) {
        StringJoiner joiner = new StringJoiner("\n\n");
        if (lines == null) {
            return "";
        }
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                joiner.add(line);
            }
        }
        return joiner.toString();
    }

    /**
     * Builds the prompt used when all Skill metadata is exposed.
     *
     * @param skillLines rendered Skill metadata lines
     * @param language requested prompt language
     * @return all-mode Skill prompt
     */
    public static String buildAllModeSkillPrompt(String skillLines, String language) {
        String resolvedLanguage = resolveLanguage(language);
        String text = skillLines == null ? "" : skillLines.trim();
        if (text.isEmpty()) {
            return getNoSkillPrompt(resolvedLanguage);
        }
        return getAllModeHeader(resolvedLanguage)
                + text
                + getAllModeInstruction(resolvedLanguage);
    }

    /**
     * Builds the compact prompt that directs the model to list Skills first.
     *
     * @param language requested prompt language
     * @return auto-list Skill prompt
     */
    public static String buildAutoListModeSkillPrompt(String language) {
        return "en".equals(resolveLanguage(language))
                ? AUTO_LIST_EN
                : AUTO_LIST_CN;
    }

    /**
     * Builds the list_skill selector system prompt.
     *
     * @param language requested prompt language
     * @return system prompt for selecting Skills
     */
    public static String getListSkillSystemPrompt(String language) {
        return "en".equals(resolveLanguage(language))
                ? LIST_SKILL_EN
                : LIST_SKILL_CN;
    }

    /**
     * Builds a Skills prompt section for the requested mode.
     *
     * @param skillLines rendered Skill metadata lines for all mode
     * @param language requested prompt language
     * @param mode all or auto_list rendering mode
     * @return Skills prompt section, or empty for an unknown mode
     */
    public static Optional<PromptSection> buildSkillsSection(String skillLines, String language, String mode) {
        String resolvedLanguage = resolveLanguage(language);
        String content;
        if ("all".equals(mode)) {
            content = buildAllModeSkillPrompt(skillLines, resolvedLanguage);
        } else if ("auto_list".equals(mode)) {
            content = buildAutoListModeSkillPrompt(resolvedLanguage);
        } else {
            return Optional.empty();
        }
        return Optional.of(
                new PromptSection(SectionName.SKILLS, Map.of(resolvedLanguage, content), PRIORITY));
    }

    /**
     * Builds a Skills prompt section for the requested mode.
     *
     * @param skillLines rendered Skill metadata lines
     * @param language requested prompt language
     * @param mode all or auto_list rendering mode
     * @return Skills prompt section, or empty for an unknown mode
     */
    public static Optional<PromptSection> build(String skillLines, String language, String mode) {
        return buildSkillsSection(skillLines, language, mode);
    }

    /**
     * Builds the default auto-list Skills prompt section.
     *
     * @return Skills prompt section
     */
    public static Optional<PromptSection> build() {
        return buildSkillsSection("", DEFAULT_LANGUAGE, "auto_list");
    }

    private static String resolveLanguage(String language) {
        return "en".equals(language)
                ? "en"
                : DEFAULT_LANGUAGE;
    }

    private static String getAllModeHeader(String language) {
        return "en".equals(language)
                ? ALL_MODE_HEADER_EN
                : ALL_MODE_HEADER_CN;
    }

    private static String getAllModeInstruction(String language) {
        return "en".equals(language)
                ? ALL_MODE_INSTRUCTION_EN
                : ALL_MODE_INSTRUCTION_CN;
    }

    private static String getNoSkillPrompt(String language) {
        return "en".equals(language)
                ? NO_SKILL_EN
                : NO_SKILL_CN;
    }
}
