/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.common.logging.Loggers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages skill registration and retrieval.
 *
 * <p>Maintains a registry of skills and provides methods to register,
 * unregister, and query skills. Skills are loaded from YAML files containing
 * metadata such as name and description.</p>
 */
public class SkillManager {

    private final Map<String, Skill> registry = new LinkedHashMap<>();
    private String sysOperationId;
    private String description = "";

    public SkillManager(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * Register skill(s) from path.
     *
     * @param skillPath path to the skill directory or file
     * @param sessionId session ID for file operations
     * @param overwrite whether to overwrite existing skills
     */
    public void register(String skillPath, String sessionId, boolean overwrite) {
        if (skillPath == null || skillPath.isEmpty()) {
            return;
        }

        try {
            registerRoot(Path.of(skillPath), sessionId, overwrite);
        } catch (Exception e) {
            Loggers.AGENT.warning("Failed to register skill from path: " + skillPath + " - " + e.getMessage());
        }
    }

    public void register(String skillPath) {
        register(skillPath, null, false);
    }

    /**
     * Register skill from a {@link Path} (mirrors Python's Path type).
     *
     * @param skillPath path to the skill directory or file
     * @param sessionId session ID for file operations
     * @param overwrite whether to overwrite existing skills
     */
    public void register(Path skillPath, String sessionId, boolean overwrite) {
        if (skillPath == null) {
            return;
        }
        register(skillPath.toString(), sessionId, overwrite);
    }

    /**
     * Register skill from a {@link Path} with defaults.
     */
    public void register(Path skillPath) {
        register(skillPath, null, false);
    }

    /**
     * Register skills from a list of paths.
     *
     * @param skillPaths list of paths to skill directories or files
     * @param sessionId  session ID for file operations
     * @param overwrite  whether to overwrite existing skills
     */
    public void register(List<String> skillPaths, String sessionId, boolean overwrite) {
        if (skillPaths == null || skillPaths.isEmpty()) {
            return;
        }
        for (String path : skillPaths) {
            register(path, sessionId, overwrite);
        }
    }

    /**
     * Register skills from a list of {@link Path} objects.
     *
     * @param skillPaths list of Path objects pointing to skill directories or files
     * @param sessionId  session ID for file operations
     * @param overwrite  whether to overwrite existing skills
     */
    public void registerPaths(List<Path> skillPaths, String sessionId, boolean overwrite) {
        if (skillPaths == null || skillPaths.isEmpty()) {
            return;
        }
        for (Path p : skillPaths) {
            register(p, sessionId, overwrite);
        }
    }

    /**
     * Register skills from a list of {@link Path} objects with defaults.
     */
    public void registerPaths(List<Path> skillPaths) {
        registerPaths(skillPaths, null, false);
    }

    /**
     * Register skill directory by scanning for Skill.md files.
     */
    private void registerRoot(Path root, String sessionId, boolean overwrite) {
        // Try to create skill from the file directly
        Skill skill = createSkillFromPath(root);
        if (skill != null) {
            if (!overwrite && registry.containsKey(skill.getName())) {
                throw new IllegalStateException("Skill already exists: " + skill.getName());
            }
            registry.put(skill.getName(), skill);
            return;
        }

        // Scan subdirectories for Skill.md
        java.io.File dir = root.toFile();
        if (dir.isDirectory()) {
            java.io.File[] subdirs = dir.listFiles(java.io.File::isDirectory);
            if (subdirs != null) {
                for (java.io.File subdir : subdirs) {
                    java.io.File skillMd = new java.io.File(subdir, "Skill.md");
                    if (!skillMd.exists()) {
                        skillMd = new java.io.File(subdir, "SKILL.md");
                    }
                    if (skillMd.exists()) {
                        Skill s = createSkillFromPath(skillMd.toPath());
                        if (s != null) {
                            if (!overwrite && registry.containsKey(s.getName())) {
                                throw new IllegalStateException("Skill already exists: " + s.getName());
                            }
                            registry.put(s.getName(), s);
                        }
                    }
                }
            }
        }
    }

    /**
     * Create a Skill object from a Skill.md file path.
     */
    private Skill createSkillFromPath(Path path) {
        try {
            String descriptionText = loadDescription(path);
            if (descriptionText != null) {
                Path skillDir = path.getParent();
                return Skill.builder()
                        .name(skillDir.getFileName().toString())
                        .description(descriptionText)
                        .directory(skillDir.toString())
                        .build();
            }
        } catch (Exception e) {
            Loggers.AGENT.warning("Failed to create skill from path: " + path + " - " + e.getMessage());
        }
        return null;
    }

    /**
     * Load description from YAML front matter in Skill.md file.
     */
    private String loadDescription(Path path) {
        try {
            String content = java.nio.file.Files.readString(path);
            if (content.startsWith("---")) {
                String[] parts = content.split("---", 3);
                if (parts.length >= 2) {
                    String yamlBlock = parts[1];
                    // Simple YAML parsing for description field
                    for (String line : yamlBlock.split("\n")) {
                        line = line.trim();
                        if (line.startsWith("description:")) {
                            return line.substring("description:".length()).trim();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // File might not exist or not be readable
        }
        return null;
    }

    /**
     * Unregister a skill by name.
     */
    public void unregister(String name) {
        registry.remove(name);
    }

    /**
     * Get skill by name.
     */
    public Skill get(String name) {
        return registry.get(name);
    }

    /**
     * Get all registered skills.
     */
    public List<Skill> getAll() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Get all registered skill names.
     */
    public List<String> getNames() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * Check if a skill is registered.
     */
    public boolean has(String name) {
        return registry.containsKey(name);
    }

    /**
     * Clear all registered skills.
     */
    public void clear() {
        registry.clear();
    }

    /**
     * Get the number of registered skills.
     */
    public int count() {
        return registry.size();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
