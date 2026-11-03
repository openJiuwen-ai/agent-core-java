/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.tool.Tool;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Code-level tools owned by one Skill.
 */
public final class SkillToolBinding {
    private final String skillName;
    private final List<Tool> tools;

    private SkillToolBinding(String skillName, List<Tool> tools) {
        String normalizedSkillName = normalizeName(skillName);
        if (normalizedSkillName == null) {
            throw new IllegalArgumentException("skillName must not be blank");
        }
        this.skillName = normalizedSkillName;
        this.tools = List.copyOf(validateTools(normalizedSkillName, tools));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSkillName() {
        return skillName;
    }

    public List<Tool> getTools() {
        return tools;
    }

    private static List<Tool> validateTools(String skillName, List<Tool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<Tool> normalized = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (Tool tool : tools) {
            if (tool == null) {
                continue;
            }
            String toolName = toolName(tool);
            if (toolName == null) {
                throw new IllegalArgumentException("Skill tool name must not be blank for skill: " + skillName);
            }
            if (!names.add(toolName)) {
                throw duplicateToolName(skillName, toolName);
            }
            normalized.add(tool);
        }
        return normalized;
    }

    static IllegalArgumentException duplicateToolName(String skillName, String toolName) {
        return new IllegalArgumentException(
                "Duplicate skill tool name '" + toolName + "' for skill '" + skillName + "'"
        );
    }

    static String toolName(Tool tool) {
        if (tool == null || tool.getCard() == null) {
            return null;
        }
        return normalizeName(tool.getCard().getName());
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public static final class Builder {
        private String skillName;
        private List<Tool> tools;

        private Builder() {
        }

        public Builder skillName(String skillName) {
            this.skillName = skillName;
            return this;
        }

        public Builder tools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }

        public SkillToolBinding build() {
            return new SkillToolBinding(skillName, Objects.requireNonNullElseGet(tools, List::of));
        }
    }
}
