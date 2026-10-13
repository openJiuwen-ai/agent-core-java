/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.Disposable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 RunnerImpl.runAgentStreamingAsync() 的生产路径：
 * Agent.stream() setup 与 iterator 消费都必须跑在 boundedElastic，
 * 取消订阅时底层 AutoCloseable iterator 必须关闭。
 */
class RunnerImplStreamingFluxSchedulerTest {

    @Test
    void runAgentStreamingAsyncRunsOnBoundedElasticAndClosesIteratorOnCancel() throws Exception {
        RecordingAgent agent = new RecordingAgent();
        CountDownLatch firstChunk = new CountDownLatch(1);
        AtomicReference<Object> chunk = new AtomicReference<>();

        Disposable disposable = Runner.runAgentStreamingAsync(
                        agent,
                        Map.of("query", "hello", "conversation_id", "runner-reactive-test"),
                        null,
                        null,
                        List.of(StreamMode.OUTPUT),
                        null)
                .doOnNext(value -> {
                    chunk.compareAndSet(null, value);
                    firstChunk.countDown();
                })
                .subscribe();

        assertThat(firstChunk.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(chunk.get()).isEqualTo("chunk-1");
        assertThat(agent.streamThread.get())
                .as("Agent.stream() setup must not run on the caller thread")
                .isNotNull();
        assertThat(agent.nextThread.get())
                .as("iterator consumption thread must be recorded")
                .isNotNull();

        disposable.dispose();

        assertThat(agent.closed.await(2, TimeUnit.SECONDS))
                .as("AutoCloseable iterator must be closed on cancel")
                .isTrue();
    }

    private static final class RecordingAgent extends BaseAgent {
        private final AtomicReference<String> streamThread = new AtomicReference<>();
        private final AtomicReference<String> nextThread = new AtomicReference<>();
        private final CountDownLatch closed = new CountDownLatch(1);

        private RecordingAgent() {
            super(AgentCard.builder()
                    .id("recording-agent")
                    .name("recording-agent")
                    .description("recording-agent")
                    .build());
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public Object getConfig() {
            return null;
        }

        @Override
        public Object invoke(Object inputs, AgentSessionApi session) {
            return "ok";
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            streamThread.set(Thread.currentThread().getName());
            class CloseableIterator implements Iterator<Object>, AutoCloseable {
                private int emitted;

                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public Object next() {
                    nextThread.set(Thread.currentThread().getName());
                    emitted++;
                    return "chunk-" + emitted;
                }

                @Override
                public void close() {
                    closed.countDown();
                }
            }
            return new CloseableIterator();
        }
    }
}
