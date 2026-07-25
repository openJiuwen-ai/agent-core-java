/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.sysop.cwd.CwdContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * High-level utility for managing and working with skills.
 * <p>
 * Combines SkillManager and RemoteSkillUtil functionalities,
 * providing skill registration, tool management, and prompt generation.
 * </p>
 * 
 * @since 0.1.7
 */
public class SkillUtil {
    private static final String SKILL_PROMPT_CONTENT =
        "\nTo help you better complete tasks, the following skill knowledge is equipped:\n" + "%s\n"
                + "You can use the readFile tool to read the corresponding SKILL.md file "
                + "to obtain the relevant skill.\n";

    private final SkillManager skillManager;
    private final RemoteSkillUtil remoteSkillUtil;

    /**
     * SkillUtil.
     * 
     * @param sysOperationId sysOperationId
     * @since 0.1.7
     */
    public SkillUtil(String sysOperationId) {
        this.skillManager = new SkillManager(sysOperationId);
        this.remoteSkillUtil = new RemoteSkillUtil(sysOperationId);
    }

    /**
     * setSysOperationId.
     * 
     * @param sysOperationId sysOperationId
     * @since 0.1.7
     */
    public void setSysOperationId(String sysOperationId) {
        this.skillManager.setSysOperationId(sysOperationId);
        this.remoteSkillUtil.setSysOperationId(sysOperationId);
    }

    /**
     * getSkillManager.
     * 
     * @return the result
     * @since 0.1.7
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * getRemoteSkillUtil.
     * 
     * @return the result
     * @since 0.1.7
     */
    public RemoteSkillUtil getRemoteSkillUtil() {
        return remoteSkillUtil;
    }

    /**
     * Register skills from a path.
     * 
     * @param skillPath the path to the skill directory
     * @param agent the agent instance (for compatibility)
     * @since 0.1.7
     */
    public void registerSkills(Object skillPath, BaseAgent agent) {
        if (skillPath instanceof String path) {
            skillManager.register(path);
        } else if (skillPath instanceof List<?> list) {
            for (Object p : list) {
                if (p instanceof String s) {
                    skillManager.register(s);
                }
            }
        } else {
            // no-op
        }
    }

    /**
     * Register remote skills from GitHub.
     * 
     * @param skillsDir local skills directory
     * @param githubTree the GitHub tree reference
     * @param token GitHub API token (optional)
     * @since 0.1.7
     */
    public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token) {
        remoteSkillUtil.uploadSkillFromGitHub(githubTree, skillsDir, token);
    }

    /**
     * Check if any skills are registered.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean hasSkill() {
        return skillManager.count() > 0;
    }

    /**
     * Generate a formatted prompt string with information about all registered skills.
     * <p>
     * SKILL.md paths are only emitted when the skill directory lies inside the LOCAL FS
     * bases (workspace / cwd / projectRoot). Paths outside those roots are omitted so the
     * model is not steered to invent relative paths or copy long absolute temp paths.
     * </p>
     *
     * @return system + skills prompt text for the agent
     * @since 0.1.7
     */
    public String getSkillPrompt() {
        String systemPrompt = "You are an agent equipped with various skills to solve problems.\n"
                + "Before attempting any task, read the relevant skill document (SKILL.md) "
                + "using readFile and follow its workflow.\n";

        List<Skill> skills = skillManager.getAll();
        StringBuilder skillsInfo = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            skillsInfo.append(i).append(".Skill name: ").append(skill.getName()).append("; Skill description: ")
                    .append(skill.getDescription());
            String skillMdPath = resolveSkillMdPathForPrompt(skill.getDirectory());
            if (skillMdPath != null) {
                skillsInfo.append("; Skill directory: ").append(skillMdPath);
            }
            skillsInfo.append("\n");
        }

        String skillText = String.format(SKILL_PROMPT_CONTENT, skillsInfo.toString());
        return systemPrompt + "\n" + skillText;
    }

    /**
     * Resolves a relative {@code SKILL.md} path for prompts when the skill lies under LOCAL FS bases.
     * <p>
     * Skills outside workspace / cwd / projectRoot return {@code null} so no absolute temp path is emitted.
     *
     * @param directory skill directory absolute or relative path
     * @return POSIX-style relative path to {@code SKILL.md}, or {@code null} when outside allowed bases
     * @since 0.1.14
     */
    static String resolveSkillMdPathForPrompt(String directory) {
        if (directory == null || directory.isBlank()) {
            return null;
        }
        Path skillDir;
        try {
            skillDir = Path.of(directory).toAbsolutePath().normalize();
        } catch (Exception e) {
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

    /**
     * Collects normalized LOCAL FS roots used to decide whether a skill path is prompt-safe.
     *
     * @return ordered unique absolute bases from workspace, cwd, and projectRoot
     * @since 0.1.7
     */
    private static List<Path> localFsBases() {
        List<Path> bases = new ArrayList<>();
        addBase(bases, CwdContext.getWorkspace());
        addBase(bases, CwdContext.getCwd());
        addBase(bases, CwdContext.getProjectRoot());
        return bases;
    }

    /**
     * Appends a usable absolute root to {@code bases} when {@code root} is non-blank and parseable.
     *
     * @param bases accumulator of absolute base paths
     * @param root candidate root string; blank or unusable values are skipped
     * @since 0.1.7
     */
    private static void addBase(List<Path> bases, String root) {
        if (root == null || root.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(root).toAbsolutePath().normalize();
            if (!bases.contains(path)) {
                bases.add(path);
            }
        } catch (Exception ignored) {
            // skip unusable roots
        }
    }

    /**
     * Returns whether {@code candidate} is the same as or nested under {@code base}.
     *
     * @param base allowed root; {@code null} yields {@code false}
     * @param candidate path to test; {@code null} yields {@code false}
     * @return {@code true} when candidate is within base
     * @since 0.1.7
     */
    private static boolean isWithinBase(Path base, Path candidate) {
        if (base == null || candidate == null) {
            return false;
        }
        return candidate.startsWith(base);
    }
}
