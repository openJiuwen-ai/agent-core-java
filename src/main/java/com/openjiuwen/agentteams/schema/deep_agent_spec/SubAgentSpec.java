/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.schema.deep_agent_spec;

import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable sub-agent specification.
 * Mirrors Python SubAgentSpec.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentSpec {
    private AgentCard agentCard;
    private String systemPrompt;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<ToolCard> tools = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<McpServerConfig> mcps = new ArrayList<>();
    private TeamModelConfig model;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<RailSpec> rails = new ArrayList<>();
    private List<String> skills;
    private WorkspaceSpec workspace;
    private SysOperationSpec sysOperation;
    private String language;
    private String promptMode;
    @Builder.Default
    private boolean isTaskLoopEnabled = false;
    private Integer maxIterations;
    private String factoryName;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();
}
