/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializable sub-agent specification.
 *
 * <p>Mirrors Python's {@code SubAgentSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class SubAgentSpec {

    private AgentCard agentCard;
    private String systemPrompt;
    private List<Object> tools = new ArrayList<>();
    private List<Object> mcps = new ArrayList<>();
    private TeamModelConfig model;
    private List<RailSpec> rails;
    private List<String> skills;
    private WorkspaceSpec workspace;
    private SysOperationSpec sysOperation;
    private String language;
    private String promptMode;
    private boolean enableTaskLoop;
    private Integer maxIterations;
    private String factoryName;
    private Map<String, Object> factoryKwargs = new LinkedHashMap<>();

    public SubAgentBuildConfig build(Model parentModel, String language) {
        Model resolvedModel = model == null ? null : model.build();
        Workspace resolvedWorkspace = workspace == null ? null : workspace.build();
        List<Object> resolvedRails = rails == null ? null : rails.stream()
                .map(rail -> rail.build(language, resolvedWorkspace))
                .toList();
        Object resolvedSysOperation = sysOperation == null ? null : sysOperation.buildCard();
        List<Object> resolvedTools = resolveTools(language, agentCard == null ? null : agentCard.getId());
        return new SubAgentBuildConfig(
                agentCard,
                systemPrompt,
                resolvedTools,
                new ArrayList<>(mcps),
                resolvedModel == null ? parentModel : resolvedModel,
                resolvedRails,
                skills == null ? null : new ArrayList<>(skills),
                resolvedWorkspace,
                resolvedSysOperation,
                this.language,
                promptMode,
                enableTaskLoop,
                maxIterations,
                factoryName,
                new LinkedHashMap<>(factoryKwargs)
        );
    }

    private List<Object> resolveTools(String language, String prefix) {
        if (tools == null || tools.isEmpty()) {
            return null;
        }
        List<Object> resolved = new ArrayList<>();
        for (Object item : tools) {
            if (item instanceof BuiltinToolSpec spec) {
                String toolId = prefix == null || prefix.isBlank() ? null : prefix + "." + spec.getType();
                resolved.add(spec.build(language, toolId));
            } else {
                resolved.add(item);
            }
        }
        return resolved.isEmpty() ? null : resolved;
    }

    /**
     * Resolved sub-agent configuration produced by {@link #build(Model, String)}.
     *
     * <p>Mirrors Python's {@code SubAgentConfig} materialization in
     * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
     */
    public record SubAgentBuildConfig(
            AgentCard agentCard,
            String systemPrompt,
            List<Object> tools,
            List<Object> mcps,
            Model model,
            List<Object> rails,
            List<String> skills,
            Workspace workspace,
            Object sysOperation,
            String language,
            String promptMode,
            boolean enableTaskLoop,
            Integer maxIterations,
            String factoryName,
            Map<String, Object> factoryKwargs
    ) {
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public void setAgentCard(AgentCard agentCard) {
        this.agentCard = agentCard;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public List<Object> getTools() {
        return tools == null ? null : new ArrayList<>(tools);
    }

    public void setTools(List<Object> tools) {
        this.tools = tools == null ? new ArrayList<>() : new ArrayList<>(tools);
    }

    public List<Object> getMcps() {
        return new ArrayList<>(mcps);
    }

    public void setMcps(List<Object> mcps) {
        this.mcps = mcps == null ? new ArrayList<>() : new ArrayList<>(mcps);
    }

    public TeamModelConfig getModel() {
        return model;
    }

    public void setModel(TeamModelConfig model) {
        this.model = model;
    }

    public List<RailSpec> getRails() {
        return rails == null ? null : new ArrayList<>(rails);
    }

    public void setRails(List<RailSpec> rails) {
        this.rails = rails == null ? null : new ArrayList<>(rails);
    }

    public List<String> getSkills() {
        return skills == null ? null : new ArrayList<>(skills);
    }

    public void setSkills(List<String> skills) {
        this.skills = skills == null ? null : new ArrayList<>(skills);
    }

    public WorkspaceSpec getWorkspaceSpec() {
        return workspace;
    }

    public void setWorkspaceSpec(WorkspaceSpec workspace) {
        this.workspace = workspace;
    }

    public SysOperationSpec getSysOperation() {
        return sysOperation;
    }

    public void setSysOperation(SysOperationSpec sysOperation) {
        this.sysOperation = sysOperation;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getPromptMode() {
        return promptMode;
    }

    public void setPromptMode(String promptMode) {
        this.promptMode = promptMode;
    }

    public boolean isEnableTaskLoop() {
        return enableTaskLoop;
    }

    public void setEnableTaskLoop(boolean enableTaskLoop) {
        this.enableTaskLoop = enableTaskLoop;
    }

    public Integer getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getFactoryName() {
        return factoryName;
    }

    public void setFactoryName(String factoryName) {
        this.factoryName = factoryName;
    }

    public Map<String, Object> getFactoryKwargs() {
        return new LinkedHashMap<>(factoryKwargs);
    }

    public void setFactoryKwargs(Map<String, Object> factoryKwargs) {
        this.factoryKwargs = factoryKwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(factoryKwargs);
    }
}
