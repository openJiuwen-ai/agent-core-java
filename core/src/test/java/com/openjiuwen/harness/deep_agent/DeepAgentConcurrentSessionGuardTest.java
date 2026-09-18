/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.multitenant.TenantContextHolder;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.cwd.CwdContext;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@code startTaskLoopRuntime} rejects concurrent task loops that
 * share the same session ID, so one request's cleanup cannot tear down another.
 */
class DeepAgentConcurrentSessionGuardTest {

    @TempDir
    private Path tempDir;

    private DeepAgent agent;

    @BeforeEach
    void resetContext() {
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @AfterEach
    void cleanup() {
        if (agent != null) {
            agent.shutdown();
        }
        TenantContextHolder.clearCurrentTenant();
        CwdContext.reset();
    }

    @Test
    @DisplayName("concurrent invoke with same sessionId: only one enters task loop, rest rejected")
    @Timeout(30)
    void concurrentSameSessionIdRejectsDuplicate() throws Exception {
        agent = newTaskLoopAgent();
        CountDownLatch modelEntered = new CountDownLatch(1);
        CountDownLatch releaseModel = new CountDownLatch(1);
        installHoldingModel(agent, modelEntered, releaseModel);

        String warmupSession = "warmup-session";
        agent.invoke(Map.of("query", "warmup", "conversation_id", warmupSession));

        String sessionId = "concurrent-guard-session";
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    awaitQuietly(startLatch);
                    try {
                        results.add(agent.invoke(Map.of("query", "test", "conversation_id", sessionId)));
                    } catch (Throwable ex) {
                        errors.add(ex);
                    }
                }));
            }
            startLatch.countDown();
            assertThat(modelEntered.await(10, TimeUnit.SECONDS)).isTrue();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && errors.size() < threadCount - 1) {
                Thread.sleep(20);
            }
            releaseModel.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            releaseModel.countDown();
            executor.shutdownNow();
        }

        long rejected = errors.stream().filter(DeepAgentConcurrentSessionGuardTest::isSessionGuardError).count();
        long enteredLoop = results.size() + errors.stream()
                .filter(ex -> !isSessionGuardError(ex))
                .count();

        assertThat(rejected)
                .as("at least one thread should be rejected by the session guard")
                .isGreaterThanOrEqualTo(1);
        assertThat(enteredLoop)
                .as("at most one thread should enter the task loop")
                .isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("sequential invoke with same sessionId: second request not rejected after first completes")
    @Timeout(15)
    void sequentialSameSessionIdAllowsReuse() {
        agent = newTaskLoopAgent();
        installEchoModel(agent);
        String sessionId = "sequential-reuse-session";

        agent.invoke(Map.of("query", "first", "conversation_id", sessionId));
        try {
            agent.invoke(Map.of("query", "second", "conversation_id", sessionId));
        } catch (RuntimeException ex) {
            if (isSessionGuardError(ex)) {
                throw new AssertionError("Session was not released after the first request completed", ex);
            }
            throw ex;
        }
    }

    private DeepAgent newTaskLoopAgent() {
        return HarnessFactory.createDeepAgent(
                uniqueCard("guard-test"),
                DeepAgentConfig.builder()
                        .enableTaskLoop(true)
                        .maxIterations(1)
                        .completionTimeout(2.0)
                        .workspacePath(tempDir.toString())
                        .build(),
                null);
    }

    private static AgentCard uniqueCard(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().replace("-", "");
        return AgentCard.builder().id(id).name(prefix).description("session guard test").build();
    }

    private static void installEchoModel(DeepAgent agent) {
        installHoldingModel(agent, null, null);
    }

    @SuppressWarnings("unchecked")
    private static void installHoldingModel(DeepAgent agent, CountDownLatch entered, CountDownLatch release) {
        Model model = Mockito.mock(Model.class);
        when(model.invoke(any(List.class), any(ModelInvokeOptions.class)))
                .thenAnswer(invocation -> {
                    signalAndHold(entered, release);
                    return java.util.concurrent.CompletableFuture.completedFuture(echoMessage(invocation.getArgument(0)));
                });
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    signalAndHold(entered, release);
                    return echoMessage(invocation.getArgument(0));
                });
        agent.getAgent().setLlm(model);
    }

    private static void signalAndHold(CountDownLatch entered, CountDownLatch release) throws InterruptedException {
        if (entered != null) {
            entered.countDown();
        }
        if (release != null) {
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static AssistantMessage echoMessage(Object rawMessages) {
        return AssistantMessage.builder().content(extractLastMessageText(rawMessages)).build();
    }

    private static String extractLastMessageText(Object rawMessages) {
        if (rawMessages instanceof List<?> messages && !messages.isEmpty()) {
            Object last = messages.get(messages.size() - 1);
            if (last instanceof BaseMessage baseMessage && baseMessage.getContent() != null) {
                return String.valueOf(baseMessage.getContent());
            }
        }
        return String.valueOf(rawMessages);
    }

    private static boolean isSessionGuardError(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof IllegalStateException
                    && current.getMessage() != null
                    && current.getMessage().contains("Task loop already active")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
