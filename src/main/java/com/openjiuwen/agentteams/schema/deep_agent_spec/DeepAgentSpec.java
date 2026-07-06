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
import java.util.List;

/**
 * Fully serializable specification for constructing a DeepAgent.
 * Mirrors Python DeepAgentSpec.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepAgentSpec {

    private TeamModelConfig model;
    private AgentCard card;
    private String systemPrompt;
    @Builder.Default
    private List<ToolCard> tools = new ArrayList<>();
    @Builder.Default
    private List<McpServerConfig> mcps = new ArrayList<>();
    @Builder.Default
    private List<SubAgentSpec> subagents = new ArrayList<>();
    @Builder.Default
    private List<RailSpec> rails = new ArrayList<>();
    @Builder.Default
    private boolean enableTaskLoop = false;
    @Builder.Default
    private boolean enableAsyncSubagent = false;
    @Builder.Default
    private boolean addGeneralPurposeAgent = false;
    @Builder.Default
    private int maxIterations = 15;
    private WorkspaceSpec workspace;
    private List<String> skills;
    @Builder.Default
    private boolean enableSkillDiscovery = false;
    private SysOperationSpec sysOperation;
    private String language;
    private String promptMode;
    private VisionModelSpec visionModel;
    private AudioModelSpec audioModel;
    @Builder.Default
    private boolean enableTaskPlanning = false;
    @Builder.Default
    private boolean restrictToSandbox = false;
    @Builder.Default
    private boolean autoCreateWorkspace = true;
    @Builder.Default
    private double completionTimeout = 600.0;
    private ProgressiveToolSpec progressiveTool;
    private List<String> approvalRequiredTools;
}
