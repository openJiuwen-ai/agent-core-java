/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.evolution.EvolutionRail;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Public class SkillCreateRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SkillCreateRail extends EvolutionRail {
  private final String skillsDir;
  private final String language;
  private final boolean isAutoTrigger;
  private final int toolCallThreshold;
  private final int toolDiversityThreshold;
  private DeepAgent owner;
  private boolean isProposalSent;

  /** Auto-generated for codecheck compliance. */
  public SkillCreateRail(String skillsDir) {
    this(skillsDir, "cn", true, 10, 5);
  }

  /** Auto-generated for codecheck compliance. */
  public SkillCreateRail(
      String skillsDir,
      String language,
      boolean isAutoTrigger,
      int toolCallThreshold,
      int toolDiversityThreshold) {
    super(EvolutionTriggerPoint.NONE, false);
    this.skillsDir = skillsDir != null ? skillsDir : "skills";
    this.language = language != null ? language : "cn";
    this.isAutoTrigger = isAutoTrigger;
    this.toolCallThreshold = toolCallThreshold;
    this.toolDiversityThreshold = toolDiversityThreshold;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 85;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      owner = deepAgent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void uninit(Object agent) {
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected void onBeforeInvoke(AgentCallbackContext ctx) {
    isProposalSent = false;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  protected void onAfterInvoke(AgentCallbackContext ctx) {
    proposeIfNeeded(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean proposeIfNeeded(AgentCallbackContext ctx) {
    if (!isAutoTrigger || isProposalSent || !shouldProposeNewSkill()) {
      return false;
    }
    DeepAgent agent =
        owner != null
            ? owner
            : (ctx != null && ctx.getAgent() instanceof DeepAgent deepAgent ? deepAgent : null);
    if (agent == null || agent.getLoopController() == null) {
      return false;
    }
    agent.getLoopController().enqueueFollowUp(buildFollowUpPrompt());
    isProposalSent = true;
    return true;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean shouldProposeNewSkill() {
    Set<String> unique = new HashSet<>(toolTrace());
    return toolTrace().size() >= toolCallThreshold && unique.size() >= toolDiversityThreshold;
  }

  /** Auto-generated for codecheck compliance. */
  public String buildFollowUpPrompt() {
    if ("en".equalsIgnoreCase(language)) {
      return "**Important: You MUST call the ask_user tool to confirm with the user first. Do not"
                 + " skip this step.**\n"
                 + "The system detected a reusable pattern that may be worth creating as a new"
                 + " skill. Please follow these steps:\n"
                 + "1. Use ask_user tool to confirm with the user:\n"
                 + "   - Question: \"I detected a pattern that may be worth creating as a new"
                 + " skill. Create it?\"\n"
                 + "   - Options: [\"Create\", \"Skip\", \"Custom instruction: (describe your"
                 + " needs)\"]\n"
                 + "2. If user chooses \"Create\" or provides a custom instruction, invoke the"
                 + " **skill-creator** skill to execute the skill creation.\n"
                 + "   Save the new skill to: "
          + skillsDir;
    }
    return "**重要：你必须先调用 ask_user 工具向用户确认，不可跳过此步骤。**\n"
        + "系统检测到对话中存在可复用模式，可能值得创建新技能。请按以下步骤执行：\n"
        + "1. 调用 ask_user 工具向用户确认：\n"
        + "   - 问题：\"我检测到您可能值得创建一个新技能。是否创建？\"\n"
        + "   - 选项：[\"创建\"，\"跳过\"，\"自定义指令：（请描述需求）\"]\n"
        + "2. 如果用户选择\"创建\"或提供了自定义指令，请调用 **skill-creator** 技能，"
        + "根据用户的要求和当前对话上下文执行技能创建。\n"
        + "   新技能应保存到技能目录："
        + skillsDir;
  }

  /** Auto-generated for codecheck compliance. */
  public String getSkillsDir() {
    return skillsDir;
  }

  /** Auto-generated for codecheck compliance. */
  public String getLanguage() {
    return language.toLowerCase(Locale.ROOT);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isAutoTrigger() {
    return isAutoTrigger;
  }

  /** Auto-generated for codecheck compliance. */
  public int getToolCallThreshold() {
    return toolCallThreshold;
  }

  /** Auto-generated for codecheck compliance. */
  public int getToolDiversityThreshold() {
    return toolDiversityThreshold;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean isProposalSent() {
    return isProposalSent;
  }
}
