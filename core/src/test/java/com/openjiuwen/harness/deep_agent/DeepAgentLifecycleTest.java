/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallback;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle regression: per-task DeepAgent instances must not leak callbacks,
 * task-scheduler threads, or executor registrations across create/destroy cycles.
 */
@DisplayName("DeepAgent lifecycle leak regression")
class DeepAgentLifecycleTest {
    private static final int CYCLES = 12;

    static final class CountingRail extends AgentRail {
        final AtomicInteger fires = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
            Map<AgentCallbackEvent, AgentCallback> callbacks = new LinkedHashMap<>();
            for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
                callbacks.put(event, ctx -> {
                    fires.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });
            }
            return callbacks;
        }
    }

    static final class AgentPinningRail extends DeepAgentRail {
        private volatile Object pinnedAgent;
        private final AtomicInteger fires = new AtomicInteger();

        @Override
        public void init(DeepAgent agent) {
            super.init(agent);
            this.pinnedAgent = agent;
        }

        @Override
        public Map<AgentCallbackEvent, AgentCallback> getCallbacks() {
            Map<AgentCallbackEvent, AgentCallback> callbacks = new LinkedHashMap<>();
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> {
                fires.incrementAndGet();
                return CompletableFuture.completedFuture(null);
            });
            return callbacks;
        }
    }

    @Test
    void destroyReleasesGlobalCallbacks() {
        String agentId = null;
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                    .workspacePath("./target/lifecycle-test-repo")
                    .rails(List.of(new CountingRail()))
                    .build());
            agentId = agent.getCard().getId();
            agent.ensureInitialized();
            agent.destroy();
        }
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            String agentEvent = agentId + "_" + "AgentCallbackEvent." + event.name();
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent))
                    .as("event %s must have no callbacks left after destroy", agentEvent)
                    .isEmpty();
        }
    }

    @Test
    void destroyIsIdempotent() {
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath("./target/lifecycle-test-repo")
                .rails(List.of(new CountingRail()))
                .build());
        agent.ensureInitialized();
        agent.destroy();
        agent.destroy();
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            String agentEvent = agent.getCard().getId() + "_" + "AgentCallbackEvent." + event.name();
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent)).isEmpty();
        }
    }

    @Test
    void destroyStopsTaskSchedulerThread() throws Exception {
        int before = countTaskSchedulerThreads();
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                    .workspacePath("./target/lifecycle-test-repo")
                    .enableTaskLoop(true)
                    .build());
            agent.ensureInitialized();
            agent.destroy();
        }
        long deadline = System.currentTimeMillis() + 5000L;
        int after;
        do {
            Thread.sleep(200L);
            after = countTaskSchedulerThreads();
        } while (after > before + 1 && System.currentTimeMillis() < deadline);
        assertThat(after)
                .as("task-scheduler threads must not accumulate across create/destroy cycles")
                .isLessThanOrEqualTo(before + 1);
    }

    @Test
    void destroyMakesAgentGraphCollectable() throws Exception {
        java.lang.ref.WeakReference<DeepAgent> reference;
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath("./target/lifecycle-test-repo")
                .rails(List.of(new AgentPinningRail()))
                .build());
        agent.ensureInitialized();
        reference = new java.lang.ref.WeakReference<>(agent);
        agent.destroy();
        agent = null;
        boolean collected = false;
        for (int attempt = 0; attempt < 20 && !collected; attempt++) {
            System.gc();
            Thread.sleep(50L);
            collected = reference.get() == null;
        }
        assertThat(collected)
                .as("destroyed per-task DeepAgent must be garbage collected")
                .isTrue();
    }

    @Test
    void sharedEventCallbacksStayBoundedAcrossCycles() {
        AgentCard sharedCard = AgentCard.builder()
                .id("issue-196-shared-agent")
                .name("issue-196-shared-agent")
                .description("shared card")
                .build();
        String firstEvent = "issue-196-shared-agent_AgentCallbackEvent.BEFORE_INVOKE";
        int baselineLive = -1;
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory.createDeepAgent(
                    sharedCard,
                    DeepAgentConfig.builder()
                            .workspacePath("./target/lifecycle-test-repo")
                            .rails(List.of(new CountingRail()))
                            .build(),
                    null);
            agent.ensureInitialized();
            int live = Runner.callbackFramework().listCallbacks(firstEvent).size();
            if (baselineLive < 0) {
                baselineLive = live;
                assertThat(baselineLive)
                        .as("a live agent must register at least the counting rail callback")
                        .isGreaterThanOrEqualTo(1);
            }
            assertThat(live)
                    .as("cycle %d: live registrations must stay bounded", i)
                    .isEqualTo(baselineLive);
            agent.destroy();
            assertThat(Runner.callbackFramework().listCallbacks(firstEvent))
                    .as("cycle %d: destroy must drain the shared event list", i)
                    .isEmpty();
        }
    }

    private static int countTaskSchedulerThreads() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int count = 0;
        for (ThreadInfo info : threads.dumpAllThreads(false, false)) {
            if (info != null && info.getThreadName() != null && info.getThreadName().contains("task-scheduler")) {
                count++;
            }
        }
        return count;
    }
}
