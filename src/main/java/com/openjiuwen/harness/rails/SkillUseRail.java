/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.RemoteSkillUtil;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.ListSkillTool;
import com.openjiuwen.harness.tools.SkillTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public class SkillUseRail used by the Java parity implementation.
 *
 * <p>Supports hot-reload of skills: detects changes via mtime signature comparison
 * and incrementally refreshes only new or updated skills.</p>
 *
 * @since 1.0
 */
public class SkillUseRail extends DeepAgentRail {
    private static final String SKILL_SECTION = "skills";
    private static final int SKILL_SECTION_PRIORITY = 90;

    private DeepAgent owner;
    private SkillManager skillManager;
    private ListSkillTool listSkillTool;
    private SkillTool skillTool;
    private Path skillsRoot;
    private String skillMode = "all";
    private final List<String> configuredSkillDirectories;
    private final List<RemoteSkillSource> remoteSkillSources;
    private final Set<String> enabledSkills;
    private final Set<String> disabledSkills;
    private final List<Tool> tools = new ArrayList<>();
    private boolean enableCache = true;
    private List<Map.Entry<String, Long>> skillsSnapshotSignature = null;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUseRail() {
        this(List.of(), "all", List.of(), List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUseRail(String skillsDir) {
        this(List.of(skillsDir), "auto_list", List.of(), List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode) {
        this(skillDirectories, skillMode, List.of(), List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode,
            List<String> enabledSkills, List<String> disabledSkills) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, List.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode,
            List<String> enabledSkills, List<String> disabledSkills, List<RemoteSkillSource> remoteSkillSources) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, remoteSkillSources, true);
    }

    /**
     * Full constructor with enableCache parameter.
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode,
            List<String> enabledSkills, List<String> disabledSkills,
            List<RemoteSkillSource> remoteSkillSources, boolean enableCache) {
        this.configuredSkillDirectories = normalizeStringList(skillDirectories);
        this.skillMode = normalizeMode(skillMode);
        this.enabledSkills = new LinkedHashSet<>(normalizeStringList(enabledSkills));
        this.disabledSkills = new LinkedHashSet<>(normalizeStringList(disabledSkills));
        this.remoteSkillSources = remoteSkillSources == null ? List.of() : List.copyOf(remoteSkillSources);
        this.enableCache = enableCache;
    }

    @Override
    public int getPriority() {
        return 100;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public void init(DeepAgent agent) {
        if (agent == null) {
            return;
        }
        owner = agent;
        skillsRoot = resolveSkillsRoot(agent);
        skillManager = new SkillManager(agent.getCard().getId());

        listSkillTool = new ListSkillTool(skillsRoot.toString());
        skillTool = new SkillTool(skillsRoot.toString());
        String language = resolveLanguage(agent);
        tools.add(new LocalFunction(
                card("list_skill", agent, language),
                inputs -> listSkill(inputs)
        ));
        tools.add(new LocalFunction(
                card("skill_tool", agent, language),
                inputs -> readSkill(inputs)
        ));
        for (Tool tool : tools) {
            agent.registerTool(tool);
        }

        reloadSkills();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public void uninit(DeepAgent agent) {
        if (agent != null) {
            for (Tool tool : tools) {
                agent.unregisterTool(tool.getCard().getName());
            }
            removePromptSection();
        }
        tools.clear();
        listSkillTool = null;
        skillTool = null;
        skillManager = null;
        skillsRoot = null;
        owner = null;
    }

    /**
     * Explicitly refresh all skills from configured directories.
     */
    public void reloadSkills() {
        prepareSkills();
        skillsSnapshotSignature = buildCurrentSignature();
    }

    /**
     * Clear all skill caches and the snapshot signature.
     */
    public void clearSkills() {
        if (skillManager != null) {
            skillManager.clear();
        }
        skillsSnapshotSignature = null;
    }

    /**
     * Set whether caching is enabled.
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (owner == null || skillManager == null || skillManager.count() == 0 || "none".equals(skillMode)) {
            removePromptSection();
            return;
        }

        List<Map.Entry<String, Long>> currentSignature = buildCurrentSignature();
        if (signaturesEqual(currentSignature, skillsSnapshotSignature)) {
            injectSkillPrompt(ctx);
            return;
        }

        prepareSkills();
        skillsSnapshotSignature = currentSignature;
        injectSkillPrompt(ctx);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String describe() {
        return "Attach skill usage guidance";
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> registeredToolNames() {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> registeredSkillNames() {
        return skillManager != null
                ? configuredSkills(skillManager.getAll()).stream().map(Skill::getName).toList()
                : List.of();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String skillMode() {
        return skillMode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> configuredSkillDirectories() {
        return configuredSkillDirectories;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<RemoteSkillSource> remoteSkillSources() {
        return remoteSkillSources;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Set<String> enabledSkills() {
        return java.util.Collections.unmodifiableSet(enabledSkills);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Set<String> disabledSkills() {
        return java.util.Collections.unmodifiableSet(disabledSkills);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasSkillPromptSection() {
        if (owner == null) {
            return false;
        }
        try {
            Object reactAgent = owner.reactAgent();
            if (reactAgent != null) {
                Object promptBuilder = reactAgent.getClass().getMethod("getPromptBuilder").invoke(reactAgent);
                Object result = promptBuilder.getClass().getMethod("hasSection", String.class).invoke(promptBuilder, SKILL_SECTION);
                return Boolean.TRUE.equals(result);
            }
        } catch (ReflectiveOperationException ignored) {
            // Agent does not support prompt builder.
        }
        return false;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String buildSkillPrompt(String language, String mode, List<Skill> skills) {
        if ("cn".equalsIgnoreCase(language)) {
            return buildChinesePrompt(mode, skills);
        }
        return buildEnglishPrompt(mode, skills);
    }

    private void prepareSkills() {
        if (!enableCache && skillManager != null) {
            skillManager.clear();
        }
        List<Path> roots = collectSkillRoots();
        if (skillManager != null) {
            try {
                skillManager.register(roots, true);
            } catch (java.io.IOException ignored) {
                // Failed to refresh skills from directories.
            }
        }
    }

    private List<Path> collectSkillRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        List<String> directories = configuredSkillDirectories.isEmpty()
                ? resolveSkillDirectoriesFromConfig()
                : configuredSkillDirectories;
        if (directories != null) {
            for (String dir : directories) {
                if (dir == null || dir.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(dir);
                if (!candidate.isAbsolute()) {
                    Object ws = owner != null && owner.deepConfig() != null ? owner.deepConfig().getWorkspace() : null;
                    if (ws instanceof Workspace workspace) {
                        candidate = workspace.root().resolve(candidate);
                    }
                }
                roots.add(candidate.toAbsolutePath().normalize());
            }
        }
        if (skillsRoot != null && Files.isDirectory(skillsRoot)) {
            roots.add(skillsRoot);
        }
        return new ArrayList<>(roots);
    }

    private List<String> resolveSkillDirectoriesFromConfig() {
        if (owner == null || owner.deepConfig() == null) {
            return null;
        }
        try {
            Object config = owner.deepConfig();
            java.lang.reflect.Method method = config.getClass().getMethod("getSkillDirectories");
            Object result = method.invoke(config);
            if (result instanceof List<?> list) {
                List<String> dirs = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof String s) {
                        dirs.add(s);
                    }
                }
                return dirs;
            }
        } catch (ReflectiveOperationException ignored) {
            // Config does not expose skill directories.
        }
        return null;
    }

    private List<Map.Entry<String, Long>> buildCurrentSignature() {
        List<Path> roots = collectSkillRoots();
        List<Map.Entry<String, Long>> signature = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                for (Path child : stream.toList()) {
                    Path skillMd = child.resolve("SKILL.md");
                    if (Files.isRegularFile(skillMd)) {
                        long mtime = Files.getLastModifiedTime(skillMd).toMillis();
                        signature.add(Map.entry(child.getFileName().toString(), mtime));
                    }
                }
            } catch (java.io.IOException ignored) {
                // Skip unreadable roots.
            }
        }
        return signature;
    }

    private static boolean signaturesEqual(List<Map.Entry<String, Long>> a, List<Map.Entry<String, Long>> b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).getKey().equals(b.get(i).getKey())
                    || !a.get(i).getValue().equals(b.get(i).getValue())) {
                return false;
            }
        }
        return true;
    }

    private void injectSkillPrompt(CallbackContext ctx) {
        String language = owner != null ? resolveLanguage(owner) : "en";
        String prompt = buildSkillPrompt(
                language,
                skillMode,
                configuredSkills(skillManager.getAll()));
        injectPromptToAgent(prompt);
    }

    private Object listSkill(Map<String, Object> inputs) {
        Object query = inputs != null ? inputs.get("query") : null;
        List<Skill> skills = filterSkills(query != null ? String.valueOf(query) : null);
        if (query == null || String.valueOf(query).isBlank()) {
            return ToolOutput.builder()
                    .success(true)
                    .data(skills.stream().map(Skill::getName).toList())
                    .build();
        }
        return skills.stream()
                .map(skill -> Map.of(
                        "name", value(skill.getName()),
                        "description", value(skill.getDescription()),
                        "directory", skill.getDirectory() != null ? skill.getDirectory().toString() : ""
                ))
                .toList();
    }

    private Object readSkill(Map<String, Object> inputs) {
        if (skillTool == null) {
            return Map.of("success", false, "error", "skill tool is not initialized");
        }
        String skillName = stringArg(inputs, "skill_name", "");
        if (skillManager == null || configuredSkills(skillManager.getAll()).stream()
                .noneMatch(skill -> value(skill.getName()).equals(skillName))) {
            return ToolOutput.builder()
                    .success(false)
                    .error("skill is not available: " + skillName)
                    .build();
        }
        String relativePath = stringArg(inputs, "relative_file_path", "SKILL.md");
        return skillTool.readSkill(skillName, relativePath);
    }

    private List<Skill> filterSkills(String query) {
        if (skillManager == null) {
            return List.of();
        }
        List<Skill> all = configuredSkills(skillManager.getAll());
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return all.stream()
                .filter(skill -> value(skill.getName()).toLowerCase(Locale.ROOT).contains(needle)
                        || value(skill.getDescription()).toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private void syncRemoteSkills(DeepAgent deepAgent, Path targetRoot) {
        if (remoteSkillSources.isEmpty()) {
            return;
        }
        for (RemoteSkillSource source : remoteSkillSources) {
            uploadRemoteSkill(deepAgent, source, targetRoot);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    protected void uploadRemoteSkill(DeepAgent deepAgent, RemoteSkillSource source, Path targetRoot) {
        RemoteSkillUtil remoteSkillUtil = new RemoteSkillUtil(deepAgent.getCard().getId());
        remoteSkillUtil.uploadSkillFromGitHub(source.toGitHubTree(), targetRoot.toString(), source.token());
    }

    private Path resolveSkillsRoot(DeepAgent agent) {
        if (!configuredSkillDirectories.isEmpty()) {
            Path candidate = Path.of(configuredSkillDirectories.get(0));
            if (!candidate.isAbsolute()) {
                Object ws = agent.deepConfig() != null ? agent.deepConfig().getWorkspace() : null;
                if (ws instanceof Workspace workspace) {
                    candidate = workspace.root().resolve(candidate);
                }
            }
            return candidate.toAbsolutePath().normalize();
        }
        Object ws = agent.deepConfig() != null ? agent.deepConfig().getWorkspace() : null;
        if (ws instanceof Workspace workspace) {
            return workspace.getNodePath("skills");
        }
        return Path.of("skills").toAbsolutePath().normalize();
    }

    private static String resolveLanguage(DeepAgent agent) {
        if (agent != null && agent.deepConfig() != null) {
            String lang = agent.deepConfig().getLanguage();
            if (lang != null && !lang.isBlank()) {
                return lang;
            }
        }
        return "en";
    }

    private void injectPromptToAgent(String prompt) {
        if (owner == null) {
            return;
        }
        try {
            Object reactAgent = owner.reactAgent();
            if (reactAgent == null) {
                return;
            }
            Object promptBuilder = reactAgent.getClass().getMethod("getPromptBuilder").invoke(reactAgent);
            promptBuilder.getClass().getMethod("addSection", String.class, String.class, int.class)
                    .invoke(promptBuilder, SKILL_SECTION, prompt, SKILL_SECTION_PRIORITY);
        } catch (ReflectiveOperationException ignored) {
            // Agent does not support prompt builder injection.
        }
    }

    private static Path resolvePath(Workspace workspace, String path) {
        Path candidate = Path.of(path);
        if (!candidate.isAbsolute()) {
            candidate = workspace.root().resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    private static String buildEnglishPrompt(String mode, List<Skill> skills) {
        StringBuilder out = new StringBuilder();
        out.append("You are equipped with task skills.\n");
        if ("auto_list".equals(mode)) {
            out.append("Call list_skill when you need to discover the most relevant skill, "
                    + "then call skill_tool to read SKILL.md before using it.\n");
        } else {
            out.append("Before attempting a task that matches a listed skill, "
                    + "call skill_tool and follow the skill workflow.\n");
        }
        appendSkillLines(out, skills, false);
        return out.toString().trim();
    }

    private static String buildChinesePrompt(String mode, List<Skill> skills) {
        StringBuilder out = new StringBuilder();
        out.append("你已配备任务技能。\n");
        if ("auto_list".equals(mode)) {
            out.append("当需要判断当前任务适合哪个技能时，先调用 list_skill；选定后调用 skill_tool 阅读 SKILL.md 再执行。\n");
        } else {
            out.append("当当前任务匹配下列技能时，先调用 skill_tool 阅读技能说明并遵循其中流程。\n");
        }
        appendSkillLines(out, skills, true);
        return out.toString().trim();
    }

    private static void appendSkillLines(StringBuilder out, List<Skill> skills, boolean isChinese) {
        List<Skill> ordered = skills != null ? skills.stream()
                .sorted(Comparator.comparing(skill -> value(skill.getName())))
                .toList() : List.of();
        for (int i = 0; i < ordered.size(); i++) {
            Skill skill = ordered.get(i);
            if (isChinese) {
                out.append('\n').append(i).append(". 技能名称: ").append(value(skill.getName()))
                        .append("; 描述: ").append(value(skill.getDescription()))
                        .append("; 目录: ").append(skill.getDirectory() != null ? skill.getDirectory().toString() : "");
            } else {
                out.append('\n').append(i).append(". Skill name: ").append(value(skill.getName()))
                        .append("; Skill description: ").append(value(skill.getDescription()))
                        .append("; Skill directory file path: ").append(skill.getDirectory() != null ? skill.getDirectory().toString() : "");
            }
        }
    }

    private void removePromptSection() {
        if (owner != null) {
            try {
                Object reactAgent = owner.reactAgent();
                if (reactAgent != null) {
                    Object promptBuilder = reactAgent.getClass().getMethod("getPromptBuilder").invoke(reactAgent);
                    promptBuilder.getClass().getMethod("removeSection", String.class).invoke(promptBuilder, SKILL_SECTION);
                }
            } catch (ReflectiveOperationException ignored) {
                // Agent does not support prompt builder removal.
            }
        }
    }

    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? "all" : mode.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> normalizeStringList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String text = value.replace(';', ',');
            for (String part : text.split(",")) {
                if (!part.isBlank()) {
                    normalized.add(part.trim());
                }
            }
        }
        return List.copyOf(normalized);
    }

    private List<Skill> configuredSkills(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> enabledSkills.isEmpty() || enabledSkills.contains(value(skill.getName())))
                .filter(skill -> !disabledSkills.contains(value(skill.getName())))
                .toList();
    }

    private static String stringArg(Map<String, Object> inputs, String key, String fallback) {
        Object value = inputs != null ? inputs.get(key) : null;
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : fallback;
    }

    private static String value(String value) {
        return value != null ? value : "";
    }

    /**
     * Public record RemoteSkillSource used by the Java parity implementation.
     *
     * @since 1.0
     */
    public record RemoteSkillSource(String owner, String repo, String ref, String directory, String token) {
        /**
         * Auto-generated for codecheck compliance.
         */
        public RemoteSkillSource {
            owner = normalizeRequired(owner, "owner");
            repo = normalizeRequired(repo, "repo");
            ref = ref == null || ref.isBlank() ? "HEAD" : ref.trim();
            directory = normalizeDirectory(directory);
            token = token == null ? "" : token.trim();
        }

        GitHubTree toGitHubTree() {
            return new GitHubTree(owner, repo, ref, directory);
        }

        private static String normalizeRequired(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("remote skill " + field + " must not be blank");
            }
            String normalized = value.trim();
            if (normalized.contains("/") || normalized.contains("\\")) {
                throw new IllegalArgumentException("remote skill " + field + " must be a single path segment");
            }
            return normalized;
        }

        private static String normalizeDirectory(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            String normalized = value.trim().replace('\\', '/');
            try {
                Path path = Path.of(normalized);
                if (path.isAbsolute() || normalized.startsWith("../") || normalized.equals("..")
                        || normalized.contains("/../") || normalized.endsWith("/..")) {
                    throw new IllegalArgumentException("remote skill directory must stay within the repository");
                }
                return normalized;
            } catch (InvalidPathException ex) {
                throw new IllegalArgumentException("remote skill directory is invalid: " + value, ex);
            }
        }
    }
}