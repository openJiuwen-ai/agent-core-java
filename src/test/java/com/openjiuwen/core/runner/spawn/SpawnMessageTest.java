/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpawnMessageTest {

    @Test
    void defaultConstructorLeavesMessageIdNull() {
        SpawnMessage message = new SpawnMessage(SpawnMessageType.INPUT, Map.of("text", "hello"));

        assertThat(message.getType()).isEqualTo(SpawnMessageType.INPUT);
        assertThat(message.getPayload()).isEqualTo(Map.of("text", "hello"));
        assertThat(message.getMessageId()).isNull();
    }

    @Test
    void serializeAndDeserializeRoundTrip() {
        SpawnMessage message = new SpawnMessage(
                SpawnMessageType.STREAM_CHUNK,
                Map.of("delta", "hi"),
                Instant.parse("2026-06-06T04:00:00Z"),
                "msg-7"
        );

        SpawnMessage decoded = SpawnMessage.deserializeMessage(SpawnMessage.serializeMessage(message));

        assertThat(decoded.getType()).isEqualTo(SpawnMessageType.STREAM_CHUNK);
        assertThat(decoded.getPayload()).isEqualTo(Map.of("delta", "hi"));
        assertThat(decoded.getTimestamp()).isEqualTo(Instant.parse("2026-06-06T04:00:00Z"));
        assertThat(decoded.getMessageId()).isEqualTo("msg-7");
    }

    @Test
    void deserializeMessageFromStreamSkipsNonProtocolLines() throws IOException {
        SpawnMessage message = new SpawnMessage(
                SpawnMessageType.DONE,
                Map.of("ok", true),
                Instant.parse("2026-06-06T04:00:01Z"),
                null
        );
        StringWriter writer = new StringWriter();
        SpawnMessage.serializeMessageToStream(message, writer);
        BufferedReader reader = new BufferedReader(new StringReader("plain log line\n" + writer));

        SpawnMessage decoded = SpawnMessage.deserializeMessageFromStream(reader);

        assertThat(decoded.getType()).isEqualTo(SpawnMessageType.DONE);
        assertThat(decoded.getPayload()).isEqualTo(Map.of("ok", true));
        assertThat(decoded.getTimestamp()).isEqualTo(Instant.parse("2026-06-06T04:00:01Z"));
        assertThat(decoded.getMessageId()).isNull();
    }
}
