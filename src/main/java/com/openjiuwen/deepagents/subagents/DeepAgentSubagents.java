/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.subagents;

import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.subagents.ExploreAgentFactory;
import com.openjiuwen.harness.subagents.PlanAgentFactory;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.subagents.VerificationAgentFactory;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.List;
import java.util.Locale;

/** Public deepagents subagent facade mirroring Python's harness subagents exports. */
public final class DeepAgentSubagents {
  private DeepAgentSubagents() {}

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildCodeAgentConfig(String language) {
    return CodeAgentFactory.buildCodeAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildExploreAgentConfig(String language) {
    return ExploreAgentFactory.buildExploreAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildPlanAgentConfig(String language) {
    return PlanAgentFactory.buildPlanAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildResearchAgentConfig(String language) {
    return ResearchAgentFactory.buildResearchAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildVerificationAgentConfig(String language) {
    return VerificationAgentFactory.buildVerificationAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static SubAgentConfig buildBrowserAgentConfig(
      BrowserRuntimeSettings settings, String language) {
    return BrowserAgentFactory.buildBrowserAgentConfig(settings, language);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createCodeAgent(String language, Workspace workspace) {
    return CodeAgentFactory.createCodeAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createExploreAgent(String language, Workspace workspace) {
    return ExploreAgentFactory.createExploreAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createPlanAgent(String language, Workspace workspace) {
    return PlanAgentFactory.createPlanAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createResearchAgent(String language, Workspace workspace) {
    return ResearchAgentFactory.createResearchAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createVerificationAgent(String language, Workspace workspace) {
    return VerificationAgentFactory.createVerificationAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent createBrowserAgent(
      BrowserRuntimeSettings settings, String language, Workspace workspace) {
    return BrowserAgentFactory.createBrowserAgent(
        settings, language, workspace, List.of(), List.of());
  }

  /** Auto-generated for codecheck compliance. */
  public static DeepAgent create(String subagentType, String language, Workspace workspace) {
    String normalized = subagentType == null ? "" : subagentType.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "code", "code_agent" -> createCodeAgent(language, workspace);
      case "explore", "explore_agent" -> createExploreAgent(language, workspace);
      case "plan", "plan_agent" -> createPlanAgent(language, workspace);
      case "research", "research_agent" -> createResearchAgent(language, workspace);
      case "verification", "verification_agent" -> createVerificationAgent(language, workspace);
      default -> {
        DeepAgent host = new DeepAgentsFactory().createDeepAgent();
        host.getConfig().setLanguage(language != null ? language : host.getConfig().getLanguage());
        host.getConfig()
            .setWorkspacePath(
                workspace != null
                    ? workspace.root().toString()
                    : host.getConfig().getWorkspacePath());
        yield host.createSubagent(subagentType, null);
      }
    };
  }
}
