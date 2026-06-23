/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import java.io.IOException;
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
    public static final String SKILL_PROMPT_CONTENT = """
            To help you better complete tasks, the following skill knowledge is equipped:
            {{skills}}
            You can use the read_file tool to read the corresponding SKILL.md file to obtain the relevant skill.
            """;

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

    public boolean registerSkills(List<String> skillPaths, Object agent, String sessionId) throws IOException {
        List<Path> paths = skillPaths == null ? List.of() : skillPaths.stream().map(Path::of).toList();
        skillManager.register(paths, false);
        return true;
    }

    public boolean register_skills(List<String> skillPaths, Object agent, String sessionId) throws IOException {
        return registerSkills(skillPaths, agent, sessionId);
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

    public String getSkillPrompt() {
        String systemPrompt = "You are an agent equipped with various skills to solve problems.\n"
                + "Before attempting any task, read the relevant skill document (SKILL.md) "
                + "using read_file and follow its workflow.\n";
        List<String> skillsInfo = new ArrayList<>();
        List<Skill> skills = skillManager.getAll();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            skillsInfo.add(i + ".Skill name: " + skill.getName()
                    + "; Skill description: " + skill.getDescription()
                    + "; Skill directory: " + skill.getDirectory());
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("skills", skillsInfo.stream().collect(Collectors.joining("\n")));
        PromptTemplate template = PromptTemplate.builder().content(SKILL_PROMPT_CONTENT).build();
        return systemPrompt + "\n" + template.format(values).getContent();
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
