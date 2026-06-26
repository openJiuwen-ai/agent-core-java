/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.AgentTeamsContext;
import com.openjiuwen.agent_teams.agent.coordination.CoordinationKernel.EventListener;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Focused tests for team monitor queries and live event filtering.
 *
 * <p>Mirrors Python's tests for
 * {@code openjiuwen/agent_teams/monitor/team_monitor.py}.</p>
 */
class TeamMonitorTest {

    @AfterEach
    void clearSession() {
        AgentTeamsContext.resetSessionId(null);
    }

    @Test
    void queriesBindMonitorSessionAndConvertRows() {
        FakeDatabase db = new FakeDatabase();
        RecordingEventSource source = new RecordingEventSource();
        AgentTeamsContext.setSessionId("outer-session");
        TeamMonitor monitor = new TeamMonitor("spec-team", "monitor-session", db, source, false);

        TeamInfo team = monitor.getTeamInfo().toCompletableFuture().join();
        List<MemberInfo> members = monitor.getMembers("active").toCompletableFuture().join();
        MemberInfo member = monitor.getMember("leader").toCompletableFuture().join();
        List<TaskInfo> tasks = monitor.getTasks("open").toCompletableFuture().join();
        List<MessageInfo> messages = monitor.getMessages("worker", "leader").toCompletableFuture().join();

        assertThat(team.teamName()).isEqualTo("spec-team");
        assertThat(members).extracting(MemberInfo::memberName).containsExactly("leader");
        assertThat(member.displayName()).isEqualTo("Leader");
        assertThat(tasks).extracting(TaskInfo::taskId).containsExactly("task-1");
        assertThat(messages).extracting(MessageInfo::messageId).containsExactly("dm-1");
        assertThat(db.sessionSnapshots).containsOnly("monitor-session");
        assertThat(AgentTeamsContext.getSessionId()).isEqualTo("outer-session");
    }

    @Test
    void hideDmDropsDirectQueriesAndDirectMessageEvents() {
        FakeDatabase db = new FakeDatabase();
        RecordingEventSource source = new RecordingEventSource();
        TeamMonitor monitor = new TeamMonitor("spec-team", "sid", db, source, true);

        assertThat(monitor.getMessages("worker", null).toCompletableFuture().join()).isEmpty();
        List<MessageInfo> messages = monitor.getMessages().toCompletableFuture().join();
        assertThat(messages).extracting(MessageInfo::messageId).containsExactly("broadcast-1");
        assertThat(db.lastBroadcastFilter).isTrue();

        monitor.start().toCompletableFuture().join();
        source.fire(event(TeamEvent.MESSAGE, Map.of("team_name", "spec-team", "message_id", "dm-1")));
        source.fire(event(TeamEvent.BROADCAST, Map.of("team_name", "spec-team", "message_id", "broadcast-1")));

        Optional<MonitorEvent> received = monitor.pollEvent(Duration.ofMillis(200));
        assertThat(received).isPresent();
        assertThat(received.get().eventType()).isEqualTo(MonitorEventType.BROADCAST);
        assertThat(monitor.pollEvent(Duration.ofMillis(20))).isEmpty();
    }

    @Test
    void startStopAreIdempotentAndStopTerminatesEventIterator() {
        FakeDatabase db = new FakeDatabase();
        RecordingEventSource source = new RecordingEventSource();
        TeamMonitor monitor = new TeamMonitor("spec-team", "sid", db, source, false);

        monitor.start().toCompletableFuture().join();
        monitor.start().toCompletableFuture().join();
        assertThat(source.listeners).hasSize(1);

        source.fire(event(TeamEvent.TASK_CREATED, Map.of("team_name", "spec-team", "task_id", "task-1")));
        assertThat(monitor.nextEvent().toCompletableFuture().join().eventType()).isEqualTo(MonitorEventType.TASK_CREATED);

        monitor.stop().toCompletableFuture().join();
        monitor.stop().toCompletableFuture().join();
        assertThat(source.listeners).isEmpty();
        assertThat(monitor.nextEvent().toCompletableFuture().join()).isNull();
    }

    @Test
    void unknownEventsAreSilentlyDropped() {
        FakeDatabase db = new FakeDatabase();
        RecordingEventSource source = new RecordingEventSource();
        TeamMonitor monitor = new TeamMonitor("spec-team", "sid", db, source, false);
        monitor.start().toCompletableFuture().join();

        source.fire(new EventMessage("unknown.event", Map.of("team_name", "spec-team"), ""));

        assertThat(monitor.pollEvent(Duration.ofMillis(20))).isEmpty();
    }

    private static EventMessage event(String type, Map<String, Object> payload) {
        return new EventMessage(type, new LinkedHashMap<>(payload), "");
    }

    private static final class RecordingEventSource implements TeamMonitor.EventSource {
        private final List<EventListener> listeners = new ArrayList<>();

        @Override
        public void addEventListener(EventListener listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public void removeEventListener(EventListener listener) {
            listeners.remove(listener);
        }

        void fire(EventMessage event) {
            for (EventListener listener : List.copyOf(listeners)) {
                listener.onEvent(event).toCompletableFuture().join();
            }
        }
    }

    private static final class FakeDatabase implements TeamMonitor.TeamMonitorDatabase {
        private final List<String> sessionSnapshots = new ArrayList<>();
        private Boolean lastBroadcastFilter;

        @Override
        public CompletionStage<Optional<?>> getTeam(String teamName) {
            rememberSession();
            return CompletableFuture.completedFuture(Optional.of(Map.of(
                    "team_name", teamName,
                    "display_name", "Spec Team",
                    "leader_member_name", "leader",
                    "desc", "demo",
                    "created", 1L
            )));
        }

        @Override
        public CompletionStage<List<?>> getTeamMembers(String teamName, String status) {
            rememberSession();
            return CompletableFuture.completedFuture(List.of(Map.of(
                    "member_name", "leader",
                    "team_name", teamName,
                    "display_name", "Leader",
                    "status", status,
                    "execution_status", "idle",
                    "mode", "agent"
            )));
        }

        @Override
        public CompletionStage<Optional<?>> getMember(String memberName, String teamName) {
            rememberSession();
            return CompletableFuture.completedFuture(Optional.of(Map.of(
                    "member_name", memberName,
                    "team_name", teamName,
                    "display_name", "Leader",
                    "status", "active",
                    "execution_status", "idle"
            )));
        }

        @Override
        public CompletionStage<List<?>> getTeamTasks(String teamName, String status) {
            rememberSession();
            return CompletableFuture.completedFuture(List.of(Map.of(
                    "task_id", "task-1",
                    "team_name", teamName,
                    "title", "Plan",
                    "content", "Do work",
                    "status", status,
                    "assignee", "leader",
                    "updated_at", 2L
            )));
        }

        @Override
        public CompletionStage<List<?>> getMessages(String teamName, String toMemberName, String fromMemberName) {
            rememberSession();
            return CompletableFuture.completedFuture(List.of(Map.of(
                    "message_id", "dm-1",
                    "team_name", teamName,
                    "from_member_name", fromMemberName,
                    "to_member_name", toMemberName,
                    "content", "hello",
                    "timestamp", 3L,
                    "broadcast", false,
                    "is_read", false
            )));
        }

        @Override
        public CompletionStage<List<?>> getTeamMessages(String teamName, Boolean broadcast) {
            rememberSession();
            lastBroadcastFilter = broadcast;
            return CompletableFuture.completedFuture(List.of(Map.of(
                    "message_id", broadcast == null ? "team-1" : "broadcast-1",
                    "team_name", teamName,
                    "from_member_name", "leader",
                    "to_member_name", "",
                    "content", "all",
                    "timestamp", 4L,
                    "broadcast", Boolean.TRUE.equals(broadcast),
                    "is_read", false
            )));
        }

        private void rememberSession() {
            sessionSnapshots.add(AgentTeamsContext.getSessionId());
        }
    }
}
