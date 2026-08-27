/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema.config;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.AgentMode;
import com.openjiuwen.harness.security.ToolPermissionHost;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public class DeepAgentConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeepAgentConfig {
    @Builder.Default
    private String systemPrompt = "";
    @Builder.Default
    private int maxIterations = 15;
    @Builder.Default
    private int maxParallelToolCalls = 3;
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
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> permissions = new LinkedHashMap<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> tools = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> rails = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<McpServerConfig> mcps = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Object> subagents = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<Map<String, Object>> extraPromptSections = new ArrayList<>();
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> skillDirectories = new ArrayList<>();
    @Builder.Default
    private String skillMode = "all";
    private Object model;
    private Object backend;
    private String promptMode;
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> skills = new ArrayList<>();
    @Builder.Default
    private boolean enableSkillDiscovery = false;
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();
    @Builder.Default
    private boolean isAsyncSubagentEnabled = false;
    @Builder.Default
    private boolean isGeneralPurposeAgentEnabled = false;
    @Builder.Default
    private boolean isRestrictToWorkDirEnabled = true;
    private SysOperation sysOperation;
    private ToolPermissionHost permissionHost;
    @Builder.Default
    private boolean isEnableTenantIsolation = false;
    private String tenantDataRoot;
    private java.util.List<String> workspaceSecondaryTiers;
    private java.util.Map<String, java.util.Map<String, Object>> workspaceTierConfigs;
    @Builder.Default
    private String todoStorageType = "file";
    @Builder.Default
    private String sessionStoreType = "file";
    private Map<String, Object> kvStoreConfig;
    @Builder.Default
    private Duration tmpTtl = Duration.ofHours(24);
    @Builder.Default
    private Duration tmpTtlScanInterval = Duration.ofHours(1);

    /**
     * DeepAgentConfigBuilder.
     * 
     * @since 0.1.7
     */
    public static class DeepAgentConfigBuilder {
        /**
         * enableTaskLoop.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder enableTaskLoop(boolean isEnabled) {
            return this.isTaskLoopEnabled(isEnabled);
        }

        /**
         * enableTaskPlanning.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder enableTaskPlanning(boolean isEnabled) {
            return this.isTaskPlanningEnabled(isEnabled);
        }

        /**
         * enableAsyncSubagent.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder enableAsyncSubagent(boolean isEnabled) {
            return this.isAsyncSubagentEnabled(isEnabled);
        }

        /**
         * isRestrictToWorkDir.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder isRestrictToWorkDir(boolean isEnabled) {
            return this.isRestrictToWorkDirEnabled(isEnabled);
        }

        /**
         * restrictToWorkDir.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder restrictToWorkDir(boolean isEnabled) {
            return this.isRestrictToWorkDirEnabled(isEnabled);
        }

        /**
         * addGeneralPurposeAgent.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder addGeneralPurposeAgent(boolean isEnabled) {
            return this.isGeneralPurposeAgentEnabled(isEnabled);
        }

        /**
         * enableTenantIsolation.
         * 
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder enableTenantIsolation(boolean isEnabled) {
            return this.isEnableTenantIsolation(isEnabled);
        }
    }

    /**
     * isEnableTaskLoop.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableTaskLoop() {
        return isTaskLoopEnabled;
    }

    /**
     * isEnableTaskPlanning.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableTaskPlanning() {
        return isTaskPlanningEnabled;
    }

    /**
     * isEnableAsyncSubagent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableAsyncSubagent() {
        return isAsyncSubagentEnabled;
    }

    /**
     * isAddGeneralPurposeAgent.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isAddGeneralPurposeAgent() {
        return isGeneralPurposeAgentEnabled;
    }

    /**
     * isRestrictToWorkDir.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isRestrictToWorkDir() {
        return isRestrictToWorkDirEnabled;
    }
}
