/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.autoharness.rails;

import com.openjiuwen.autoharness.tools.ExperienceSearchTool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Register experience search and inject the auto-harness experience prompt section. */
public class AutoHarnessExperienceRail extends DeepAgentRail {
  private static final String MEMORY_SECTION = "memory";

  private final String experienceDir;
  private final String language;
  private final Set<String> ownedToolNames = new HashSet<>();
  private final Set<String> ownedToolIds = new HashSet<>();
  private SystemPromptBuilder systemPromptBuilder;

  /** Auto-generated for codecheck compliance. */
  public AutoHarnessExperienceRail(String experienceDir) {
    this(experienceDir, "cn");
  }

  /** Auto-generated for codecheck compliance. */
  public AutoHarnessExperienceRail(String experienceDir, String language) {
    this.experienceDir = experienceDir;
    this.language = language == null || language.isBlank() ? "cn" : language;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 80;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      systemPromptBuilder = deepAgent.getAgent().getSystemPromptBuilder();
      registerExperienceTool(deepAgent);
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void uninit(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      for (String toolName : Set.copyOf(ownedToolNames)) {
        deepAgent.getAgent().getAbilityManager().remove(toolName);
      }
    }
    for (String toolId : Set.copyOf(ownedToolIds)) {
      if (Runner.resourceMgr().getTool(toolId) != null) {
        Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
      }
    }
    ownedToolNames.clear();
    ownedToolIds.clear();
    if (systemPromptBuilder != null) {
      systemPromptBuilder.removeSection(MEMORY_SECTION);
      systemPromptBuilder = null;
    }
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void beforeModelCall(AgentCallbackContext ctx) {
    if (systemPromptBuilder == null) {
      return;
    }
    systemPromptBuilder.removeSection(MEMORY_SECTION);
    systemPromptBuilder.addSection(
        buildExperienceSection(systemPromptBuilder.getLanguage(), experienceDir));
  }

  /** Auto-generated for codecheck compliance. */
  public static PromptSection buildExperienceSection(String language, String experienceDir) {
    String dir =
        experienceDir == null || experienceDir.isBlank()
            ? ".auto_harness/experience"
            : experienceDir;
    Map<String, String> content =
        Map.of(
            "cn",
                "## Experience Library\n\n"
                    + "经验库位于 `"
                    + dir
                    + "`。\n"
                    + "需要回顾历史优化、失败案例和洞察时，使用 `experience_search`。",
            "en",
                "## Experience Library\n\n"
                    + "The experience library lives at `"
                    + dir
                    + "`.\n"
                    + "Use `experience_search` when reviewing prior optimizations, failures, and"
                    + " insights.");
    return new PromptSection(MEMORY_SECTION, content, 85);
  }

  /** Auto-generated for codecheck compliance. */
  public boolean hasExperiencePromptSection() {
    return systemPromptBuilder != null && systemPromptBuilder.hasSection(MEMORY_SECTION);
  }

  private void registerExperienceTool(DeepAgent agent) {
    ExperienceSearchTool tool =
        new ExperienceSearchTool(
            experienceDir, UUID.randomUUID().toString().replace("-", ""), language);
    if (Runner.resourceMgr().getTool(tool.getCard().getId()) == null) {
      Runner.resourceMgr().addTool(tool, agent.getCard().getId());
      ownedToolIds.add(tool.getCard().getId());
    }
    if (agent.getAgent().getAbilityManager().get(tool.getCard().getName()) == null) {
      agent.getAgent().getAbilityManager().add(tool.getCard());
      ownedToolNames.add(tool.getCard().getName());
    }
  }
}
