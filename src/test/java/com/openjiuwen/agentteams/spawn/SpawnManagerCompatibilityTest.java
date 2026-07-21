/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Mirrors Python 0.1.15 {@code test_spawn_manager_chunk_forward.py}.
 * Validates spawn handle registration, cleanup, and in-process spawn lifecycle.
 */
class SpawnManagerCompatibilityTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
        TeamBackend.resetSharedDbCache();
    }

    @Test
    void spawnManager_initialHandlesEmpty() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("spawn-team")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build());

        // Before explicit spawn, handles are empty (members registered but not spawned)
        assertThat(agent.getSpawnManager().getSpawnedHandles()).isEmpty();
    }

    @Test
    void spawnManager_cleanupIsIdempotent() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("spawn-cleanup")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build());

        // Cleanup non-existent handle should not throw
        agent.getSpawnManager().cleanupTeammate("dev-1");
        assertThat(agent.getSpawnManager().getSpawnedHandles()).doesNotContainKey("dev-1");
    }

    @Test
    void spawnManager_leaderNotInSpawnedHandles() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("spawn-leader")
                .members(List.of(
                        TeamMemberSpec.builder().name("leader1").role(TeamRole.LEADER).build(),
                        TeamMemberSpec.builder().name("dev-1").role(TeamRole.MEMBER).build()))
                .build());

        // Leader runs in-process, not spawned
        assertThat(agent.getSpawnManager().getSpawnedHandles()).doesNotContainKey("leader1");
    }

    @Test
    void inProcessSpawnHandle_interfaceExists() {
        // Verify SpawnHandle interface defines expected methods
        assertThat(SpawnHandle.class).isInterface();
        try {
            SpawnHandle.class.getMethod("isAlive");
            SpawnHandle.class.getMethod("isHealthy");
            SpawnHandle.class.getMethod("shutdown", Long.class);
            SpawnHandle.class.getMethod("forceKill");
            SpawnHandle.class.getMethod("waitForCompletion");
            SpawnHandle.class.getMethod("setOnUnhealthy", Runnable.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("SpawnHandle missing method: " + e.getMessage());
        }
    }
}
