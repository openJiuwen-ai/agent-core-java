/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * High-level utility for local and remote skill management.
 *
 * <p>Mirrors Python's {@code SkillUtil} in
 * {@code openjiuwen/core/single_agent/skills/skill_util.py}.</p>
 */
public class SkillUtil {
    /**
     * Prompt template used when Skill documents are loaded on demand through skill_tool.
     *
     */
    public static final String SKILL_PROMPT_CONTENT_ON_DEMAND = """
            To help you better complete tasks, the following skill knowledge is equipped:
            {{skills}}
            You can use the skill_tool meta tool to read the corresponding skill document and obtain the relevant skill.
            """;

    /**
     * Prompt template used when Skill documents are read directly with read_file.
     *
     */
    public static final String SKILL_PROMPT_CONTENT_LEGACY = """
            To help you better complete tasks, the following skill knowledge is equipped:
            {{skills}}
            You can use the read_file tool to read the corresponding SKILL.md file to obtain the relevant skill.
            """;

    /**
     * Deprecated compatibility alias for the legacy read-file prompt template.
     *
     * @deprecated use {@link #SKILL_PROMPT_CONTENT_ON_DEMAND} or
     *     {@link #SKILL_PROMPT_CONTENT_LEGACY} based on the loading mode.
     */
    @Deprecated
    public static final String SKILL_PROMPT_CONTENT = SKILL_PROMPT_CONTENT_LEGACY;

    private final SkillManager skillManager;
    private final RemoteSkillUtil remoteSkillUtil;

    public SkillUtil(String sysOperationId) {
        this.skillManager = new SkillManager(sysOperationId);
        this.remoteSkillUtil = new RemoteSkillUtil(sysOperationId);
    }

    SkillUtil(SkillManager skillManager, RemoteSkillUtil remoteSkillUtil) {
        this.skillManager = Objects.requireNonNull(skillManager, "skillManager");
        this.remoteSkillUtil = Objects.requireNonNull(remoteSkillUtil, "remoteSkillUtil");
    }

    public void setSysOperationId(String sysOperationId) {
        skillManager.setSysOperationId(sysOperationId);
        remoteSkillUtil.setSysOperationId(sysOperationId);
    }

    public void set_sys_operation_id(String sysOperationId) {
        setSysOperationId(sysOperationId);
    }

    public boolean registerSkills(String skillPath, Object agent, String sessionId) throws IOException {
        return registerSkills(List.of(skillPath), agent, sessionId);
    }

    public boolean registerSkills(String skillPath, Object agent, String sessionId, boolean useMetadataName)
            throws IOException {
        return registerSkills(List.of(skillPath), agent, sessionId, useMetadataName);
    }

    public boolean registerSkills(List<String> skillPaths, Object agent, String sessionId) throws IOException {
        return registerSkills(skillPaths, agent, sessionId, false);
    }

    public boolean registerSkills(List<String> skillPaths, Object agent, String sessionId, boolean useMetadataName)
            throws IOException {
        List<Path> paths = skillPaths == null ? List.of() : skillPaths.stream().map(Path::of).toList();
        skillManager.register(paths, false, useMetadataName);
        return true;
    }

    public boolean register_skills(List<String> skillPaths, Object agent, String sessionId) throws IOException {
        return registerSkills(skillPaths, agent, sessionId);
    }

    /**
     * Register skills after checking each real path against a trusted root.
     *
     * <p>Absolute paths are accepted when they canonicalize inside {@code skillsRoot}.</p>
     */
    public boolean registerSkills(List<String> skillPaths, Path skillsRoot, Object agent, String sessionId,
                                  boolean useMetadataName) throws IOException {
        if (skillPaths == null) {
            return true;
        }
        List<Path> paths = new ArrayList<>();
        for (String skillPath : skillPaths) {
            paths.add(SkillManager.resolveSafeSkillPath(skillPath, skillsRoot));
        }
        skillManager.register(paths, false, useMetadataName);
        return true;
    }

    public boolean registerSkills(List<String> skillPaths, Path skillsRoot, Object agent, String sessionId)
            throws IOException {
        return registerSkills(skillPaths, skillsRoot, agent, sessionId, false);
    }

    public boolean registerSkills(String skillPath, Path skillsRoot, Object agent, String sessionId)
            throws IOException {
        return registerSkills(List.of(skillPath), skillsRoot, agent, sessionId);
    }

    public List<Path> registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token) {
        return remoteSkillUtil.uploadSkillFromGithub(githubTree, skillsDir, token);
    }

    public List<Path> register_remote_skills(String skillsDir, GitHubTree githubTree, String token) {
        return registerRemoteSkills(skillsDir, githubTree, token);
    }

    public boolean hasSkill() {
        return skillManager.count() > 0;
    }

    public boolean has_skill() {
        return hasSkill();
    }

    private SkillConfig skillConfig = SkillConfig.fromEnvironment();

    public void setSkillConfig(SkillConfig config) {
        this.skillConfig = config == null ? new SkillConfig() : config;
        skillManager.setSkillConfig(this.skillConfig);
    }

    public SkillConfig getSkillConfig() {
        return skillConfig;
    }

    public String getSkillPrompt() {
        String loadingInstruction;
        String templateContent;
        if (skillConfig.isOnDemandLoading()) {
            loadingInstruction = "Before attempting any task, use the skill_tool meta tool "
                    + "to read the relevant skill document (SKILL.md) and follow its workflow.\n";
            templateContent = SKILL_PROMPT_CONTENT_ON_DEMAND;
        } else {
            loadingInstruction = "Before attempting any task, read the relevant skill document (SKILL.md) "
                    + "using read_file and follow its workflow.\n";
            templateContent = SKILL_PROMPT_CONTENT_LEGACY;
        }
        String systemPrompt = "You are an agent equipped with various skills to solve problems.\n"
                + loadingInstruction;
        List<String> skillsInfo = new ArrayList<>();
        List<Skill> skills = skillManager.getAll();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            skillsInfo.add(SkillPromptRenderer.renderPromptLine(i, skill, skillConfig.isOnDemandLoading()));
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("skills", skillsInfo.stream().collect(Collectors.joining("\n")));
        PromptTemplate template = PromptTemplate.builder().content(templateContent).build();
        return systemPrompt + "\n" + template.format(values).getContent();
    }

    /**
     * Resolves a relative {@code SKILL.md} path for prompts when the skill lies under LOCAL FS bases.
     * Skills outside workspace / cwd / projectRoot return {@code null} so no absolute temp path is emitted.
     */
    static String resolveSkillMdPathForPrompt(Path directory) {
        if (directory == null) {
            return null;
        }
        Path skillDir;
        try {
            skillDir = toNormalizedAbsolute(directory);
        } catch (InvalidPathException e) {
            return null;
        }
        Path skillMd = skillDir.resolve("SKILL.md").normalize();
        for (Path base : localFsBases()) {
            if (isWithinBase(base, skillDir) || isWithinBase(base, skillMd)) {
                Path relative = base.relativize(skillMd);
                return relative.toString().replace('\\', '/');
            }
        }
        return null;
    }

    private static List<Path> localFsBases() {
        List<Path> bases = new ArrayList<>();
        addBase(bases, CwdContext.getWorkspace());
        addBase(bases, CwdContext.getCwd());
        addBase(bases, CwdContext.getProjectRoot());
        return bases;
    }

    private static void addBase(List<Path> bases, String root) {
        if (root == null || root.isBlank()) {
            return;
        }
        try {
            Path path = toNormalizedAbsolute(Path.of(root));
            if (!bases.contains(path)) {
                bases.add(path);
            }
        } catch (InvalidPathException ignored) {
            // skip unusable roots
        }
    }

    private static Path toNormalizedAbsolute(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath().normalize();
        } catch (IOException e) {
            return absolute;
        }
    }

    private static boolean isWithinBase(Path base, Path candidate) {
        if (base == null || candidate == null) {
            return false;
        }
        return candidate.startsWith(base);
    }

    public String get_skill_prompt() {
        return getSkillPrompt();
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public SkillManager get_skill_manager() {
        return skillManager;
    }

    public RemoteSkillUtil getRemoteSkillUtil() {
        return remoteSkillUtil;
    }

    public RemoteSkillUtil get_remote_skill_util() {
        return remoteSkillUtil;
    }
}
