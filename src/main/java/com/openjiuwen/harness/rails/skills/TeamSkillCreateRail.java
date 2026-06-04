/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.skills;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.DeepAgentRail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Rail for team skill creation in multi-agent scenarios.
 * <p>
 * Mirrors Python's {@code TeamSkillCreateRail} in
 * {@code openjiuwen.harness.rails.skills.team_skill_create_rail}.
 */
public class TeamSkillCreateRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(TeamSkillCreateRail.class);

    private static final String FOLLOW_UP_PROMPT_CN =
            "**重要：你必须先调用 ask_user 工具向用户确认，不可跳过此步骤。**\n"
                    + "系统检测到对话中 spawn 了多个团队成员，可能值得创建团队技能。请按以下步骤执行：\n"
                    + "1. 调用 ask_user 工具向用户确认：\n"
                    + "   - 问题：\"我检测到多 Agent 协作模式可能值得创建为团队技能。是否创建？\"\n"
                    + "   - 选项：[\"创建\"，\"跳过\"，\"自定义指令：（请描述需求）\"]\n"
                    + "2. 如果用户选择\"创建\"或提供了自定义指令，请调用 **team-skill-creator** 技能，"
                    + "根据用户的要求和当前对话上下文执行团队技能创建。\n"
                    + "   新技能应保存到技能目录：%s";

    private static final String FOLLOW_UP_PROMPT_EN =
            "**Important: You MUST call the ask_user tool to confirm with the user first. Do not skip this step.**\n"
                    + "The system detected multiple team member spawns that may be worth creating as a Team Skill. "
                    + "Please follow these steps:\n"
                    + "1. Use ask_user tool to confirm with the user:\n"
                    + "   - Question: \"I detected a multi-agent collaboration pattern that may be worth creating "
                    + "as a Team Skill. Create it?\"\n"
                    + "   - Options: [\"Create\", \"Skip\", \"Custom instruction: (describe your needs)\"]\n"
                    + "2. If user chooses \"Create\" or provides a custom instruction, invoke the **team-skill-creator** skill "
                    + "to execute the team skill creation.\n"
                    + "   Save the new skill to: %s";

    private final String skillsDir;
    private final String language;
    private final boolean autoTrigger;
    private final int minTeamMembers;
    private boolean proposalSent;
    private int spawnMemberCount;
    private Object builder;

    public TeamSkillCreateRail() {
        this("./skills");
    }

    public TeamSkillCreateRail(String skillsDir) {
        this(skillsDir, "cn", true, 2);
    }

    public TeamSkillCreateRail(Path skillsDir) {
        this(skillsDir != null ? skillsDir.toString() : "./skills");
    }

    public TeamSkillCreateRail(String skillsDir, String language, boolean autoTrigger, int minTeamMembers) {
        super();
        this.skillsDir = (skillsDir == null || skillsDir.isBlank()) ? "./skills" : skillsDir;
        this.language = (language == null || language.isBlank()) ? "cn" : language;
        this.autoTrigger = autoTrigger;
        this.minTeamMembers = minTeamMembers;
        this.proposalSent = false;
        this.spawnMemberCount = 0;
        setPriority(85);
    }

    @Override
    public void init(Object agent) {
        LOG.info("[TeamSkillCreateRail] Initialized");
    }

    @Override
    public void uninit(Object agent) {
        LOG.info("[TeamSkillCreateRail] Uninitialized");
    }

    @Override
    public void beforeInvoke(AgentCallbackContext ctx) {
        proposalSent = false;
        spawnMemberCount = 0;
    }

    @Override
    public void afterToolCall(AgentCallbackContext ctx) {
        if (ctx != null && ctx.getInputs() instanceof ToolCallInputs inputs
                && inputs.getToolName() != null
                && inputs.getToolName().contains("spawn_member")) {
            spawnMemberCount++;
        }
    }

    @Override
    public void afterTaskIteration(AgentCallbackContext ctx) {
        if (!autoTrigger || proposalSent || !shouldProposeNewTeamSkill()) {
            return;
        }
        Object controller = SkillCreateRail.resolveLoopController(ctx != null ? ctx.getAgent() : null);
        if (controller == null) {
            LOG.warn("[TeamSkillCreateRail] team skill creation proposal dropped: no TaskLoopController available");
            return;
        }
        String prompt = ("en".equalsIgnoreCase(language) ? FOLLOW_UP_PROMPT_EN : FOLLOW_UP_PROMPT_CN).formatted(skillsDir);
        if (SkillCreateRail.enqueueFollowUp(controller, prompt)) {
            proposalSent = true;
            LOG.info("[TeamSkillCreateRail] follow_up enqueued successfully");
        }
    }

    public boolean shouldProposeNewTeamSkill() {
        int spawnCount = 0;
        SkillCreateRail helper = new SkillCreateRail(skillsDir);
        helper.setBuilder(builder);
        List<String> toolCalls = helper.collectToolCalls(builder);
        for (String toolName : toolCalls) {
            if (toolName != null && toolName.contains("spawn_member")) {
                spawnCount++;
            }
        }
        return Math.max(spawnCount, spawnMemberCount) >= minTeamMembers;
    }

    public String getSkillsDir() {
        return skillsDir;
    }

    public String getLanguage() {
        return language;
    }

    public boolean isAutoTrigger() {
        return autoTrigger;
    }

    public int getMinTeamMembers() {
        return minTeamMembers;
    }

    public boolean isProposalSent() {
        return proposalSent;
    }

    public int getSpawnMemberCount() {
        return spawnMemberCount;
    }

    public Object getBuilder() {
        return builder;
    }

    public void setBuilder(Object builder) {
        this.builder = builder;
    }
}
