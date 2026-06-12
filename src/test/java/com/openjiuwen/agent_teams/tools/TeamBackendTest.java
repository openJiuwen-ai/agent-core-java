/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.schema.BridgeMemberSpec;
import com.openjiuwen.agent_teams.schema.ExternalCliAgentSpec;
import com.openjiuwen.agent_teams.schema.MemberOpResult;
import com.openjiuwen.agent_teams.schema.TeamCompletionSnapshot;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.status.ExecutionStatus;
import com.openjiuwen.agent_teams.schema.status.MemberMode;
import com.openjiuwen.agent_teams.schema.status.MemberStatus;
import com.openjiuwen.agent_teams.schema.status.TaskStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Focused parity tests for {@link TeamBackend}.
 *
 * <p>Mirrors Python's {@code TeamBackend} in
 * {@code openjiuwen/agent_teams/tools/team.py}.</p>
 */
class TeamBackendTest {

    @TempDir
    private Path tempDir;

    @AfterEach
    void resetSessionContext() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void buildTeamAppliesCapabilityOverridesAndRegistersAllowedMembers() {
        AgentTeamsContext.setSessionId("backend-session");
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        AtomicInteger builtCallbacks = new AtomicInteger();
        TeamBackend backend = new TeamBackend(
                "team-a",
                "leader",
                true,
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(teammate("worker"), human("human"), bridge("bridge")),
                null,
                null,
                true,
                true,
                List.of(externalConfig("codex")),
                null,
                () -> {
                    builtCallbacks.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                },
                tempDir,
                "team plan",
                null
        );

        backend.buildTeam(
                        "Team A",
                        "do work",
                        "Leader",
                        "lead",
                        new TeamBackend.CapabilityOverrides(true, false))
                .toCompletableFuture()
                .join();

        assertThat(database.getTeam("team-a").join()).get()
                .extracting(Team::getDisplayName)
                .isEqualTo("Team A");
        assertThat(database.getMember("leader", "team-a").join()).get()
                .extracting(TeamMember::getStatus)
                .isEqualTo(MemberStatus.BUSY.value());
        assertThat(database.getMember("worker", "team-a").join()).isPresent();
        assertThat(database.getMember("human", "team-a").join()).get()
                .extracting(TeamMember::getRole)
                .isEqualTo(TeamRole.HUMAN_AGENT.value());
        assertThat(database.getMember("bridge", "team-a").join()).isEmpty();
        assertThat(backend.hittEnabled()).isTrue();
        assertThat(backend.bridgeEnabled()).isFalse();
        assertThat(backend.humanAgentNames().toCompletableFuture().join()).containsExactly("human");
        assertThat(backend.bridgeAgentNames()).doesNotContain("bridge");
        assertThat(backend.externalCliKinds()).containsExactly("codex");
        assertThat(builtCallbacks).hasValue(1);
        assertThat(messager.eventTypes()).contains("team_created");
    }

    @Test
    void startupMemberUsesStartingGuardAndRollsBackOnSpawnFailure() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.BUILD_MODE, List.of(teammate("worker")));
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();

        boolean started = backend.startupMember("worker", member -> CompletableFuture.completedFuture(null))
                .toCompletableFuture()
                .join();
        boolean secondStart = backend.startupMember("worker", member -> CompletableFuture.completedFuture(null))
                .toCompletableFuture()
                .join();
        backend.spawnMember(
                        "failer",
                        "Failer",
                        new AgentConfigurator.AgentCard("team-a_failer", "Failer", "desc"),
                        "desc",
                        null,
                        MemberStatus.UNSTARTED,
                        ExecutionStatus.IDLE,
                        MemberMode.BUILD_MODE,
                        null,
                        TeamRole.TEAMMATE)
                .toCompletableFuture()
                .join();

        assertThatThrownBy(() -> backend.startupMember(
                        "failer",
                        member -> CompletableFuture.failedFuture(new IllegalStateException("spawn failed")))
                .toCompletableFuture()
                .join()).hasRootCauseMessage("spawn failed");

        assertThat(started).isTrue();
        assertThat(secondStart).isFalse();
        assertThat(database.getMember("worker", "team-a").join()).get()
                .extracting(TeamMember::getStatus)
                .isEqualTo(MemberStatus.STARTING.value());
        assertThat(database.getMember("failer", "team-a").join()).get()
                .extracting(TeamMember::getStatus)
                .isEqualTo(MemberStatus.UNSTARTED.value());
        assertThat(messager.eventTypes()).contains("member_spawned");
    }

    @Test
    void approvePlanUsesSubmittedPlanIdAndDelegatesDecisionToTaskManager() throws Exception {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.PLAN_MODE, List.of(teammate("planner")));
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        backend.getTaskManager().add("Plan task", "write plan", "task-plan", null).toCompletableFuture().join();
        TeamTaskManager planner = new TeamTaskManager("team-a", "planner", database, messager, tempDir, "team plan", "leader");
        Path sourcePlan = tempDir.resolve("source-plan.md");
        Files.writeString(sourcePlan, "# Plan\n\nDo it.");

        Map<String, Object> submit = planner.submitPlan("task-plan", sourcePlan.toString(), "plan:one", "tool-1")
                .toCompletableFuture()
                .join();
        boolean approved = backend.approvePlan("plan_one", true, "ok").toCompletableFuture().join();

        assertThat(submit).containsEntry("success", true);
        assertThat(approved).isTrue();
        assertThat(database.getTask("task-plan").join()).get()
                .extracting(TeamTask::getStatus)
                .isEqualTo(TaskStatus.PLAN_APPROVED.value());
        assertThat(backend.approvePlan("missing", true, null).toCompletableFuture().join()).isFalse();
    }

    @Test
    void completionSnapshotRequiresTerminalTasksSettledMembersAndNoUnreadMessages() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = backend(database, messager, MemberMode.BUILD_MODE, List.of(teammate("worker")));
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();
        backend.getTaskManager().add("Task", "content", "task-1", null).toCompletableFuture().join();
        backend.getTaskManager().claim("task-1").toCompletableFuture().join();
        backend.getTaskManager().complete("task-1").toCompletableFuture().join();
        database.updateMemberStatus("leader", "team-a", MemberStatus.READY.value()).join();
        database.updateMemberStatus("worker", "team-a", MemberStatus.READY.value()).join();

        Optional<TeamCompletionSnapshot> snapshot = backend.isTeamCompleted().toCompletableFuture().join();

        assertThat(snapshot).get().satisfies(value -> {
            assertThat(value.getMemberCount()).isEqualTo(2);
            assertThat(value.getTaskCount()).isEqualTo(1);
        });
    }

    @Test
    void dynamicBridgeAndExternalCliSpawnsPreserveRegistriesAndFailureReasons() {
        InMemoryTeamDatabase database = new InMemoryTeamDatabase();
        RecordingMessager messager = new RecordingMessager();
        TeamBackend backend = new TeamBackend(
                "team-a",
                "leader",
                true,
                database,
                messager,
                MemberMode.BUILD_MODE,
                List.of(),
                null,
                null,
                false,
                true,
                List.of(externalConfig("codex")),
                null,
                null,
                tempDir,
                null,
                null
        );
        backend.buildTeam("Team", "desc", "Leader", "lead").toCompletableFuture().join();

        MemberOpResult bridge = backend.spawnBridgeAgent(
                        "remote",
                        "Remote",
                        "remote persona",
                        null,
                        null,
                        BridgeMailboxInjectMode.REPHRASE,
                        "proto",
                        Map.of("timeout", 3))
                .toCompletableFuture()
                .join();
        MemberOpResult cli = backend.spawnExternalCliAgent(
                        "cli-worker",
                        "CLI",
                        "codex",
                        "cli persona",
                        null,
                        null)
                .toCompletableFuture()
                .join();
        MemberOpResult undeclared = backend.spawnExternalCliAgent(
                        "bad-cli",
                        "Bad",
                        "unknown",
                        "persona",
                        null,
                        null)
                .toCompletableFuture()
                .join();

        assertThat(bridge.isOk()).isTrue();
        assertThat(backend.isBridgeAgent("remote")).isTrue();
        assertThat(backend.getBridgeMemberSpec("remote").getMailboxInjectMode()).isEqualTo(BridgeMailboxInjectMode.REPHRASE);
        assertThat(cli.isOk()).isTrue();
        assertThat(backend.isExternalCliAgent("cli-worker")).isTrue();
        assertThat(backend.getExternalCliAgent("cli-worker")).isEqualTo("codex");
        assertThat(undeclared.isOk()).isFalse();
        assertThat(undeclared.getReason()).contains("not declared");
    }

    private TeamBackend backend(
            InMemoryTeamDatabase database,
            RecordingMessager messager,
            MemberMode teammateMode,
            List<TeamMemberSpec> predefinedMembers) {
        return new TeamBackend(
                "team-a",
                "leader",
                true,
                database,
                messager,
                teammateMode,
                predefinedMembers,
                null,
                null,
                true,
                true,
                List.of(),
                null,
                null,
                tempDir,
                "team plan",
                null
        );
    }

    private static TeamMemberSpec teammate(String name) {
        TeamMemberSpec spec = new TeamMemberSpec(name, title(name), TeamRole.TEAMMATE, name + " persona");
        spec.setPromptHint(name + " prompt");
        return spec;
    }

    private static TeamMemberSpec human(String name) {
        TeamMemberSpec spec = new TeamMemberSpec(name, title(name), TeamRole.HUMAN_AGENT, name + " persona");
        spec.setPromptHint(name + " prompt");
        return spec;
    }

    private static BridgeMemberSpec bridge(String name) {
        BridgeMemberSpec spec = new BridgeMemberSpec();
        spec.setMemberName(name);
        spec.setDisplayName(title(name));
        spec.setPersona(name + " persona");
        spec.setMailboxInjectMode(BridgeMailboxInjectMode.PASSTHROUGH);
        return spec;
    }

    private static ExternalCliAgentSpec externalConfig(String cliAgent) {
        ExternalCliAgentSpec spec = new ExternalCliAgentSpec();
        spec.setCliAgent(cliAgent);
        return spec;
    }

    private static String title(String name) {
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /**
     * Test messager that records backend event publication.
     *
     * <p>Mirrors Python's {@code TeamBackend.messager} collaboration in
     * {@code openjiuwen/agent_teams/tools/team.py}.</p>
     */
    private static final class RecordingMessager implements Messager {
        private final List<String> publishedTopics = new ArrayList<>();
        private final List<EventMessage> publishedMessages = new ArrayList<>();

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
            publishedTopics.add(topicId);
            publishedMessages.add(message);
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

        private List<String> eventTypes() {
            return publishedMessages.stream().map(EventMessage::getEventType).toList();
        }
    }
}
