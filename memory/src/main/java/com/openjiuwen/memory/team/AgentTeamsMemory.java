/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.memory.team;

import com.openjiuwen.agentteams.memory.TeamExecutionContext;
import com.openjiuwen.agentteams.memory.TeamMemberContext;
import com.openjiuwen.agentteams.memory.TeamMemory;
import com.openjiuwen.agentteams.memory.TeamMemoryContext;
import com.openjiuwen.agentteams.schema.team.TeamMemoryConfig;
import com.openjiuwen.harness.deep_agent.DeepAgent;

import java.io.IOException;
import java.util.Optional;

/**
 * Agent Teams integration backed by the Memory module.
 *
 * @since 0.1.7
 */
public final class AgentTeamsMemory implements TeamMemory {
    private final TeamMemoryManager manager;

    /**
     * Creates the Agent Teams memory integration.
     *
     * @param context team memory creation context
     * @since 0.1.7
     */
    public AgentTeamsMemory(TeamMemoryContext context) {
        this.manager = new TeamMemoryManager(createManagerParams(context));
    }

    @Override
    public void startRound(DeepAgent deepAgent, String query) throws IOException {
        if (manager.initToolkit()) {
            manager.registerTools(deepAgent);
            manager.loadAndInject(deepAgent, query == null ? "" : query);
        }
    }

    @Override
    public void finishRound() throws IOException {
        manager.extractAfterRound();
    }

    @Override
    public void close() {
        manager.close();
    }

    /**
     * Returns the underlying manager for Memory-module diagnostics and tests.
     *
     * @return underlying team memory manager
     * @since 0.1.7
     */
    public TeamMemoryManager manager() {
        return manager;
    }

    private static TeamMemoryManagerParams createManagerParams(TeamMemoryContext context) {
        TeamMemoryConfig config = context.config();
        TeamMemberContext member = context.member();
        TeamExecutionContext execution = context.execution();
        TeamLifecycle lifecycle = parseLifecycle(member.lifecycle());
        Optional<String> teamMemoryDir = resolveTeamMemoryDir(config, lifecycle, execution.defaultTeamMemoryDir());
        String readOnlySource = lifecycle == TeamLifecycle.TEMPORARY ? config.getParentWorkspacePath() : null;
        return TeamMemoryManagerParams.builder().memberName(member.memberName()).teamName(member.teamName())
                .role(TeamRole.LEADER).lifecycle(lifecycle).scenario(parseScenario(config.getScenario()))
                .embeddingConfig(TeamMemoryConfig.resolveEmbeddingConfig(config)).workspace(execution.workspace())
                .sysOperation(execution.sysOperation()).teamMemoryDir(teamMemoryDir.orElse(null))
                .language(parseLanguage(member.language()))
                .promptMode(parsePromptMode(config.getMemberMemoryPromptMode()))
                .enableAutoExtract(config.isAutoExtract() && lifecycle == TeamLifecycle.PERSISTENT)
                .readOnlySourceWorkspace(readOnlySource).db(execution.backend().getDb())
                .taskManager(execution.backend().getTaskManager()).extractionModel(execution.extractionModel())
                .timezoneOffsetHours(config.getTimezoneOffsetHours()).build();
    }

    private static Optional<String> resolveTeamMemoryDir(TeamMemoryConfig config, TeamLifecycle lifecycle,
            String defaultTeamMemoryDir) {
        if (!config.isSharedMemory() || lifecycle != TeamLifecycle.PERSISTENT) {
            return Optional.empty();
        }
        if (config.getTeamMemoryDir() != null && !config.getTeamMemoryDir().isBlank()) {
            return Optional.of(config.getTeamMemoryDir());
        }
        return Optional.ofNullable(defaultTeamMemoryDir);
    }

    private static TeamLifecycle parseLifecycle(String lifecycle) {
        return "persistent".equalsIgnoreCase(lifecycle) ? TeamLifecycle.PERSISTENT : TeamLifecycle.TEMPORARY;
    }

    private static TeamScenario parseScenario(String scenario) {
        return "coding".equalsIgnoreCase(scenario) ? TeamScenario.CODING : TeamScenario.GENERAL;
    }

    private static TeamLanguage parseLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? TeamLanguage.EN : TeamLanguage.CN;
    }

    private static PromptMode parsePromptMode(String mode) {
        return "passive".equalsIgnoreCase(mode) ? PromptMode.PASSIVE : PromptMode.PROACTIVE;
    }
}
