/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OtelTeamMonitorHandlerTest {

    @Test
    void teamMonitorHandlerEmitsTeamEventsAndTaskSpan() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(new ObservabilityConfig(), tracer);

        publish(handler, TeamEvent.CREATED, payload(
                "team_name", "alpha",
                "display_name", "Alpha Team",
                "leader_member_name", "leader"
        ));
        publish(handler, TeamEvent.MEMBER_SPAWNED, payload(
                "team_name", "alpha",
                "member_name", "alice"
        ));
        publish(handler, TeamEvent.MEMBER_STATUS_CHANGED, payload(
                "team_name", "alpha",
                "member_name", "alice",
                "old_status", "UNSTARTED",
                "new_status", "READY"
        ));
        publish(handler, TeamEvent.MESSAGE, payload(
                "team_name", "alpha",
                "message_id", "m1",
                "from_member_name", "leader",
                "to_member_name", "alice"
        ));
        publish(handler, TeamEvent.BROADCAST, payload(
                "team_name", "alpha",
                "message_id", "m2",
                "from_member_name", "leader"
        ));
        publish(handler, TeamEvent.TASK_CREATED, payload(
                "team_name", "alpha",
                "task_id", "t1",
                "status", "open"
        ));
        publish(handler, TeamEvent.TASK_COMPLETED, payload(
                "team_name", "alpha",
                "task_id", "t1"
        ));
        publish(handler, TeamEvent.CLEANED, payload("team_name", "alpha"));

        TelemetrySpan teamSpan = findSpan(tracer, "team.alpha");
        assertThat(teamSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.AT_TEAM_NAME, "alpha")
                .containsEntry(ObservabilitySemconv.AT_TEAM_DISPLAY_NAME, "Alpha Team")
                .containsEntry("agentteam.team.leader", "leader");
        assertThat(teamSpan.getEvents())
                .extracting(TelemetrySpan.Event::name)
                .contains(
                        TeamEvent.MEMBER_SPAWNED,
                        TeamEvent.MEMBER_STATUS_CHANGED,
                        TeamEvent.MESSAGE,
                        TeamEvent.BROADCAST
                );
        assertThat(teamSpan.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.OK);
        assertThat(teamSpan.isEnded()).isTrue();
        assertThat(handler.getTeamSpans()).isEmpty();

        TelemetrySpan taskSpan = findSpan(tracer, "task.t1");
        assertThat(taskSpan.getAttributes())
                .containsEntry(ObservabilitySemconv.AT_TASK_ID, "t1")
                .containsEntry(ObservabilitySemconv.AT_TEAM_NAME, "alpha")
                .containsEntry(ObservabilitySemconv.AT_TASK_STATUS, "completed");
        assertThat(taskSpan.getStatusCode()).isEqualTo(TelemetrySpan.StatusCode.OK);
        assertThat(taskSpan.isEnded()).isTrue();
        assertThat(handler.getTaskSpans()).isEmpty();
    }

    @Test
    void handlerIgnoresUnknownInternalEventsWithoutEmittingSpans() {
        TelemetryTracer.InMemory tracer = new TelemetryTracer.InMemory();
        OtelTeamMonitorHandler handler = new OtelTeamMonitorHandler(new ObservabilityConfig(), tracer);

        publish(handler, "workspace_artifact_updated", payload(
                "team_name", "alpha",
                "path", "ignored"
        ));

        assertThat(tracer.getSpans()).isEmpty();
    }

    private static void publish(OtelTeamMonitorHandler handler, String eventType, Map<String, Object> payload) {
        handler.onEvent(new EventMessage(eventType, payload, "")).toCompletableFuture().join();
    }

    private static TelemetrySpan findSpan(TelemetryTracer.InMemory tracer, String name) {
        return tracer.getSpans().stream()
                .filter(span -> name.equals(span.getName()))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            values.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return values;
    }
}
