/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
 *
 * <p>Mirrors Python's {@code SkillManager} in
 * {@code openjiuwen.core.single_agent.skills.skill_manager}.</p>
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

        registerRoot(Path.of(skillPath), sessionId, overwrite);
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
        if (Files.isRegularFile(root)) {
            Skill skill = createSkillFromPath(root);
            if (!overwrite && registry.containsKey(skill.getName())) {
                throw new IllegalStateException("Skill already exists: " + skill.getName());
            }
            registry.put(skill.getName(), skill);
            return;
        }

        File dir = root.toFile();
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("Skill path does not exist or is not readable: " + root);
        }

        // Python first treats the provided directory as a skill directory.
        Path skillMd = findSkillMarkdown(dir);
        if (skillMd != null) {
            Skill s = createSkillFromPath(skillMd);
            if (s != null) {
                if (!overwrite && registry.containsKey(s.getName())) {
                    throw new IllegalStateException("Skill already exists: " + s.getName());
                }
                registry.put(s.getName(), s);
                return;
            }
        }

        // Scan subdirectories for Skill.md
        File[] subdirs = dir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                Path childSkillMd = findSkillMarkdown(subdir);
                if (childSkillMd != null) {
                    Skill s = createSkillFromPath(childSkillMd);
                    if (!overwrite && registry.containsKey(s.getName())) {
                        throw new IllegalStateException("Skill already exists: " + s.getName());
                    }
                    registry.put(s.getName(), s);
                }
            }
        }
    }

    private Path findSkillMarkdown(File dir) {
        File[] files = dir.listFiles(file -> file.isFile() && "skill.md".equalsIgnoreCase(file.getName()));
        if (files == null || files.length == 0) {
            return null;
        }
        return files[0].toPath();
    }

    /**
     * Create a Skill object from a Skill.md file path.
     */
    private Skill createSkillFromPath(Path path) {
        String descriptionText = loadDescription(path);
        Path skillDir = path.getParent();
        if (skillDir == null) {
            throw new IllegalArgumentException("Skill.md must have a parent directory: " + path);
        }
        return Skill.builder()
                .name(skillDir.getFileName().toString())
                .description(descriptionText)
                .directory(skillDir.toString())
                .build();
    }

    /**
     * Load description from YAML front matter in Skill.md file.
     */
    private String loadDescription(Path path) {
        try {
            String content = Files.readString(path);
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
            throw new IllegalArgumentException("description is required in skill front matter: " + path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read skill file: " + path, e);
        }
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
