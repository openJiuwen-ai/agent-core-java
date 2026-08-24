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

    /** Python {@code Workspace(root_path="./")} / {@code WorkspaceSpec.root_path} default. */
    public static final String DEFAULT_WORKSPACE_PATH = "./";

    /** Python general-purpose subagent card name ({@code factory._inject_general_purpose_subagent}). */
    public static final String GENERAL_PURPOSE_AGENT_NAME = "general-purpose";

    /** Legacy factoryKwargs key; prefer {@link #enableReadImageMultimodal}. */
    public static final String FACTORY_KWARG_ENABLE_READ_IMAGE_MULTIMODAL = "enable_read_image_multimodal";

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
    private String workspacePath = DEFAULT_WORKSPACE_PATH;
    /**
     * Whether {@code read_file} attaches image bytes natively.
     * Mirrors Python {@code DeepAgentConfig.enable_read_image_multimodal} (default True when unset).
     */
    @Builder.Default
    private boolean enableReadImageMultimodal = true;
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
    private String skillMode = "auto_list";
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
    /**
     * Whether {@link com.openjiuwen.harness.deep_agent.DeepAgent#ensureInitialized()}
     * materializes the workspace schema. Python default is {@code True}.
     */
    @Builder.Default
    private boolean autoCreateWorkspace = true;
    private SysOperation sysOperation;
    private ToolPermissionHost permissionHost;
    @Builder.Default
    private boolean isEnableTenantIsolation = false;
    private String tenantDataRoot;
    private List<String> workspaceSecondaryTiers;
    private Map<String, Map<String, Object>> workspaceTierConfigs;
    private String todoStorageType;
    private String sessionStoreType;
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
         * enableTenantIsolation.
         *
         * @param isEnabled isEnabled
         * @return the result
         * @since 0.1.7
         */
        public DeepAgentConfigBuilder enableTenantIsolation(boolean isEnabled) {
            return this.isEnableTenantIsolation(isEnabled);
        }

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

    public Object getWorkspace() {
        return workspacePath;
    }

    public boolean isEnableReadImageMultimodal() {
        // Prefer first-class field; factoryKwargs kept for older dumps / external injectors.
        if (factoryKwargs != null) {
            Object value = factoryKwargs.get(FACTORY_KWARG_ENABLE_READ_IMAGE_MULTIMODAL);
            if (value instanceof Boolean b) {
                return b;
            }
        }
        return enableReadImageMultimodal;
    }

    public void setEnableReadImageMultimodal(boolean enableReadImageMultimodal) {
        this.enableReadImageMultimodal = enableReadImageMultimodal;
        if (factoryKwargs == null) {
            factoryKwargs = new LinkedHashMap<>();
        }
        factoryKwargs.put(FACTORY_KWARG_ENABLE_READ_IMAGE_MULTIMODAL, enableReadImageMultimodal);
    }

    public Map<String, Object> getModelSelection() {
        if (factoryKwargs == null) {
            return Map.of();
        }
        Object value = factoryKwargs.get("model_selection");
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copied = new LinkedHashMap<>();
            map.forEach((k, v) -> copied.put(String.valueOf(k), v));
            return copied;
        }
        return Map.of();
    }

    /** Develop-compat alias for plan-mode / task-planning flag. */
    public boolean isEnablePlanMode() {
        Object value = factoryKwargs == null ? null : factoryKwargs.get("enable_plan_mode");
        if (value instanceof Boolean b) {
            return b;
        }
        return isTaskPlanningEnabled;
    }

    /** Develop-compat progressive-tool default-visible list. */
    @SuppressWarnings("unchecked")
    public List<String> getProgressiveToolDefaultVisibleTools() {
        Object value = factoryKwargs == null ? null : factoryKwargs.get("progressive_tool_default_visible_tools");
        if (value instanceof List<?> list) {
            List<String> copied = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    copied.add(String.valueOf(item));
                }
            }
            return copied;
        }
        return List.of();
    }
}
