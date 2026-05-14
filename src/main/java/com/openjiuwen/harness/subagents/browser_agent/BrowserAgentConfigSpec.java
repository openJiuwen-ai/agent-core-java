package com.openjiuwen.harness.subagents.browser_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's browser-agent SubAgentConfig payload in
 * {@code openjiuwen.harness.subagents.browser_agent}.
 */
public record BrowserAgentConfigSpec(
        AgentCard agentCard,
        String systemPrompt,
        List<Tool> tools,
        List<AgentRail> rails,
        Model model,
        boolean enableTaskLoop,
        int maxIterations,
        String factoryName,
        Map<String, Object> factoryKwargs
) {
    public BrowserAgentConfigSpec {
        tools = tools != null ? new ArrayList<>(tools) : new ArrayList<>();
        rails = rails != null ? new ArrayList<>(rails) : new ArrayList<>();
        factoryKwargs = factoryKwargs != null ? Map.copyOf(factoryKwargs) : Map.of();
    }

    public RuntimeSettings settings() {
        Object raw = factoryKwargs.get("settings");
        return raw instanceof RuntimeSettings value ? value : null;
    }
}
