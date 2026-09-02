/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.deepagents.subagents;

import com.openjiuwen.deepagents.DeepAgentsFactory;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.subagents.BrowserAgentFactory;
import com.openjiuwen.harness.subagents.CodeAgentFactory;
import com.openjiuwen.harness.subagents.ExploreAgentFactory;
import com.openjiuwen.harness.subagents.PlanAgentFactory;
import com.openjiuwen.harness.subagents.ResearchAgentFactory;
import com.openjiuwen.harness.subagents.SubAgentConfig;
import com.openjiuwen.harness.subagents.VerificationAgentFactory;
import com.openjiuwen.harness.tools.browser.BrowserRuntimeSettings;
import com.openjiuwen.harness.workspace.Workspace;

import java.util.List;
import java.util.Locale;

/**
 * Public deepagents subagent facade mirroring Python's harness subagents exports.
 * 
 * @since 0.1.7
 */
public final class DeepAgentSubagents {
    /**
     * DeepAgentSubagents.
     * 
     * @since 0.1.7
     */
    private DeepAgentSubagents() {
    }

    /**
     * buildCodeAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildCodeAgentConfig(String language) {
        return CodeAgentFactory.buildCodeAgentConfig(language);
    }

    /**
     * buildExploreAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildExploreAgentConfig(String language) {
        return ExploreAgentFactory.buildExploreAgentConfig(language);
    }

    /**
     * buildPlanAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildPlanAgentConfig(String language) {
        return PlanAgentFactory.buildPlanAgentConfig(language);
    }

    /**
     * buildResearchAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildResearchAgentConfig(String language) {
        return ResearchAgentFactory.buildResearchAgentConfig(language);
    }

    /**
     * buildVerificationAgentConfig.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildVerificationAgentConfig(String language) {
        return VerificationAgentFactory.buildVerificationAgentConfig(language);
    }

    /**
     * buildBrowserAgentConfig.
     * 
     * @param settings settings
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    public static SubAgentConfig buildBrowserAgentConfig(BrowserRuntimeSettings settings, String language) {
        return BrowserAgentFactory.buildBrowserAgentConfig(settings, language);
    }

    /**
     * createCodeAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createCodeAgent(String language, Workspace workspace) {
        return CodeAgentFactory.createCodeAgent(language, workspace);
    }

    /**
     * createExploreAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createExploreAgent(String language, Workspace workspace) {
        return ExploreAgentFactory.createExploreAgent(language, workspace);
    }

    /**
     * createPlanAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createPlanAgent(String language, Workspace workspace) {
        return PlanAgentFactory.createPlanAgent(language, workspace);
    }

    /**
     * createResearchAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createResearchAgent(String language, Workspace workspace) {
        return ResearchAgentFactory.createResearchAgent(language, workspace);
    }

    /**
     * createVerificationAgent.
     * 
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createVerificationAgent(String language, Workspace workspace) {
        return VerificationAgentFactory.createVerificationAgent(language, workspace);
    }

    /**
     * createBrowserAgent.
     * 
     * @param settings settings
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent createBrowserAgent(BrowserRuntimeSettings settings, String language, Workspace workspace) {
        return BrowserAgentFactory.createBrowserAgent(settings, language, workspace, List.of(), List.of());
    }

    /**
     * create.
     * 
     * @param subagentType subagentType
     * @param language language
     * @param workspace workspace
     * @return the result
     * @since 0.1.7
     */
    public static DeepAgent create(String subagentType, String language, Workspace workspace) {
        String normalized = subagentType == null ? "" : subagentType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "code", "code_agent" -> createCodeAgent(language, workspace);
            case "explore", "explore_agent" -> createExploreAgent(language, workspace);
            case "plan", "plan_agent" -> createPlanAgent(language, workspace);
            case "research", "research_agent" -> createResearchAgent(language, workspace);
            case "verification", "verification_agent" -> createVerificationAgent(language, workspace);
            default -> {
                DeepAgent host = new DeepAgentsFactory().createDeepAgent();
                host.getConfig().setLanguage(language != null ? language : host.getConfig().getLanguage());
                host.getConfig().setWorkspacePath(
                        workspace != null ? workspace.root().toString() : host.getConfig().getWorkspacePath());
                yield host.createSubagent(subagentType, null);
            }
        };
    }
}
