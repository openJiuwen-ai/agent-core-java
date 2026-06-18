/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.single_agent.skills;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill metadata loaded from a {@code SKILL.md} file.
 *
 * <p>Mirrors Python's {@code Skill} in
 * {@code openjiuwen/core/single_agent/skills/skill_manager.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Skill {
    private String name;
    private String description;
    private Path directory;

    public Skill() {
    }

    public Skill(String name, String description, Path directory) {
        this.name = name;
        this.description = description;
        this.directory = directory;
    }

    public Map<String, Object> asDict() {
        return asDict(true);
    }

    public Map<String, Object> asDict(boolean includeDirectory) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("description", description);
        if (includeDirectory) {
            result.put("directory", directory == null ? null : directory.toString());
        }
        return result;
    }

    public Map<String, Object> asdict(boolean includeDirectory) {
        return asDict(includeDirectory);
    }

    @Override
    public String toString() {
        return "Skill: " + name + "\nDescription: " + description + "\nDirectory: " + directory;
    }

    public String repr() {
        String text = description == null ? "" : description;
        String preview = text.length() > 30 ? text.substring(0, 30) + "..." : text + "...";
        return "[Skill: " + name + " / Description: " + preview + " / Directory: " + directory + "]";
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }
}
