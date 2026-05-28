/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Rail that manages skill prompt injection and tool registration.
 * <p>
 * Mirrors Python's {@code SkillUseRail} in
 * {@code openjiuwen.harness.rails.skills.skill_use_rail}.
 * </p>
 *
 * <p>Use cases:</p>
 * <ul>
 *   <li>Skill prompt injection into system prompts</li>
 *   <li>Tool registration (SkillTool, ListSkillTool, ReadFileTool, etc.)</li>
 *   <li>Skill cache management across invokes</li>
 * </ul>
 */
public class SkillUseRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(SkillUseRail.class);

    /** Priority for this rail */
    public static final int PRIORITY = 100;

    /** Skill modes */
    public static final String SKILL_MODE_ALL = "all";
    public static final String SKILL_MODE_AUTO_LIST = "auto_list";
    private static final Set<String> VALID_SKILL_MODES = Set.of(SKILL_MODE_ALL, SKILL_MODE_AUTO_LIST);

    private final List<String> skillsDir;
    private final String skillMode;
    private final boolean enableCache;
    private final boolean includeTools;
    private final Set<String> enabledSkills;
    private final Set<String> disabledSkills;

    // Cache loaded skills across invokes
    private final Map<String, Object> skillCache = new LinkedHashMap<>();
    private final Map<String, Long> skillUpdateAt = new LinkedHashMap<>();
    private final List<String> skillOrder = new ArrayList<>();

    // Track tools added by this rail
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();

    /**
     * Create SkillUseRail with default settings.
     */
    public SkillUseRail() {
        this(Collections.emptyList());
    }

    /**
     * Create SkillUseRail with skill directories.
     *
     * @param skillsDir Skill root directory or directories
     */
    public SkillUseRail(List<String> skillsDir) {
        this(skillsDir, SKILL_MODE_AUTO_LIST, true, true, null, null);
    }

    /**
     * Create SkillUseRail with full configuration.
     *
     * @param skillsDir       Skill root directory or directories
     * @param skillMode       Skill expose mode ("all" or "auto_list")
     * @param enableCache     Whether to cache loaded skills across invokes
     * @param includeTools    Whether to register read_file/code/bash tools
     * @param enabledSkills   Optional allow-list of skill names
     * @param disabledSkills  Optional deny-list of skill names
     */
    public SkillUseRail(
            List<String> skillsDir,
            String skillMode,
            boolean enableCache,
            boolean includeTools,
            Set<String> enabledSkills,
            Set<String> disabledSkills) {

        if (!VALID_SKILL_MODES.contains(skillMode)) {
            throw new IllegalArgumentException(
                    "Unsupported skill_mode: " + skillMode + ". Expected one of " + VALID_SKILL_MODES);
        }

        this.skillsDir = skillsDir != null ? skillsDir : Collections.emptyList();
        this.skillMode = skillMode;
        this.enableCache = enableCache;
        this.includeTools = includeTools;
        this.enabledSkills = enabledSkills != null ? new LinkedHashSet<>(enabledSkills) : new LinkedHashSet<>();
        this.disabledSkills = disabledSkills != null ? new LinkedHashSet<>(disabledSkills) : new LinkedHashSet<>();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[SkillUseRail] Initialized with skillMode={}, skillsDir={}", skillMode, skillsDir);

        // Register tools if enabled
        if (includeTools) {
            registerTools();
        }

        // Prepare skills
        prepareSkills();
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[SkillUseRail] Uninitialized");

        // Clear caches
        if (!enableCache) {
            skillCache.clear();
            skillUpdateAt.clear();
            skillOrder.clear();
        }
        ownedToolNames.clear();
        ownedToolIds.clear();
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    // ==================== Skill Management ====================

    /**
     * Refresh skills from skills directories.
     */
    private void prepareSkills() {
        if (!enableCache) {
            skillCache.clear();
            skillUpdateAt.clear();
            skillOrder.clear();
        }

        refreshSkillsIncrementally();
        LOG.info("[SkillUseRail] Prepared {} skills", skillOrder.size());
    }

    /**
     * Refresh skills by loading only new or updated SKILL.md files.
     */
    private void refreshSkillsIncrementally() {
        if (skillsDir.isEmpty()) {
            LOG.debug("[SkillUseRail] skillsDir is empty, skipping skill refresh");
            return;
        }

        Set<String> discoveredKeys = new LinkedHashSet<>();
        List<String> orderedKeys = new ArrayList<>();

        for (String dir : skillsDir) {
            Path root = Paths.get(dir);
            if (!root.toFile().exists()) {
                LOG.debug("[SkillUseRail] skills_dir does not exist, skipping: {}", root);
                continue;
            }

            // Scan for skill files
            scanSkillsDirectory(root, discoveredKeys, orderedKeys);
        }

        // Update order
        skillOrder.clear();
        skillOrder.addAll(orderedKeys);
    }

    /**
     * Scan directory for skill files.
     */
    private void scanSkillsDirectory(Path root, Set<String> discoveredKeys, List<String> orderedKeys) {
        // Placeholder: actual implementation would scan for SKILL.md files
        LOG.debug("[SkillUseRail] Scanning directory: {}", root);
    }

    // ==================== Tool Registration ====================

    /**
     * Register tools for skill operations.
     */
    private void registerTools() {
        LOG.info("[SkillUseRail] Registering tools: SkillTool, ListSkillTool, ReadFileTool, etc.");

        // Register SkillTool
        ownedToolNames.add("skill");

        // Register ListSkillTool
        ownedToolNames.add("list_skill");

        // Register ReadFileTool
        ownedToolNames.add("read_file");

        // Register CodeTool
        ownedToolNames.add("code");

        // Register BashTool
        ownedToolNames.add("bash");

        LOG.debug("[SkillUseRail] Registered {} tools", ownedToolNames.size());
    }

    // ==================== Getters ====================

    /**
     * Get all managed skills.
     */
    public List<Object> getSkillsMeta() {
        return new ArrayList<>(skillCache.values());
    }

    /**
     * Get skill mode.
     */
    public String getSkillMode() {
        return skillMode;
    }

    /**
     * Get owned tool names.
     */
    public Set<String> getOwnedToolNames() {
        return Collections.unmodifiableSet(ownedToolNames);
    }
}