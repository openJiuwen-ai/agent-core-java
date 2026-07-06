/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.security.ToolPermissionHost;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
/**
 * Public class DeepAgentConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
@AllArgsConstructor
public class DeepAgentConfig {
    @Builder.Default
    private String systemPrompt = "";
    @Builder.Default
    private int maxIterations = 15;
    @Builder.Default
    private boolean isTaskLoopEnabled = false;
    @Builder.Default
    private boolean isTaskPlanningEnabled = false;
    @Builder.Default
    private String language = "cn";
    @Builder.Default
    private AgentMode defaultMode = AgentMode.NORMAL;
    @Builder.Default
    private String workspacePath = "./";
    private Double completionTimeout;
    @Builder.Default
    private Map<String, Object> permissions = new LinkedHashMap<>();
    @Builder.Default
    private List<Object> tools = new ArrayList<>();
    @Builder.Default
    private List<Object> rails = new ArrayList<>();
    @Builder.Default
    private List<McpServerConfig> mcps = new ArrayList<>();
    @Builder.Default
    private List<Object> subagents = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> extraPromptSections = new ArrayList<>();
    @Builder.Default
    private List<String> skillDirectories = new ArrayList<>();
    @Builder.Default
    private String skillMode = "all";
    private Object model;
    private Object backend;
    private String promptMode;
    @Builder.Default
    private List<String> skills = new ArrayList<>();
    @Builder.Default
    private boolean enableSkillDiscovery = false;
    @Builder.Default
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();
    @Builder.Default
    private boolean isAsyncSubagentEnabled = false;
    @Builder.Default
    private boolean isGeneralPurposeAgentEnabled = false;
    @Builder.Default
    private boolean isRestrictToWorkDirEnabled = true;
    private SysOperation sysOperation;
    private ToolPermissionHost permissionHost;

    /**
     * Auto-generated for codecheck compliance.
     */
    public static class DeepAgentConfigBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder enableTaskLoop(boolean isEnabled) {
            return this.isTaskLoopEnabled(isEnabled);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder enableTaskPlanning(boolean isEnabled) {
            return this.isTaskPlanningEnabled(isEnabled);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder enableAsyncSubagent(boolean isEnabled) {
            return this.isAsyncSubagentEnabled(isEnabled);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder isRestrictToWorkDir(boolean isEnabled) {
            return this.isRestrictToWorkDirEnabled(isEnabled);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder restrictToWorkDir(boolean isEnabled) {
            return this.isRestrictToWorkDirEnabled(isEnabled);
        }

        /**
         * Auto-generated for codecheck compliance.
         */
        public DeepAgentConfigBuilder addGeneralPurposeAgent(boolean isEnabled) {
            return this.isGeneralPurposeAgentEnabled(isEnabled);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnableTaskLoop() {
        return isTaskLoopEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnableTaskPlanning() {
        return isTaskPlanningEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnableAsyncSubagent() {
        return isAsyncSubagentEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isAddGeneralPurposeAgent() {
        return isGeneralPurposeAgentEnabled;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isRestrictToWorkDir() {
        return isRestrictToWorkDirEnabled;
    }
}
