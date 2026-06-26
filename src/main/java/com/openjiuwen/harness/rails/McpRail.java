/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.single_agent.AbilityManager;
import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.tools.ListMcpResourcesTool;
import com.openjiuwen.harness.tools.ReadMcpResourceTool;

import java.util.List;

/**
 * Tracks MCP registration state for a DeepAgent runtime.
 *
 * <p>Mirrors Python's {@code McpRail} in
 * {@code openjiuwen/harness/rails/mcp_rail.py}.</p>
 */
public class McpRail extends DeepAgentRail {

    public static final int PRIORITY = 95;

    private boolean registered;
    private List<Tool> tools;

    public McpRail() {
        setPriority(PRIORITY);
    }

    @Override
    public void init(DeepAgent agent) {
        init((Object) agent);
    }

    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent)) {
            return;
        }
        super.init(deepAgent);
        String language = deepAgent.deepConfig() == null ? "cn" : deepAgent.deepConfig().getLanguage();
        AgentCard card = deepAgent.getCard();
        String agentId = card == null ? null : card.getId();

        ListMcpResourcesTool listTool = new ListMcpResourcesTool(language, agentId);
        ReadMcpResourceTool readTool = new ReadMcpResourceTool(language, agentId);
        tools = List.of(listTool, readTool);

        Runner.resourceMgr().addTools(tools, null, true);
        AbilityManager abilityManager = deepAgent.getAbilityManager();
        if (abilityManager != null) {
            for (Tool tool : tools) {
                abilityManager.add(tool.getCard());
            }
        }
    }

    @Override
    public void uninit(DeepAgent agent) {
        uninit((Object) agent);
    }

    public void uninit(Object agent) {
        if (tools == null || tools.isEmpty()) {
            return;
        }
        AbilityManager abilityManager = agent instanceof DeepAgent deepAgent ? deepAgent.getAbilityManager() : null;
        for (Tool tool : tools) {
            if (tool == null || tool.getCard() == null) {
                continue;
            }
            String name = tool.getCard().getName();
            if (name != null && !name.isBlank() && abilityManager != null) {
                abilityManager.remove(name);
            }
            String toolId = tool.getCard().getId();
            if (toolId != null && !toolId.isBlank() && Runner.resourceMgr().getTool(toolId) != null) {
                Runner.resourceMgr().removeTool(toolId);
            }
        }
    }

    @Override
    public void beforeInvoke(CallbackContext ctx) {
        registered = true;
        ctx.put("mcp_registered", true);
    }

    @Override
    public void afterInvoke(CallbackContext ctx) {
        ctx.put("mcp_registered", registered);
    }

    public boolean isRegistered() {
        return registered;
    }

    public List<Tool> getTools() {
        return tools == null ? null : List.copyOf(tools);
    }
}
