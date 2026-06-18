/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SessionToolsSection;
import com.openjiuwen.harness.prompts.sections.TaskToolSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rail that registers task or session delegation metadata for subagents.
 *
 * <p>Mirrors Python's {@code SubagentRail} in
 * {@code openjiuwen/harness/rails/subagent/subagent_rail.py}.</p>
 */
public class SubagentRail extends DeepAgentRail {

    private static final Map<String, String> KNOWN_AGENT_TOOLS = Map.of(
            "explore_agent", "bash, glob, grep, list_files, read_file",
            "plan_agent", "bash, glob, grep, list_files, read_file"
    );

    private final boolean enableAsyncSubagent;
    private final Set<String> registeredToolNames = new LinkedHashSet<>();
    private String availableAgentsDescription = "";
    private String language = "cn";

    public SubagentRail() {
        this(false);
    }

    public SubagentRail(boolean enableAsyncSubagent) {
        setPriority(95);
        this.enableAsyncSubagent = enableAsyncSubagent;
    }

    @Override
    public void init(DeepAgent agent) {
        super.init(agent);
        if (agent == null || agent.deepConfig() == null || agent.deepConfig().getSubagents().isEmpty()) {
            return;
        }
        language = agent.deepConfig().getLanguage();
        availableAgentsDescription = buildAvailableAgentsDescription(new ArrayList<>(agent.deepConfig().getSubagents().values()));
        registeredToolNames.clear();
        if (enableAsyncSubagent) {
            registeredToolNames.add("sessions_spawn");
            registeredToolNames.add("sessions_status");
            registeredToolNames.add("sessions_cancel");
        } else {
            registeredToolNames.add("task_tool");
        }
    }

    @Override
    public void uninit(DeepAgent agent) {
        registeredToolNames.clear();
        availableAgentsDescription = "";
        if (agent != null && enableAsyncSubagent) {
            agent.setSessionToolkit(null);
        }
    }

    @Override
    public void beforeModelCall(CallbackContext ctx) {
        if (registeredToolNames.isEmpty()) {
            return;
        }
        String resolvedLanguage = String.valueOf(ctx.getValues().getOrDefault("language", language));
        if (enableAsyncSubagent) {
            ctx.put("session_tools_section", SessionToolsSection.buildSessionToolsSection(resolvedLanguage));
        } else {
            ctx.put("task_tool_section", TaskToolSection.buildTaskSection(resolvedLanguage));
        }
        ctx.put("available_agents", availableAgentsDescription);
        ctx.put("subagent_tool_names", new ArrayList<>(registeredToolNames));
    }

    public boolean isEnableAsyncSubagent() {
        return enableAsyncSubagent;
    }

    public Set<String> getRegisteredToolNames() {
        return new LinkedHashSet<>(registeredToolNames);
    }

    public String getAvailableAgentsDescription() {
        return availableAgentsDescription;
    }

    public String buildAvailableAgentsDescription(List<DeepAgentConfig.SubAgentConfig> subagents) {
        if (subagents == null || subagents.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (DeepAgentConfig.SubAgentConfig spec : subagents) {
            String name = extractAgentName(spec);
            String description = extractAgentDescription(spec);
            String tools = extractAgentTools(spec, name);
            lines.add("- " + name + ": " + description + " (Tools: " + tools + ")");
        }
        return String.join("\n", lines);
    }

    private String extractAgentName(DeepAgentConfig.SubAgentConfig spec) {
        if (spec == null || spec.getAgentCard() == null || spec.getAgentCard().getName() == null) {
            return "general-purpose";
        }
        return spec.getAgentCard().getName();
    }

    private String extractAgentDescription(DeepAgentConfig.SubAgentConfig spec) {
        if (spec == null || spec.getAgentCard() == null || spec.getAgentCard().getDescription() == null) {
            return "DeepAgent instance";
        }
        return spec.getAgentCard().getDescription();
    }

    private String extractAgentTools(DeepAgentConfig.SubAgentConfig spec, String agentName) {
        if (spec != null && spec.getTools() != null && !spec.getTools().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Tool tool : spec.getTools()) {
                if (tool != null && tool.getCard() != null && tool.getCard().getName() != null) {
                    names.add(tool.getCard().getName());
                }
            }
            if (!names.isEmpty()) {
                return String.join(", ", names);
            }
        }
        return KNOWN_AGENT_TOOLS.getOrDefault(agentName, "All tools");
    }
}
