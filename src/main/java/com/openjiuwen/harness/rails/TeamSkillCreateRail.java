/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;

import java.util.Locale;

/**
 * Public class TeamSkillCreateRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class TeamSkillCreateRail extends EvolutionRail {
    private final String skillsDir;
    private final String language;
    private final boolean isAutoTrigger;
    private final int minTeamMembersForCreate;
    private DeepAgent owner;
    private boolean isProposalSent;

    /**
     * TeamSkillCreateRail.
     * 
     * @param skillsDir skillsDir
     * @since 0.1.7
     */
    public TeamSkillCreateRail(String skillsDir) {
        this(skillsDir, "cn", true, 2);
    }

    /**
     * TeamSkillCreateRail.
     * 
     * @param skillsDir skillsDir
     * @param language language
     * @param isAutoTrigger isAutoTrigger
     * @param minTeamMembersForCreate minTeamMembersForCreate
     * @since 0.1.7
     */
    public TeamSkillCreateRail(String skillsDir, String language, boolean isAutoTrigger, int minTeamMembersForCreate) {
        super(EvolutionTriggerPoint.NONE, false);
        this.skillsDir = skillsDir != null ? skillsDir : "skills";
        this.language = language != null ? language : "cn";
        this.isAutoTrigger = isAutoTrigger;
        this.minTeamMembersForCreate = minTeamMembersForCreate;
    }

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 85;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            owner = deepAgent;
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        owner = null;
    }

    /**
     * onBeforeInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    protected void onBeforeInvoke(AgentCallbackContext ctx) {
        isProposalSent = false;
    }

    /**
     * onAfterInvoke.
     * 
     * @param ctx ctx
     * @since 0.1.7
     */
    @Override
    protected void onAfterInvoke(AgentCallbackContext ctx) {
        proposeIfNeeded(ctx);
    }

    /**
     * proposeIfNeeded.
     * 
     * @param ctx ctx
     * @return the result
     * @since 0.1.7
     */
    public boolean proposeIfNeeded(AgentCallbackContext ctx) {
        if (!isAutoTrigger || isProposalSent || !shouldProposeNewTeamSkill()) {
            return false;
        }
        DeepAgent agent =
            owner != null ? owner : (ctx != null && ctx.getAgent() instanceof DeepAgent deepAgent ? deepAgent : null);
        if (agent == null || agent.getLoopController() == null) {
            return false;
        }
        agent.getLoopController().enqueueFollowUp(buildFollowUpPrompt());
        isProposalSent = true;
        return true;
    }

    /**
     * shouldProposeNewTeamSkill.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean shouldProposeNewTeamSkill() {
        long spawnCount = toolTrace().stream().filter(name -> name.contains("spawn_member")).count();
        return spawnCount >= minTeamMembersForCreate;
    }

    /**
     * buildFollowUpPrompt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String buildFollowUpPrompt() {
        if ("en".equalsIgnoreCase(language)) {
            return "**Important: You MUST call the ask_user tool to confirm with the user first. "
                    + "Do not skip this step.**\n"
                    + "The system detected multiple team member spawns that may be worth creating as a Team Skill. "
                    + "Please follow these steps:\n" + "1. Use ask_user tool to confirm with the user:\n"
                    + "   - Question: \"I detected a multi-agent collaboration pattern that may be worth creating "
                    + "as a Team Skill. Create it?\"\n"
                    + "   - Options: [\"Create\", \"Skip\", \"Custom instruction: (describe your needs)\"]\n"
                    + "2. If user chooses \"Create\" or provides a custom instruction, "
                    + "invoke the **team-skill-creator** skill " + "to execute the team skill creation.\n"
                    + "   Save the new skill to: " + skillsDir;
        }
        return "**重要：你必须先调用 ask_user 工具向用户确认，不可跳过此步骤。**\n" + "系统检测到对话中 spawn 了多个团队成员，可能值得创建团队技能。请按以下步骤执行：\n"
                + "1. 调用 ask_user 工具向用户确认：\n" + "   - 问题：\"我检测到多 Agent 协作模式可能值得创建为团队技能。是否创建？\"\n"
                + "   - 选项：[\"创建\"，\"跳过\"，\"自定义指令：（请描述需求）\"]\n"
                + "2. 如果用户选择\"创建\"或提供了自定义指令，请调用 **team-skill-creator** 技能，" + "根据用户的要求和当前对话上下文执行团队技能创建。\n"
                + "   新技能应保存到技能目录：" + skillsDir;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language.toLowerCase(Locale.ROOT);
    }

    /**
     * getSkillsDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSkillsDir() {
        return skillsDir;
    }

    /**
     * isAutoTrigger.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAutoTrigger() {
        return isAutoTrigger;
    }

    /**
     * getMinTeamMembersForCreate.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getMinTeamMembersForCreate() {
        return minTeamMembersForCreate;
    }

    /**
     * isProposalSent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isProposalSent() {
        return isProposalSent;
    }
}
