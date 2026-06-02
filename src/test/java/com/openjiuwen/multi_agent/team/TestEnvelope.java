/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.multi_agent.team;

import com.openjiuwen.core.multiagent.teamruntime.MessageEnvelope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MessageEnvelope.
 *
 * <p>Mirrors Python's {@code test_envelope.py} in
 * {@code tests.unit_tests.multi_agent.team}.
 */
class TestEnvelope {

    @Test
    void testCreateP2pEnvelope() {
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-001", "hello", "agent_a", "agent_b", null, null, null);

        assertEquals("msg-001", envelope.getMessageId());
        assertEquals("hello", envelope.getMessage());
        assertEquals("agent_a", envelope.getSender().orElse(null));
        assertEquals("agent_b", envelope.getRecipient().orElse(null));
        assertTrue(envelope.getTopicId().isEmpty());
        assertTrue(envelope.getSessionId().isEmpty());
        assertEquals(Map.of(), envelope.getMetadata());
    }

    @Test
    void testCreatePubsubEnvelope() {
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-002", Map.of("event", "done"), "agent_a", null, "code_events", null, null);

        assertEquals("code_events", envelope.getTopicId().orElse(null));
        assertTrue(envelope.getRecipient().isEmpty());
    }

    @Test
    void testIsP2pTrueWhenRecipientSet() {
        MessageEnvelope envelope = new MessageEnvelope("x", "payload", null, "agent_b", null, null, null);

        assertTrue(envelope.isP2p());
        assertTrue(envelope.isP2P());
        assertFalse(envelope.isPubsub());
    }

    @Test
    void testIsPubsubTrueWhenTopicSet() {
        MessageEnvelope envelope = new MessageEnvelope("y", "payload", null, null, "events", null, null);

        assertTrue(envelope.isPubsub());
        assertTrue(envelope.isPubSub());
        assertFalse(envelope.isP2p());
    }

    @Test
    void testBothFlagsCanBeFalse() {
        MessageEnvelope envelope = new MessageEnvelope("z", "payload");

        assertFalse(envelope.isP2p());
        assertFalse(envelope.isPubsub());
    }

    @Test
    void testEnvelopeIsFrozen() {
        MessageEnvelope envelope = new MessageEnvelope("id", "data");
        Map<String, Object> metadata = envelope.getMetadata();

        metadata.put("mutated", true);

        assertFalse(envelope.getMetadata().containsKey("mutated"));
        assertEquals("id", envelope.getMessageId());
    }

    @Test
    void testMetadataDefaultIsEmptyDict() {
        MessageEnvelope envelope = new MessageEnvelope("id", "data");
        MessageEnvelope envelope2 = new MessageEnvelope("id2", "data2");

        assertEquals(Map.of(), envelope.getMetadata());
        assertNotSame(envelope.getMetadata(), envelope2.getMetadata());
    }

    @Test
    void testReprContainsKeyFields() {
        MessageEnvelope envelope = new MessageEnvelope(
                "repr-test", "payload", "alice", "bob", null, null, null);

        String repr = envelope.toString();

        assertTrue(repr.contains("repr-test"));
        assertTrue(repr.contains("alice"));
        assertTrue(repr.contains("bob"));
    }

    @Test
    void testSessionIdStored() {
        MessageEnvelope envelope = new MessageEnvelope(
                "s1", "data", null, null, null, "session-xyz", null);

        assertEquals("session-xyz", envelope.getSessionId().orElse(null));
    }

    @Test
    void testMessageCanBeAnyType() {
        Object marker = new Object();
        for (Object payload : List.of(42, List.of(1, 2), Map.of("a", 1), marker)) {
            MessageEnvelope envelope = new MessageEnvelope("id", payload);
            assertSame(payload, envelope.getMessage());
        }
        assertNull(new MessageEnvelope("id", null).getMessage());
    }

    @Test
    void testMetadataCustom() {
        Map<String, Object> metadata = Map.of("priority", "high", "retry", 3);
        MessageEnvelope envelope = new MessageEnvelope("m1", "x", null, null, null, null, metadata);

        assertEquals(metadata, envelope.getMetadata());
    }
}
