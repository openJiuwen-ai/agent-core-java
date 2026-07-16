/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.subagents;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Public class SubAgentConfig used by the Java parity implementation.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubAgentConfig {
    private AgentCard agentCard;
    @Builder.Default
    private String systemPrompt = "";
    @Builder.Default
    private String language = "cn";
    @Builder.Default
    private int maxIterations = 15;
    @Builder.Default
    private boolean isTaskLoopEnabled = false;
    @Builder.Default
    private String factoryName = "";
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();
    @Builder.Default
    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Builder.Default
    private String executionMode = "ephemeral";
    @Builder.Default
    private String role = "";
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
    private List<String> skillDirectories = new ArrayList<>();
    @Builder.Default
    private String skillMode = "all";
    @Builder.Default
    /**
     * ArrayList<>.
     * 
     * @since 0.1.7
     */
    private List<String> skills = new ArrayList<>();
    @Builder.Default
    private boolean enableSkillDiscovery = false;
    private Object model;
    private Object backend;
    private String promptMode;
    private SysOperation sysOperation;
    @Builder.Default
    private String workspacePath = "";
    @Builder.Default
    private boolean isRestrictToWorkDir = true;

    /**
     * setEnableTaskLoop.
     * 
     * @param value value
     * @since 0.1.7
     */
    public void setEnableTaskLoop(Boolean value) {
        this.isTaskLoopEnabled = value != null && value;
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
     * SubAgentConfigBuilder.
     * 
     * @since 0.1.7
     */
    public static class SubAgentConfigBuilder {
        /**
         * restrictToWorkDir.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
        public SubAgentConfigBuilder restrictToWorkDir(boolean value) {
            return this.isRestrictToWorkDir(value);
        }
    }

    /**
     * toDeepAgentConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public DeepAgentConfig toDeepAgentConfig() {
        return DeepAgentConfig.builder().systemPrompt(systemPrompt).maxIterations(maxIterations)
                .isTaskLoopEnabled(isTaskLoopEnabled).language(language).tools(new ArrayList<>(tools))
                .rails(new ArrayList<>(rails)).mcps(new ArrayList<>(mcps)).subagents(new ArrayList<>(subagents))
                .skillDirectories(new ArrayList<>(skillDirectories)).skillMode(skillMode)
                .skills(new ArrayList<>(skills)).enableSkillDiscovery(enableSkillDiscovery).model(model)
                .backend(backend).promptMode(promptMode).sysOperation(sysOperation)
                .factoryKwargs(new LinkedHashMap<>(factoryKwargs)).workspacePath(workspacePath)
                .isRestrictToWorkDir(isRestrictToWorkDir).build();
    }

    /**
     * hasRail.
     * 
     * @param railClass railClass
     * @return the result
     * @since 0.1.7
     */
    public boolean hasRail(Class<?> railClass) {
        if (railClass == null || rails == null) {
            return false;
        }
        return rails.stream().anyMatch(railClass::isInstance);
    }

    /**
     * railClassNames.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<String> railClassNames() {
        if (rails == null) {
            return List.of();
        }
        return rails.stream().map(item -> item.getClass().getSimpleName()).toList();
    }
}
