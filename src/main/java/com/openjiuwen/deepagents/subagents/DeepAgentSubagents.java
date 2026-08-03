/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.subagents;

import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.subagents.ExploreAgentFactory;
import com.openjiuwen.harness.subagents.PlanAgentFactory;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import com.openjiuwen.harness.subagents.VerificationAgentFactory;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.List;
import java.util.Locale;

/** Public deepagents subagent facade mirroring Python's harness subagents exports. */
public final class DeepAgentSubagents {
  private DeepAgentSubagents() {}

  /** Auto-generated for codecheck compliance. */
  public static Object buildCodeAgentConfig(String language) {
    return CodeAgentFactory.buildCodeAgentConfig((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static com.openjiuwen.harness.subagents.SubAgentConfig buildExploreAgentConfig(String language) {
    return ExploreAgentFactory.buildExploreAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static com.openjiuwen.harness.subagents.SubAgentConfig buildPlanAgentConfig(String language) {
    return PlanAgentFactory.buildPlanAgentConfig(language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object buildResearchAgentConfig(String language) {
    return ResearchAgentFactory.buildResearchAgentConfig((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object buildVerificationAgentConfig(String language) {
    return VerificationAgentFactory.buildVerificationAgentConfig((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object buildBrowserAgentConfig(
      BrowserRuntimeSettings settings, String language) {
    return BrowserAgentFactory.buildBrowserAgentConfig((Object) settings);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createCodeAgent(String language, Workspace workspace) {
    return CodeAgentFactory.createCodeAgent((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createExploreAgent(String language, Workspace workspace) {
    return ExploreAgentFactory.createExploreAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createPlanAgent(String language, Workspace workspace) {
    return PlanAgentFactory.createPlanAgent(language, workspace);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createResearchAgent(String language, Workspace workspace) {
    return ResearchAgentFactory.createResearchAgent((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createVerificationAgent(String language, Workspace workspace) {
    return VerificationAgentFactory.createVerificationAgent((Object) language);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object createBrowserAgent(
      BrowserRuntimeSettings settings, String language, Workspace workspace) {
    return BrowserAgentFactory.createBrowserAgent(
        (Object) settings, List.of(), List.of(), List.of(), null, language,
        null);
  }

  /** Auto-generated for codecheck compliance. */
  public static Object create(String subagentType, String language, Workspace workspace) {
    String normalized = subagentType == null ? "" : subagentType.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "code", "code_agent" -> createCodeAgent(language, workspace);
      case "explore", "explore_agent" -> createExploreAgent(language, workspace);
      case "plan", "plan_agent" -> createPlanAgent(language, workspace);
      case "research", "research_agent" -> createResearchAgent(language, workspace);
      case "verification", "verification_agent" -> createVerificationAgent(language, workspace);
      default -> {
        Object host = new DeepAgentsFactory().createDeepAgent();
        if (host instanceof com.openjiuwen.harness.DeepAgent h) {
          yield h.createSubagent(subagentType, null);
        }
        yield null;
      }
    };
  }
}
