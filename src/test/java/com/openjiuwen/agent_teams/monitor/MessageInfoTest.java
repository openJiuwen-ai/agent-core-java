package com.openjiuwen.agent_teams.monitor;

import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code MessageInfo} in {@code openjiuwen.agent_teams.monitor.models}.
 */
class MessageInfoTest {

    @Test
    void fromInternalPreservesMessageRecordFields() {
        MessageRecord record = new MessageRecord(
                "msg-1",
                "team-a",
                "leader",
                "worker",
                "hello",
                false,
                true
        );

        MessageInfo info = MessageInfo.fromInternal(record);

        assertEquals("msg-1", info.getMessageId());
        assertEquals("team-a", info.getTeamId());
        assertEquals("leader", info.getFromMember());
        assertEquals("worker", info.getToMember());
        assertEquals("hello", info.getContent());
        assertEquals(record.getCreatedAt(), info.getTimestamp());
        assertEquals(record.getCreatedAt(), info.getSentAt());
        assertFalse(info.isBroadcast());
        assertTrue(info.isRead());
    }

    @Test
    void fromInternalPreservesPythonStyleMapFields() {
        MessageInfo info = MessageInfo.fromInternal(Map.of(
                "message_id", "msg-2",
                "team_name", "team-b",
                "from_member_name", "user",
                "to_member_name", "worker",
                "content", "review this",
                "timestamp", 12345L,
                "broadcast", true,
                "is_read", false
        ));

        assertEquals("msg-2", info.getMessageId());
        assertEquals("team-b", info.getTeamId());
        assertEquals("user", info.getFromMember());
        assertEquals("worker", info.getToMember());
        assertEquals("review this", info.getContent());
        assertEquals(12345L, info.getTimestamp());
        assertEquals(12345L, info.getSentAt());
        assertTrue(info.isBroadcast());
        assertFalse(info.isRead());
    }
}
