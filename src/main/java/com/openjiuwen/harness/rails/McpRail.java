/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

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

    private static final int PRIORITY = 95;

    private List<Object> tools;

    public McpRail() {
        super();
        this.tools = null;
    }

    @Override
    public void init(Object agent) {
        // Extract language and agentId from agent
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

        ListMcpResourcesTool listTool = new ListMcpResourcesTool();
        ReadMcpResourceTool readTool = new ReadMcpResourceTool();

        this.tools = new ArrayList<>();
        this.tools.add(listTool);
        this.tools.add(readTool);

        LOG.info("[McpRail] Registered MCP resource tools");
    }

    @Override
    public void uninit(Object agent) {
        if (this.tools == null) {
            return;
        }
        LOG.info("[McpRail] Unregistered MCP resource tools");
        this.tools = null;
    }
}
