/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import com.openjiuwen.agent_teams.prompts.TeamPlanAgent;
import com.openjiuwen.agent_teams.prompts.TeamPlanMode;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;
import com.openjiuwen.harness.prompts.sections.SectionName;
import com.openjiuwen.harness.schema.DeepAgentState;

import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

/**
 * Injects team.plan leader instructions during plan mode.
 *
 * <p>Mirrors Python's {@code TeamPlanModeRail} in
 * {@code openjiuwen/agent_teams/rails/team_plan_mode_rail.py}.</p>
 */
public class TeamPlanModeRail {

    public static final int PRIORITY = 84;

    private static final Logger LOGGER = Logger.getLogger(TeamPlanModeRail.class.getName());

    private final String languageOverride;
    private PlanModeAgent agent;
    private SystemPromptBuilder systemPromptBuilder;

    public TeamPlanModeRail() {
        this(null);
    }

    public TeamPlanModeRail(String language) {
        this.languageOverride = language == null ? null : HarnessPromptsPackage.resolveLanguage(language);
    }

    public int getPriority() {
        return PRIORITY;
    }

    public SystemPromptBuilder getSystemPromptBuilder() {
        return systemPromptBuilder;
    }

    public void init(PlanModeAgent agent) {
        this.agent = agent;
        this.systemPromptBuilder = agent == null ? null : agent.getSystemPromptBuilder();
        specializePlanAgent();
    }

    public void uninit(PlanModeAgent ignoredAgent) {
        if (systemPromptBuilder != null) {
            systemPromptBuilder.removeSection(SectionName.MODE_INSTRUCTIONS);
        }
        agent = null;
        systemPromptBuilder = null;
    }

    public CompletionStage<Void> beforeModelCall(PlanModeCallbackContext ctx) {
        if (agent == null || systemPromptBuilder == null) {
            return CompletableFuture.completedFuture(null);
        }

        TeamPlanMode.PlanSession session = ctx == null ? null : ctx.getSession();
        DeepAgentState state = agent.loadState(session);
        if (state == null
                || state.getPlanMode() == null
                || !"plan".equals(state.getPlanMode().getMode())) {
            systemPromptBuilder.removeSection(SectionName.MODE_INSTRUCTIONS);
            return CompletableFuture.completedFuture(null);
        }

        specializePlanAgent();
        String language = resolveLanguage();
        systemPromptBuilder.addSection(TeamPlanMode.buildTeamPlanModeSection(language, agent, session));
        return CompletableFuture.completedFuture(null);
    }

    String resolveLanguage() {
        if (languageOverride != null) {
            return languageOverride;
        }
        return HarnessPromptsPackage.resolveLanguage(
                systemPromptBuilder == null ? null : systemPromptBuilder.getLanguage()
        );
    }

    boolean specializePlanAgent() {
        if (agent == null) {
            return false;
        }
        DeepConfigView deepConfig = agent.getDeepConfig();
        Collection<?> subagents = deepConfig == null ? null : deepConfig.getSubagents();
        boolean applied = TeamPlanAgent.applyTeamPlanAgentPrompt(subagents, resolveLanguage());
        if (applied) {
            LOGGER.info("[team.plan] specialized built-in plan_agent prompt");
        }
        return applied;
    }

    /**
     * Narrow Java view of the DeepAgent behavior used by this rail.
     *
     * <p>Mirrors Python's dynamic agent access in
     * {@code openjiuwen/agent_teams/rails/team_plan_mode_rail.py}.</p>
     */
    public interface PlanModeAgent extends TeamPlanMode.PlanFileProvider {
        SystemPromptBuilder getSystemPromptBuilder();

        DeepAgentState loadState(TeamPlanMode.PlanSession session);

        DeepConfigView getDeepConfig();
    }

    /**
     * Minimal deep-config view for plan subagent specialization.
     *
     * <p>Mirrors Python's {@code agent.deep_config.subagents} access in
     * {@code openjiuwen/agent_teams/rails/team_plan_mode_rail.py}.</p>
     */
    public interface DeepConfigView {
        Collection<?> getSubagents();
    }

    /**
     * Minimal callback context view required by this rail.
     *
     * <p>Mirrors Python's {@code AgentCallbackContext.session} access in
     * {@code openjiuwen/agent_teams/rails/team_plan_mode_rail.py}.</p>
     */
    public static final class PlanModeCallbackContext {
        private final TeamPlanMode.PlanSession session;

        public PlanModeCallbackContext(TeamPlanMode.PlanSession session) {
            this.session = Objects.requireNonNull(session, "session");
        }

        public TeamPlanMode.PlanSession getSession() {
            return session;
        }
    }
}
