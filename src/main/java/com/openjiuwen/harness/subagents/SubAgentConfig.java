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

@Data
@Builder
@NoArgsConstructor
/**
 * Public class SubAgentConfig used by the Java parity implementation.
 *
 * @since 1.0
 */
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
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();
    @Builder.Default
    private String executionMode = "ephemeral";
    @Builder.Default
    private String role = "";
    @Builder.Default
    private List<Object> tools = new ArrayList<>();
    @Builder.Default
    private List<Object> rails = new ArrayList<>();
    @Builder.Default
    private List<McpServerConfig> mcps = new ArrayList<>();
    @Builder.Default
    private List<Object> subagents = new ArrayList<>();
    @Builder.Default
    private List<String> skillDirectories = new ArrayList<>();
    @Builder.Default
    private String skillMode = "all";
    @Builder.Default
    private List<String> skills = new ArrayList<>();
    private Object model;
    private Object backend;
    private String promptMode;
    private SysOperation sysOperation;
    @Builder.Default
    private String workspacePath = "";
    @Builder.Default
    private boolean isRestrictToWorkDir = true;

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEnableTaskLoop(Boolean value) {
        this.isTaskLoopEnabled = value != null && value;
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
    public static class SubAgentConfigBuilder {
        /**
         * Auto-generated for codecheck compliance.
         */
        public SubAgentConfigBuilder restrictToWorkDir(boolean value) {
            return this.isRestrictToWorkDir(value);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public DeepAgentConfig toDeepAgentConfig() {
        return DeepAgentConfig.builder()
                .systemPrompt(systemPrompt)
                .maxIterations(maxIterations)
                .isTaskLoopEnabled(isTaskLoopEnabled)
                .language(language)
                .tools(new ArrayList<>(tools))
                .rails(new ArrayList<>(rails))
                .mcps(new ArrayList<>(mcps))
                .subagents(new ArrayList<>(subagents))
                .skillDirectories(new ArrayList<>(skillDirectories))
                .skillMode(skillMode)
                .skills(new ArrayList<>(skills))
                .model(model)
                .backend(backend)
                .promptMode(promptMode)
                .sysOperation(sysOperation)
                .factoryKwargs(new LinkedHashMap<>(factoryKwargs))
                .workspacePath(workspacePath)
                .isRestrictToWorkDir(isRestrictToWorkDir)
                .build();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean hasRail(Class<?> railClass) {
        if (railClass == null || rails == null) {
            return false;
        }
        return rails.stream().anyMatch(railClass::isInstance);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> railClassNames() {
        if (rails == null) {
            return List.of();
        }
        return rails.stream().map(item -> item.getClass().getSimpleName()).toList();
    }
}
