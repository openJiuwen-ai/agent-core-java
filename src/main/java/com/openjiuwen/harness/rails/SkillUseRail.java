/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.common.logging.Loggers;
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
import com.openjiuwen.harness.tools.BashTool;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.FilesystemTool;
import com.openjiuwen.harness.tools.OverlaySkillManager;
import com.openjiuwen.harness.tools.SkillTool;
import com.openjiuwen.harness.tools.ToolOutput;
import com.openjiuwen.harness.workspace.Workspace;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public static final String SKILL_MODE_ALL = "all";
    public static final String SKILL_MODE_AUTO_LIST = "auto_list";
    private static final String SKILL_SECTION = "skills";
    private static final int SKILL_SECTION_PRIORITY = 90;
    private static final String SKILL_RAIL_ALL_MODE_HEADER_CN =
        "# 技能\n\n执行前先用 read_file 阅读相关 SKILL.md。\n\n可用技能：\n";
    private static final String SKILL_RAIL_ALL_MODE_HEADER_EN =
        "# Skills\n\nRead the relevant SKILL.md using read_file before execution.\n\nAvailable skills:\n";
    private static final String SKILL_RAIL_ALL_MODE_INSTRUCTION_CN = "\n选择最相关的技能，先阅读其 SKILL.md 再执行。";
    private static final String SKILL_RAIL_ALL_MODE_INSTRUCTION_EN =
        "\nSelect the most relevant skill by reading its SKILL.md first.";
    private static final String SKILL_RAIL_AUTO_LIST_MODE_PROMPT_CN = """
            # 技能

            需要时先调用 list_skill 查看可用技能，再用 read_file 读取相关 SKILL.md 后执行。
            需要时使用 code 执行 Python 或 JavaScript；执行 shell 命令时，根据运行环境信息选择合适的 shell
            （Windows 按 Git Bash/PowerShell 可用性选择，Linux/macOS 通常使用 bash/sh）。
            """;
    private static final String SKILL_RAIL_AUTO_LIST_MODE_PROMPT_EN = """
            # Skills

            When needed, call list_skill first to see available skills,
            then read the relevant SKILL.md with read_file before execution.
            Use code for Python or JavaScript snippets when needed.
            For shell commands, choose the shell according to the runtime environment information
            (Windows depends on Git Bash/PowerShell availability; Linux/macOS usually use bash/sh).
            """;
    private static final String SKILL_RAIL_NO_SKILL_PROMPT_CN = """
            # 技能

            当前任务没有选择任何技能。如有技能信息可用，请用 read_file 阅读相关 SKILL.md。
            """;
    private static final String SKILL_RAIL_NO_SKILL_PROMPT_EN = """
            # Skills

            No skill was selected for this task. When skill information is available, read the relevant SKILL.md using read_file.
            """;

    OverlaySkillManager overlaySkillManager;
    SkillManager tenantSkillManager;
    TenantWorkspaceResolver railWorkspaceResolver;

    private DeepAgent owner;
    private SkillManager skillManager;
    private SkillTool skillTool;
    private Path skillsRoot;
    private String skillMode = SKILL_MODE_ALL;
    private final List<String> configuredSkillDirectories;
    private final List<RemoteSkillSource> remoteSkillSources;
    private final Set<String> enabledSkills;
    private final Set<String> disabledSkills;
    private final List<Tool> tools = new ArrayList<>();
    private boolean enableCache = true;
    private final boolean includeTools;
    private FilesystemTool filesystemTool;
    private CodeTool codeTool;
    private BashTool bashTool;
    private List<Map.Entry<String, Long>> skillsSnapshotSignature = null;

    /**
     * SkillUseRail.
     * 
     * @since 0.1.7
     */
    public SkillUseRail() {
        this(List.of(), SKILL_MODE_ALL, List.of(), List.of());
    }

    /**
     * SkillUseRail.
     * 
     * @param skillsDir skillsDir
     * @since 0.1.7
     */
    public SkillUseRail(String skillsDir) {
        this(List.of(skillsDir), SKILL_MODE_AUTO_LIST, List.of(), List.of());
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
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, remoteSkillSources, enableCache, true);
    }

    /**
     * Full constructor with enableCache and includeTools.
     *
     * @param skillDirectories skillDirectories
     * @param skillMode skillMode
     * @param enabledSkills enabledSkills
     * @param disabledSkills disabledSkills
     * @param remoteSkillSources remoteSkillSources
     * @param enableCache enableCache
     * @param includeTools whether to register fallback read_file / code / bash
     * @since 0.1.15
     */
    public SkillUseRail(List<String> skillDirectories, String skillMode, List<String> enabledSkills,
            List<String> disabledSkills, List<RemoteSkillSource> remoteSkillSources, boolean enableCache,
            boolean includeTools) {
        this.configuredSkillDirectories = normalizeStringList(skillDirectories);
        this.skillMode = normalizeMode(skillMode);
        this.enabledSkills = new LinkedHashSet<>(normalizeStringList(enabledSkills));
        this.disabledSkills = new LinkedHashSet<>(normalizeStringList(disabledSkills));
        this.remoteSkillSources = remoteSkillSources == null ? List.of() : List.copyOf(remoteSkillSources);
        this.enableCache = enableCache;
        this.includeTools = includeTools;
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
            overlaySkillManager = new OverlaySkillManager(tenantSkillManager, skillManager,
                overlayDir, railWorkspaceResolver);
            skillTool = new SkillTool(skillsRoot.toString(), railWorkspaceResolver, overlaySkillManager);
        } else {
            skillTool = new SkillTool(skillsRoot.toString());
        }

        String language = deepAgent.getWorkspace().getLanguage();
        registerOwnedTool(deepAgent, "skill_tool", language, inputs -> readSkill(inputs));
        if (shouldRegisterFallbackTools(deepAgent)) {
            filesystemTool = new FilesystemTool(deepAgent.getWorkspace().root().toString());
            codeTool = new CodeTool();
            bashTool = new BashTool();
            registerOwnedTool(deepAgent, "read_file", language, this::readFile);
            registerOwnedTool(deepAgent, "code", language, this::executeCode);
            registerOwnedTool(deepAgent, "bash", language, this::executeBash);
        }
        // all mode already injects the full catalog into the prompt; list_skill is only
        // useful when the model must search instead of reading a pre-expanded list.
        if (SKILL_MODE_AUTO_LIST.equals(skillMode)) {
            registerOwnedTool(deepAgent, "list_skill", language, this::listSkill);
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
        skillTool = null;
        filesystemTool = null;
        codeTool = null;
        bashTool = null;
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
        if (owner == null || skillManager == null || "none".equals(skillMode)) {
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
     * includeTools.
     *
     * @return whether fallback read_file / code / bash registration is enabled
     * @since 0.1.15
     */
    public boolean includeTools() {
        return includeTools;
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
        // 注意：租户技能根目录不再纳入公共 skillManager 的刷新范围。
        // 租户技能由 OverlaySkillManager 按租户缓存独立加载（getOrRefreshTenantSkillManager），
        // 若在此混入公共 skillManager，并发多租户请求时会互相污染公共技能列表。
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
                    .anyMatch(SkillUseRail::isSkillPrompt);
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
        boolean hasQuery = query != null && !String.valueOf(query).isBlank();
        List<Skill> skills = filterSkills(hasQuery ? String.valueOf(query) : null);
        String mode = hasQuery ? "filtered" : "all";
        // Substring match on the whole user task often yields nothing; fall back to the
        // full catalog so auto_list can still discover skills (Python does the same when
        // list_skill_model is not configured).
        if (hasQuery && skills.isEmpty()) {
            skills = filterSkills(null);
            mode = "all";
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skills", dumpSkills(skills));
        data.put("mode", mode);
        if (hasQuery && "all".equals(mode)) {
            data.put("message", "list_skill_model is not configured, fallback to all skills.");
        }
        return ToolOutput.builder().success(true).data(data).build();
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
     * Register a tool only when this rail still owns the ability name.
     *
     * @param deepAgent deepAgent
     * @param name tool name
     * @param language language
     * @param handler handler
     * @since 0.1.15
     */
    private void registerOwnedTool(DeepAgent deepAgent, String name, String language,
            java.util.function.Function<Map<String, Object>, Object> handler) {
        if (deepAgent.getAgent() != null && deepAgent.getAgent().getAbilityManager().get(name) != null) {
            Loggers.AGENT.debug("[SkillUseRail] ability '{}' is already registered by another owner; skip fallback",
                    name);
            return;
        }
        Tool tool = new LocalFunction(card(name, deepAgent, language), handler);
        tools.add(tool);
        deepAgent.registerHarnessTool(tool);
    }

    /**
     * Fallback read_file / code / bash are only useful when no SysOperationRail owns them.
     *
     * @param deepAgent deepAgent
     * @return true when this rail should register the fallback tools
     * @since 0.1.15
     */
    private boolean shouldRegisterFallbackTools(DeepAgent deepAgent) {
        if (!includeTools) {
            return false;
        }
        List<Object> rails = deepAgent.getConfig() != null ? deepAgent.getConfig().getRails() : null;
        if (rails == null) {
            return true;
        }
        for (Object rail : rails) {
            if (rail instanceof SysOperationRail) {
                return false;
            }
        }
        return true;
    }

    private Object readFile(Map<String, Object> inputs) {
        if (filesystemTool == null) {
            return ToolOutput.builder().success(false).error("read_file is not initialized").build();
        }
        String filePath = stringArg(inputs, "file_path", stringArg(inputs, "path", ""));
        return filesystemTool.readFile(filePath);
    }

    private Object executeCode(Map<String, Object> inputs) {
        if (codeTool == null) {
            return ToolOutput.builder().success(false).error("code is not initialized").build();
        }
        return codeTool.invoke(stringArg(inputs, "code", ""), stringArg(inputs, "language", "python"));
    }

    private Object executeBash(Map<String, Object> inputs) {
        if (bashTool == null) {
            return ToolOutput.builder().success(false).error("bash is not initialized").build();
        }
        String workdir = stringArg(inputs, "workdir",
                owner != null ? owner.getWorkspace().root().toString() : null);
        return bashTool.invoke(stringArg(inputs, "command", ""), workdir, booleanArg(inputs, "run_in_background", false),
                integerArg(inputs, "max_output_chars"));
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
        if (SKILL_MODE_AUTO_LIST.equals(mode)) {
            return SKILL_RAIL_AUTO_LIST_MODE_PROMPT_EN;
        }
        String skillLines = renderSkillLines(skills);
        if (skillLines.isEmpty()) {
            return SKILL_RAIL_NO_SKILL_PROMPT_EN;
        }
        return SKILL_RAIL_ALL_MODE_HEADER_EN + skillLines + SKILL_RAIL_ALL_MODE_INSTRUCTION_EN;
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
        if (SKILL_MODE_AUTO_LIST.equals(mode)) {
            return SKILL_RAIL_AUTO_LIST_MODE_PROMPT_CN;
        }
        String skillLines = renderSkillLines(skills);
        if (skillLines.isEmpty()) {
            return SKILL_RAIL_NO_SKILL_PROMPT_CN;
        }
        return SKILL_RAIL_ALL_MODE_HEADER_CN + skillLines + SKILL_RAIL_ALL_MODE_INSTRUCTION_CN;
    }

    /**
     * renderSkillLines.
     *
     * @param skills skills
     * @return the result
     * @since 0.1.15
     */
    private static String renderSkillLines(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            lines.add(i + ". " + value(skill.getName()) + ": " + value(skill.getDescription()));
        }
        return String.join("\n\n", lines);
    }

    /**
     * dumpSkills.
     *
     * @param skills skills
     * @return serializable skill maps
     * @since 0.1.15
     */
    private static List<Map<String, Object>> dumpSkills(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> dumped = new ArrayList<>();
        for (Skill skill : skills) {
            dumped.add(dumpSkill(skill));
        }
        return dumped;
    }

    /**
     * dumpSkill.
     *
     * @param skill skill
     * @return serializable skill map
     * @since 0.1.15
     */
    private static Map<String, Object> dumpSkill(Skill skill) {
        String directory = value(skill.getDirectory());
        Map<String, Object> dumped = new LinkedHashMap<>();
        dumped.put("name", value(skill.getName()));
        dumped.put("description", value(skill.getDescription()));
        dumped.put("directory", directory);
        dumped.put("skill_md_path", directory.isBlank() ? "SKILL.md" : Path.of(directory).resolve("SKILL.md").toString());
        return dumped;
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
        return mode == null || mode.isBlank() ? SKILL_MODE_ALL : mode.trim().toLowerCase(Locale.ROOT);
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

    private static boolean booleanArg(Map<String, Object> inputs, String key, boolean fallback) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer integerArg(Map<String, Object> inputs, String key) {
        Object value = inputs != null ? inputs.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
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
     * isSkillPrompt.
     *
     * @param content content
     * @return the result
     * @since 0.1.15
     */
    private static boolean isSkillPrompt(String content) {
        if (content == null) {
            return false;
        }
        boolean hasSkillHeader = content.contains("# Skills") || content.contains("# 技能");
        return hasSkillHeader && (content.contains("SKILL.md") || content.contains("list_skill"));
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