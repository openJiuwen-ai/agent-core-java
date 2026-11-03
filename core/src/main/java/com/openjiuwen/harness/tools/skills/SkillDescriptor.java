/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.skills;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable skill descriptor used by harness skill tools.
 *
 * <p>Mirrors Python's {@code Skill.asdict(...)} payload consumed in
 * {@code openjiuwen/harness/tools/skills/list_skill.py} and
 * {@code openjiuwen/harness/tools/skills/skill_tool.py}.</p>
 */
public record SkillDescriptor(String name, String description, String directory, Map<String, Object> metadata) {

    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("description", description);
        result.put("directory", directory);
        if (metadata != null) {
            result.putAll(metadata);
        }
        result.put("skill_md_path", Path.of(directory == null ? "" : directory, "SKILL.md").toString());
        return result;
    }
}
