// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages skill registration and retrieval.
 *
 * <p>This class maintains a registry of skills and provides methods to register,
 * unregister, and query skills. Skills are loaded from files containing
 * metadata such as name and description.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/skills/skill_manager.py::SkillManager}
 *
 * @since 0.1.4
 */
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    /**
     * The skill file name to look for.
     */
    public static final String SKILL_FILE_NAME = "SKILL.md";

    /**
     * The registry of skills, keyed by skill name.
     */
    private final Map<String, Skill> registry = new ConcurrentHashMap<>();

    /**
     * The system operation ID used for file operations.
     */
    private String sysOperationId;

    /**
     * The description of the current skill being loaded.
     */
    private String description = "";

    /**
     * Constructs a SkillManager with the specified system operation ID.
     *
     * @param sysOperationId the system operation ID for file operations
     */
    public SkillManager(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Sets the system operation ID.
     *
     * @param sysOperationId the new system operation ID
     */
    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    /**
     * Gets the system operation ID.
     *
     * @return the system operation ID
     */
    public String getSysOperationId() {
        return sysOperationId;
    }

    /**
     * Registers skills from the specified path(s).
     *
     * <p>This method scans the given path(s) for skill directories containing
     * a SKILL.md file and registers them in the registry.
     *
     * @param skillPath the path to the skill directory, or a list of paths
     * @param overwrite if true, overwrite existing skills; otherwise throw exception
     * @throws BaseError if skill already exists and overwrite is false
     */
    public void register(Path skillPath, boolean overwrite) {
        if (skillPath == null) {
            return;
        }
        registerRoot(skillPath, overwrite);
    }

    /**
     * Registers skills from multiple paths.
     *
     * @param skillPaths the list of paths to skill directories
     * @param overwrite  if true, overwrite existing skills
     */
    public void register(List<Path> skillPaths, boolean overwrite) {
        if (skillPaths == null) {
            return;
        }
        for (Path path : skillPaths) {
            registerRoot(path, overwrite);
        }
    }

    /**
     * Registers skills from a single root path.
     *
     * @param root      the root path to scan
     * @param overwrite if true, overwrite existing skills
     */
    private void registerRoot(Path root, boolean overwrite) {
        if (root == null || !Files.exists(root)) {
            return;
        }

        try {
            if (Files.isDirectory(root)) {
                // Scan subdirectories for skills
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                            .forEach(dir -> registerSkillDirectory(dir, overwrite));
                }
            } else {
                // Try to register as a single skill file
                registerSkillFile(root, overwrite);
            }
        } catch (Exception e) {
            log.warn("Failed to register skills from path: {}", root, e);
        }
    }

    /**
     * Registers a skill from a directory.
     *
     * @param dir       the skill directory
     * @param overwrite if true, overwrite existing skills
     */
    private void registerSkillDirectory(Path dir, boolean overwrite) {
        Path skillFile = dir.resolve(SKILL_FILE_NAME);
        if (Files.exists(skillFile)) {
            registerSkillFile(skillFile, overwrite);
        }
    }

    /**
     * Registers a skill from a SKILL.md file.
     *
     * @param skillFile the path to the SKILL.md file
     * @param overwrite if true, overwrite existing skills
     */
    private void registerSkillFile(Path skillFile, boolean overwrite) {
        try {
            Skill skill = createSkillFromPath(skillFile);
            if (skill != null) {
                String skillName = skill.getName();
                if (!overwrite && registry.containsKey(skillName)) {
                    throw BaseError.builder(StatusCode.SKILL_ALREADY_EXISTS)
                            .param("skill_name", skillName)
                            .build();
                }
                registry.put(skillName, skill);
                log.debug("Registered skill: {}", skillName);
            }
        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to register skill from file: {}", skillFile, e);
        }
    }

    /**
     * Creates a Skill object from a SKILL.md file path.
     *
     * @param skillFile the path to the SKILL.md file
     * @return the created Skill, or null if description cannot be loaded
     */
    private Skill createSkillFromPath(Path skillFile) {
        String desc = loadDescription(skillFile);
        if (desc != null && !desc.isEmpty()) {
            Path skillDir = skillFile.getParent();
            return Skill.builder()
                    .name(skillDir.getFileName().toString())
                    .description(desc)
                    .directory(skillDir)
                    .build();
        }
        return null;
    }

    /**
     * Loads the description from a SKILL.md file's YAML front matter.
     *
     * <p>The file should have YAML front matter enclosed in --- markers:
     * <pre>
     * ---
     * description: "Skill description here"
     * ---
     * Markdown content here
     * </pre>
     *
     * @param path the path to the SKILL.md file
     * @return the description string, or null if not found
     * @throws BaseError if the file cannot be read or doesn't contain a description
     */
    private String loadDescription(Path path) {
        this.description = "";

        try {
            String content = Files.readString(path);

            // Parse YAML front matter
            if (content.startsWith("---")) {
                String[] parts = content.split("---", 3);
                if (parts.length >= 2) {
                    String yamlBlock = parts[1].trim();
                    Yaml yaml = new Yaml();
                    Map<String, Object> data = yaml.load(yamlBlock);

                    if (data != null && data.containsKey("description")) {
                        this.description = String.valueOf(data.get("description"));
                        return this.description;
                    }
                }
            }

            throw BaseError.builder(StatusCode.SKILL_DESCRIPTION_NOT_FOUND)
                    .msg("SKILL.md file does not contain a description field")
                    .build();

        } catch (BaseError e) {
            throw e;
        } catch (Exception e) {
            throw BaseError.builder(StatusCode.SKILL_FILE_READ_ERROR)
                    .param("file_path", path.toString())
                    .param("error_msg", e.getMessage())
                    .cause(e)
                    .build();
        }
    }

    /**
     * Unregisters a skill by name.
     *
     * @param name the name of the skill to unregister
     */
    public void unregister(String name) {
        if (name != null) {
            registry.remove(name);
        }
    }

    /**
     * Gets a skill by name.
     *
     * @param name the name of the skill
     * @return the skill, or null if not found
     */
    public Skill get(String name) {
        return name != null ? registry.get(name) : null;
    }

    /**
     * Gets all registered skills.
     *
     * @return a list of all registered skills
     */
    public List<Skill> getAll() {
        return new ArrayList<>(registry.values());
    }

    /**
     * Gets all registered skill names.
     *
     * @return a list of all registered skill names
     */
    public List<String> getNames() {
        return new ArrayList<>(registry.keySet());
    }

    /**
     * Checks if a skill is registered.
     *
     * @param name the name of the skill to check
     * @return true if the skill is registered, false otherwise
     */
    public boolean has(String name) {
        return name != null && registry.containsKey(name);
    }

    /**
     * Clears all registered skills.
     */
    public void clear() {
        registry.clear();
    }

    /**
     * Gets the number of registered skills.
     *
     * @return the number of registered skills
     */
    public int count() {
        return registry.size();
    }

    /**
     * Checks if any skills are registered.
     *
     * @return true if at least one skill is registered
     */
    public boolean hasSkills() {
        return !registry.isEmpty();
    }
}
