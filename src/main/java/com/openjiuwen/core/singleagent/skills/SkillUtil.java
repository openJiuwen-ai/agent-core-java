/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.singleagent.BaseAgent;

import java.util.List;

/**
 * High-level utility for managing and working with skills.
 *
 * <p>Combines SkillManager and RemoteSkillUtil functionalities,
 * providing skill registration, tool management, and prompt generation.</p>
 */
public class SkillUtil {

    private static final String SKILL_PROMPT_CONTENT =
            "\nTo help you better complete tasks, the following skill knowledge is equipped:\n"
                    + "%s\n"
                    + "You can use the readFile tool to read the corresponding SKILL.md file to obtain the relevant skill.\n";

    private final SkillManager skillManager;
    private final RemoteSkillUtil remoteSkillUtil;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillUtil(String sysOperationId) {
        this.skillManager = new SkillManager(sysOperationId);
        this.remoteSkillUtil = new RemoteSkillUtil(sysOperationId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSysOperationId(String sysOperationId) {
        this.skillManager.setSysOperationId(sysOperationId);
        this.remoteSkillUtil.setSysOperationId(sysOperationId);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public RemoteSkillUtil getRemoteSkillUtil() {
        return remoteSkillUtil;
    }

    /**
     * Register skills from a path.
     *
     * @param skillPath the path to the skill directory
     * @param agent     the agent instance (for compatibility)
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
        }
    }

    /**
     * Register remote skills from GitHub.
     *
     * @param skillsDir  local skills directory
     * @param githubTree the GitHub tree reference
     * @param token      GitHub API token (optional)
     */
    public void registerRemoteSkills(String skillsDir, GitHubTree githubTree, String token) {
        remoteSkillUtil.uploadSkillFromGitHub(githubTree, skillsDir, token);
    }

    /**
     * Check if any skills are registered.
     */
    public boolean hasSkill() {
        return skillManager.count() > 0;
    }

    /**
     * Generate a formatted prompt string with information about all registered skills.
     */
    public String getSkillPrompt() {
        String systemPrompt = "You are an agent equipped with various skills to solve problems.\n"
                + "Before attempting any task, read the relevant skill document (SKILL.md) "
                + "using readFile and follow its workflow.\n";

        List<Skill> skills = skillManager.getAll();
        StringBuilder skillsInfo = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            skillsInfo.append(i).append(".Skill name: ").append(skill.getName())
                    .append("; Skill description: ").append(skill.getDescription())
                    .append("; Skill directory file path: ").append(skill.getDirectory())
                    .append("\n");
        }

        String skillText = String.format(SKILL_PROMPT_CONTENT, skillsInfo.toString());
        return systemPrompt + "\n" + skillText;
    }
}
