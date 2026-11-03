/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.remoteclient;

import static org.junit.jupiter.api.Assertions.assertTrue;

import reactor.test.StepVerifier;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 {@link RemoteClient} 接口的默认 Mono/Flux 方法：
 * 任何具体实现（A2A、MQ、自定义）都能直接获得可用的响应式 API，
 * 无需自行实现 Mono/Flux 方法。
 */
class RemoteClientReactiveTest {
    /** 最小化的 RemoteClient 实现，由固定值 + 迭代器驱动。 */
    private static class FakeRemoteClient implements RemoteClient {
        private final Object invokeResult;
        private final Iterable<Object> streamItems;
        private RuntimeException invokeError;
        private final AtomicInteger streamCallCount = new AtomicInteger(0);

        FakeRemoteClient(Object invokeResult, Iterable<Object> streamItems) {
            this.invokeResult = invokeResult;
            this.streamItems = streamItems;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Double timeoutSeconds) {
            if (invokeError != null) {
                throw invokeError;
            }
            return invokeResult;
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Double timeoutSeconds) {
            streamCallCount.incrementAndGet();
            return streamItems.iterator();
        }
    }

    /** invokeAsync 把同步 invoke 的返回值原样发出。 */
    @Test
    void invokeMonoDefaultDelegatesToInvoke() {
        FakeRemoteClient client = new FakeRemoteClient("ok", List.of());
        StepVerifier.create(client.invokeAsync(Map.of(), 1.0)).expectNext("ok").verifyComplete();
    }

    /** invokeAsync 抛出的异常对象身份不变，不被 Reactor 包装。 */
    @Test
    void invokeMonoDefaultPropagatesExceptionUnwrapped() {
        FakeRemoteClient client = new FakeRemoteClient(null, List.of());
        IllegalStateException boom = new IllegalStateException("remote boom");
        client.invokeError = boom;
        StepVerifier.create(client.invokeAsync(Map.of(), null)).expectErrorMatches(t -> t == boom).verify();
    }

    /** streamAsync 按序发射 stream() 迭代器中的全部元素。 */
    @Test
    void streamFluxDefaultEmitsAllItemsInOrder() {
        FakeRemoteClient client = new FakeRemoteClient(null, Arrays.<Object>asList("a", "b", "c"));
        StepVerifier.create(client.streamAsync(Map.of(), null)).expectNext("a", "b", "c").verifyComplete();
    }

    /** 取消订阅后无限流必须立刻停止（允许至多 1 个已在途的多余发射）。 */
    @Test
    void streamFluxDefaultCancellationStopsIteration() throws Exception {
        AtomicInteger emitted = new AtomicInteger();
        CountDownLatch streamCalled = new CountDownLatch(1);
        Iterable<Object> infinite = () -> {
            streamCalled.countDown();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return true;
                }

                @Override
                public Object next() {
                    return emitted.incrementAndGet();
                }
            };
        };
        FakeRemoteClient client = new FakeRemoteClient(null, infinite);

        StepVerifier.create(client.streamAsync(Map.of(), null), 4).expectNextCount(4).thenCancel()
                .verify(Duration.ofSeconds(5));

        assertTrue(streamCalled.await(1, TimeUnit.SECONDS));
        int afterCancel = emitted.get();
        Thread.sleep(200);
        assertTrue(emitted.get() - afterCancel <= 1,
                "iteration must stop on cancel; saw " + (emitted.get() - afterCancel) + " extra");
    }
}
