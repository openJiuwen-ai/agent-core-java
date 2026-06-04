package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.events.EventMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code MonitorEvent} in {@code openjiuwen.agent_teams.monitor.models}.
 */
class MonitorEventTest {

    @Test
    void fromEventMessageFlattensPythonPayloadFields() {
        long before = System.currentTimeMillis();

        MonitorEvent event = MonitorEvent.fromEventMessage(new EventMessage("member_restarted", Map.ofEntries(
                entry("team_name", "team-a"),
                entry("member_name", "worker"),
                entry("display_name", "Team A"),
                entry("leader_member_name", "leader"),
                entry("created", 100L),
                entry("old_status", "ERROR"),
                entry("new_status", "READY"),
                entry("reason", "health_check_failure"),
                entry("restart_count", 2),
                entry("force", true),
                entry("task_id", "task-1"),
                entry("status", "IN_PROGRESS"),
                entry("message_id", "msg-1"),
                entry("from_member_name", "leader"),
                entry("to_member_name", "worker")
        )));

        long after = System.currentTimeMillis();
        assertNotNull(event);
        assertEquals("member_restarted", event.getEventType());
        assertEquals("team-a", event.getTeamId());
        assertEquals("worker", event.getMemberId());
        assertTrue(event.getTimestamp() >= before && event.getTimestamp() <= after);
        assertEquals("Team A", event.getName());
        assertEquals("leader", event.getLeaderId());
        assertEquals(100L, event.getCreated());
        assertEquals("ERROR", event.getOldStatus());
        assertEquals("READY", event.getNewStatus());
        assertEquals("health_check_failure", event.getReason());
        assertEquals(2, event.getRestartCount());
        assertEquals(Boolean.TRUE, event.getForce());
        assertEquals("task-1", event.getTaskId());
        assertEquals("IN_PROGRESS", event.getStatus());
        assertEquals("msg-1", event.getMessageId());
        assertEquals("leader", event.getFromMember());
        assertEquals("worker", event.getToMember());
        assertInstanceOf(Map.class, event.getData());
    }

    @Test
    void fromEventMessageDropsInternalEvents() {
        MonitorEvent event = MonitorEvent.fromEventMessage(new EventMessage("plan_approval", Map.of(
                "team_name", "team-a",
                "member_name", "worker",
                "approved", true
        )));

        assertNull(event);
    }

    @Test
    void fromEventMessageSupportsMapEnvelopePayload() {
        MonitorEvent event = MonitorEvent.fromEventMessage(Map.of(
                "event_type", "message",
                "payload", Map.of(
                        "team_name", "team-b",
                        "message_id", "msg-2",
                        "from_member_name", "leader",
                        "to_member_name", "worker"
                )
        ));

        assertNotNull(event);
        assertEquals("message", event.getEventType());
        assertEquals("team-b", event.getTeamId());
        assertEquals("msg-2", event.getMessageId());
        assertEquals("leader", event.getFromMember());
        assertEquals("worker", event.getToMember());
    }
}
