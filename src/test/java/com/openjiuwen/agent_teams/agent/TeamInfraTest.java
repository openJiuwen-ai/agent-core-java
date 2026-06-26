/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceConfig;
import com.openjiuwen.agent_teams.team_workspace.TeamWorkspaceManager;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link TeamInfra}.
 *
 * <p>Mirrors Python's team infrastructure dataclass in
 * {@code openjiuwen/agent_teams/agent/infra.py}.</p>
 */
class TeamInfraTest {

    @Test
    void defaultsMirrorPythonDataclassDefaults() {
        TeamInfra infra = new TeamInfra();

        assertNull(infra.getMessager());
        assertNull(infra.getTeamBackend());
        assertNull(infra.getWorkspaceManager());
        assertFalse(infra.isWorkspaceInitialized());
        assertNull(infra.getTaskManager());
        assertNull(infra.getMessageManager());
    }

    @Test
    void settersPreserveInjectedResourceReferences() {
        TeamInfra infra = new TeamInfra();
        Messager messager = new RecordingMessager();
        ConfiguredTeamBackend backend = new ConfiguredTeamBackend(
                "team",
                "leader",
                true,
                Map.of(),
                null,
                "",
                List.of(),
                null,
                null,
                true,
                false,
                List.of(),
                null,
                null,
                "leader"
        );
        TeamWorkspaceManager workspaceManager = new TeamWorkspaceManager(new TeamWorkspaceConfig(), "workspace", "team");
        Object taskManager = new Object();
        Object messageManager = new Object();

        infra.setMessager(messager);
        infra.setTeamBackend(backend);
        infra.setWorkspaceManager(workspaceManager);
        infra.setWorkspaceInitialized(true);
        infra.setTaskManager(taskManager);
        infra.setMessageManager(messageManager);

        assertSame(messager, infra.getMessager());
        assertSame(backend, infra.getTeamBackend());
        assertSame(workspaceManager, infra.getWorkspaceManager());
        assertTrue(infra.isWorkspaceInitialized());
        assertSame(taskManager, infra.getTaskManager());
        assertSame(messageManager, infra.getMessageManager());
    }

    private static final class RecordingMessager implements Messager {
        @Override
        public CompletionStage<Void> start() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stop() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> publish(String topicId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> subscribe(String topicId, MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unsubscribe(String topicId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> send(String agentId, EventMessage message) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> registerDirectMessageHandler(MessagerHandler handler) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> unregisterDirectMessageHandler() {
            return CompletableFuture.completedFuture(null);
        }
    }
}
