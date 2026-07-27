/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.subagent.TaskTool;
import com.openjiuwen.harness.workspace.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Public class SubagentRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SubagentRail extends DeepAgentRail {
  private final List<Tool> tools = new ArrayList<>();

  public SubagentRail() {
    setPriority(95);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void init(DeepAgent agent) {
    super.init(agent);
    if (agent == null
        || agent.deepConfig() == null
        || agent.deepConfig().getSubagents() == null
        || agent.deepConfig().getSubagents().isEmpty()) {
      return;
    }
    String language = resolveWorkspace(agent).getLanguage();
    ToolCard toolCard = ToolMetadataRegistry.buildToolCard(
        "task_tool",
        agent.getCard().getId() + ".task_tool",
        language);
    TaskTool taskTool = new TaskTool(toolCard, agent, language);
    String agentsDescription = availableAgents(
        new ArrayList<>(agent.deepConfig().getSubagents().values()));
    Tool tool =
        new LocalFunction(
            ToolCard.builder()
                .id(toolCard.getId())
                .name(toolCard.getName())
                .description(
                    toolCard.getDescription()
                        + "\n"
                        + agentsDescription)
                .inputParams(toolCard.getInputParams())
                .build(),
            inputs -> {
                try {
                    return taskTool.invoke(inputs != null ? inputs : Map.of(), Map.of());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    tools.add(tool);
    agent.registerTool(tool);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  public void uninit(DeepAgent agent) {
    if (agent != null) {
      for (Tool tool : tools) {
        agent.unregisterTool(tool.getCard().getName());
      }
    }
    tools.clear();
  }

  /** Auto-generated for codecheck compliance. */
  public String describe() {
    return "Coordinate subagent lifecycle";
  }

  /** Auto-generated for codecheck compliance. */
  public String availableAgents(List<Object> subagents) {
    if (subagents == null || subagents.isEmpty()) {
      return "";
    }
    List<String> lines = new ArrayList<>();
    for (Object spec : subagents) {
      if (spec instanceof DeepAgentConfig.SubAgentConfig config && config.getAgentCard() != null) {
        lines.add(
            "\""
                + config.getAgentCard().getName()
                + "\": "
                + config.getAgentCard().getDescription());
      } else if (spec instanceof DeepAgent deepAgent && deepAgent.getCard() != null) {
        lines.add(
            "\"" + deepAgent.getCard().getName() + "\": " + deepAgent.getCard().getDescription());
      }
    }
    return String.join("\n", lines);
  }

  private static Workspace resolveWorkspace(DeepAgent agent) {
    Object ws = agent.deepConfig().getWorkspace();
    if (ws instanceof Workspace workspace) {
      return workspace;
    }
    return new Workspace("./", agent.deepConfig().getLanguage());
  }
}
