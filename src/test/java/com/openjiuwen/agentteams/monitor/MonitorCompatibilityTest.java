package com.openjiuwen.agentteams.monitor;

import com.openjiuwen.agentteams.messager.InProcessMessager;
import com.openjiuwen.agentteams.messager.MessagerTransportConfig;
import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.SpawnManager;
import com.openjiuwen.agentteams.agent.TeamAgent;
import com.openjiuwen.agentteams.factory.TeamFactory;
import com.openjiuwen.agentteams.schema.blueprint.TeamAgentSpec;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamMemberSpec;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.SpawnHandle;
import com.openjiuwen.core.runner.spawn.SpawnConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorCompatibilityTest {

    @AfterEach
    void cleanup() {
        InProcessMessager.cleanupInprocessBus();
    }

    @Test
    void monitorShouldQueryBackendStateAndReceiveEvents() throws Exception {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        // Unique team id: MEMORY TeamDatabase is process-shared; "team-a" leaks members from other tests.
        TeamBackend backend = new TeamBackend("team-monitor-query", "leader", true, messager);
        backend.spawnMember("member1", "Member One", AgentCard.builder().name("agent").description("desc").build()).join();

        TeamMonitor monitor = new TeamMonitor(backend);
        monitor.start();

        backend.getTaskManager().add("Task 1", "Content 1").join();
        backend.getMessageManager().sendMessage("hello", "member1").join();

        assertThat(monitor.getTeamInfo().orElseThrow().getTeamId()).isEqualTo("team-monitor-query");
        assertThat(monitor.getMembers()).hasSize(2);
        assertThat(monitor.getTasks()).hasSize(1);
        assertThat(monitor.getMessages()).isNotEmpty();

        MonitorEvent event = monitor.nextEvent();
        assertThat(event.getTeamId()).isEqualTo("team-monitor-query");
    }

    @Test
    void monitorShouldReceiveMemberStatusAndExecutionChangesFromBackendLikePythonMemberUpdates() throws Exception {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-recovery-events", "leader", true, messager);
        backend.spawnMember("worker-1", "Worker One", AgentCard.builder().name("agent").description("desc").build()).join();
        TeamMonitor monitor = new TeamMonitor(backend);

        monitor.start();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isTrue();
        assertThat(backend.updateMemberStatus("worker-1", MemberStatus.READY)).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", "starting")).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", "running")).isTrue();
        assertThat(backend.updateMemberExecutionStatus("worker-1", "running")).isTrue();

        MonitorEvent status = monitor.nextEvent();
        MonitorEvent executionStarting = monitor.nextEvent();
        MonitorEvent executionRunning = monitor.nextEvent();
        assertThat(status.getEventType()).isEqualTo(MonitorEventType.MEMBER_STATUS_CHANGED);
        assertThat(status.getTeamId()).isEqualTo("team-recovery-events");
        assertThat(status.getMemberId()).isEqualTo("worker-1");
        assertThat(status.getOldStatus()).isEqualTo("unstarted");
        assertThat(status.getNewStatus()).isEqualTo("ready");
        assertThat(executionStarting.getEventType()).isEqualTo(MonitorEventType.MEMBER_EXECUTION_CHANGED);
        assertThat(executionStarting.getOldStatus()).isEmpty();
        assertThat(executionStarting.getNewStatus()).isEqualTo("starting");
        assertThat(executionRunning.getEventType()).isEqualTo(MonitorEventType.MEMBER_EXECUTION_CHANGED);
        assertThat(executionRunning.getOldStatus()).isEqualTo("starting");
        assertThat(executionRunning.getNewStatus()).isEqualTo("running");
        assertThat(monitor.hasQueuedEvents()).isFalse();
    }

    @Test
    void monitorShouldObserveProcessHealthRecoveryStatusAndRestartEvents() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("team-monitor-process-recovery")
                .spawnMode("process")
                .members(List.of(
                        TeamMemberSpec.builder()
                                .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                .role(TeamRole.LEADER)
                                .build(),
                        TeamMemberSpec.builder()
                                .name("worker-1")
                                .role(TeamRole.MEMBER)
                                .description("Worker one")
                                .build()
                ))
                .build());
        agent.resumeForNewSession("monitor-process-recovery-session");
        TeamMonitor monitor = new TeamMonitor(agent.getTeamBackend());
        TeamRuntimeContext ctx = TeamRuntimeContext.builder()
                .teamId("team-monitor-process-recovery")
                .sessionId("monitor-process-recovery-session")
                .memberName("worker-1")
                .role(TeamRole.MEMBER)
                .build();

        monitor.start();
        SpawnHandle firstHandle = agent.getSpawnManager().spawnTeammate(
                ctx,
                "run long enough for monitor recovery",
                SpawnConfig.builder().healthCheckInterval(0.01).healthCheckTimeout(0.001).build()
        );

        MonitorEvent restarting = nextEventOfType(monitor, MonitorEventType.MEMBER_STATUS_CHANGED);
        MonitorEvent restarted = nextEventOfType(monitor, MonitorEventType.MEMBER_RESTARTED);

        assertThat(firstHandle.isAlive()).isFalse();
        assertThat(restarting.getTeamId()).isEqualTo("team-monitor-process-recovery");
        assertThat(restarting.getMemberId()).isEqualTo("worker-1");
        assertThat(restarting.getOldStatus()).isEqualTo("unstarted");
        assertThat(restarting.getNewStatus()).isEqualTo("restarting");
        assertThat(restarted.getTeamId()).isEqualTo("team-monitor-process-recovery");
        assertThat(restarted.getMemberId()).isEqualTo("worker-1");
        assertThat(restarted.getReason()).isEqualTo("health_check_failure");
        assertThat(agent.getTeamBackend().getMember("worker-1").getStatus()).isEqualTo(MemberStatus.RESTARTING);
        assertThat(agent.getSpawnManager().getSpawnedHandles().get("worker-1")).isNotSameAs(firstHandle);

        agent.getSpawnManager().shutdownAllHandles();
    }

    @Test
    void monitorShouldObserveRestartFailureReturningMemberToError() throws Exception {
        TeamAgent agent = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("team-monitor-recovery-fail")
                .spawnMode("inprocess")
                .members(List.of(
                        TeamMemberSpec.builder()
                                .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                .role(TeamRole.LEADER)
                                .build(),
                        TeamMemberSpec.builder()
                                .name("worker-1")
                                .role(TeamRole.MEMBER)
                                .description("Worker one")
                                .build()
                ))
                .build());
        agent.resumeForNewSession("monitor-recovery-fail-session");
        TeamMonitor monitor = new TeamMonitor(agent.getTeamBackend());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(0, 1, 60L,
                java.util.concurrent.TimeUnit.SECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>());
        executor.shutdownNow();
        SpawnManager failingSpawnManager = new SpawnManager(
                agent,
                agent.getTeamBackend(),
                agent.getRecoveryManager(),
                () -> agent.getContext().getSessionId(),
                executor
        );

        monitor.start();
        agent.getTeamBackend().forceUpdateMemberStatus("worker-1", MemberStatus.RESTARTING);
        monitor.nextEvent();

        boolean restarted = failingSpawnManager.restartTeammate("worker-1", 1);

        MonitorEvent error = monitor.nextEvent();
        assertThat(restarted).isFalse();
        assertThat(error.getEventType()).isEqualTo(MonitorEventType.MEMBER_STATUS_CHANGED);
        assertThat(error.getTeamId()).isEqualTo("team-monitor-recovery-fail");
        assertThat(error.getMemberId()).isEqualTo("worker-1");
        assertThat(error.getOldStatus()).isEqualTo("restarting");
        assertThat(error.getNewStatus()).isEqualTo("error");
        assertThat(agent.getTeamBackend().getMember("worker-1").getStatus()).isEqualTo(MemberStatus.ERROR);
    }

    @Test
    void monitorQueriesShouldUseDatabaseBackedFiltersLikePythonMonitor() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-filter", "leader", true, messager);
        backend.spawnMember("member1", "Member One", AgentCard.builder().name("agent").description("dev").build()).join();
        backend.getDb().member.updateMemberStatus("member1", "team-filter", "busy");
        backend.updateMemberExecutionStatus("member1", "starting");
        backend.getTaskManager().add("Pending", "p", "task-pending", List.of()).join();
        backend.getTaskManager().add("Blocked", "b", "task-blocked", List.of("task-pending")).join();
        backend.getMessageManager().sendMessage("from leader", "member1", "leader").join();
        backend.getMessageManager().sendMessage("from member", "leader", "member1").join();
        backend.getMessageManager().broadcastMessage("broadcast", "member1").join();
        TeamMonitor monitor = new TeamMonitor(backend);

        assertThat(monitor.getMembers("busy"))
                .singleElement()
                .satisfies(member -> {
                    assertThat(member.getMemberId()).isEqualTo("member1");
                    assertThat(member.getExecutionStatus()).isEqualTo("starting");
                });
        assertThat(monitor.getMember("member1").getStatus()).isEqualTo("busy");
        assertThat(monitor.getMember("missing")).isNull();
        assertThat(monitor.getTasks("blocked"))
                .singleElement()
                .satisfies(task -> {
                    assertThat(task.getTaskId()).isEqualTo("task-blocked");
                    assertThat(task.getUpdatedAt()).isNotNull().isPositive();
                });
        assertThat(monitor.getMessages("member1", "leader"))
                .singleElement()
                .satisfies(message -> {
                    assertThat(message.getContent()).isEqualTo("from leader");
                    assertThat(message.getTimestamp()).isPositive();
                    assertThat(message.isBroadcast()).isFalse();
                });
        assertThat(monitor.getMessages(null, "member1"))
                .extracting(MessageInfo::getContent)
                .containsExactlyInAnyOrder("from member", "broadcast");
    }

    @Test
    void monitorShouldQueryTeamInfoFromDatabaseLikePythonMonitor() {
        InProcessMessager messager = new InProcessMessager(MessagerTransportConfig.builder().nodeId("leader").build());
        TeamBackend backend = new TeamBackend("team-db-info", "leader", true, messager);
        TeamMonitor monitor = new TeamMonitor(backend);

        TeamInfo info = monitor.getTeamInfo().orElseThrow();
        assertThat(info.getTeamId()).isEqualTo("team-db-info");
        assertThat(info.getName()).isEqualTo("team-db-info");
        assertThat(info.getLeaderId()).isEqualTo("leader");
        assertThat(info.getCreated()).isPositive();

        backend.getDb().team.deleteTeam("team-db-info");
        assertThat(monitor.getTeamInfo()).isEmpty();
    }

    @Test
    void createMonitorShouldRequireLeaderTeamAgentLikePythonFactory() throws Exception {
        TeamAgent leader = TeamFactory.createAgentTeam(TeamAgentSpec.builder()
                .name("team-monitor-factory")
                .members(List.of(TeamMemberSpec.builder()
                        .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                        .role(TeamRole.LEADER)
                        .build()))
                .build());

        TeamMonitor monitor = TeamMonitor.createMonitor(leader);
        assertThat(monitor.getTeamInfo().orElseThrow().getTeamId()).isEqualTo("team-monitor-factory");

        monitor.start();
        assertThat(leader.eventListeners()).hasSize(1);
        @SuppressWarnings("unchecked")
        Consumer<EventMessage> listener = (Consumer<EventMessage>) leader.eventListeners().get(0);
        listener.accept(EventMessage.builder()
                .eventType("member_restarted")
                .payload(Map.of(
                        "team_name", "team-monitor-factory",
                        "member_name", "worker-1",
                        "reason", "health_check_failure",
                        "restart_count", 1))
                .build());
        MonitorEvent event = monitor.nextEvent();
        assertThat(event.getEventType()).isEqualTo(MonitorEventType.MEMBER_RESTARTED);
        assertThat(event.getTeamId()).isEqualTo("team-monitor-factory");
        assertThat(event.getMemberId()).isEqualTo("worker-1");
        monitor.stop();
        assertThat(leader.eventListeners()).isEmpty();
        MonitorEvent sentinel = monitor.nextEvent();
        assertThat(sentinel.getEventType()).isNull();

        TeamAgent teammate = new TeamAgent().configure(TeamAgentSpec.builder()
                        .name("team-monitor-factory-member")
                        .members(List.of(
                                TeamMemberSpec.builder()
                                        .name(TeamConstants.DEFAULT_LEADER_MEMBER_NAME)
                                        .role(TeamRole.LEADER)
                                        .build(),
                                TeamMemberSpec.builder()
                                        .name("worker-1")
                                        .role(TeamRole.MEMBER)
                                        .build()))
                        .build(),
                TeamRuntimeContext.builder()
                        .teamId("team-monitor-factory-member")
                        .memberName("worker-1")
                        .role(TeamRole.MEMBER)
                        .build());

        assertThatThrownBy(() -> TeamMonitor.createMonitor(teammate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("leader TeamAgent");
    }

    @Test
    void monitorEventShouldFlattenPublicTeamMemberTaskAndMessageEventsOnly() {
        MonitorEvent team = MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("team_created")
                .payload(Map.of(
                        "team_name", "team-a",
                        "display_name", "Team A",
                        "leader_member_name", "leader",
                        "created", 1234L))
                .build(), "fallback-team");
        assertThat(team.getEventType()).isEqualTo(MonitorEventType.TEAM_CREATED);
        assertThat(team.getTeamId()).isEqualTo("team-a");
        assertThat(team.getName()).isEqualTo("Team A");
        assertThat(team.getLeaderId()).isEqualTo("leader");
        assertThat(team.getCreated()).isEqualTo(1234L);

        MonitorEvent member = MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("member_restarted")
                .payload(Map.of(
                        "team_name", "team-a",
                        "member_name", "worker-1",
                        "reason", "health_check_failure",
                        "restart_count", 2))
                .build(), "fallback-team");
        assertThat(member.getEventType()).isEqualTo(MonitorEventType.MEMBER_RESTARTED);
        assertThat(member.getMemberId()).isEqualTo("worker-1");
        assertThat(member.getReason()).isEqualTo("health_check_failure");
        assertThat(member.getRestartCount()).isEqualTo(2);

        MonitorEvent status = MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("member_execution_changed")
                .payload(Map.of("team_name", "team-a", "member_name", "worker-1", "old_status", "running", "new_status", "completed"))
                .build(), "fallback-team");
        assertThat(status.getEventType()).isEqualTo(MonitorEventType.MEMBER_EXECUTION_CHANGED);
        assertThat(status.getOldStatus()).isEqualTo("running");
        assertThat(status.getNewStatus()).isEqualTo("completed");

        MonitorEvent task = MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("task_unblocked")
                .payload(Map.of("team_name", "team-a", "task_id", "task-1", "status", "pending"))
                .build(), "fallback-team");
        assertThat(task.getEventType()).isEqualTo(MonitorEventType.TASK_UNBLOCKED);
        assertThat(task.getTaskId()).isEqualTo("task-1");
        assertThat(task.getStatus()).isEqualTo("pending");

        MonitorEvent message = MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("message")
                .payload(Map.of("team_name", "team-a", "message_id", "msg-1", "from_member_name", "leader", "to_member_name", "worker-1"))
                .build(), "fallback-team");
        assertThat(message.getEventType()).isEqualTo(MonitorEventType.MESSAGE);
        assertThat(message.getMessageId()).isEqualTo("msg-1");
        assertThat(message.getFromMember()).isEqualTo("leader");
        assertThat(message.getToMember()).isEqualTo("worker-1");

        assertThat(MonitorEvent.fromEventMessage(EventMessage.builder()
                .eventType("tool_approval_result")
                .payload(Map.of("team_name", "team-a"))
                .build(), "fallback-team")).isNull();
    }

    private static MonitorEvent nextEventOfType(TeamMonitor monitor, MonitorEventType eventType) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            MonitorEvent event = monitor.nextEvent();
            if (eventType == event.getEventType()) {
                return event;
            }
        }
        throw new AssertionError("Timed out waiting for monitor event " + eventType);
    }
}
