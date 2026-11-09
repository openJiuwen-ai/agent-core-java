/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.LeaderTeammateAgentTeam;
import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.messager.PyZmqMessager;
import com.openjiuwen.agentteams.messager.TeamTransportRuntime;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.InProcessSpawn;
import com.openjiuwen.agentteams.spawn.InProcessSpawnHandle;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.core.runner.Runner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that public team builders and serialized spawn payloads use the same real transport ownership.
 *
 * @since 2026-11-09
 */
@Isolated("Team construction registers process-global Runner resources")
@Timeout(20)
class TeamTransportAssemblyTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<TeamAgent> agents = new ArrayList<>();

    @AfterEach
    void closeOwnedAgents() {
        try {
            agents.forEach(TeamAgent::close);
        } finally {
            Runner.stop();
            TeamBackend.resetSharedDbCache();
        }
    }

    @Test
    void publicBuilderInfersBackendAndCopiesTransportConfiguration() {
        MessagerTransportConfig settings = zmq();
        LeaderTeammateAgentTeam team = LeaderTeammateAgentTeam.builder().teamName(name())
                .transportConfig(settings).storage("memory").build();
        settings.setBackend("inprocess");
        settings.setRequestTimeout(99.0d);

        assertThat(team.transport()).isEqualTo("pyzmq");
        assertThat(team.spec().getTransportConfig().getBackend()).isEqualTo("pyzmq");
        assertThat(team.spec().getTransportConfig().getRequestTimeout()).isEqualTo(3.0d);
    }

    @Test
    void spawnJsonContainsResolvedEndpointsAndRestoresMemberTransport() throws Exception {
        TeamAgent leader = leader();
        TeamRuntimeContext context = member(leader);
        Map<String, Object> serialized = MAPPER.readValue(
                MAPPER.writeValueAsBytes(leader.buildSpawnConfig(context).getPayload()), new TypeReference<>() { });
        TeamAgent teammate = TeamAgent.fromSpawnPayload(serialized);
        agents.add(teammate);

        assertThat(teammate.getTeamBackend().getMessager()).isInstanceOf(PyZmqMessager.class);
        assertThat(teammate.getSpec().getStorage()).isEqualTo("memory");
        assertThat(teammate.getSpec().getSpawnMode()).isEqualTo("inprocess");
        MessagerTransportConfig resolved = teammate.getSpec().getTransportConfig();
        assertThat(resolved.getPubsubPublishAddr()).startsWith("tcp://127.0.0.1:").doesNotContain("*");
        assertThat(resolved.getPubsubSubscribeAddr()).startsWith("tcp://127.0.0.1:").doesNotContain("*");
        assertThat(resolved.getMetadata()).containsEntry("pubsub_bind", false);
        assertThat(resolved.getKnownPeers()).extracting(peer -> peer.getAgentId())
                .contains(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        assertThat(leader.getSpec().getTransportConfig().getDirectAddr()).isNull();
    }

    @Test
    void rejectedExecutorReleasesMemberTransportBeforeReportingFailure() {
        TeamAgent leader = leader();
        ExecutorService rejected = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException("fixture rejection")).when(rejected).execute(any(Runnable.class));

        assertThatThrownBy(() -> InProcessSpawn.inprocessSpawn(leader, member(leader), rejected, "", "session"))
                .isInstanceOf(RejectedExecutionException.class).hasMessage("fixture rejection");
        try (TeamTransportRuntime replacement = leader.getTransportRuntime().fork("worker")) {
            assertThat(replacement.messager()).isInstanceOf(PyZmqMessager.class);
        }
    }

    @Test
    void cancellationBeforeExecutorStartReleasesMemberWithoutRunningItLater() {
        TeamAgent leader = leader();
        ExecutorService queued = mock(ExecutorService.class);
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        doAnswer(invocation -> {
            submitted.set(invocation.getArgument(0));
            return null;
        }).when(queued).execute(any(Runnable.class));
        InProcessSpawnHandle handle = InProcessSpawn.inprocessSpawn(leader, member(leader), queued, "", "session");

        assertThat(handle.getTask().cancel(false)).isTrue();
        submitted.get().run();

        assertThat(handle.isAlive()).isFalse();
        try (TeamTransportRuntime replacement = leader.getTransportRuntime().fork("worker")) {
            assertThat(replacement.messager()).isInstanceOf(PyZmqMessager.class);
        }
    }

    private TeamAgent leader() {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder().name(name())
                .spawnMode("inprocess").transportConfig(zmq()).storage("memory").build());
        agents.add(agent);
        return agent;
    }

    private static TeamRuntimeContext member(TeamAgent leader) {
        return TeamRuntimeContext.builder().teamId(leader.getSpec().getName())
                .memberName("worker").role(TeamRole.MEMBER).sessionId("session").build();
    }

    private static MessagerTransportConfig zmq() {
        return MessagerTransportConfig.builder().backend("pyzmq").requestTimeout(3.0d).build();
    }

    private static String name() {
        return "assembly-" + UUID.randomUUID();
    }
}
