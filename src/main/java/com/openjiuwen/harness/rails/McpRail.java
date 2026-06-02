/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.resourcemanager.ResourceMgr;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.tools.ListMcpResourcesTool;
import com.openjiuwen.harness.tools.ReadMcpResourceTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Rail that exposes MCP resource listing and reading as agent tools.
 * <p>
 * Mirrors Python's {@code McpRail} in
 * {@code openjiuwen.harness.rails.mcp_rail}.
 */
public class McpRail extends DeepAgentRail {

    private static final Logger LOG = LoggerFactory.getLogger(McpRail.class);

    /** Priority for rail ordering. Mirrors Python's priority = 95. */
    public static final int PRIORITY = 95;

    /** MCP tools registered by this rail. Mirrors Python's self.tools. */
    private List<Object> mcpTools;

    public McpRail() {
        super();
        // Set priority to 95 (mirrors Python)
        setPriority(PRIORITY);
        // Initialize tools as null (mirrors Python's self.tools = None)
        this.mcpTools = null;
    }

    /**
     * Get the MCP tools registered by this rail.
     * @return the list of MCP tools, or null if not initialized
     */
    public List<Object> getMcpTools() {
        return mcpTools;
    }

    /**
     * Get the static priority value for this rail class.
     * @return the priority value (95)
     */
    public static int getStaticPriority() {
        return PRIORITY;
    }

    @Override
    public void init(Object agent) {
        // Extract language and agentId from agent (mirrors Python)
        String lang = "cn";
        String agentId = null;

        try {
            Object builder = agent.getClass().getMethod("getSystemPromptBuilder").invoke(agent);
            if (builder != null) {
                lang = (String) builder.getClass().getMethod("getLanguage").invoke(builder);
            }
        } catch (Exception e) {
            LOG.debug("Could not extract language from agent, defaulting to cn");
        }

        try {
            Object card = agent.getClass().getMethod("getCard").invoke(agent);
            if (card != null) {
                agentId = (String) card.getClass().getMethod("getId").invoke(card);
            }
        } catch (Exception e) {
            LOG.debug("Could not extract agentId from agent");
        }

        // Create MCP tools (mirrors Python: ListMcpResourcesTool(lang, agent_id))
        ListMcpResourcesTool listTool = new ListMcpResourcesTool(lang, agentId);
        ReadMcpResourceTool readTool = new ReadMcpResourceTool(lang, agentId);

        this.mcpTools = new ArrayList<>();
        this.mcpTools.add(listTool);
        this.mcpTools.add(readTool);

        // Register tools with Runner.resource_mgr (mirrors Python)
        try {
            ResourceMgr resourceMgr = Runner.resourceMgr();
            if (resourceMgr != null) {
                // In Python: Runner.resource_mgr.add_tool(self.tools)
                // In Java: we add each tool individually
                for (Object tool : this.mcpTools) {
                    if (tool instanceof com.openjiuwen.core.foundation.tool.Tool) {
                        resourceMgr.addTool((com.openjiuwen.core.foundation.tool.Tool) tool, null);
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not register tools with resource_mgr: {}", e.getMessage());
        }

        // Add tool cards to agent.ability_manager (mirrors Python)
        try {
            Object abilityManager = agent.getClass().getMethod("getAbilityManager").invoke(agent);
            if (abilityManager != null) {
                for (Object tool : this.mcpTools) {
                    try {
                        Object toolCard = tool.getClass().getMethod("getCard").invoke(tool);
                        abilityManager.getClass().getMethod("add", Object.class).invoke(abilityManager, toolCard);
                    } catch (Exception ex) {
                        LOG.debug("Could not add tool card to ability_manager");
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not access ability_manager: {}", e.getMessage());
        }

        LOG.info("[McpRail] Registered MCP resource tools");
    }

    @Override
    public void uninit(Object agent) {
        if (this.mcpTools == null) {
            return;
        }

        // Remove tool cards from agent.ability_manager (mirrors Python)
        try {
            Object abilityManager = agent.getClass().getMethod("getAbilityManager").invoke(agent);
            if (abilityManager != null) {
                for (Object tool : this.mcpTools) {
                    try {
                        Object toolCard = tool.getClass().getMethod("getCard").invoke(tool);
                        String name = (String) toolCard.getClass().getMethod("getName").invoke(toolCard);
                        if (name != null) {
                            abilityManager.getClass().getMethod("remove", String.class).invoke(abilityManager, name);
                        }
                    } catch (Exception ex) {
                        LOG.debug("Could not remove tool card from ability_manager");
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not access ability_manager for cleanup: {}", e.getMessage());
        }

        // Remove tools from Runner.resource_mgr (mirrors Python)
        try {
            ResourceMgr resourceMgr = Runner.resourceMgr();
            if (resourceMgr != null) {
                for (Object tool : this.mcpTools) {
                    try {
                        Object toolCard = tool.getClass().getMethod("getCard").invoke(tool);
                        String toolId = (String) toolCard.getClass().getMethod("getId").invoke(toolCard);
                        if (toolId != null) {
                            // removeTool(Object toolId, Object tag, TagMatchStrategy, boolean)
                            resourceMgr.removeTool(toolId, null, TagMatchStrategy.ALL, false);
                        }
                    } catch (Exception ex) {
                        LOG.debug("Could not remove tool from resource_mgr");
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not access resource_mgr for cleanup: {}", e.getMessage());
        }

        LOG.info("[McpRail] Unregistered MCP resource tools");
        this.mcpTools = null;
    }
}
