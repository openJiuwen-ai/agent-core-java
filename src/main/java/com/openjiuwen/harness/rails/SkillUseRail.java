/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.multitenant.TenantContext;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.multitenant.TenantWorkspaceResolver;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ModelCallInputs;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.RemoteSkillUtil;
import com.openjiuwen.core.singleagent.skills.Skill;
import com.openjiuwen.core.singleagent.skills.SkillManager;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.ListSkillTool;
import com.openjiuwen.harness.tools.OverlaySkillManager;
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
 * <p>
 * Supports hot-reload of skills: detects changes via mtime signature comparison
 * and incrementally refreshes only new or updated skills.
 * </p>
 * 
 * @since 0.1.7
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

    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private final List<Tool> tools = new ArrayList<>();
    private boolean enableCache = true;
    private List<Map.Entry<String, Long>> skillsSnapshotSignature = null;
    OverlaySkillManager overlaySkillManager;
    SkillManager tenantSkillManager;
    TenantWorkspaceResolver railWorkspaceResolver;

    /**
     * SkillUseRail.
     * 
     * @since 0.1.7
     */
    public SkillUseRail() {
        this(List.of(), "all", List.of(), List.of());
    }

    /**
     * SkillUseRail.
     * 
     * @param skillsDir skillsDir
     * @since 0.1.7
     */
    public SkillUseRail(String skillsDir) {
        this(List.of(skillsDir), "auto_list", List.of(), List.of());
    }

    /**
     * SkillUseRail.
     * 
     * @param skillDirectories skillDirectories
     * @param skillMode skillMode
     * @since 0.1.7
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode) {
        this(skillDirectories, skillMode, List.of(), List.of());
    }

    /**
     * SkillUseRail.
     * 
     * @param skillDirectories skillDirectories
     * @param skillMode skillMode
     * @param enabledSkills enabledSkills
     * @param disabledSkills disabledSkills
     * @since 0.1.7
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode, List<String> enabledSkills,
            List<String> disabledSkills) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, List.of());
    }

    /**
     * SkillUseRail.
     * 
     * @param skillDirectories skillDirectories
     * @param skillMode skillMode
     * @param enabledSkills enabledSkills
     * @param disabledSkills disabledSkills
     * @param remoteSkillSources remoteSkillSources
     * @since 0.1.7
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode, List<String> enabledSkills,
            List<String> disabledSkills, List<RemoteSkillSource> remoteSkillSources) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, remoteSkillSources, true);
    }

    /**
     * Full constructor with enableCache parameter.
     * 
     * @param skillDirectories skillDirectories
     * @param skillMode skillMode
     * @param enabledSkills enabledSkills
     * @param disabledSkills disabledSkills
     * @param remoteSkillSources remoteSkillSources
     * @param enableCache enableCache
     * @since 0.1.7
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode, List<String> enabledSkills,
            List<String> disabledSkills, List<RemoteSkillSource> remoteSkillSources, boolean enableCache) {
        this.configuredSkillDirectories = normalizeStringList(skillDirectories);
        this.skillMode = normalizeMode(skillMode);
        this.enabledSkills = new LinkedHashSet<>(normalizeStringList(enabledSkills));
        this.disabledSkills = new LinkedHashSet<>(normalizeStringList(disabledSkills));
        this.remoteSkillSources = remoteSkillSources == null ? List.of() : List.copyOf(remoteSkillSources);
        this.enableCache = enableCache;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 100;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        owner = deepAgent;
        skillMode =
            configuredSkillDirectories.isEmpty() ? normalizeMode(deepAgent.getConfig().getSkillMode()) : skillMode;
        skillsRoot = resolveSkillsRoot(deepAgent);
        skillManager = new SkillManager(deepAgent.getCard().getId());

        if (deepAgent.isTenantIsolationEnabled() && deepAgent.getWorkspaceResolver() != null) {
            railWorkspaceResolver = deepAgent.getWorkspaceResolver();
            tenantSkillManager = new SkillManager(deepAgent.getCard().getId() + ".tenant");
            Path overlayDir = railWorkspaceResolver.resolveTenantRoot(
                    TenantContext.builder().tenantId("_placeholder").build()).resolve(".overlay");
            overlaySkillManager = new OverlaySkillManager(tenantSkillManager, skillManager, overlayDir, railWorkspaceResolver);
            listSkillTool = new ListSkillTool(skillsRoot.toString(), railWorkspaceResolver, overlaySkillManager);
            skillTool = new SkillTool(skillsRoot.toString(), railWorkspaceResolver, overlaySkillManager);
        } else {
            listSkillTool = new ListSkillTool(skillsRoot.toString());
            skillTool = new SkillTool(skillsRoot.toString());
        }

        String language = deepAgent.getWorkspace().getLanguage();
        tools.add(new LocalFunction(card("list_skill", deepAgent, language), inputs -> listSkill(inputs)));
        tools.add(new LocalFunction(card("skill_tool", deepAgent, language), inputs -> readSkill(inputs)));
        for (Tool tool : tools) {
            deepAgent.registerHarnessTool(tool);
        }

        syncRemoteSkills(deepAgent, skillsRoot);
        reloadSkills();
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : tools) {
                deepAgent.unregisterHarnessTool(tool);
            }
            deepAgent.getAgent().getPromptBuilder().removeSection(SKILL_SECTION);
        }
        tools.clear();
        listSkillTool = null;
        skillTool = null;
        skillManager = null;
        overlaySkillManager = null;
        tenantSkillManager = null;
        railWorkspaceResolver = null;
        skillsRoot = null;
        owner = null;
    }

    /**
     * Explicitly refresh all skills from configured directories.
     * 
     * @since 0.1.7
     */
    public void reloadSkills() {
        prepareSkills();
        skillsSnapshotSignature = buildCurrentSignature();
    }

    /**
     * Clear all skill caches and the snapshot signature.
     * 
     * @since 0.1.7
     */
    public void clearSkills() {
        if (skillManager != null) {
            skillManager.clearAll();
        }
        skillsSnapshotSignature = null;
    }

    /**
     * Set whether caching is enabled.
     * 
     * @param enableCache enableCache
     * @since 0.1.7
     */
    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    /**
     * beforeModelCall.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    public void beforeModelCall(AgentCallbackContext ctx) {
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
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String describe() {
        return "Attach skill usage guidance";
    }

    /**
     * registeredToolNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> registeredToolNames() {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    /**
     * registeredSkillNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> registeredSkillNames() {
        return skillManager != null
                ? configuredSkills(skillManager.getAllInOrder()).stream().map(Skill::getName).toList()
                : List.of();
    }

    /**
     * skillMode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String skillMode() {
        return skillMode;
    }

    /**
     * configuredSkillDirectories.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> configuredSkillDirectories() {
        return configuredSkillDirectories;
    }

    /**
     * remoteSkillSources.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<RemoteSkillSource> remoteSkillSources() {
        return remoteSkillSources;
    }

    /**
     * enabledSkills.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> enabledSkills() {
        return java.util.Collections.unmodifiableSet(enabledSkills);
    }

    /**
     * disabledSkills.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Set<String> disabledSkills() {
        return java.util.Collections.unmodifiableSet(disabledSkills);
    }

    /**
     * hasSkillPromptSection.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasSkillPromptSection() {
        return owner != null && owner.getAgent().getPromptBuilder().hasSection(SKILL_SECTION);
    }

    /**
     * buildSkillPrompt.
     * 
     * @param language language
     * @param mode mode
     * @param skills skills
     * @return the result
     * @since 0.1.7
     */
    public String buildSkillPrompt(String language, String mode, List<Skill> skills) {
        if ("cn".equalsIgnoreCase(language)) {
            return buildChinesePrompt(mode, skills);
        }
        return buildEnglishPrompt(mode, skills);
    }

    /**
     * prepareSkills.
     * 
     * @since 0.1.7
     */
    private void prepareSkills() {
        if (!enableCache && skillManager != null) {
            skillManager.clearAll();
        }
        List<Path> roots = collectSkillRoots();
        if (skillManager != null) {
            skillManager.refreshIncrementally(roots);
        }
        // Refresh tenant skill manager when tenant context is available
        if (tenantSkillManager != null && overlaySkillManager != null) {
            TenantContext ctx = TenantContextHolder.getCurrentTenant();
            if (ctx != null && ctx.isTenantAware() && railWorkspaceResolver != null) {
                Path tenantSkillRoot = railWorkspaceResolver.resolveSkillRoot(ctx);
                if (tenantSkillRoot != null && Files.isDirectory(tenantSkillRoot)) {
                    tenantSkillManager.refreshIncrementally(List.of(tenantSkillRoot));
                }
            }
        }
    }

    /**
     * collectSkillRoots.
     * 
     * @return the result
     * @since 0.1.7
     */
    private List<Path> collectSkillRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        List<String> directories = configuredSkillDirectories.isEmpty()
                ? (owner != null ? owner.getConfig().getSkillDirectories() : null)
                : configuredSkillDirectories;
        if (directories != null) {
            for (String dir : directories) {
                if (dir == null || dir.isBlank()) {
                    continue;
                }
                roots.add(resolvePath(owner.getWorkspace(), dir));
            }
        }
        if (skillsRoot != null && Files.isDirectory(skillsRoot)) {
            roots.add(skillsRoot);
        }
        // Include tenant skill root when tenant context is available
        if (overlaySkillManager != null && railWorkspaceResolver != null) {
            TenantContext ctx = TenantContextHolder.getCurrentTenant();
            if (ctx != null && ctx.isTenantAware()) {
                Path tenantSkillRoot = railWorkspaceResolver.resolveSkillRoot(ctx);
                if (tenantSkillRoot != null && Files.isDirectory(tenantSkillRoot)) {
                    roots.add(tenantSkillRoot);
                }
            }
        }
        return new ArrayList<>(roots);
    }

    /**
     * buildCurrentSignature.
     * 
     * @return the result
     * @since 0.1.7
     */
    private List<Map.Entry<String, Long>> buildCurrentSignature() {
        List<Path> roots = collectSkillRoots();
        if (skillManager != null) {
            return skillManager.buildSnapshotSignature(roots);
        }
        return List.of();
    }

    /**
     * signaturesEqual.
     * 
     * @param a a
     * @param b b
     * @return the result
     * @since 0.1.7
     */
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
            if (!a.get(i).getKey().equals(b.get(i).getKey()) || !a.get(i).getValue().equals(b.get(i).getValue())) {
                return false;
            }
        }
        return true;
    }

    /**
     * injectSkillPrompt.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    private void injectSkillPrompt(AgentCallbackContext ctx) {
        List<Skill> visibleSkills;
        if (overlaySkillManager != null) {
            visibleSkills = overlaySkillManager.getAllVisibleSkills();
        } else {
            visibleSkills = skillManager.getAllInOrder();
        }
        String prompt = buildSkillPrompt(owner.getWorkspace().getLanguage(), skillMode,
                configuredSkills(visibleSkills));
        owner.getAgent().addPromptBuilderSection(SKILL_SECTION, prompt, SKILL_SECTION_PRIORITY);
        if (ctx != null && ctx.getInputs() instanceof ModelCallInputs inputs && inputs.getMessages() != null) {
            boolean isPromptAlreadyInjected = inputs.getMessages().stream().filter(SystemMessage.class::isInstance)
                    .map(message -> String.valueOf(((SystemMessage) message).getContent()))
                    .anyMatch(content -> content.contains("Skill name:") || content.contains("技能名称:"));
            if (!isPromptAlreadyInjected) {
                inputs.getMessages().add(0, new SystemMessage(prompt));
            }
        }
    }

    /**
     * listSkill.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private Object listSkill(Map<String, Object> inputs) {
        Object query = inputs != null ? inputs.get("query") : null;
        List<Skill> skills = filterSkills(query != null ? String.valueOf(query) : null);
        if (query == null || String.valueOf(query).isBlank()) {
            return ToolOutput.builder().success(true).data(skills.stream().map(Skill::getName).toList()).build();
        }
        return skills.stream().map(skill -> Map.of("name", value(skill.getName()), "description",
                value(skill.getDescription()), "directory", value(skill.getDirectory()))).toList();
    }

    /**
     * readSkill.
     * 
     * @param inputs inputs
     * @return the result
     * @since 0.1.7
     */
    private Object readSkill(Map<String, Object> inputs) {
        if (skillTool == null) {
            return Map.of("success", false, "error", "skill tool is not initialized");
        }
        String skillName = stringArg(inputs, "skill_name", "");
        List<Skill> visibleSkills;
        if (overlaySkillManager != null) {
            visibleSkills = overlaySkillManager.getAllVisibleSkills();
        } else {
            visibleSkills = configuredSkills(skillManager.getAllInOrder());
        }
        if (visibleSkills.stream().noneMatch(skill -> value(skill.getName()).equals(skillName))) {
            return ToolOutput.builder().success(false).error("skill is not available: " + skillName).build();
        }
        String relativePath = stringArg(inputs, "relative_file_path", "SKILL.md");
        return skillTool.readSkill(skillName, relativePath);
    }

    /**
     * filterSkills.
     * 
     * @param query query
     * @return the result
     * @since 0.1.7
     */
    List<Skill> filterSkills(String query) {
        if (overlaySkillManager != null) {
            List<Skill> all = overlaySkillManager.getAllVisibleSkills();
            List<Skill> filtered = configuredSkills(all);
            if (query == null || query.isBlank()) {
                return filtered;
            }
            String needle = query.toLowerCase(Locale.ROOT);
            return filtered.stream().filter(skill -> value(skill.getName()).toLowerCase(Locale.ROOT).contains(needle)
                    || value(skill.getDescription()).toLowerCase(Locale.ROOT).contains(needle)).toList();
        }
        if (skillManager == null) {
            return List.of();
        }
        List<Skill> all = configuredSkills(skillManager.getAllInOrder());
        if (query == null || query.isBlank()) {
            return all;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return all.stream().filter(skill -> value(skill.getName()).toLowerCase(Locale.ROOT).contains(needle)
                    || value(skill.getDescription()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    /**
     * syncRemoteSkills.
     * 
     * @param deepAgent deepAgent
     * @param targetRoot targetRoot
     * @since 0.1.7
     */
    private void syncRemoteSkills(DeepAgent deepAgent, Path targetRoot) {
        if (remoteSkillSources.isEmpty()) {
            return;
        }
        for (RemoteSkillSource source : remoteSkillSources) {
            uploadRemoteSkill(deepAgent, source, targetRoot);
        }
    }

    /**
     * uploadRemoteSkill.
     * 
     * @param deepAgent deepAgent
     * @param source source
     * @param targetRoot targetRoot
     * @since 0.1.7
     */
    protected void uploadRemoteSkill(DeepAgent deepAgent, RemoteSkillSource source, Path targetRoot) {
        RemoteSkillUtil remoteSkillUtil = new RemoteSkillUtil(deepAgent.getCard().getId());
        remoteSkillUtil.uploadSkillFromGitHub(source.toGitHubTree(), targetRoot.toString(), source.token());
    }

    /**
     * resolveSkillsRoot.
     * 
     * @param deepAgent deepAgent
     * @return the result
     * @since 0.1.7
     */
    private Path resolveSkillsRoot(DeepAgent deepAgent) {
        List<String> dirs = !configuredSkillDirectories.isEmpty()
                ? configuredSkillDirectories
                : deepAgent.getConfig().getSkillDirectories();
        if (dirs != null && !dirs.isEmpty()) {
            return resolvePath(deepAgent.getWorkspace(), dirs.get(0));
        }
        return deepAgent.getWorkspace().getNodePath("skills");
    }

    /**
     * resolvePath.
     * 
     * @param workspace workspace
     * @param path path
     * @return the result
     * @since 0.1.7
     */
    private static Path resolvePath(Workspace workspace, String path) {
        Path candidate = Path.of(path);
        if (!candidate.isAbsolute()) {
            candidate = workspace.root().resolve(candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    /**
     * card.
     * 
     * @param name name
     * @param agent agent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    /**
     * buildEnglishPrompt.
     * 
     * @param mode mode
     * @param skills skills
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * buildChinesePrompt.
     * 
     * @param mode mode
     * @param skills skills
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * appendSkillLines.
     * 
     * @param out out
     * @param skills skills
     * @param isChinese isChinese
     * @since 0.1.7
     */
    private static void appendSkillLines(StringBuilder out, List<Skill> skills, boolean isChinese) {
        List<Skill> ordered = skills != null
                ? skills.stream().sorted(Comparator.comparing(skill -> value(skill.getName()))).toList()
                : List.of();
        for (int i = 0; i < ordered.size(); i++) {
            Skill skill = ordered.get(i);
            if (isChinese) {
                out.append('\n').append(i).append(". 技能名称: ").append(value(skill.getName())).append("; 描述: ")
                        .append(value(skill.getDescription())).append("; 目录: ").append(value(skill.getDirectory()));
            } else {
                out.append('\n').append(i).append(". Skill name: ").append(value(skill.getName()))
                        .append("; Skill description: ").append(value(skill.getDescription()))
                        .append("; Skill directory file path: ").append(value(skill.getDirectory()));
            }
        }
    }

    /**
     * removePromptSection.
     * 
     * @since 0.1.7
     */
    private void removePromptSection() {
        if (owner != null) {
            owner.getAgent().getPromptBuilder().removeSection(SKILL_SECTION);
        }
    }

    /**
     * normalizeMode.
     * 
     * @param mode mode
     * @return the result
     * @since 0.1.7
     */
    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? "all" : mode.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * normalizeStringList.
     * 
     * @param values values
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * configuredSkills.
     * 
     * @param skills skills
     * @return the result
     * @since 0.1.7
     */
    private List<Skill> configuredSkills(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        return skills.stream()
                .filter(skill -> enabledSkills.isEmpty() || enabledSkills.contains(value(skill.getName())))
                .filter(skill -> !disabledSkills.contains(value(skill.getName()))).toList();
    }

    /**
     * stringArg.
     * 
     * @param inputs inputs
     * @param key key
     * @param fallback fallback
     * @return the result
     * @since 0.1.7
     */
    private static String stringArg(Map<String, Object> inputs, String key, String fallback) {
        Object value = inputs != null ? inputs.get(key) : null;
        return value != null && !String.valueOf(value).isBlank() ? String.valueOf(value) : fallback;
    }

    /**
     * value.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String value(String value) {
        return value != null ? value : "";
    }

    /**
     * Public record RemoteSkillSource used by the Java parity implementation.
     * 
     * @since 0.1.7
     */
    public record RemoteSkillSource(String owner, String repo, String ref, String directory, String token) {
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