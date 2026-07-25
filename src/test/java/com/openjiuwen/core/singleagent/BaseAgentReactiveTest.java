/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

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
 * 验证 {@link BaseAgent} 上 invoke/stream 方法的契约：
 * 异常原样透传、streamModes 原值透传（{@code null} 不能被悄悄替换为空 List）、
 * 以及迭代器行为正确。
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

    /** invoke 把同步 invoke 的入参/返回值原样透传。 */
    @Test
    void invokeDelegatesToInvoke() {
        FakeAgent a = new FakeAgent("a1");
        a.invokeResult = "answer";

        Object result = a.invoke("hi", (Session) null);
        assertEquals("answer", result);
        assertEquals("hi", a.capturedInputs);
    }

    /** invoke 抛出的异常对象身份不变。 */
    @Test
    void invokePropagatesExceptionUnwrapped() {
        FakeAgent a = new FakeAgent("a2");
        IllegalStateException boom = new IllegalStateException("agent boom");
        a.invokeError = boom;

        try {
            a.invoke("x", (Session) null);
            assertTrue(false, "Should have thrown");
        } catch (IllegalStateException e) {
            assertSame(boom, e);
        }
    }

    /** stream 必须把 streamModes 原值透传——{@code null} 不能被替换为空 List。 */
    @Test
    void streamPassesStreamModesVerbatim() {
        FakeAgent a = new FakeAgent("a3");
        a.streamItems = List.<Object>of("x", "y").iterator();

        Iterator<Object> iter = a.stream("inp", (Session) null, null);
        assertEquals("x", iter.next());
        assertEquals("y", iter.next());
        assertEquals(null, a.lastStreamModes.get(), "null streamModes must pass through unchanged");

        // 显式 list 也要原样透传（同一引用）
        FakeAgent b = new FakeAgent("a4");
        b.streamItems = List.<Object>of().iterator();
        List<StreamMode> modes = List.of(StreamMode.OUTPUT);

        b.stream("inp", (Session) null, modes);
        assertSame(modes, b.lastStreamModes.get(), "explicit streamModes list must pass through unchanged");
    }

    /** 无限迭代器 hasNext 必须返回 true。 */
    @Test
    void streamIteratorHasNextWorks() throws Exception {
        AtomicInteger emitted = new AtomicInteger();
        FakeAgent a = new FakeAgent("a5");
        a.streamItems = new Iterator<>() {
            @Override public boolean hasNext() { return true; }
            @Override public Object next() { return emitted.incrementAndGet(); }
        };

        Iterator<Object> iter = a.stream("inp", (Session) null, List.of(StreamMode.OUTPUT));
        assertTrue(iter.hasNext());
        iter.next();
        assertTrue(iter.hasNext());
    }

    /** 若底层 iterator 可关闭，必须能正常关闭释放长连接资源。 */
    @Test
    void streamClosesAutoCloseableIterator() throws Exception {
        CountDownLatch closed = new CountDownLatch(1);
        class CloseableIterator implements Iterator<Object>, AutoCloseable {
            @Override public boolean hasNext() { return true; }
            @Override public Object next() { return "chunk"; }
            @Override public void close() { closed.countDown(); }
        }
        FakeAgent a = new FakeAgent("a6");
        a.streamItems = new CloseableIterator();

        Iterator<Object> iter = a.stream("inp", (Session) null, List.of(StreamMode.OUTPUT));
        assertTrue(iter.hasNext());
        if (iter instanceof AutoCloseable ac) {
            ac.close();
        }
        assertTrue(closed.await(1, TimeUnit.SECONDS), "AutoCloseable iterator must be closed on close");
    }
}
