package com.openjiuwen.core.multiagent.team_runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestMessageEnvelope} in
 * {@code tests/unit_tests/multi_agent/team/test_envelope.py}.
 */
class MessageEnvelopeTest {

    @Test
    void testCreateP2pEnvelope() {
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-001",
                "hello",
                "agent_a",
                "agent_b",
                null,
                null,
                null
        );

        assertThat(envelope.getMessageId()).isEqualTo("msg-001");
        assertThat(envelope.getMessage()).isEqualTo("hello");
        assertThat(envelope.getSender()).isEqualTo("agent_a");
        assertThat(envelope.getRecipient()).isEqualTo("agent_b");
        assertThat(envelope.getTopicId()).isNull();
        assertThat(envelope.getSessionId()).isNull();
        assertThat(envelope.getMetadata()).isEmpty();
    }

    @Test
    void testCreatePubsubEnvelope() {
        Map<String, Object> payload = Map.of("event", "done");
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-002",
                payload,
                "agent_a",
                null,
                "code_events",
                null,
                null
        );

        assertThat(envelope.getTopicId()).isEqualTo("code_events");
        assertThat(envelope.getRecipient()).isNull();
    }

    @Test
    void testIsP2pTrueWhenRecipientSet() {
        MessageEnvelope envelope = new MessageEnvelope("x", "payload", null, "agent_b", null, null, null);

        assertThat(envelope.isP2p()).isTrue();
        assertThat(envelope.isPubsub()).isFalse();
    }

    @Test
    void testIsPubsubTrueWhenTopicSet() {
        MessageEnvelope envelope = new MessageEnvelope("y", "payload", null, null, "events", null, null);

        assertThat(envelope.isPubsub()).isTrue();
        assertThat(envelope.isP2p()).isFalse();
    }

    @Test
    void testBothFlagsCanBeFalse() {
        MessageEnvelope envelope = new MessageEnvelope("z", "payload");

        assertThat(envelope.isP2p()).isFalse();
        assertThat(envelope.isPubsub()).isFalse();
    }

    @Test
    void testEnvelopeIsFrozen() {
        List<String> mutableFieldNames = List.of("messageId", "message", "sender", "recipient", "topicId", "sessionId",
                "metadata");

        assertThat(mutableFieldNames)
                .allSatisfy(fieldName -> {
                    Field field = MessageEnvelope.class.getDeclaredField(fieldName);
                    assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
                });
    }

    @Test
    void testMetadataDefaultIsEmptyDict() {
        MessageEnvelope envelope = new MessageEnvelope("id", "data");
        MessageEnvelope other = new MessageEnvelope("id2", "data2");

        assertThat(envelope.getMetadata()).isEmpty();
        assertThat(envelope.getMetadata()).isNotSameAs(other.getMetadata());
    }

    @Test
    void testReprContainsKeyFields() {
        MessageEnvelope envelope = new MessageEnvelope("repr-test", "payload", "alice", "bob", null, null, null);
        String representation = envelope.toString();

        assertThat(representation).contains("repr-test", "alice", "bob");
    }

    @Test
    void testSessionIdStored() {
        MessageEnvelope envelope = new MessageEnvelope("s1", "data", null, null, null, "session-xyz", null);

        assertThat(envelope.getSessionId()).isEqualTo("session-xyz");
    }

    @Test
    void testMessageCanBeAnyType() {
        Object[] payloads = new Object[] {42, List.of(1, 2), Map.of("a", 1), null, new Object()};

        for (Object payload : payloads) {
            MessageEnvelope envelope = new MessageEnvelope("id", payload);
            assertThat(envelope.getMessage()).isSameAs(payload);
        }
    }

    @Test
    void testMetadataCustom() {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("priority", "high");
        metadata.put("retry", 3);
        MessageEnvelope envelope = new MessageEnvelope("m1", "x", null, null, null, null, metadata);

        assertThat(envelope.getMetadata()).isEqualTo(metadata);
        assertThat(envelope.getMetadata()).isSameAs(metadata);
    }
}
