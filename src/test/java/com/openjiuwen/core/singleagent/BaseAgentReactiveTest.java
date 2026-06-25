/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 {@link BaseAgent} 上 Mono/Flux 包装方法的契约：
 * 异常原样透传、streamModes 原值透传（{@code null} 不能被悄悄替换为空 List）、
 * 以及取消时能中止底层迭代。
 */
class BaseAgentReactiveTest {

    private static AgentCard cardOf(String id) {
        return AgentCard.builder().id(id).name(id).description(id).build();
    }

    /** 最小 BaseAgent 实现，仅用于断言入参/出参。 */
    private static class FakeAgent extends BaseAgent {
        Object invokeResult;
        RuntimeException invokeError;
        Iterator<Object> streamItems = List.<Object>of().iterator();
        Object capturedInputs;
        Session capturedSession;
        List<StreamMode> capturedStreamModes;
        final AtomicReference<List<StreamMode>> lastStreamModes = new AtomicReference<>();

        FakeAgent(String id) {
            super(cardOf(id));
        }

        @Override public BaseAgent configure(Object config) { return this; }
        @Override public Object getConfig() { return null; }

        @Override
        public Object invoke(Object inputs, Session session) {
            capturedInputs = inputs;
            capturedSession = session;
            if (invokeError != null) throw invokeError;
            return invokeResult;
        }

        @Override
        public Iterator<Object> stream(Object inputs, Session session, List<StreamMode> streamModes) {
            capturedInputs = inputs;
            capturedSession = session;
            capturedStreamModes = streamModes;
            lastStreamModes.set(streamModes);
            return streamItems;
        }
    }

    /** invokeAsync 把同步 invoke 的入参/返回值原样透传。 */
    @Test
    void invokeMonoDelegatesToInvoke() {
        FakeAgent a = new FakeAgent("a1");
        a.invokeResult = "answer";

        StepVerifier.create(a.invokeAsync("hi", null))
                .expectNext("answer")
                .verifyComplete();
        assertEquals("hi", a.capturedInputs);
    }

    /** invokeAsync 抛出的异常对象身份不变，不被 Reactor 包装。 */
    @Test
    void invokeMonoPropagatesExceptionUnwrapped() {
        FakeAgent a = new FakeAgent("a2");
        IllegalStateException boom = new IllegalStateException("agent boom");
        a.invokeError = boom;

        StepVerifier.create(a.invokeAsync("x", null))
                .expectErrorMatches(t -> t == boom)
                .verify();
    }

    /** streamAsync 必须把 streamModes 原值透传——{@code null} 不能被替换为空 List，否则事件会被静默丢弃。 */
    @Test
    void streamFluxPassesStreamModesVerbatim() {
        FakeAgent a = new FakeAgent("a3");
        a.streamItems = List.<Object>of("x", "y").iterator();

        StepVerifier.create(a.streamAsync("inp", null, null))
                .expectNext("x", "y")
                .verifyComplete();
        assertEquals(null, a.lastStreamModes.get(), "null streamModes must pass through unchanged");

        // 显式 list 也要原样透传（同一引用）
        FakeAgent b = new FakeAgent("a4");
        b.streamItems = List.<Object>of().iterator();
        List<StreamMode> modes = List.of(StreamMode.OUTPUT);

        StepVerifier.create(b.streamAsync("inp", null, modes))
                .verifyComplete();
        assertSame(modes, b.lastStreamModes.get(), "explicit streamModes list must pass through unchanged");
    }

    /** 取消订阅后无限流必须立刻停止（允许至多 1 个已在途的多余发射）。 */
    @Test
    void streamFluxCancellationStopsIteration() throws Exception {
        AtomicInteger emitted = new AtomicInteger();
        FakeAgent a = new FakeAgent("a5");
        a.streamItems = new Iterator<>() {
            @Override public boolean hasNext() { return true; }
            @Override public Object next() { return emitted.incrementAndGet(); }
        };

        StepVerifier.create(a.streamAsync("inp", null, List.of(StreamMode.OUTPUT)), 3)
                .expectNextCount(3)
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        int afterCancel = emitted.get();
        Thread.sleep(200);
        assertTrue(emitted.get() - afterCancel <= 1, "iteration must stop on cancel");
    }

    /** 取消订阅时，若底层 iterator 可关闭，必须调用 close() 释放长连接资源。 */
    @Test
    void streamFluxCancellationClosesAutoCloseableIterator() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        class CloseableIterator implements Iterator<Object>, AutoCloseable {
            @Override public boolean hasNext() { return true; }
            @Override public Object next() { return "chunk"; }
            @Override public void close() { closed.countDown(); }
        }
        FakeAgent a = new FakeAgent("a6");
        a.streamItems = new CloseableIterator();

        StepVerifier.create(a.streamAsync("inp", null, List.of(StreamMode.OUTPUT)), 1)
                .expectNext("chunk")
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        assertTrue(closed.await(1, TimeUnit.SECONDS), "AutoCloseable iterator must be closed on cancel");
    }
}
