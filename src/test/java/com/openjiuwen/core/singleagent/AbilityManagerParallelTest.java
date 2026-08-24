/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.SessionContextHolder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parallel execution coverage for {@link AbilityManager}, mirroring Python's
 * {@code parallel_tool_calls} behaviour.
 */
class AbilityManagerParallelTest {

    private final List<String> toolIds = new ArrayList<>();
    private AbilityManager manager;

    @BeforeEach
    void setUp() {
        manager = new AbilityManager();
    }

    @AfterEach
    void tearDown() {
        for (String toolId : toolIds) {
            Runner.resourceMgr().removeTool(toolId, null, TagMatchStrategy.ALL, true);
        }
        toolIds.clear();
        SessionContextHolder.clearCurrentSession();
    }

    @Test
    @DisplayName("多个 ToolCall 默认并行执行并保持输入顺序")
    void executeRunsMultipleToolCallsInParallelAndKeepsResultOrder() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        String firstId = registerBlockingTool("parallel-first", bothStarted);
        String secondId = registerBlockingTool("parallel-second", bothStarted);

        long start = System.nanoTime();
        List<AbilityManager.ExecutionResult> results = manager.execute(
                List.of(
                        ToolCall.builder().id("tc-1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name(secondId).arguments("{}").build()
                ),
                true
        );
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertThat(results).hasSize(2);
        assertThat(String.valueOf(results.get(0).result())).contains("name=" + firstId);
        assertThat(String.valueOf(results.get(1).result())).contains("name=" + secondId);
        assertThat(String.valueOf(results.get(0).result())).contains("parallel=true");
        assertThat(String.valueOf(results.get(1).result())).contains("parallel=true");
        assertThat(elapsedMillis).isLessThan(1500L);
    }

    @Test
    @DisplayName("parallel_tool_calls=false 时串行执行")
    void executeRunsSequentiallyWhenParallelDisabled() {
        List<String> order = new ArrayList<>();
        String firstId = registerOrderingTool("serial-first", true, order);
        String secondId = registerOrderingTool("serial-second", true, order);

        List<AbilityManager.ExecutionResult> results = manager.execute(
                List.of(
                        ToolCall.builder().id("tc-1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name(secondId).arguments("{}").build()
                ),
                false
        );

        assertThat(results).hasSize(2);
        assertThat(order).containsExactly(firstId, secondId);
        assertThat(String.valueOf(results.get(0).result())).contains(firstId);
        assertThat(String.valueOf(results.get(1).result())).contains(secondId);
    }

    @Test
    @DisplayName("同一 file_path 的文件工具按输入顺序串行")
    void executeSerializesSameFilePathInEmittedOrder() {
        List<String> order = new ArrayList<>();
        registerNamedFileTool("read_file", order);
        registerNamedFileTool("write_file", order);

        List<AbilityManager.ExecutionResult> results = manager.execute(
                List.of(
                        ToolCall.builder().id("tc-1").name("read_file")
                                .arguments("{\"file_path\":\"D:/tmp/same.txt\"}").build(),
                        ToolCall.builder().id("tc-2").name("write_file")
                                .arguments("{\"file_path\":\"D:/tmp/same.txt\"}").build()
                ),
                true
        );

        assertThat(results).hasSize(2);
        assertThat(order).containsExactly("read_file", "write_file");
    }

    @Test
    @DisplayName("parallel_safe=false 的工具形成独占屏障")
    void executeTreatsNonParallelSafeToolAsBarrier() {
        List<String> order = new ArrayList<>();
        String firstId = registerOrderingTool("safe-first", true, order);
        String barrierId = registerOrderingTool("unsafe-barrier", false, order);
        String lastId = registerOrderingTool("safe-last", true, order);

        List<AbilityManager.ExecutionResult> results = manager.execute(
                List.of(
                        ToolCall.builder().id("tc-1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-2").name(barrierId).arguments("{}").build(),
                        ToolCall.builder().id("tc-3").name(lastId).arguments("{}").build()
                ),
                true
        );

        assertThat(results).hasSize(3);
        assertThat(order).containsExactly(firstId, barrierId, lastId);
        assertThat(String.valueOf(results.get(1).result())).contains("parallel_safe=false");
    }

    @Test
    @DisplayName("并行执行时 worker 线程可读取 SessionContextHolder")
    void parallelExecuteKeepsSessionContextAvailableInWorkerThreads() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        String firstId = registerSessionAwareTool("session-first", bothStarted);
        String secondId = registerSessionAwareTool("session-second", bothStarted);

        AgentSession session = AgentSession.createAgentSession("worker-session", null, null);
        SessionContextHolder.setCurrentSession(session);
        List<AbilityManager.ExecutionResult> results = manager.execute(
                List.of(
                        ToolCall.builder().id("tc-s1").name(firstId).arguments("{}").build(),
                        ToolCall.builder().id("tc-s2").name(secondId).arguments("{}").build()
                ),
                true
        );

        assertThat(results).hasSize(2);
        assertThat(results).extracting(result -> String.valueOf(result.result()))
                .allMatch(text -> text.contains("session=worker-session"));
    }

    private String registerBlockingTool(String prefix, CountDownLatch bothStarted) {
        return registerThreadCapturingTool(prefix, bothStarted, new AtomicReference<>(), true);
    }

    private String registerThreadCapturingTool(
            String prefix,
            CountDownLatch bothStarted,
            AtomicReference<String> threadName,
            boolean awaitPeer
    ) {
        String toolId = prefix + "-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description(prefix).inputParams(Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of()
                )).build(),
                inputs -> {
                    threadName.set(Thread.currentThread().getName());
                    bothStarted.countDown();
                    boolean parallel = awaitPeer && await(bothStarted);
                    return "name=" + toolId + ":parallel=" + parallel
                            + ":thread=" + Thread.currentThread().getName();
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        toolIds.add(toolId);
        manager.add(tool.getCard());
        return toolId;
    }

    private void registerNamedFileTool(String name, List<String> order) {
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(name).name(name).description(name).inputParams(Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of()
                )).build(),
                inputs -> {
                    synchronized (order) {
                        order.add(name);
                    }
                    sleepQuietly(80);
                    return "name=" + name;
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        toolIds.add(name);
        manager.add(tool.getCard());
    }

    private String registerOrderingTool(String prefix, boolean parallelSafe, List<String> order) {
        String toolId = prefix + "-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder()
                        .id(toolId)
                        .name(toolId)
                        .description(prefix)
                        .inputParams(Map.of("type", "object", "properties", Map.of(), "required", List.of()))
                        .parallelSafe(parallelSafe)
                        .build(),
                inputs -> {
                    synchronized (order) {
                        order.add(toolId);
                    }
                    sleepQuietly(80);
                    return "name=" + toolId + ":parallel_safe=" + parallelSafe;
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        toolIds.add(toolId);
        manager.add(tool.getCard());
        return toolId;
    }

    private String registerSessionAwareTool(String prefix, CountDownLatch bothStarted) {
        String toolId = prefix + "-" + UUID.randomUUID();
        LocalFunction tool = new LocalFunction(
                ToolCard.builder().id(toolId).name(toolId).description(prefix).inputParams(Map.of(
                        "type", "object", "properties", Map.of(), "required", List.of()
                )).build(),
                (LocalFunction.ContextFunction) (inputs, kwargs) -> {
                    bothStarted.countDown();
                    await(bothStarted);
                    Object current = SessionContextHolder.getCurrentSession();
                    return "session=" + SessionContextHolder.resolveSessionId(current);
                }
        );
        Runner.resourceMgr().addTool(tool, null);
        toolIds.add(toolId);
        manager.add(tool.getCard());
        return toolId;
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
