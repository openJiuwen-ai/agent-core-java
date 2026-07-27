/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.agent_evolving.trajectory.InMemoryTrajectoryStore;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.evolution.EvolutionTriggerPoint;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Public class SkillCreateRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SkillCreateRail extends com.openjiuwen.harness.rails.evolution.EvolutionRail {
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
    super(new InMemoryTrajectoryStore(), true);
    setPriority(85);
    this.skillsDir = skillsDir != null ? skillsDir : "skills";
    this.language = language != null ? language : "cn";
    this.isAutoTrigger = isAutoTrigger;
    this.toolCallThreshold = toolCallThreshold;
    this.toolDiversityThreshold = toolDiversityThreshold;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    super.init(agent);
    if (agent != null) {
      owner = agent;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void uninit(DeepAgent agent) {
    super.uninit(agent);
    owner = null;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void beforeInvoke(CallbackContext ctx) {
    super.beforeInvoke(ctx);
    isProposalSent = false;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void afterTaskIteration(CallbackContext ctx) {
    super.afterTaskIteration(ctx);
    proposeIfNeeded(ctx);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean proposeIfNeeded(CallbackContext ctx) {
    if (!isAutoTrigger || isProposalSent || !shouldProposeNewSkill()) {
      return false;
    }
    DeepAgent agent = owner != null ? owner
        : (ctx != null && ctx.getAgent() != null ? ctx.getAgent() : null);
    if (agent == null) {
      return false;
    }
    String prompt = buildFollowUpPrompt();
    ctx.put("skill_create_follow_up", prompt);
    agent.loopController().enqueueFollowUp(prompt);
    isProposalSent = true;
    return true;
  }

  /** Auto-generated for codecheck compliance. */
  public boolean shouldProposeNewSkill() {
    Set<String> unique = new HashSet<>();
    int totalCalls = 0;
    for (Map<String, Object> step : buildTrajectory()) {
      Object values = step.get("values");
      if (!(values instanceof Map<?, ?> map)) {
        continue;
      }
      Object name = map.get("tool_name");
      if (name == null || String.valueOf(name).isBlank()) {
        continue;
      }
      totalCalls += 1;
      unique.add(String.valueOf(name));
    }
    return totalCalls >= toolCallThreshold && unique.size() >= toolDiversityThreshold;
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
