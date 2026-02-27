// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Utility class for managing and working with skills.
 *
 * <p>This class provides a high-level interface for skill registration, tool management,
 * and prompt generation. It combines SkillManager, SkillToolKit, and RemoteSkillUtil functionalities.
 *
 * <p>Python reference: {@code agent-core/openjiuwen/core/single_agent/skills/skill_util.py::SkillUtil}
 *
 * @since 0.1.4
 */
public class SkillUtil {

    private static final Logger log = LoggerFactory.getLogger(SkillUtil.class);

    /**
     * The skill prompt template content.
     */
    private static final String SKILL_PROMPT_CONTENT = """
            To help you better complete tasks, the following skill knowledge is equipped:
            {{skills}}
            You can use the view_file tool to read the corresponding SKILL.md file to obtain the relevant skill.
            """;

    /**
     * The system prompt prefix for skills.
     */
    private static final String SYSTEM_PROMPT_PREFIX = """
            You are an agent equipped with various skills to solve problems.
            Before attempting any task, read the relevant skill document (SKILL.md)
            using view_file and follow its workflow.
            """;

    private final SkillManager skillManager;

    private final SkillToolKit skillToolKit;

    private final RemoteSkillUtil remoteSkillUtil;

    /**
     * Constructs a SkillUtil with the specified system operation ID.
     *
     * @param sysOperationId the system operation ID used for file and code operations
     */
    public SkillUtil(String sysOperationId) {
        this.skillManager = new SkillManager(sysOperationId);
        this.skillToolKit = new SkillToolKit(sysOperationId);
        this.remoteSkillUtil = new RemoteSkillUtil(sysOperationId);
    }

    /**
     * Sets the system operation ID for all components.
     *
     * @param sysOperationId the new system operation ID
     */
    public void setSysOperationId(String sysOperationId) {
        skillManager.setSysOperationId(sysOperationId);
        skillToolKit.setSysOperationId(sysOperationId);
        remoteSkillUtil.setSysOperationId(sysOperationId);
    }

    /**
     * Registers skills and adds skill tools to an agent.
     *
     * <p>This method registers the skill at the given path and prepares all skill-related
     * tools (view_file, execute_code, run_command).
     *
     * @param skillPath the path to the skill directory to register
     * @param overwrite if true, overwrite existing skills
     * @return a CompletableFuture that completes when registration is done
     */
    public CompletableFuture<Void> registerSkills(String skillPath, boolean overwrite) {
        return CompletableFuture.runAsync(() -> {
            skillManager.register(Path.of(skillPath), overwrite);
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Registers skills without overwriting existing ones.
     *
     * @param skillPath the path to the skill directory to register
     * @return a CompletableFuture that completes when registration is done
     */
    public CompletableFuture<Void> registerSkills(String skillPath) {
        return registerSkills(skillPath, false);
    }

    /**
     * Registers skills from multiple paths.
     *
     * @param skillPaths the list of paths to skill directories
     * @param overwrite  if true, overwrite existing skills
     * @return a CompletableFuture that completes when registration is done
     */
    public CompletableFuture<Void> registerSkills(List<String> skillPaths, boolean overwrite) {
        return CompletableFuture.runAsync(() -> {
            List<Path> paths = skillPaths.stream()
                    .map(Path::of)
                    .toList();
            skillManager.register(paths, overwrite);
        }, java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Registers remote skills from a GitHub repository.
     *
     * @param skillsDir the local directory to save skills
     * @param tree      the GitHub tree to download from
     * @param token     the GitHub API token (optional)
     * @return a CompletableFuture containing the list of skill paths
     */
    public CompletableFuture<List<Path>> registerRemoteSkills(
            String skillsDir,
            GitHubTree tree,
            String token) {

        return remoteSkillUtil.uploadSkillFromGitHub(tree, skillsDir, token);
    }

    /**
     * Registers remote skills from a GitHub repository without a token.
     *
     * @param skillsDir the local directory to save skills
     * @param tree      the GitHub tree to download from
     * @return a CompletableFuture containing the list of skill paths
     */
    public CompletableFuture<List<Path>> registerRemoteSkills(String skillsDir, GitHubTree tree) {
        return registerRemoteSkills(skillsDir, tree, null);
    }

    /**
     * Checks if any skills are registered.
     *
     * @return true if at least one skill is registered, false otherwise
     */
    public boolean hasSkill() {
        return skillManager.count() > 0;
    }

    /**
     * Gets a skill by name.
     *
     * @param name the name of the skill
     * @return the skill, or null if not found
     */
    public Skill getSkill(String name) {
        return skillManager.get(name);
    }

    /**
     * Gets all registered skills.
     *
     * @return a list of all registered skills
     */
    public List<Skill> getAllSkills() {
        return skillManager.getAll();
    }

    /**
     * Gets all registered skill names.
     *
     * @return a list of all registered skill names
     */
    public List<String> getSkillNames() {
        return skillManager.getNames();
    }

    /**
     * Unregisters a skill by name.
     *
     * @param name the name of the skill to unregister
     */
    public void unregisterSkill(String name) {
        skillManager.unregister(name);
    }

    /**
     * Clears all registered skills.
     */
    public void clearSkills() {
        skillManager.clear();
    }

    /**
     * Gets the number of registered skills.
     *
     * @return the number of registered skills
     */
    public int getSkillCount() {
        return skillManager.count();
    }

    /**
     * Generates a formatted prompt string containing information about all registered skills.
     *
     * <p>The prompt includes:
     * <ul>
     *   <li>System instructions for using skills</li>
     *   <li>List of available skills with names, descriptions, and directories</li>
     * </ul>
     *
     * @return a formatted prompt string with skill information
     */
    public String getSkillPrompt() {
        List<Skill> skills = skillManager.getAll();

        if (skills.isEmpty()) {
            return SYSTEM_PROMPT_PREFIX;
        }

        StringBuilder skillsInfo = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            Skill skill = skills.get(i);
            if (i > 0) {
                skillsInfo.append("\n");
            }
            skillsInfo.append(String.format(
                    "%d. Skill name: %s; Skill description: %s; Skill directory file path: %s",
                    i + 1,
                    skill.getName(),
                    skill.getDescription(),
                    skill.getDirectory()
            ));
        }

        // Replace {{skills}} placeholder
        String skillText = SKILL_PROMPT_CONTENT.replace("{{skills}}", skillsInfo.toString());

        return SYSTEM_PROMPT_PREFIX + "\n" + skillText;
    }

    /**
     * Gets the view file tool.
     *
     * @return the view file tool function
     */
    public SkillToolKit.ToolFunction getViewFileTool() {
        return skillToolKit.createViewFileTool();
    }

    /**
     * Gets the execute code tool.
     *
     * @return the execute code tool function
     */
    public SkillToolKit.ToolFunction getExecuteCodeTool() {
        return skillToolKit.createExecuteCodeTool();
    }

    /**
     * Gets the run command tool.
     *
     * @return the run command tool function
     */
    public SkillToolKit.ToolFunction getRunCommandTool() {
        return skillToolKit.createRunCommandTool();
    }

    /**
     * Gets all skill tools.
     *
     * @return a list of all skill tool functions
     */
    public List<SkillToolKit.ToolFunction> getAllTools() {
        return List.of(
                getViewFileTool(),
                getExecuteCodeTool(),
                getRunCommandTool()
        );
    }

    /**
     * Gets the skill manager.
     *
     * @return the skill manager
     */
    public SkillManager getSkillManager() {
        return skillManager;
    }

    /**
     * Gets the skill tool kit.
     *
     * @return the skill tool kit
     */
    public SkillToolKit getSkillToolKit() {
        return skillToolKit;
    }

    /**
     * Gets the remote skill util.
     *
     * @return the remote skill util
     */
    public RemoteSkillUtil getRemoteSkillUtil() {
        return remoteSkillUtil;
    }
}
