/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public class SkillTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SkillTool {
    private final Path skillsRoot;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillTool(String skillsRoot) {
        this.skillsRoot = Path.of(skillsRoot).toAbsolutePath().normalize();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput readSkill(String skillName, String relativeFilePath) {
        String fileName = (relativeFilePath == null || relativeFilePath.isBlank()) ? "SKILL.md" : relativeFilePath;
        Path skillDir = skillsRoot.resolve(skillName).normalize();
        Path target = skillDir.resolve(fileName).normalize();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("skill_directory", skillDir.toString());
            payload.put("skill_content", Files.readString(target, StandardCharsets.UTF_8));
            return ToolOutput.builder().success(true).data(payload).build();
        } catch (IOException ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }
}
