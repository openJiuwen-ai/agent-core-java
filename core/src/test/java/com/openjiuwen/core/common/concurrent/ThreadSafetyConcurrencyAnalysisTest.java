/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.concurrent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.graph.Vertex;
import com.openjiuwen.core.workflow.component.llm.LLMExecutableState;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Trimmed port of the 730 thread-safety analysis coverage for classes that
 * still exist with the same race on develop. TeamDatabase / InMemoryKVStore /
 * WorkflowSpec / LoopQueues blocks are omitted because those surfaces diverged.
 */
class ThreadSafetyConcurrencyAnalysisTest {

    @Test
    @DisplayName("ContextEngine.createContext 对同一 session/context 只物化一份")
    void contextEngineCreateContextIsAtomic() throws Exception {
        ContextEngine engine = new ContextEngine();
        int threadCount = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<ModelContext> created = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    created.add(engine.createContext("shared.context", null));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(created).hasSize(threadCount);
        ModelContext first = created.get(0);
        assertThat(created).allSatisfy(context -> assertThat(context).isSameAs(first));
        engine.clearContext(null, ContextEngine.DEFAULT_SESSION_ID);
        assertThat(engine.getContext("shared.context", ContextEngine.DEFAULT_SESSION_ID)).isNull();
    }

    @Test
    @DisplayName("LLMExecutableState 并发累积内容不丢字")
    void llmExecutableStateAccumulateIsThreadSafe() throws Exception {
        LLMExecutableState state = new LLMExecutableState();
        int threadCount = 8;
        int perThread = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        state.accumulateContent("x");
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Field accumulated = LLMExecutableState.class.getDeclaredField("accumulatedContent");
        accumulated.setAccessible(true);
        StringBuilder content = (StringBuilder) accumulated.get(state);
        assertThat(content.length()).isEqualTo(threadCount * perThread);
        state.clear();
        assertThat(((StringBuilder) accumulated.get(state)).length()).isZero();
    }

    @Test
    @DisplayName("Vertex 计数器是 AtomicInteger，可并发自增")
    void vertexCountersAreAtomicIntegers() throws Exception {
        Field callCount = Vertex.class.getDeclaredField("callCount");
        Field streamCallCount = Vertex.class.getDeclaredField("streamCallCount");
        assertThat(callCount.getType()).isEqualTo(AtomicInteger.class);
        assertThat(streamCallCount.getType()).isEqualTo(AtomicInteger.class);
        assertThat(Modifier.isFinal(callCount.getModifiers())).isTrue();
        assertThat(Modifier.isFinal(streamCallCount.getModifiers())).isTrue();
    }

    @Test
    @DisplayName("Model 注册表使用 ConcurrentHashMap")
    void modelRegistriesUseConcurrentHashMap() throws Exception {
        Field invokers = com.openjiuwen.core.foundation.llm.Model.class.getDeclaredField("INVOKERS");
        Field factories = com.openjiuwen.core.foundation.llm.Model.class.getDeclaredField("CLIENT_FACTORIES");
        invokers.setAccessible(true);
        factories.setAccessible(true);
        assertThat(invokers.get(null)).isInstanceOf(ConcurrentHashMap.class);
        assertThat(factories.get(null)).isInstanceOf(ConcurrentHashMap.class);
    }
}
