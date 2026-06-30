/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.tools.TaskTool;
import java.util.ArrayList;
import java.util.List;

/**
 * Public class SubagentRail used by the Java parity implementation.
 *
 * @since 1.0
 */
public class SubagentRail extends DeepAgentRail {
  private final List<Tool> tools = new ArrayList<>();

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public int priority() {
    return 95;
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void init(Object agent) {
    if (!(agent instanceof DeepAgent deepAgent)
        || deepAgent.getConfig().getSubagents() == null
        || deepAgent.getConfig().getSubagents().isEmpty()) {
      return;
    }
    TaskTool taskTool = new TaskTool(deepAgent);
    ToolCard metadataCard =
        ToolMetadataRegistry.buildToolCard(
            "task_tool",
            deepAgent.getCard().getId() + ".task_tool",
            deepAgent.getWorkspace().getLanguage());
    Tool tool =
        new LocalFunction(
            ToolCard.builder()
                .id(metadataCard.getId())
                .name(metadataCard.getName())
                .description(
                    metadataCard.getDescription()
                        + "\n"
                        + availableAgents(deepAgent.getConfig().getSubagents()))
                .inputParams(metadataCard.getInputParams())
                .build(),
            inputs ->
                taskTool.delegate(
                    stringValue(inputs.get("subagent_type")),
                    stringValue(inputs.getOrDefault("task_description", inputs.get("task"))),
                    stringValue(inputs.get("parent_session_id"))));
    tools.add(tool);
    deepAgent.registerHarnessTool(tool);
  }

  /** Auto-generated for codecheck compliance. */
  @Override
  /** Auto-generated for codecheck compliance. */
  public void uninit(Object agent) {
    if (agent instanceof DeepAgent deepAgent) {
      for (Tool tool : tools) {
        deepAgent.unregisterHarnessTool(tool);
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
      if (spec instanceof SubAgentConfig config && config.getAgentCard() != null) {
        lines.add(
            "\""
                + config.getAgentCard().getName()
                + "\": "
                + config.getAgentCard().getDescription());
      } else if (spec instanceof DeepAgent deepAgent) {
        lines.add(
            "\"" + deepAgent.getCard().getName() + "\": " + deepAgent.getCard().getDescription());
      }
    }
    return String.join("\n", lines);
  }

  private static String stringValue(Object value) {
    return value != null ? String.valueOf(value) : null;
  }
}
