/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.stream_actor;

import com.openjiuwen.core.workflow.component.ComponentAbility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class StreamActorHotPathTest {

    @Test
    @DisplayName("send 不再是方法级 synchronized，避免 latch.await 把全部生产者串行化")
    void sendIsNotMethodSynchronized() throws Exception {
        Method send = StreamActor.class.getDeclaredMethod(
                "send", Object.class, ComponentAbility.class, boolean.class, String.class);
        assertThat(Modifier.isSynchronized(send.getModifiers())).isFalse();
    }

    @Test
    @DisplayName("不同 producer 的首帧可以并发进入 send")
    void concurrentProducersCanSendFirstFrame() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        StreamActor actor = new StreamActor(
                "sink",
                new ImmediateConsumer(started),
                List.of(ComponentAbility.COLLECT),
                List.of(),
                1.0d);
        try {
            actor.send("left", ComponentAbility.STREAM, true, "left");
            actor.send("right", ComponentAbility.STREAM, true, "right");
            assertThat(started.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        } finally {
            actor.shutdown();
        }
    }

    private static final class ImmediateConsumer implements StreamConsumer {
        private final CountDownLatch started;

        private ImmediateConsumer(CountDownLatch started) {
            this.started = started;
        }

        @Override
        public boolean shouldHandleMessage() {
            return true;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public void streamCall(CountDownLatch latch, Consumer<Exception> errorCallback) {
            started.countDown();
            latch.countDown();
        }
    }
}
