/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's serializer behavior in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_serializer.py}.
 */
class MessageSerializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void serializesRequestWithPythonClassMarkerAndSnakeCaseFields() throws Exception {
        DmqRequestMessage message = new DmqRequestMessage();
        message.setMessageId("message-1");
        message.setPayload(Map.of("role", "user"));
        message.setReplyTopic("reply-topic");
        message.setRequestId("request-1");
        message.setSenderId("sender-1");
        message.setReceiverId("receiver-1");
        message.setEnableStream(true);
        message.setExpireAt(12.5d);

        byte[] bytes = MessageSerializer.serializeMessage(message);
        Map<String, Object> raw = MAPPER.readValue(bytes, Map.class);

        assertThat(raw).containsEntry("__class__", "DmqRequestMessage");
        assertThat(raw).containsEntry("message_id", "message-1");
        assertThat(raw).containsEntry("reply_topic", "reply-topic");
        assertThat(raw).containsEntry("enable_stream", true);
        assertThat(raw).containsEntry("expire_at", 12.5d);
        assertThat(raw).containsEntry("type", "INPUT");
        assertThat(raw).doesNotContainKey("messageId");
    }

    @Test
    void deserializesResponseAndTypedPayload() {
        DmqResponseMessage message = new DmqResponseMessage();
        message.setMessageId("message-2");
        message.setSenderId("agent-1");
        message.setReceiverId("runner-1");
        message.setRequestId("request-2");
        message.setResultType(ResultType.MESSAGE);
        message.setSeq(3);
        message.setLastChunk(true);
        message.setPayload(new OutputSchema("text", 0, "hello"));

        DmqResponseMessage restored = (DmqResponseMessage) MessageSerializer.deserializeMessage(
                MessageSerializer.serializeMessage(message));

        assertThat(restored.getMessageId()).isEqualTo("message-2");
        assertThat(restored.getSenderId()).isEqualTo("agent-1");
        assertThat(restored.getReceiverId()).isEqualTo("runner-1");
        assertThat(restored.getRequestId()).isEqualTo("request-2");
        assertThat(restored.getResultType()).isEqualTo(ResultType.MESSAGE);
        assertThat(restored.getSeq()).isEqualTo(3);
        assertThat(restored.isLastChunk()).isTrue();
        assertThat(restored.getBody()).isInstanceOf(OutputSchema.class);
        OutputSchema payload = (OutputSchema) restored.getBody();
        assertThat(payload.getType()).isEqualTo("text");
        assertThat(payload.getIndex()).isZero();
        assertThat(payload.getPayload()).isEqualTo("hello");
    }

    @Test
    void rejectsUnknownPayloadClass() {
        String json = "{\"__class__\":\"UnknownPayload\",\"value\":1}";

        assertThatThrownBy(() -> MessageSerializer.deserializeMessage(json.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown payload class");
    }

    @Test
    void rejectsPayloadNestedTooDeep() {
        Map<String, Object> nested = new LinkedHashMap<>();
        Map<String, Object> cursor = nested;
        for (int index = 0; index < MessageSerializer.MAX_RECURSE_DEPTH + 2; index++) {
            Map<String, Object> child = new LinkedHashMap<>();
            cursor.put("child", child);
            cursor = child;
        }

        DmqRequestMessage message = new DmqRequestMessage();
        message.setPayload(nested);

        assertThatThrownBy(() -> MessageSerializer.serializeMessage(message))
                .isInstanceOf(StackOverflowError.class)
                .hasMessageContaining("Payload nested too deep");
    }
}
