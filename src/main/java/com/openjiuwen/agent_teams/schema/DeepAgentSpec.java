/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-serializable specification for constructing a DeepAgent runtime configuration.
 *
 * <p>Mirrors Python's {@code DeepAgentSpec} in
 * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
 */
public class DeepAgentSpec extends AgentConfigurator.DeepAgentSpec {

    private DeepAgentConfig config;
    private AgentCard card;
    private List<Object> mcps;
    private List<SubAgentSpec> subagents;
    private List<RailSpec> rails;
    private boolean enableTaskLoop;
    private boolean enableAsyncSubagent;
    private boolean addGeneralPurposeAgent;
    private int maxIterations = 15;
    private WorkspaceSpec workspaceSpec;
    private List<String> skills;
    private boolean enableSkillDiscovery;
    private SysOperationSpec sysOperation;
    private String promptMode;
    private VisionModelSpec visionModel;
    private AudioModelSpec audioModel;
    private boolean enableTaskPlanning;
    private boolean restrictToSandbox;
    private boolean autoCreateWorkspace = true;
    private double completionTimeout = 600.0;
    private ProgressiveToolSpec progressiveTool;

    @Override
    public TeamModelConfig getModel() {
        Object model = super.getModel();
        return model instanceof TeamModelConfig config ? config : null;
    }

    public void setModel(TeamModelConfig model) {
        super.setModel(model);
        if (config != null) {
            config.setModel(model);
        }
    }

    @Override
    public String getLanguage() {
        return super.getLanguage();
    }

    @Override
    public void setLanguage(String language) {
        super.setLanguage(language);
        if (config != null) {
            config.setLanguage(language);
        }
    }

    @Override
    public List<String> getApprovalRequiredTools() {
        return super.getApprovalRequiredTools();
    }

    @Override
    public void setApprovalRequiredTools(List<String> approvalRequiredTools) {
        super.setApprovalRequiredTools(approvalRequiredTools);
    }

    public DeepAgentConfig getConfig() {
        if (config == null) {
            config = new DeepAgentConfig();
            config.setModel(super.getModel());
            config.setLanguage(super.getLanguage());
            config.setSystemPrompt(getSystemPrompt());
            config.setEnableTaskLoop(enableTaskLoop);
            config.setEnableAsyncSubagent(enableAsyncSubagent);
            config.setAddGeneralPurposeAgent(addGeneralPurposeAgent);
            config.setMaxIterations(maxIterations);
            config.setEnableSkillDiscovery(enableSkillDiscovery);
            config.setPromptMode(promptMode);
            config.setAutoCreateWorkspace(autoCreateWorkspace);
            config.setCompletionTimeout(completionTimeout);
        }
        return config;
    }

    public void setConfig(DeepAgentConfig config) {
        this.config = config;
        if (config == null) {
            return;
        }
        super.setModel(config.getModel());
        super.setLanguage(config.getLanguage());
        super.setSystemPrompt(config.getSystemPrompt());
        enableTaskLoop = config.isEnableTaskLoop();
        enableAsyncSubagent = config.isEnableAsyncSubagent();
        addGeneralPurposeAgent = config.isAddGeneralPurposeAgent();
        maxIterations = config.getMaxIterations();
        enableSkillDiscovery = config.isEnableSkillDiscovery();
        promptMode = config.getPromptMode();
        autoCreateWorkspace = config.isAutoCreateWorkspace();
        completionTimeout = config.getCompletionTimeout();
    }

    public DeepAgentBuildConfig build() {
        TeamModelConfig modelConfig = getModel();
        Model llmModel = modelConfig == null ? null : modelConfig.build();
        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(getLanguage());
        Workspace workspace = workspaceSpec == null ? null : workspaceSpec.build();
        List<Object> resolvedRails = rails == null ? null : rails.stream()
                .map(rail -> rail.build(resolvedLanguage, workspace))
                .toList();
        List<SubAgentSpec.SubAgentBuildConfig> resolvedSubagents = subagents == null ? null : subagents.stream()
                .map(subagent -> subagent.build(llmModel, resolvedLanguage))
                .toList();
        Object resolvedSysOperation = sysOperation == null ? null : sysOperation.buildCard();
        return new DeepAgentBuildConfig(
                llmModel,
                card,
                getSystemPrompt(),
                resolveTools(resolvedLanguage, card == null ? null : card.getId()),
                mcps == null ? null : new ArrayList<>(mcps),
                resolvedSubagents,
                resolvedRails,
                true,
                enableAsyncSubagent,
                addGeneralPurposeAgent,
                maxIterations,
                workspace,
                skills == null ? null : new ArrayList<>(skills),
                enableSkillDiscovery,
                resolvedSysOperation,
                getLanguage(),
                promptMode,
                visionModel == null ? null : visionModel.build(),
                audioModel == null ? null : audioModel.build(),
                enableTaskPlanning,
                restrictToSandbox,
                autoCreateWorkspace,
                completionTimeout,
                progressiveToolKwargs()
        );
    }

    private List<Object> resolveTools(String language, String prefix) {
        List<Object> tools = getTools();
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

    public Map<String, Object> progressiveToolKwargs() {
        if (progressiveTool == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("progressive_tool_enabled", progressiveTool.isEnabled());
        values.put("progressive_tool_always_visible_tools", progressiveTool.getAlwaysVisibleTools());
        values.put("progressive_tool_default_visible_tools", progressiveTool.getDefaultVisibleTools());
        values.put("progressive_tool_max_loaded_tools", progressiveTool.getMaxLoadedTools());
        return values;
    }

    /**
     * Resolved DeepAgent construction arguments.
     *
     * <p>Mirrors Python's {@code create_deep_agent(...)} argument materialization in
     * {@code openjiuwen/agent_teams/schema/deep_agent_spec.py}.</p>
     */
    public record DeepAgentBuildConfig(
            Model model,
            AgentCard card,
            String systemPrompt,
            List<Object> tools,
            List<Object> mcps,
            List<SubAgentSpec.SubAgentBuildConfig> subagents,
            List<Object> rails,
            boolean enableTaskLoop,
            boolean enableAsyncSubagent,
            boolean addGeneralPurposeAgent,
            int maxIterations,
            Workspace workspace,
            List<String> skills,
            boolean enableSkillDiscovery,
            Object sysOperation,
            String language,
            String promptMode,
            Object visionModelConfig,
            Object audioModelConfig,
            boolean enableTaskPlanning,
            boolean restrictToWorkDir,
            boolean autoCreateWorkspace,
            double completionTimeout,
            Map<String, Object> progressiveToolKwargs
    ) {
    }

    public AgentCard getCard() {
        return card;
    }

    public void setCard(AgentCard card) {
        this.card = card;
    }

    public List<Object> getMcps() {
        return mcps == null ? null : new ArrayList<>(mcps);
    }

    public void setMcps(List<Object> mcps) {
        this.mcps = mcps == null ? null : new ArrayList<>(mcps);
    }

    public List<SubAgentSpec> getSubagents() {
        return subagents == null ? null : new ArrayList<>(subagents);
    }

    public void setSubagents(List<SubAgentSpec> subagents) {
        this.subagents = subagents == null ? null : new ArrayList<>(subagents);
    }

    public List<RailSpec> getRails() {
        return rails == null ? null : new ArrayList<>(rails);
    }

    public void setRails(List<RailSpec> rails) {
        this.rails = rails == null ? null : new ArrayList<>(rails);
    }

    public boolean isEnableTaskLoop() {
        return enableTaskLoop;
    }

    public void setEnableTaskLoop(boolean enableTaskLoop) {
        this.enableTaskLoop = enableTaskLoop;
    }

    public boolean isEnableAsyncSubagent() {
        return enableAsyncSubagent;
    }

    public void setEnableAsyncSubagent(boolean enableAsyncSubagent) {
        this.enableAsyncSubagent = enableAsyncSubagent;
    }

    public boolean isAddGeneralPurposeAgent() {
        return addGeneralPurposeAgent;
    }

    public void setAddGeneralPurposeAgent(boolean addGeneralPurposeAgent) {
        this.addGeneralPurposeAgent = addGeneralPurposeAgent;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public WorkspaceSpec getWorkspaceSpec() {
        return workspaceSpec;
    }

    public void setWorkspaceSpec(WorkspaceSpec workspaceSpec) {
        this.workspaceSpec = workspaceSpec;
    }

    public List<String> getSkills() {
        return skills == null ? null : new ArrayList<>(skills);
    }

    public void setSkills(List<String> skills) {
        this.skills = skills == null ? null : new ArrayList<>(skills);
    }

    public boolean isEnableSkillDiscovery() {
        return enableSkillDiscovery;
    }

    public void setEnableSkillDiscovery(boolean enableSkillDiscovery) {
        this.enableSkillDiscovery = enableSkillDiscovery;
    }

    public SysOperationSpec getSysOperationSpec() {
        return sysOperation;
    }

    public void setSysOperationSpec(SysOperationSpec sysOperation) {
        this.sysOperation = sysOperation;
    }

    public String getPromptMode() {
        return promptMode;
    }

    public void setPromptMode(String promptMode) {
        this.promptMode = promptMode;
    }

    public VisionModelSpec getVisionModel() {
        return visionModel;
    }

    public void setVisionModel(VisionModelSpec visionModel) {
        this.visionModel = visionModel;
    }

    public AudioModelSpec getAudioModel() {
        return audioModel;
    }

    public void setAudioModel(AudioModelSpec audioModel) {
        this.audioModel = audioModel;
    }

    public boolean isEnableTaskPlanning() {
        return enableTaskPlanning;
    }

    public void setEnableTaskPlanning(boolean enableTaskPlanning) {
        this.enableTaskPlanning = enableTaskPlanning;
    }

    public boolean isRestrictToSandbox() {
        return restrictToSandbox;
    }

    public void setRestrictToSandbox(boolean restrictToSandbox) {
        this.restrictToSandbox = restrictToSandbox;
    }

    public boolean isAutoCreateWorkspace() {
        return autoCreateWorkspace;
    }

    public void setAutoCreateWorkspace(boolean autoCreateWorkspace) {
        this.autoCreateWorkspace = autoCreateWorkspace;
    }

    public double getCompletionTimeout() {
        return completionTimeout;
    }

    public void setCompletionTimeout(double completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public ProgressiveToolSpec getProgressiveTool() {
        return progressiveTool;
    }

    public void setProgressiveTool(ProgressiveToolSpec progressiveTool) {
        this.progressiveTool = progressiveTool;
    }
}
