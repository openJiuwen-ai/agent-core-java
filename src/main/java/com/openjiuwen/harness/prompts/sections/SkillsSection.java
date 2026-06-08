/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections;

import com.openjiuwen.core.single_agent.prompts.PromptSection;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Skill prompt section helpers.
 *
 * <p>Mirrors Python's {@code skills} in
 * {@code openjiuwen/harness/prompts/sections/skills.py}.</p>
 */
public final class SkillsSection {

    private static final String DEFAULT_LANGUAGE = "cn";
    private static final int PRIORITY = 40;

    private static final String LIST_SKILL_CN = """
            你是一个技能选择器。
            你的任务是为给定的用户任务选择最相关的技能。
            仅返回一个 JSON 对象。
            输出格式：
            {
              "skills": ["skill_name_1", "skill_name_2"]
            }
            """;

    private static final String LIST_SKILL_EN = """
            You are a list_skill selector.
            Your task is to select the most relevant skills for the given user task.
            Return a JSON object only.
            Output format:
            {
              "skills": ["skill_name_1", "skill_name_2"]
            }
            """;

    private static final String ALL_MODE_HEADER_CN = """
            # 技能

            执行前先用 read_file 阅读相关 SKILL.md。

            可用技能：
            """;

    private static final String ALL_MODE_HEADER_EN = """
            # Skills

            Read the relevant SKILL.md using read_file before execution.

            Available skills:
            """;

    private static final String ALL_MODE_INSTRUCTION_CN = "\n选择最相关的技能，先阅读其 SKILL.md 再执行。";
    private static final String ALL_MODE_INSTRUCTION_EN =
            "\nSelect the most relevant skill by reading its SKILL.md first.";

    private static final String AUTO_LIST_CN = """
            # 技能

            需要时先调用 list_skill 查看可用技能，再用 read_file 读取相关 SKILL.md 后执行。
            需要时使用 code 执行 Python 或 JavaScript；执行 shell 命令时，根据运行环境信息选择合适的 shell
            （Windows 按 Git Bash/PowerShell 可用性选择，Linux/macOS 通常使用 bash/sh）。
            """;

    private static final String AUTO_LIST_EN = """
            # Skills

            When needed, call list_skill first to see available skills,
            then read the relevant SKILL.md with read_file before execution.
            Use code for Python or JavaScript snippets when needed.
            For shell commands, choose the shell according to the runtime environment information
            (Windows depends on Git Bash/PowerShell availability; Linux/macOS usually use bash/sh).
            """;

    private static final String NO_SKILL_CN = """
            # 技能

            当前任务没有选择任何技能。如有技能信息可用，请用 read_file 阅读相关 SKILL.md。
            """;

    private static final String NO_SKILL_EN = """
            # Skills

            No skill was selected for this task. When skill information is available, read the relevant SKILL.md using read_file.
            """;

    private SkillsSection() {
    }

    public static String buildSkillLine(int index, String skillName, String description, String skillMdPath) {
        StringBuilder builder = new StringBuilder()
                .append(index)
                .append(". ")
                .append(skillName)
                .append(": ")
                .append(description);
        if (skillMdPath != null && !skillMdPath.isEmpty()) {
            builder.append("\n   Path: ").append(skillMdPath);
        }
        return builder.toString();
    }

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

    public static String buildAllModeSkillPrompt(String skillLines, String language) {
        String resolvedLanguage = resolveLanguage(language);
        String text = skillLines == null ? "" : skillLines.trim();
        if (text.isEmpty()) {
            return getNoSkillPrompt(resolvedLanguage);
        }
        return getAllModeHeader(resolvedLanguage) + text + getAllModeInstruction(resolvedLanguage);
    }

    public static String buildAutoListModeSkillPrompt(String language) {
        return "en".equals(resolveLanguage(language)) ? AUTO_LIST_EN : AUTO_LIST_CN;
    }

    public static String getListSkillSystemPrompt(String language) {
        return "en".equals(resolveLanguage(language)) ? LIST_SKILL_EN : LIST_SKILL_CN;
    }

    public static PromptSection buildSkillsSection(String skillLines, String language, String mode) {
        String resolvedLanguage = resolveLanguage(language);
        String content;
        if ("all".equals(mode)) {
            content = buildAllModeSkillPrompt(skillLines, resolvedLanguage);
        } else if ("auto_list".equals(mode)) {
            content = buildAutoListModeSkillPrompt(resolvedLanguage);
        } else {
            return null;
        }
        return new PromptSection(
                SectionName.SKILLS,
                Map.of(resolvedLanguage, content),
                PRIORITY
        );
    }

    public static PromptSection build(String skillLines, String language, String mode) {
        return buildSkillsSection(skillLines, language, mode);
    }

    public static PromptSection build() {
        return buildSkillsSection("", DEFAULT_LANGUAGE, "auto_list");
    }

    private static String resolveLanguage(String language) {
        return "en".equals(language) ? "en" : DEFAULT_LANGUAGE;
    }

    private static String getAllModeHeader(String language) {
        return "en".equals(language) ? ALL_MODE_HEADER_EN : ALL_MODE_HEADER_CN;
    }

    private static String getAllModeInstruction(String language) {
        return "en".equals(language) ? ALL_MODE_INSTRUCTION_EN : ALL_MODE_INSTRUCTION_CN;
    }

    private static String getNoSkillPrompt(String language) {
        return "en".equals(language) ? NO_SKILL_EN : NO_SKILL_CN;
    }
}
