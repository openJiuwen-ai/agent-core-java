/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python tests for {@code openjiuwen/core/runner/resources_manager/agent_team_manager.py}.
 */
class AgentTeamManagerTest {

    @Test
    void providerIsResolvedLazilyByTeamId() {
        AgentTeamManager manager = new AgentTeamManager();
        Object team = new Object();

        manager.addAgentTeam("team-1", () -> team);

        assertSame(team, manager.getAgentTeam("team-1").toCompletableFuture().join());
        assertNull(manager.getAgentTeam("missing").toCompletableFuture().join());
    }

    @Test
    void duplicateProviderIsRejected() {
        AgentTeamManager manager = new AgentTeamManager();
        manager.addAgentTeam("team-1", Object::new);

        assertThrows(IllegalArgumentException.class,
                () -> manager.addAgentTeam("team-1", Object::new));
    }

    @Test
    void removeAgentTeamReturnsProviderAndClearsIt() {
        AgentTeamManager manager = new AgentTeamManager();
        Supplier<Object> provider = Object::new;
        manager.addAgentTeam("team-1", provider);

        assertSame(provider, manager.removeAgentTeam("team-1"));
        assertNull(manager.getAgentTeam("team-1").toCompletableFuture().join());
    }
}
