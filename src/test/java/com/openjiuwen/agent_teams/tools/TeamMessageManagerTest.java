package com.openjiuwen.agent_teams.tools;

import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerHandler;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.schema.message.MessageRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_message_manager.py}.
 */
class TeamMessageManagerTest {

    @Test
    void sendMessageSuccess() {
        Fixture fixture = fixture();

        String messageId = fixture.manager.sendMessage("Hello member2", "member2", null);

        assertNotNull(messageId);
        List<MessageRecord> messages = fixture.manager.getMessages("member2", false, null);
        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).getMessageId());
        assertEquals("Hello member2", messages.get(0).getContent());
        assertEquals("member1", messages.get(0).getFromMemberName());
        assertEquals("member2", messages.get(0).getToMemberName());
        assertFalse(messages.get(0).isBroadcast());
        assertFalse(messages.get(0).isRead());
    }

    @Test
    void sendMessageUnicodeContent() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("\u4f60\u597d\uff0c\u4e16\u754c\uff01", "member2", null);

        assertEquals("\u4f60\u597d\uff0c\u4e16\u754c\uff01",
                fixture.manager.getMessages("member2", false, null).get(0).getContent());
    }

    @Test
    void sendMultipleMessages() {
        Fixture fixture = fixture();
        List<String> messageIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            messageIds.add(fixture.manager.sendMessage("Message " + i, "receiver_0", null));
        }

        List<MessageRecord> messages = fixture.manager.getMessages("receiver_0", false, null);
        assertEquals(5, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            assertEquals("Message " + i, messages.get(i).getContent());
            assertEquals(messageIds.get(i), messages.get(i).getMessageId());
        }
    }

    @Test
    void broadcastMessageSuccess() {
        Fixture fixture = fixture();
        TeamMessageManager leader = new TeamMessageManager("test_team_123", "leader", fixture.messages, fixture.messager);

        String messageId = leader.broadcastMessage("Team meeting at 3PM", null);

        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member1", false, null);
        assertEquals(1, broadcasts.size());
        assertEquals(messageId, broadcasts.get(0).getMessageId());
        assertEquals("Team meeting at 3PM", broadcasts.get(0).getContent());
        assertEquals("leader", broadcasts.get(0).getFromMemberName());
        assertEquals(null, broadcasts.get(0).getToMemberName());
        assertTrue(broadcasts.get(0).isBroadcast());
        assertFalse(broadcasts.get(0).isRead());
    }

    @Test
    void broadcastMultipleMessages() {
        Fixture fixture = fixture();
        List<String> senders = List.of("leader", "member1", "member2");
        List<String> ids = new ArrayList<>();

        for (String sender : senders) {
            ids.add(new TeamMessageManager("test_team_123", sender, fixture.messages, fixture.messager)
                    .broadcastMessage("Announcement from " + sender, null));
        }

        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(3, broadcasts.size());
        for (int i = 0; i < broadcasts.size(); i++) {
            assertTrue(broadcasts.get(i).isBroadcast());
            assertEquals(senders.get(i), broadcasts.get(i).getFromMemberName());
            assertEquals(ids.get(i), broadcasts.get(i).getMessageId());
        }
    }

    @Test
    void getMessagesAll() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("Msg1", "member2", null);
        fixture.manager.sendMessage("Msg2", "member2", null);

        List<MessageRecord> messages = fixture.manager.getMessages("member2", false, null);
        assertEquals(2, messages.size());
        messages.forEach(message -> assertInstanceOf(MessageRecord.class, message));
        assertEquals("Msg1", messages.get(0).getContent());
        assertEquals("Msg2", messages.get(1).getContent());
    }

    @Test
    void getMessagesForMember() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("To member2", "member2", null);
        fixture.manager.sendMessage("To member1", "member1", null);
        fixture.manager.sendMessage("Also to member2", "member2", null);

        List<MessageRecord> messages = fixture.manager.getMessages("member2", false, null);
        assertEquals(2, messages.size());
        messages.forEach(message -> assertEquals("member2", message.getToMemberName()));
    }

    @Test
    void getMessagesUnreadOnly() {
        Fixture fixture = fixture();

        String unreadId = fixture.manager.sendMessage("Unread message", "member2", null);
        String readId = fixture.manager.sendMessage("Read message", "member2", null);
        fixture.manager.markMessageRead(readId, "member2");

        List<MessageRecord> messages = fixture.manager.getMessages("member2", true, null);
        assertEquals(1, messages.size());
        assertEquals(unreadId, messages.get(0).getMessageId());
        assertFalse(messages.get(0).isRead());
    }

    @Test
    void getMessagesEmpty() {
        assertTrue(fixture().manager.getMessages("member2", false, null).isEmpty());
    }

    @Test
    void getMessagesMixedBroadcastAndDirect() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("Direct message", "member2", null);
        fixture.manager.broadcastMessage("Broadcast", null);

        List<MessageRecord> directMessages = fixture.manager.getMessages("member2", false, null);
        assertEquals(1, directMessages.size());
        assertFalse(directMessages.get(0).isBroadcast());
    }

    @Test
    void getBroadcastMessagesAll() {
        Fixture fixture = fixture();

        fixture.manager.broadcastMessage("Announcement 1", null);
        fixture.manager.broadcastMessage("Announcement 2", null);

        List<MessageRecord> messages = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(2, messages.size());
        messages.forEach(message -> {
            assertInstanceOf(MessageRecord.class, message);
            assertTrue(message.isBroadcast());
            assertEquals(null, message.getToMemberName());
        });
    }

    @Test
    void getBroadcastMessagesUnreadOnly() {
        Fixture fixture = fixture();

        String oldId = fixture.manager.broadcastMessage("Old announcement", null);
        String newId = fixture.manager.broadcastMessage("New announcement", null);
        fixture.manager.markMessageRead(oldId, "member2");

        List<MessageRecord> messages = fixture.manager.getBroadcastMessages("member2", true, null);
        assertEquals(1, messages.size());
        assertEquals(newId, messages.get(0).getMessageId());
        assertFalse(messages.get(0).isRead());
    }

    @Test
    void getBroadcastMessagesEmpty() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("Direct message", "member1", null);

        assertTrue(fixture.manager.getBroadcastMessages("member2", false, null).isEmpty());
    }

    @Test
    void getBroadcastMessagesFiltersCorrectly() {
        Fixture fixture = fixture();

        fixture.manager.sendMessage("Direct 1", "member1", null);
        fixture.manager.broadcastMessage("Broadcast 1", null);
        fixture.manager.sendMessage("Direct 2", "member2", null);
        fixture.manager.broadcastMessage("Broadcast 2", null);

        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(2, broadcasts.size());
        broadcasts.forEach(message -> {
            assertTrue(message.isBroadcast());
            assertEquals(null, message.getToMemberName());
        });
    }

    @Test
    void markMessageReadSuccess() {
        Fixture fixture = fixture();
        String messageId = fixture.manager.sendMessage("Hello", "member2", null);

        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));

        List<MessageRecord> messages = fixture.manager.getMessages("member2", false, null);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).isRead());
    }

    @Test
    void markMessageReadNonexistent() {
        assertFalse(fixture().manager.markMessageRead("nonexistent_msg", "member2"));
    }

    @Test
    void markMessageReadIdempotent() {
        Fixture fixture = fixture();
        String messageId = fixture.manager.sendMessage("Hello", "member2", null);

        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));
        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));
        assertTrue(fixture.manager.getMessages("member2", false, null).get(0).isRead());
    }

    @Test
    void markBroadcastMessageRead() {
        Fixture fixture = fixture();
        String messageId = fixture.manager.broadcastMessage("Announcement", null);

        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));

        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(1, broadcasts.size());
        assertFalse(broadcasts.get(0).isRead());
        assertTrue(broadcasts.get(0).isBroadcast());
    }

    @Test
    void sendAndGetMessageFlow() {
        Fixture fixture = fixture();
        String messageId = fixture.manager.sendMessage("Hello", "member2", null);

        List<MessageRecord> messages = fixture.manager.getMessages("member2", true, null);
        assertEquals(1, messages.size());
        assertEquals(messageId, messages.get(0).getMessageId());

        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));
        assertTrue(fixture.manager.getMessages("member2", true, null).isEmpty());
    }

    @Test
    void broadcastAndGetFlow() {
        Fixture fixture = fixture();
        String messageId = fixture.manager.broadcastMessage("Team meeting at 3PM", null);

        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(1, broadcasts.size());
        assertTrue(broadcasts.get(0).isBroadcast());
        assertEquals(messageId, broadcasts.get(0).getMessageId());

        assertTrue(fixture.manager.markMessageRead(messageId, "member2"));
        assertTrue(fixture.manager.getBroadcastMessages("member2", true, null).isEmpty());
    }

    @Test
    void multiMemberMessagingScenario() {
        Fixture fixture = fixture();
        List<String> members = List.of("member1", "member2", "member3");

        for (String sender : members) {
            for (String recipient : members) {
                if (!sender.equals(recipient)) {
                    fixture.manager.sendMessage("Hello from " + sender + " to " + recipient, recipient, sender);
                }
            }
        }
        fixture.manager.broadcastMessage("Welcome to the team!", null);

        assertEquals(2, fixture.manager.getMessages("member2", false, null).size());
        List<MessageRecord> broadcasts = fixture.manager.getBroadcastMessages("member2", false, null);
        assertEquals(1, broadcasts.size());
        assertEquals("Welcome to the team!", broadcasts.get(0).getContent());
    }

    @Test
    void messageTimestampOrdering() {
        Fixture fixture = fixture();

        String msg1 = fixture.manager.sendMessage("First", "member2", null);
        String msg2 = fixture.manager.sendMessage("Second", "member1", null);
        String msg3 = fixture.manager.sendMessage("Third", "member2", null);

        List<String> ids = fixture.messages.values().stream()
                .filter(message -> "test_team_123".equals(message.getTeamName()))
                .map(MessageRecord::getMessageId)
                .toList();
        assertEquals(List.of(msg1, msg2, msg3), ids);
    }

    @Test
    void teamMessageIsolation() {
        Map<String, MessageRecord> shared = new LinkedHashMap<>();
        TeamMessageManager messaging1 = new TeamMessageManager("team1", "leader1", shared, new RecordingMessager());
        TeamMessageManager messaging2 = new TeamMessageManager("team2", "leader2", shared, new RecordingMessager());

        messaging1.sendMessage("Team 1 message", "member1", null);
        messaging2.sendMessage("Team 2 message", "member1", null);

        List<MessageRecord> team1Messages = messaging1.getMessages("member1", false, null);
        List<MessageRecord> team2Messages = messaging2.getMessages("member1", false, null);
        assertEquals(1, team1Messages.size());
        assertEquals(1, team2Messages.size());
        assertEquals("Team 1 message", team1Messages.get(0).getContent());
        assertEquals("Team 2 message", team2Messages.get(0).getContent());
    }

    private static Fixture fixture() {
        Map<String, MessageRecord> messages = new LinkedHashMap<>();
        RecordingMessager messager = new RecordingMessager();
        return new Fixture(
                new TeamMessageManager("test_team_123", "member1", messages, messager, Set.of()),
                messages,
                messager
        );
    }

    private record Fixture(TeamMessageManager manager, Map<String, MessageRecord> messages, RecordingMessager messager) {
    }

    private static final class RecordingMessager implements Messager {
        private final List<EventMessage> published = new ArrayList<>();
        private final List<EventMessage> sent = new ArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public void publish(String topicId, EventMessage message) {
            published.add(message);
        }

        @Override
        public void subscribe(String topicId, MessagerHandler handler) {
        }

        @Override
        public void unsubscribe(String topicId) {
        }

        @Override
        public void send(String agentId, EventMessage message) {
            sent.add(message);
        }

        @Override
        public void registerDirectMessageHandler(MessagerHandler handler) {
        }

        @Override
        public void unregisterDirectMessageHandler() {
        }
    }
}
