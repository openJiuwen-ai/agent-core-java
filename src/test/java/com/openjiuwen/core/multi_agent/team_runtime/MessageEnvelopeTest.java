package com.openjiuwen.core.multi_agent.team_runtime;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageEnvelopeTest {

    @Test
    void shouldDefaultToPointToPointDisabledAndEmptyMetadata() {
        MessageEnvelope envelope = new MessageEnvelope("msg-1", "hello");

        assertThat(envelope.getMessageId()).isEqualTo("msg-1");
        assertThat(envelope.getMessage()).isEqualTo("hello");
        assertThat(envelope.getSender()).isNull();
        assertThat(envelope.getRecipient()).isNull();
        assertThat(envelope.getTopicId()).isNull();
        assertThat(envelope.getSessionId()).isNull();
        assertThat(envelope.getMetadata()).isEmpty();
        assertThat(envelope.isP2p()).isFalse();
        assertThat(envelope.isPubsub()).isFalse();
    }

    @Test
    void shouldReportPointToPointAndPreserveMetadataReference() {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("trace_id", "trace-1");
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-2",
                Map.of("payload", 1),
                "agent-a",
                "agent-b",
                null,
                "session-9",
                metadata
        );

        metadata.put("step", 2);

        assertThat(envelope.isP2p()).isTrue();
        assertThat(envelope.isPubsub()).isFalse();
        assertThat(envelope.getMetadata()).isSameAs(metadata);
        assertThat(envelope.getMetadata()).containsEntry("step", 2);
    }

    @Test
    void shouldReportPubSubAndMirrorPythonStyleDebugStringShape() {
        MessageEnvelope envelope = new MessageEnvelope(
                "msg-3",
                42,
                "agent-a",
                null,
                "topic-x",
                null,
                null
        );

        assertThat(envelope.isP2p()).isFalse();
        assertThat(envelope.isPubsub()).isTrue();
        assertThat(envelope.toString()).isEqualTo(
                "MessageEnvelope(message_id='msg-3', sender='agent-a', recipient=None, topic_id='topic-x', session_id=None, message=<Integer>)"
        );
    }
}
