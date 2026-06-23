/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.interaction.DeliverResult;
import com.openjiuwen.agent_teams.interaction.GodViewMessage;
import com.openjiuwen.core.session.interaction.InteractiveInput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Tests runtime manager lifecycle and interact behavior.
 *
 * <p>Mirrors Python's {@code TeamRuntimeManager} tests for
 * {@code openjiuwen/agent_teams/runtime/manager.py}.</p>
 *
 * <p>Mirrors Python's {@code tests.unit_tests.agent_teams.runtime.test_manager} in
 * {@code tests/unit_tests/agent_teams/runtime/test_manager.py}.</p>
 */
class TeamRuntimeManagerTest {

    @Test
    void activateCreateBuildsAgentFlushesManifestAndAddsPoolEntry() {
        FakeSession session = new FakeSession("s1");
        FakeAgent agent = new FakeAgent();
        FakeSpec spec = new FakeSpec("team-a", agent);
        TeamRuntimeManager manager = new TeamRuntimeManager();

        TeamRuntimeActivation activation = manager.activate(spec, session, Map.of("query", "hi"))
                .toCompletableFuture().join();

        assertThat(activation.agent()).isSameAs(agent);
        assertThat(activation.session()).isSameAs(session);
        assertThat(activation.action().kind()).isEqualTo(RunActionKind.CREATE);
        assertThat(activation.action().requireSpec()).isTrue();
        assertThat(spec.buildCalls).isEqualTo(1);
        assertThat(agent.persistedSessions).containsExactly(session);
        assertThat(session.preRunInputs).containsExactly(Map.of("query", "hi"));
        assertThat(session.flushCalls).isEqualTo(1);
        assertThat(manager.pool().hasActive("team-a")).isTrue();
    }

    @Test
    void activateResumesPausedPoolEntryAndResetsGate() {
        FakeSession session = new FakeSession("s1");
        FakeAgent agent = new FakeAgent();
        TeamRuntimeManager manager = new TeamRuntimeManager(
                (spec, currentSession, teamName) -> CompletableFuture.completedFuture(
                        new TeamRuntimeManager.SessionInspection(true, true, null)
                ),
                TeamRuntimeManager.RuntimeCleanup.noop(),
                (runtime, hideDm) -> runtime
        );
        TeamRuntimeManager.RuntimeEntry entry = new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.PAUSED
        );
        manager.pool().add(entry);
        entry.interactGate().closeAndDrain();

        TeamRuntimeActivation activation = manager.activate(new FakeSpec("team-a", new FakeAgent()), session)
                .toCompletableFuture().join();

        assertThat(activation.agent()).isSameAs(agent);
        assertThat(activation.action().kind()).isEqualTo(RunActionKind.RESUME_FROM_PAUSE);
        assertThat(entry.runtimeState()).isEqualTo(TeamRuntimeManager.RuntimeState.RUNNING);
        assertThat(entry.interactGate().isClosed()).isFalse();
    }

    @Test
    void pauseAndStopRequireExactTeamSessionEntry() {
        TeamRuntimeManager manager = new TeamRuntimeManager();
        FakeAgent agent = new FakeAgent();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        assertThat(manager.pause("team-a", "other").toCompletableFuture().join()).isFalse();
        assertThat(manager.pause("team-a", "s1").toCompletableFuture().join()).isTrue();
        assertThat(manager.pool().get("team-a").runtimeState()).isEqualTo(TeamRuntimeManager.RuntimeState.PAUSED);
        assertThat(agent.pauseCalls).isEqualTo(1);

        assertThat(manager.stopTeam("other", "s1").toCompletableFuture().join()).isFalse();
        assertThat(manager.stopTeam("team-a", "s1").toCompletableFuture().join()).isTrue();
        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(manager.pool().hasActive("team-a")).isFalse();
    }

    @Test
    void finalizePausesPersistentAndStopsTemporaryOrShutdownRequestedTeams() {
        TeamRuntimeManager manager = new TeamRuntimeManager();
        FakeAgent persistent = new FakeAgent();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "persistent",
                persistent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        manager.finalizeTeam("persistent", "s1").toCompletableFuture().join();
        assertThat(persistent.pauseCalls).isEqualTo(1);
        assertThat(manager.pool().get("persistent").runtimeState()).isEqualTo(TeamRuntimeManager.RuntimeState.PAUSED);

        FakeAgent temporary = new FakeAgent();
        temporary.lifecycle = "temporary";
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "temporary",
                temporary,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));
        manager.finalizeTeam("temporary", "s1").toCompletableFuture().join();
        assertThat(temporary.stopCalls).isEqualTo(1);
        assertThat(manager.pool().hasActive("temporary")).isFalse();

        FakeAgent shutdown = new FakeAgent();
        shutdown.shutdownRequested = true;
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "shutdown",
                shutdown,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));
        manager.finalizeTeam("shutdown", "s1").toCompletableFuture().join();
        assertThat(shutdown.stopCalls).isEqualTo(1);
        assertThat(manager.pool().hasActive("shutdown")).isFalse();
    }

    @Test
    void finalizeMemberHonorsTerminalStatusesAndShutdownRequests() {
        FakeAgent terminalAgent = new FakeAgent();
        terminalAgent.member = new FakeMember(TeamRuntimeManager.MemberStatus.STOPPED);
        TeamRuntimeManager.finalizeMember(terminalAgent).toCompletableFuture().join();
        assertThat(terminalAgent.stopCalls).isEqualTo(1);
        assertThat(terminalAgent.member.updated).isEmpty();

        FakeAgent shutdownAgent = new FakeAgent();
        shutdownAgent.member = new FakeMember(TeamRuntimeManager.MemberStatus.SHUTDOWN_REQUESTED);
        TeamRuntimeManager.finalizeMember(shutdownAgent).toCompletableFuture().join();
        assertThat(shutdownAgent.stopCalls).isEqualTo(1);
        assertThat(shutdownAgent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.SHUTDOWN);

        FakeAgent readyAgent = new FakeAgent();
        readyAgent.member = new FakeMember(TeamRuntimeManager.MemberStatus.READY);
        TeamRuntimeManager.finalizeMember(readyAgent).toCompletableFuture().join();
        assertThat(readyAgent.pauseCalls).isEqualTo(1);
        assertThat(readyAgent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberShutdownRequestedTransitionsToShutdown() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.SHUTDOWN_REQUESTED);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(agent.pauseCalls).isZero();
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.SHUTDOWN);
    }

    @Test
    void finalizeMemberReadyPausesAndMarksReady() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.READY);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberBusyPausesAndMarksReady() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.BUSY);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberAlreadyShutdownSkipsStatusWrite() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.SHUTDOWN);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(agent.pauseCalls).isZero();
        assertThat(agent.member.updated).isEmpty();
    }

    @Test
    void finalizeMemberAlreadyStoppedSkipsStatusWrite() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.STOPPED);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(agent.pauseCalls).isZero();
        assertThat(agent.member.updated).isEmpty();
    }

    @Test
    void finalizeMemberAlreadyPausedSkipsStatusWrite() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.PAUSED);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(agent.pauseCalls).isZero();
        assertThat(agent.member.updated).isEmpty();
    }

    @Test
    void finalizeMemberNoTeamMemberPausesDefaultPath() {
        FakeAgent agent = new FakeAgent();

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
    }

    @Test
    void finalizeMemberNoTeamMemberPausesOnOtherStatus() {
        FakeAgent agent = new FakeAgent();

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
    }

    @Test
    void finalizeMemberStatusReadFailureFallsBackToPause() {
        FakeAgent agent = new FakeAgent();
        agent.member = new FailingMember();

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberUnstartedStatusPausesAndMarksReady() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.UNSTARTED);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberErrorStatusPausesAndMarksReady() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.ERROR);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void finalizeMemberRestartingStatusPausesAndMarksReady() {
        FakeAgent agent = agentWithMember(TeamRuntimeManager.MemberStatus.RESTARTING);

        TeamRuntimeManager.finalizeMember(agent).toCompletableFuture().join();

        assertThat(agent.stopCalls).isZero();
        assertThat(agent.pauseCalls).isEqualTo(1);
        assertThat(agent.member.updated).containsExactly(TeamRuntimeManager.MemberStatus.READY);
    }

    @Test
    void interactRoutesGodViewAndRejectsInactiveOrClosedGate() {
        TeamRuntimeManager manager = new TeamRuntimeManager();
        FakeAgent agent = new FakeAgent();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        DeliverResult result = manager.interact(new GodViewMessage("hi"), "team-a", "s1")
                .toCompletableFuture().join();
        assertThat(result.ok()).isTrue();
        assertThat(agent.delivered).containsExactly("hi");

        DeliverResult missing = manager.interact(new GodViewMessage("x"), "missing", "s1")
                .toCompletableFuture().join();
        assertThat(missing.ok()).isFalse();
        assertThat(missing.reason()).isEqualTo("not_active");

        manager.pool().get("team-a").interactGate().closeAndDrain();
        DeliverResult closed = manager.interact("follow", "team-a", "s1").toCompletableFuture().join();
        assertThat(closed.ok()).isFalse();
        assertThat(closed.reason()).isEqualTo("gate_closed");
    }

    @Test
    void interactInteractiveInputResumesOnlyWhenInterruptPending() {
        TeamRuntimeManager manager = new TeamRuntimeManager();
        FakeAgent agent = new FakeAgent();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        DeliverResult unsupported = manager.interact(new InteractiveInput("raw"), "team-a", "s1")
                .toCompletableFuture().join();
        assertThat(unsupported.ok()).isFalse();
        assertThat(unsupported.reason()).isEqualTo("unsupported_interactive_input");

        agent.pendingInterrupt = true;
        DeliverResult resumed = manager.interact(new InteractiveInput("raw"), "team-a", "s1")
                .toCompletableFuture().join();
        assertThat(resumed.ok()).isTrue();
        assertThat(agent.resumeInterruptCalls).isEqualTo(1);
    }

    @Test
    void releaseAndDeleteRefuseBusyTeamsUnlessForced() {
        RecordingCleanup cleanup = new RecordingCleanup();
        TeamRuntimeManager manager = new TeamRuntimeManager(
                TeamRuntimeManager.SessionInspector.empty(),
                cleanup,
                (agent, hideDm) -> agent
        );
        FakeAgent agent = new FakeAgent();
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                agent,
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        assertThatThrownBy(() -> manager.releaseSession("s1").toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        manager.releaseSession("s1", true).toCompletableFuture().join();
        assertThat(agent.stopCalls).isEqualTo(1);
        assertThat(cleanup.releasedSessions).containsExactly("s1");

        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                new FakeAgent(),
                "s2",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));
        assertThatThrownBy(() -> manager.deleteTeam("team-a", List.of("s2"), false).toCompletableFuture().join())
                .hasCauseInstanceOf(IllegalStateException.class);
        Boolean deleted = manager.deleteTeam("team-a", List.of("s2"), true).toCompletableFuture().join();
        assertThat(deleted).isTrue();
        assertThat(cleanup.deletedTeams).containsExactly("team-a");
    }

    @Test
    void getMonitorUsesFactoryForExactEntryOnly() {
        TeamRuntimeManager manager = new TeamRuntimeManager(
                TeamRuntimeManager.SessionInspector.empty(),
                TeamRuntimeManager.RuntimeCleanup.noop(),
                (agent, hideDm) -> hideDm ? "hidden" : "visible"
        );
        manager.pool().add(new TeamRuntimeManager.RuntimeEntry(
                "team-a",
                new FakeAgent(),
                "s1",
                TeamRuntimeManager.RuntimeState.RUNNING
        ));

        assertThat(manager.getMonitor("team-a", "s1", false).toCompletableFuture().join()).isEqualTo("visible");
        assertThat(manager.getMonitor("team-a", "s1", true).toCompletableFuture().join()).isEqualTo("hidden");
        assertThat(manager.getMonitor("team-a", "other", false).toCompletableFuture().join()).isNull();
    }

    private static FakeAgent agentWithMember(TeamRuntimeManager.MemberStatus status) {
        FakeAgent agent = new FakeAgent();
        agent.member = new FakeMember(status);
        return agent;
    }

    /**
     * Fake session used by manager contract tests.
     *
     * <p>Mirrors Python's session interactions exercised by
     * {@code openjiuwen/agent_teams/runtime/manager.py} tests.</p>
     */
    private static final class FakeSession implements TeamRuntimeManager.AgentTeamSessionView {
        private final String sessionId;
        private final List<Map<String, Object>> preRunInputs = new ArrayList<>();
        private int flushCalls;

        private FakeSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public CompletionStage<Void> preRun(Map<String, Object> inputs) {
            preRunInputs.add(inputs);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> flushCheckpoint() {
            flushCalls += 1;
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Fake team spec used by manager contract tests.
     *
     * <p>Mirrors Python's team spec interactions exercised by
     * {@code openjiuwen/agent_teams/runtime/manager.py} tests.</p>
     */
    private static final class FakeSpec implements TeamRuntimeManager.TeamSpecView {
        private final String teamName;
        private final FakeAgent agent;
        private int buildCalls;

        private FakeSpec(String teamName, FakeAgent agent) {
            this.teamName = teamName;
            this.agent = agent;
        }

        @Override
        public String teamName() {
            return teamName;
        }

        @Override
        public TeamRuntimeManager.TeamAgentRuntime build() {
            buildCalls += 1;
            return agent;
        }
    }

    /**
     * Fake team agent used by manager contract tests.
     *
     * <p>Mirrors Python's team agent interactions exercised by
     * {@code openjiuwen/agent_teams/runtime/manager.py} tests.</p>
     */
    private static class FakeAgent implements TeamRuntimeManager.TeamAgentRuntime {
        private final List<String> delivered = new ArrayList<>();
        private final List<TeamRuntimeManager.AgentTeamSessionView> persistedSessions = new ArrayList<>();
        private String lifecycle = "persistent";
        private boolean shutdownRequested;
        private boolean pendingInterrupt;
        private int pauseCalls;
        private int stopCalls;
        private int resumeInterruptCalls;
        private FakeMember member;

        @Override
        public CompletionStage<Void> deliverInput(String body) {
            delivered.add(body);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> pauseCoordination() {
            pauseCalls += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> stopCoordination() {
            stopCalls += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> isShutdownRequested() {
            return CompletableFuture.completedFuture(shutdownRequested);
        }

        @Override
        public String lifecycle() {
            return lifecycle;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return pendingInterrupt;
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(InteractiveInput input) {
            resumeInterruptCalls += 1;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void persistSessionManifest(TeamRuntimeManager.AgentTeamSessionView session) {
            persistedSessions.add(session);
        }

        @Override
        public TeamRuntimeManager.TeamMemberRuntime teamMember() {
            return member;
        }
    }

    /**
     * Fake team member used by finalize-member tests.
     *
     * <p>Mirrors Python's member status interactions exercised by
     * {@code openjiuwen/agent_teams/runtime/manager.py} tests.</p>
     */
    private static class FakeMember implements TeamRuntimeManager.TeamMemberRuntime {
        protected final List<TeamRuntimeManager.MemberStatus> updated = new ArrayList<>();
        private final TeamRuntimeManager.MemberStatus status;

        private FakeMember(TeamRuntimeManager.MemberStatus status) {
            this.status = status;
        }

        @Override
        public CompletionStage<TeamRuntimeManager.MemberStatus> status() {
            return CompletableFuture.completedFuture(status);
        }

        @Override
        public CompletionStage<Void> updateStatus(TeamRuntimeManager.MemberStatus status) {
            updated.add(status);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Fake member whose status lookup fails.
     *
     * <p>Mirrors Python's status read failure path in
     * {@code tests/unit_tests/agent_teams/runtime/test_manager.py}.</p>
     */
    private static final class FailingMember extends FakeMember {
        private FailingMember() {
            super(TeamRuntimeManager.MemberStatus.ERROR);
        }

        @Override
        public CompletionStage<TeamRuntimeManager.MemberStatus> status() {
            return CompletableFuture.failedFuture(new RuntimeException("db error"));
        }
    }

    /**
     * Recording cleanup boundary used by release/delete tests.
     *
     * <p>Mirrors Python's cleanup calls exercised by
     * {@code openjiuwen/agent_teams/runtime/manager.py} tests.</p>
     */
    private static final class RecordingCleanup implements TeamRuntimeManager.RuntimeCleanup {
        private final List<String> releasedSessions = new ArrayList<>();
        private final List<String> deletedTeams = new ArrayList<>();

        @Override
        public CompletionStage<Void> releaseSession(String sessionId) {
            releasedSessions.add(sessionId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> deleteTeam(String teamName, List<String> sessionIds) {
            deletedTeams.add(teamName);
            return CompletableFuture.completedFuture(true);
        }
    }
}
