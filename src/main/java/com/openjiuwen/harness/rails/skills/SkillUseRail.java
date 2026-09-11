/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.skills.GitHubTree;
import com.openjiuwen.core.singleagent.skills.RemoteSkillUtil;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.prompts.sections.SkillsSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.SysOperationRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.FilesystemTools;
import com.openjiuwen.harness.tools.skills.ListSkillTool;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import com.openjiuwen.harness.tools.skills.SkillTool;
import com.openjiuwen.harness.tools.shell.bash.BashTool;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rail that manages skill prompt injection, local skill discovery and remote skill sync.
 *
 * @since 0.1.7
 */
public class SkillUseRail extends DeepAgentRail {

    public static final String SKILL_MODE_ALL = "all";
    public static final String SKILL_MODE_AUTO_LIST = "auto_list";
    private static final Set<String> VALID_SKILL_MODES = Set.of(SKILL_MODE_ALL, SKILL_MODE_AUTO_LIST);

    private final List<Path> skillDirs;
    private final String skillMode;
    private final boolean enableCache;
    private final boolean includeTools;
    private final Set<String> enabledSkills;
    private final Set<String> disabledSkills;
    private final List<RemoteSkillSource> remoteSkillSources;
    private final List<SkillDescriptor> skills = new ArrayList<>();
    private final Map<Path, SkillDescriptor> skillCache = new LinkedHashMap<>();
    private final Map<Path, Long> skillUpdatedAt = new LinkedHashMap<>();
    private final Set<String> ownedToolNames = new LinkedHashSet<>();
    private final Set<String> ownedToolIds = new LinkedHashSet<>();
    private final Map<String, String> evolutionTexts = new LinkedHashMap<>();
    private String language = "cn";
    private DeepAgent ownerAgent;

    public SkillUseRail() {
        this("", SKILL_MODE_AUTO_LIST, true, true, null, null);
    }

    public SkillUseRail(String skillsDir) {
        this(skillsDir, SKILL_MODE_AUTO_LIST, true, true, null, null);
    }

    public SkillUseRail(List<String> skillDirectories, String skillMode) {
        this(skillDirectories, skillMode, null, null);
    }

    public SkillUseRail(
            List<String> skillDirectories,
            String skillMode,
            Iterable<String> enabledSkills,
            Iterable<String> disabledSkills
    ) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, List.of());
    }

    /**
     * Constructs a rail that can sync remote GitHub skill sources before local discovery.
     *
     * @param skillDirectories local skill directories
     * @param skillMode skill mode
     * @param enabledSkills enabled skill names
     * @param disabledSkills disabled skill names
     * @param remoteSkillSources GitHub skill sources to download during init
     */
    public SkillUseRail(
            List<String> skillDirectories,
            String skillMode,
            Iterable<String> enabledSkills,
            Iterable<String> disabledSkills,
            List<RemoteSkillSource> remoteSkillSources
    ) {
        this(skillDirectories, skillMode, enabledSkills, disabledSkills, remoteSkillSources, true, true);
    }

    /**
     * Full constructor matching the 930 public surface used by DeepAgentDemo.
     *
     * @param skillDirectories local skill directories
     * @param skillMode skill mode
     * @param enabledSkills enabled skill names
     * @param disabledSkills disabled skill names
     * @param remoteSkillSources GitHub skill sources to download during init
     * @param enableCache whether to cache skill markdown
     * @param hasTools whether fallback read_file / code / bash may be registered
     */
    public SkillUseRail(
            List<String> skillDirectories,
            String skillMode,
            List<String> enabledSkills,
            List<String> disabledSkills,
            List<RemoteSkillSource> remoteSkillSources,
            boolean enableCache,
            boolean hasTools
    ) {
        this(
                (Iterable<String>) skillDirectories,
                skillMode,
                enabledSkills,
                disabledSkills,
                remoteSkillSources,
                enableCache,
                hasTools
        );
    }

    public SkillUseRail(
            String skillsDir,
            String skillMode,
            boolean enableCache,
            boolean includeTools,
            Iterable<String> enabledSkills,
            Iterable<String> disabledSkills
    ) {
        this(
                parseNameList(skillsDir),
                skillMode,
                enabledSkills,
                disabledSkills,
                List.of(),
                enableCache,
                includeTools
        );
    }

    private SkillUseRail(
            Iterable<String> skillDirectories,
            String skillMode,
            Iterable<String> enabledSkills,
            Iterable<String> disabledSkills,
            List<RemoteSkillSource> remoteSkillSources,
            boolean enableCache,
            boolean hasTools
    ) {
        setPriority(100);
        String resolvedMode = skillMode == null || skillMode.isBlank() ? SKILL_MODE_AUTO_LIST : skillMode;
        if (!VALID_SKILL_MODES.contains(resolvedMode)) {
            throw new IllegalArgumentException("Unsupported skill_mode: " + skillMode);
        }
        this.skillDirs = normalizeSkillDirs(joinSkillDirectories(skillDirectories));
        this.skillMode = resolvedMode;
        this.enableCache = enableCache;
        this.includeTools = hasTools;
        this.enabledSkills = normalizeNameSet(enabledSkills);
        this.disabledSkills = normalizeNameSet(disabledSkills);
        this.remoteSkillSources = normalizeRemoteSources(remoteSkillSources);
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        ownerAgent = agent;
        if (agent != null && agent.deepConfig() != null) {
            language = agent.deepConfig().getLanguage();
        }
        registerSkillTools(agent);
        registerFallbackTools(agent);
        syncRemoteSkills(agent);
        reloadSkills();
    }

    @Override
    public void uninit(DeepAgent agent) {
        if (agent != null) {
            for (String name : ownedToolNames) {
                agent.unregisterTool(name);
            }
        }
        ownedToolNames.clear();
        ownedToolIds.clear();
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        reloadSkills();
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        reloadSkills();
        String resolvedLanguage = language;
        if (ctx != null) {
            resolvedLanguage = String.valueOf(ctx.getValues().getOrDefault("language", language));
        }
        applySkillsSection(ctx, buildSkillsSection(resolvedLanguage));
    }

    /**
     * Injects the skill prompt section using the owning DeepAgent when the context has no agent.
     *
     * @param context callback context from ReAct or a direct Demo call
     */
    @Override
    public void beforeModelCall(AgentCallbackContext context) {
        reloadSkills();
        String resolvedLanguage = language;
        if (context != null && context.getExtra() != null && context.getExtra().get("language") != null) {
            resolvedLanguage = String.valueOf(context.getExtra().get("language"));
        }
        applySkillsSection(null, buildSkillsSection(resolvedLanguage));
    }

    public List<SkillDescriptor> getSkillsMeta() {
        return new ArrayList<>(skills);
    }

    public List<String> getSkillDirs() {
        return skillDirs.stream().map(Path::toString).toList();
    }

    public String getSkillMode() {
        return skillMode;
    }

    public Set<String> getEnabledSkills() {
        return new LinkedHashSet<>(enabledSkills);
    }

    public void prependSkillDirs(List<String> dirs) {
        List<Path> normalized = normalizeSkillDirs(dirs == null ? "" : String.join(",", dirs));
        skillDirs.removeAll(normalized);
        skillDirs.addAll(0, normalized);
        clearSkills();
    }

    public void removeSkillDirs(List<String> dirs) {
        List<Path> normalized = normalizeSkillDirs(dirs == null ? "" : String.join(",", dirs));
        skillDirs.removeAll(normalized);
        clearSkills();
    }

    public void reloadSkills() {
        if (!enableCache) {
            skillCache.clear();
            skillUpdatedAt.clear();
        }
        refreshSkillsIncrementally();
        skills.clear();
        skills.addAll(filterSkills(collectSkillsInOrder()));
    }

    public void clearSkills() {
        skillCache.clear();
        skillUpdatedAt.clear();
        skills.clear();
    }

    public Set<String> getOwnedToolNames() {
        return new LinkedHashSet<>(ownedToolNames);
    }

    public Set<String> getOwnedToolIds() {
        return new LinkedHashSet<>(ownedToolIds);
    }

    public boolean isIncludeTools() {
        return includeTools;
    }

    /**
     * Returns whether fallback read_file / code / bash registration is enabled.
     *
     * @return {@code true} when fallback tools may be registered
     */
    public boolean hasTools() {
        return includeTools;
    }

    /**
     * Returns the configured remote GitHub skill sources.
     *
     * @return immutable remote skill sources, never {@code null}
     */
    public List<RemoteSkillSource> remoteSkillSources() {
        return remoteSkillSources;
    }

    /**
     * Returns snake_case ids of tools this rail registered.
     *
     * @return registered tool names
     */
    public List<String> registeredToolNames() {
        return new ArrayList<>(ownedToolIds);
    }

    /**
     * Builds the skill prompt text for the given mode without mutating rail state.
     *
     * @param promptLanguage prompt language
     * @param mode skill mode
     * @param selectedSkills unused selected skills, accepted for 930 call sites
     * @return prompt text
     */
    public String buildSkillPrompt(String promptLanguage, String mode, List<?> selectedSkills) {
        String resolvedMode = mode == null || mode.isBlank() ? skillMode : mode;
        if (SKILL_MODE_AUTO_LIST.equals(resolvedMode)) {
            return SkillsSection.buildAutoListModeSkillPrompt(promptLanguage);
        }
        List<String> lines = new ArrayList<>();
        int index = 0;
        for (SkillDescriptor skill : skills) {
            lines.add(SkillsSection.buildSkillLine(
                    index++,
                    skill.name(),
                    getSkillDescription(skill),
                    Path.of(skill.directory(), "SKILL.md").toString()
            ));
        }
        return SkillsSection.buildAllModeSkillPrompt(SkillsSection.buildSkillLines(lines), promptLanguage);
    }

    public Map<String, String> getEvolutionTexts() {
        return new LinkedHashMap<>(evolutionTexts);
    }

    private void applySkillsSection(CallbackContext ctx, PromptSection section) {
        if (ctx != null) {
            if (section == null) {
                ctx.put(SectionName.SKILLS, null);
            } else {
                ctx.put("skills_section", section);
                ctx.put("skills", dumpSkills());
            }
        }
        SystemPromptBuilder builder = resolvePromptBuilder(ctx);
        if (builder == null) {
            return;
        }
        builder.removeSection(SectionName.SKILLS);
        if (section != null) {
            builder.addSection(section);
        }
    }

    private void registerSkillTools(DeepAgent agent) {
        if (agent == null) {
            return;
        }
        SkillTool skillTool = new SkillTool(this::getSkillsMeta);
        agent.registerTool(skillTool);
        ownedToolNames.add(skillTool.getCard().getName());
        ownedToolIds.add(skillTool.getCard().getId());
        if (!SKILL_MODE_AUTO_LIST.equals(skillMode)) {
            return;
        }
        ListSkillTool listSkillTool = new ListSkillTool(this::getSkillsMeta);
        agent.registerTool(listSkillTool);
        ownedToolNames.add(listSkillTool.getCard().getName());
        ownedToolIds.add(listSkillTool.getCard().getId());
    }

    private void registerFallbackTools(DeepAgent agent) {
        if (!shouldRegisterFallbackTools(agent)) {
            return;
        }
        registerOwnedTool(agent, new FilesystemTools.ReadFileTool(resolveWorkspaceRoot(agent)));
        registerOwnedTool(agent, new CodeTool(null));
        registerOwnedTool(agent, new BashTool());
    }

    private boolean shouldRegisterFallbackTools(DeepAgent agent) {
        if (!includeTools || agent == null) {
            return false;
        }
        DeepAgentConfig config = agent.deepConfig();
        List<Object> rails = config == null ? null : config.getRails();
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

    private void registerOwnedTool(DeepAgent agent, Tool tool) {
        if (tool == null || tool.getCard() == null) {
            return;
        }
        String toolId = tool.getCard().getId();
        String toolName = tool.getCard().getName();
        if (isAbilityPresent(agent, toolName) || isAbilityPresent(agent, toolId)) {
            return;
        }
        agent.registerTool(tool);
        ownedToolNames.add(toolName);
        ownedToolIds.add(toolId);
    }

    private static boolean isAbilityPresent(DeepAgent agent, String name) {
        if (name == null || agent.getAgent() == null || agent.getAgent().getAbilityManager() == null) {
            return false;
        }
        return agent.getAgent().getAbilityManager().get(name).isPresent();
    }

    private static String resolveWorkspaceRoot(DeepAgent agent) {
        if (agent.getWorkspace() == null || agent.getWorkspace().root() == null) {
            return "./";
        }
        return agent.getWorkspace().root().toString();
    }

    private SystemPromptBuilder resolvePromptBuilder(CallbackContext ctx) {
        if (ctx != null && ctx.getAgent() != null) {
            Object react = ctx.getAgent().reactAgent();
            if (react instanceof ReActAgent reactAgent) {
                return reactAgent.getPromptBuilder();
            }
        }
        if (ownerAgent != null && ownerAgent.getAgent() != null) {
            return ownerAgent.getAgent().getPromptBuilder();
        }
        return null;
    }

    private PromptSection buildSkillsSection(String resolvedLanguage) {
        if (SKILL_MODE_ALL.equals(skillMode)) {
            List<String> lines = new ArrayList<>();
            int index = 0;
            for (SkillDescriptor skill : skills) {
                lines.add(SkillsSection.buildSkillLine(
                        index++,
                        skill.name(),
                        getSkillDescription(skill),
                        Path.of(skill.directory(), "SKILL.md").toString()
                ));
            }
            return SkillsSection.buildSkillsSection(
                    SkillsSection.buildSkillLines(lines),
                    resolvedLanguage,
                    SKILL_MODE_ALL
            );
        }
        return SkillsSection.buildSkillsSection("", resolvedLanguage, SKILL_MODE_AUTO_LIST);
    }

    private String getSkillDescription(SkillDescriptor skill) {
        String base = skill.description() == null ? "" : skill.description();
        String extra = evolutionTexts.get(skill.name());
        if (extra == null || extra.isBlank()) {
            return base;
        }
        return base + "\n" + extra;
    }

    private void refreshSkillsIncrementally() {
        Set<Path> discovered = new LinkedHashSet<>();
        for (Path root : skillDirs) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                List<Path> children = stream
                        .filter(Files::isDirectory)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
                for (Path child : children) {
                    Path skillMd = child.resolve("SKILL.md");
                    if (!Files.isRegularFile(skillMd)) {
                        continue;
                    }
                    Path key = child.toAbsolutePath().normalize();
                    discovered.add(key);
                    long updatedAt = Files.getLastModifiedTime(skillMd).toMillis();
                    if (!skillCache.containsKey(key) || skillUpdatedAt.getOrDefault(key, -1L) != updatedAt) {
                        SkillDescriptor descriptor = loadSkill(child, skillMd);
                        skillCache.put(key, descriptor);
                        skillUpdatedAt.put(key, updatedAt);
                    }
                }
            } catch (IOException ignored) {
                // Python implementation logs and skips unreadable skill roots.
            }
        }
        skillCache.keySet().removeIf(path -> !discovered.contains(path));
        skillUpdatedAt.keySet().removeIf(path -> !discovered.contains(path));
    }

    private SkillDescriptor loadSkill(Path skillDir, Path skillMd) {
        String description;
        try {
            description = loadDescription(skillMd);
        } catch (IOException exception) {
            description = "Skill located in " + skillDir;
        }
        return new SkillDescriptor(
                skillDir.getFileName().toString(),
                description,
                skillDir.toAbsolutePath().normalize().toString(),
                Map.of()
        );
    }

    private static String loadDescription(Path skillMd) throws IOException {
        String text = Files.readString(skillMd, StandardCharsets.UTF_8);
        if (!text.startsWith("---")) {
            return "";
        }
        String[] parts = text.split("---", 3);
        if (parts.length < 3) {
            return "";
        }
        for (String line : parts[1].split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("description:")) {
                return trimmed.substring("description:".length()).trim().replaceAll("^['\"]|['\"]$", "");
            }
        }
        return "";
    }

    private List<SkillDescriptor> collectSkillsInOrder() {
        Map<String, SkillDescriptor> byName = new LinkedHashMap<>();
        for (SkillDescriptor skill : skillCache.values()) {
            byName.putIfAbsent(skill.name(), skill);
        }
        return new ArrayList<>(byName.values());
    }

    private List<SkillDescriptor> filterSkills(List<SkillDescriptor> candidates) {
        List<SkillDescriptor> result = new ArrayList<>();
        for (SkillDescriptor skill : candidates) {
            if (!enabledSkills.isEmpty() && !enabledSkills.contains(skill.name())) {
                continue;
            }
            if (disabledSkills.contains(skill.name())) {
                continue;
            }
            result.add(skill);
        }
        return result;
    }

    private List<Map<String, Object>> dumpSkills() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SkillDescriptor skill : skills) {
            result.add(skill.asMap());
        }
        return result;
    }

    private void syncRemoteSkills(DeepAgent deepAgent) {
        if (remoteSkillSources.isEmpty()) {
            return;
        }
        Path targetRoot = resolveRemoteTargetRoot(deepAgent);
        for (RemoteSkillSource source : remoteSkillSources) {
            uploadRemoteSkill(deepAgent, source, targetRoot);
        }
    }

    /**
     * Downloads one remote skill source into {@code targetRoot}. Tests may override this
     * to avoid real GitHub access.
     *
     * @param deepAgent owning agent
     * @param source remote source
     * @param targetRoot local directory that later skill discovery will scan
     */
    protected void uploadRemoteSkill(DeepAgent deepAgent, RemoteSkillSource source, Path targetRoot) {
        RemoteSkillUtil remoteSkillUtil = new RemoteSkillUtil(resolveOwnerId(deepAgent));
        remoteSkillUtil.uploadSkillFromGitHub(source.toGitHubTree(), targetRoot.toString(), source.token());
    }

    private Path resolveRemoteTargetRoot(DeepAgent deepAgent) {
        if (!skillDirs.isEmpty()) {
            return skillDirs.get(0);
        }
        Path targetRoot = defaultRemoteTargetRoot(deepAgent);
        skillDirs.add(targetRoot);
        return targetRoot;
    }

    private static Path defaultRemoteTargetRoot(DeepAgent deepAgent) {
        if (deepAgent != null && deepAgent.getWorkspace() != null && deepAgent.getWorkspace().root() != null) {
            return deepAgent.getWorkspace().root().resolve("skills");
        }
        return Path.of("skills").toAbsolutePath().normalize();
    }

    private static String resolveOwnerId(DeepAgent deepAgent) {
        if (deepAgent == null || deepAgent.getCard() == null || deepAgent.getCard().getId() == null) {
            return "";
        }
        return deepAgent.getCard().getId();
    }

    private static String joinSkillDirectories(Iterable<String> skillDirectories) {
        if (skillDirectories == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String directory : skillDirectories) {
            if (directory != null && !directory.isBlank()) {
                parts.add(directory.trim());
            }
        }
        return String.join(",", parts);
    }

    private static List<RemoteSkillSource> normalizeRemoteSources(List<RemoteSkillSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return List.copyOf(sources);
    }

    private static List<Path> normalizeSkillDirs(String raw) {
        List<Path> result = new ArrayList<>();
        for (String item : parseNameList(raw)) {
            result.add(Path.of(item).toAbsolutePath().normalize());
        }
        return result;
    }

    private static Set<String> normalizeNameSet(Iterable<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw == null) {
            return result;
        }
        for (String item : raw) {
            result.addAll(parseNameList(item));
        }
        return result;
    }

    private static List<String> parseNameList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.replace(';', ',');
        List<String> result = new ArrayList<>();
        for (String part : normalized.split(",")) {
            String item = part.trim();
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * GitHub skill source accepted by the 930 {@code SkillUseRail} constructor.
     *
     * @param owner repository owner
     * @param repo repository name
     * @param ref git ref, defaults to {@code HEAD}
     * @param directory optional path inside the repository
     * @param token optional GitHub token
     * @since 2026-09-11
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
                if (isUnsafeDirectory(path, normalized)) {
                    throw new IllegalArgumentException("remote skill directory must stay within the repository");
                }
                return normalized;
            } catch (InvalidPathException ex) {
                throw new IllegalArgumentException("remote skill directory is invalid: " + value, ex);
            }
        }

        private static boolean isUnsafeDirectory(Path path, String normalized) {
            return path.isAbsolute()
                    || normalized.startsWith("../")
                    || "..".equals(normalized)
                    || normalized.contains("/../")
                    || normalized.endsWith("/..");
        }
    }
}
