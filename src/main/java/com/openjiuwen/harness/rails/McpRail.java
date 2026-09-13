/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.prompts.sections.tools.ToolMetadataRegistry;
import com.openjiuwen.harness.tools.ListMcpResourcesTool;
import com.openjiuwen.harness.tools.McpResourceService;
import com.openjiuwen.harness.tools.ReadMcpResourceTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Public class McpRail used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
public class McpRail extends DeepAgentRail {
    private final List<Tool> tools = new ArrayList<>();

    /**
     * priority.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int priority() {
        return 95;
    }

    /**
     * init.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void init(Object agent) {
        if (!(agent instanceof DeepAgent deepAgent) || !tools.isEmpty()) {
            return;
        }
        String language = deepAgent.getWorkspace().getLanguage();
        McpResourceService service = new RunnerMcpResourceService();
        ListMcpResourcesTool listTool = new ListMcpResourcesTool(service);
        ReadMcpResourceTool readTool = new ReadMcpResourceTool(service);
        tools.add(new LocalFunction(card("list_mcp_resources", deepAgent, language),
                inputs -> listTool.invoke(stringValue(inputs.get("server_id")))));
        tools.add(new LocalFunction(card("read_mcp_resource", deepAgent, language),
                inputs -> readTool.invoke(stringValue(inputs.get("server_id")), stringValue(inputs.get("uri")))));
        for (Tool tool : tools) {
            deepAgent.registerHarnessTool(tool);
        }
    }

    /**
     * uninit.
     * 
     * @param agent agent
     * @since 0.1.7
     */
    @Override
    public void uninit(Object agent) {
        if (agent instanceof DeepAgent deepAgent) {
            for (Tool tool : tools) {
                deepAgent.unregisterHarnessTool(tool);
            }
        }
        tools.clear();
    }

    /**
     * toolNames.
     *
     * @return List<String>
     * @since 0.1.7
     */
    public java.util.List<String> toolNames() {
        return java.util.List.of("list_mcp_resources", "read_mcp_resource");
    }

    /**
     * registeredToolNames.
     *
     * @return List<String>
     * @since 0.1.7
     */
    public java.util.List<String> registeredToolNames() {
        return tools.stream().map(tool -> tool.getCard().getName()).toList();
    }

    /**
     * describe.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String describe() {
        return "Register MCP resource tools";
    }

    /**
     * card.
     * 
     * @param name name
     * @param agent agent
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    private static ToolCard card(String name, DeepAgent agent, String language) {
        return ToolMetadataRegistry.buildToolCard(name, agent.getCard().getId() + "." + name, language);
    }

    /**
     * stringValue.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static class RunnerMcpResourceService implements McpResourceService {
        /**
         * listResources.
         * 
         * @param serverId serverId
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public List<?> listResources(String serverId) throws Exception {
            return Runner.resourceMgr().listMcpResources(serverId, null, null, TagMatchStrategy.ALL, true);
        }

        /**
         * readResource.
         * 
         * @param serverId serverId
         * @param uri uri
         * @return the result
         * @throws Exception Exception
         * @since 0.1.7
         */
        @Override
        public List<?> readResource(String serverId, String uri) throws Exception {
            return Runner.resourceMgr().readMcpResource(serverId, uri);
        }
    }
}
