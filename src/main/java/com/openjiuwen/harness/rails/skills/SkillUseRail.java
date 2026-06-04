/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.single_agent.prompts.PromptSection;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.prompts.sections.SkillsSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
    private final Map<String, Skill> skillCache = new LinkedHashMap<>();
    private final Map<String, Long> skillUpdateAt = new LinkedHashMap<>();
    private final List<String> skillOrder = new ArrayList<>();

    // Track tools added by this rail
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();

    private SystemPromptBuilder systemPromptBuilder;

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
        configureToolCards();
    }

    @Override
    public void init(Object agent) {
        LOG.info("[SkillUseRail] Initialized with skillMode={}, skillsDir={}", skillMode, skillsDir);
        this.systemPromptBuilder = resolveSystemPromptBuilder(agent);

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
    public void beforeInvoke(AgentCallbackContext ctx) {
        prepareSkills();
    }

    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
        SystemPromptBuilder builder = systemPromptBuilder != null
                ? systemPromptBuilder
                : resolveSystemPromptBuilder(ctx != null ? ctx.getAgent() : null);
        if (builder == null) {
            return;
        }
        String language = builder.getLanguage() != null ? builder.getLanguage() : PromptSection.DEFAULT_LANGUAGE;
        String skillLines = buildSkillLines();
        PromptSection section = SkillsSection.build(skillLines, language, skillMode);
        if (section != null) {
            builder.addSection(section);
        }
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
        LOG.debug("[SkillUseRail] Scanning directory: {}", root);
        if (!Files.isDirectory(root)) {
            return;
        }

        Path rootSkill = root.resolve("SKILL.md");
        if (Files.isRegularFile(rootSkill)) {
            discoverSkill(root, rootSkill, discoveredKeys, orderedKeys);
            return;
        }

        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(skillDir -> {
                        Path skillMd = skillDir.resolve("SKILL.md");
                        if (Files.isRegularFile(skillMd)) {
                            discoverSkill(skillDir, skillMd, discoveredKeys, orderedKeys);
                        }
                    });
        } catch (IOException e) {
            LOG.warn("[SkillUseRail] Failed to scan skills directory {}: {}", root, e.getMessage());
        }
    }

    private void discoverSkill(Path skillDir, Path skillMd, Set<String> discoveredKeys, List<String> orderedKeys) {
        String name = skillDir.getFileName().toString();
        if (!enabledSkills.isEmpty() && !enabledSkills.contains(name)) {
            return;
        }
        if (disabledSkills.contains(name) || discoveredKeys.contains(name)) {
            return;
        }

        discoveredKeys.add(name);
        orderedKeys.add(name);

        long updateAt = lastModified(skillMd);
        if (enableCache && skillCache.containsKey(name) && Objects.equals(skillUpdateAt.get(name), updateAt)) {
            return;
        }

        Skill skill = loadSkill(skillDir, updateAt);
        if (skill != null) {
            skillCache.put(name, skill);
            skillUpdateAt.put(name, updateAt);
        }
    }

    /**
     * Load one skill from its directory. Subclasses in tests override this to
     * verify incremental refresh behavior.
     */
    protected Skill loadSkill(Path skillDir, long updateAt) {
        Path skillMd = skillDir.resolve("SKILL.md");
        String description = loadDescription(skillMd);
        return Skill.builder()
                .name(skillDir.getFileName().toString())
                .description(description)
                .directory(skillDir.toString())
                .build();
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static String loadDescription(Path skillMd) {
        try {
            String content = Files.readString(skillMd, StandardCharsets.UTF_8);
            if (content.startsWith("---")) {
                String[] parts = content.split("---", 3);
                if (parts.length >= 2) {
                    for (String line : parts[1].split("\\R")) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("description:")) {
                            return trimmed.substring("description:".length()).trim();
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn("[SkillUseRail] Failed to load skill description from {}: {}", skillMd, e.getMessage());
        }
        return "";
    }

    // ==================== Tool Registration ====================

    private void configureToolCards() {
        if (!includeTools || !getTools().isEmpty()) {
            return;
        }
        getTools().add(simpleToolCard("skill", "Read the content of an enabled skill file."));
        getTools().add(simpleToolCard("list_skill", "List enabled skills for the current task."));
        getTools().add(simpleToolCard("read_file", "Read files from the workspace."));
        getTools().add(simpleToolCard("code", "Execute source code snippets."));
        getTools().add(simpleToolCard("bash", "Execute shell commands."));
    }

    private static ToolCard simpleToolCard(String name, String description) {
        return ToolCard.builder()
                .id("harness." + name)
                .name(name)
                .description(description)
                .build();
    }

    /**
     * Register tools for skill operations.
     */
    private void registerTools() {
        LOG.info("[SkillUseRail] Registering tools: SkillTool, ListSkillTool, ReadFileTool, etc.");

        for (ToolCard card : getTools()) {
            ownedToolNames.add(card.getName());
            ownedToolIds.add(card.getId());
        }

        LOG.debug("[SkillUseRail] Registered {} tools", ownedToolNames.size());
    }

    // ==================== Getters ====================

    /**
     * Get all managed skills.
     */
    public List<Object> getSkillsMeta() {
        return new ArrayList<>(getLoadedSkills());
    }

    /**
     * Get loaded skills in discovery order.
     */
    public List<Skill> getLoadedSkills() {
        List<Skill> result = new ArrayList<>();
        for (String name : skillOrder) {
            Skill skill = skillCache.get(name);
            if (skill != null) {
                result.add(skill);
            }
        }
        return result;
    }

    private String buildSkillLines() {
        List<Skill> skills = getLoadedSkills();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            String skillMdPath = Paths.get(skill.getDirectory()).resolve("SKILL.md").toString();
            lines.add(SkillsSection.buildSkillLine(i + 1, skill.getName(), skill.getDescription(), skillMdPath));
        }
        return String.join("\n", lines);
    }

    private static SystemPromptBuilder resolveSystemPromptBuilder(Object agent) {
        if (agent == null) {
            return null;
        }
        if (agent instanceof BaseAgent) {
            Object direct = invokeNoArg(agent, "getSystemPromptBuilder");
            if (direct instanceof SystemPromptBuilder builder) {
                return builder;
            }
        }
        Object direct = invokeNoArg(agent, "getSystemPromptBuilder");
        if (direct instanceof SystemPromptBuilder builder) {
            return builder;
        }
        Object field = readField(agent, "systemPromptBuilder");
        return field instanceof SystemPromptBuilder builder ? builder : null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }

    /**
     * Get configured skill root directories.
     */
    public List<String> getSkillsDir() {
        return Collections.unmodifiableList(skillsDir);
    }

    /**
     * Get skill mode.
     */
    public String getSkillMode() {
        return skillMode;
    }

    /**
     * Get enabled skill names.
     */
    public Set<String> getEnabledSkills() {
        return Collections.unmodifiableSet(enabledSkills);
    }

    /**
     * Get disabled skill names.
     */
    public Set<String> getDisabledSkills() {
        return Collections.unmodifiableSet(disabledSkills);
    }

    /**
     * Get owned tool names.
     */
    public Set<String> getOwnedToolNames() {
        return Collections.unmodifiableSet(ownedToolNames);
    }
}
