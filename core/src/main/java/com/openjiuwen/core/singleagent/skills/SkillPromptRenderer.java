/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders safe, unified Skill metadata for prompt consumption.
 *
 * <p>Only metadata is rendered; SKILL.md body is loaded on demand via {@code skill_tool}.
 * Filesystem/workspace paths are output as-is (relative or absolute within workspace root);
 * classpath paths stay logical.
 *
 * @since 0.1.17
 */
public final class SkillPromptRenderer {

    private SkillPromptRenderer() {}

    /** Renders safe Skill metadata and omits the SKILL.md body. */
    public static String renderMetadata(Skill skill) {
        if (skill == null || skill.getName() == null || skill.getName().isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("name: ").append(skill.getName()).append('\n');
        if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
            sb.append("description: ").append(skill.getDescription()).append('\n');
        }
        if (skill.getId() != null && !skill.getId().isBlank()) {
            sb.append("id: ").append(skill.getId()).append('\n');
        }
        if (skill.getSource() != null) {
            sb.append("source: ").append(skill.getSource().value()).append('\n');
        }
        if (skill.getDirectory() != null && skill.getSource() != SkillSource.CLASSPATH) {
            sb.append("path: ").append(renderPath(skill)).append('\n');
        }
        sb.append("loading: on-demand\n");
        return sb.toString().stripTrailing();
    }

    /** Renders the legacy-compatible prompt line while reusing unified metadata. */
    public static String renderPromptLine(int index, Skill skill, boolean onDemandLoading) {
        String metadata = renderMetadata(skill);
        if (metadata.isEmpty()) {
            return "";
        }
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : metadata.split("\n")) {
            int separator = line.indexOf(':');
            if (separator > 0) {
                fields.put(
                        line.substring(0, separator).strip(),
                        line.substring(separator + 1).strip());
            }
        }
        StringBuilder line =
                new StringBuilder()
                        .append(index)
                        .append(".Skill name: ")
                        .append(fields.get("name"))
                        .append("; Skill description: ")
                        .append(fields.get("description"));
        if (fields.containsKey("id")) {
            line.append("; Skill id: ").append(fields.get("id"));
        }
        if (fields.containsKey("source")) {
            line.append("; Source: ").append(fields.get("source"));
        }
        if (fields.containsKey("path")) {
            line.append(onDemandLoading ? "; Skill path: " : "; Skill directory: ")
                    .append(fields.get("path"));
        }
        if (onDemandLoading) {
            line.append("; Loading: on-demand via skill_tool");
        }
        return line.toString();
    }

    private static String renderPath(Skill skill) {
        Path directory = skill.getDirectory();
        if (directory == null) {
            return "";
        }
        if (skill.getSource() == SkillSource.CLASSPATH) {
            return directory.toString();
        }
        return directory.normalize().toString();
    }
}
