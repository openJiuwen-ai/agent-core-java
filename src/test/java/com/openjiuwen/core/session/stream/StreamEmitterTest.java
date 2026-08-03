/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's stream queue and emitter lifecycle in
 * {@code openjiuwen/core/session/stream/emitter.py}.
 */
class StreamEmitterTest {

    @Test
    void queueSendReceiveAndCloseFollowPythonLifecycle() {
        AsyncStreamQueue queue = new AsyncStreamQueue(1);

        queue.send("chunk");
        assertThat(queue.receive(100)).isEqualTo("chunk");

        queue.close(100);
        assertThat(queue.isClosed()).isTrue();
        assertThatThrownBy(() -> queue.send("late"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("StreamQueue is already closed");
    }

    @Test
    void emitterCloseSendsEndFrame() {
        StreamEmitter emitter = new StreamEmitter();

        emitter.close();

        assertThat(emitter.isClosed()).isTrue();
        assertThat(emitter.getStreamQueue().receive(100)).isEqualTo(StreamEmitter.END_FRAME);
        assertThatThrownBy(() -> emitter.emit("late"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Can not emit data after the stream emitter is closed.");
    }
}
