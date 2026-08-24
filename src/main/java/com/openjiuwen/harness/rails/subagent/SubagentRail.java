/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.subagent;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.SessionToolsSection;
import com.openjiuwen.harness.prompts.sections.TaskToolSection;
import com.openjiuwen.harness.rails.CallbackContext;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.subagent.SessionTools;

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
    private SessionTools.SessionToolkit toolkit;
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
        if (agent == null || agent.deepConfig() == null || agent.getSubagents().isEmpty()) {
            return;
        }
        language = agent.deepConfig().getLanguage();
        availableAgentsDescription = buildAvailableAgentsDescription(new ArrayList<>(agent.getSubagents().values()));
        registeredToolNames.clear();
        if (enableAsyncSubagent) {
            toolkit = new EmptySessionToolkit();
            // DeepAgent.setSessionToolkit only accepts harness.tools.SessionToolkit.
            agent.setSessionToolkit(new com.openjiuwen.harness.tools.SessionToolkit());
            registeredToolNames.add("sessions_list");
            registeredToolNames.add("sessions_spawn");
            registeredToolNames.add("sessions_cancel");
        } else {
            toolkit = null;
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
        toolkit = null;
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

    public String buildAvailableAgentsDescription(List<?> subagents) {
        if (subagents == null || subagents.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (Object spec : subagents) {
            String name = extractAgentName(spec);
            String description = extractAgentDescription(spec);
            String tools = extractAgentTools(spec, name);
            lines.add("- " + name + ": " + description + " (Tools: " + tools + ")");
        }
        return String.join("\n", lines);
    }

    private String extractAgentName(Object spec) {
        if (spec instanceof DeepAgentConfig.SubAgentConfig subAgentSpec) {
            if (subAgentSpec.getAgentCard() != null && subAgentSpec.getAgentCard().getName() != null) {
                return subAgentSpec.getAgentCard().getName();
            }
            return "general-purpose";
        }
        if (spec instanceof DeepAgent agent && agent.getCard() != null && agent.getCard().getName() != null) {
            return agent.getCard().getName();
        }
        if (spec == null) {
            return "general-purpose";
        }
        return "general-purpose";
    }

    private String extractAgentDescription(Object spec) {
        if (spec instanceof DeepAgentConfig.SubAgentConfig subAgentSpec) {
            if (subAgentSpec.getAgentCard() != null && subAgentSpec.getAgentCard().getDescription() != null) {
                return subAgentSpec.getAgentCard().getDescription();
            }
            return "DeepAgent instance";
        }
        if (spec instanceof DeepAgent agent && agent.getCard() != null && agent.getCard().getDescription() != null) {
            return agent.getCard().getDescription();
        }
        if (spec == null) {
            return "DeepAgent instance";
        }
        return "DeepAgent instance";
    }

    private String extractAgentTools(Object spec, String agentName) {
        if (spec instanceof DeepAgentConfig.SubAgentConfig subAgentSpec
                && subAgentSpec.getTools() != null && !subAgentSpec.getTools().isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Tool tool : subAgentSpec.getTools()) {
                if (tool != null && tool.getCard() != null && tool.getCard().getName() != null) {
                    names.add(tool.getCard().getName());
                }
            }
            if (!names.isEmpty()) {
                return String.join(", ", names);
            }
        }
        if (spec instanceof DeepAgent agent && !agent.getTools().isEmpty()) {
            return String.join(", ", agent.getTools().keySet());
        }
        return KNOWN_AGENT_TOOLS.getOrDefault(agentName, "All tools");
    }

    private static final class EmptySessionToolkit implements SessionTools.SessionToolkit {
        @Override
        public List<SessionTools.SessionTaskRow> listTasks(Map<String, Object> kwargs) {
            return List.of();
        }

        @Override
        public Map<String, Object> cancelTask(String taskId, Map<String, Object> kwargs) {
            return Map.of("task_id", taskId == null ? "" : taskId, "status", "cancelled");
        }

        @Override
        public Map<String, Object> spawnTask(
                String title,
                String prompt,
                Map<String, Object> options,
                Map<String, Object> kwargs
        ) {
            return Map.of(
                    "title", title == null ? "" : title,
                    "prompt", prompt == null ? "" : prompt,
                    "status", "pending"
            );
        }
    }
}
