// Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.

package com.openjiuwen.harness.deep_agent;

import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.AgentCallbackEvent;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lifecycle regression tests: per-task DeepAgent instances
 * must not leak callbacks into the global callback framework, task-scheduler
 * threads, or executor registrations across create/destroy cycles.
 */
@DisplayName("DeepAgent lifecycle leak regression")
class DeepAgentLifecycleTest {
    private static final int CYCLES = 12;

    /**
     * A rail with one callback per event, registered through the config
     * path exactly like business rails (EdpaTodoRail, EdpaEventRail, ...).
     */
    static final class CountingRail extends AgentRail {
        final AtomicInteger fires = new AtomicInteger();

        @Override
        public Map<AgentCallbackEvent, java.util.function.Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, java.util.function.Consumer<AgentCallbackContext>> callbacks =
                new java.util.LinkedHashMap<>();
            for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
                callbacks.put(event, ctx -> fires.incrementAndGet());
            }
            return callbacks;
        }
    }

    /**
     * A rail that keeps a strong reference to the agent it was initialized
     * with, mirroring business rails like EdpaEventRail/EdpaTodoRail that
     * capture the DeepAgent in their constructors. With the former
     * destroy(), the global callback chain (CallbackInfo -&gt; wrapped
     * callback -&gt; rail lambda -&gt; rail -&gt; agent) pins the whole agent
     * graph; after the fix the graph must be garbage collectable.
     */
    static final class AgentPinningRail extends com.openjiuwen.harness.rails.DeepAgentRail {
        private volatile Object pinnedAgent;

        private final AtomicInteger fires = new AtomicInteger();

        @Override
        public void init(Object agent) {
            this.pinnedAgent = agent;
        }

        @Override
        public Map<AgentCallbackEvent, java.util.function.Consumer<AgentCallbackContext>> getCallbacks() {
            Map<AgentCallbackEvent, java.util.function.Consumer<AgentCallbackContext>> callbacks =
                new java.util.LinkedHashMap<>();
            // The lambda captures the rail (fires), and the rail pins the
            // DeepAgent passed to init: global CallbackInfo -> wrapped
            // callback -> rail -> DeepAgent, the exact retention chain of
            // the business rails (EdpaEventRail holds the DeepAgent).
            callbacks.put(AgentCallbackEvent.BEFORE_INVOKE, ctx -> fires.incrementAndGet());
            return callbacks;
        }
    }

    /**
     * After N create/destroy cycles the global callback framework must hold
     * zero callbacks for the agent's event names — before the fix every
     * cycle permanently retained one CallbackInfo per rail callback, which
     * pinned the whole agent graph of the destroyed instance.
     */
    @Test
    void destroyReleasesGlobalCallbacks() {
        String agentId = null;
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory
                    .createDeepAgent(DeepAgentConfig.builder()
                            .workspacePath("./target/lifecycle-test-repo").rails(List.of(new CountingRail())).build());
            agentId = agent.getCard().getId();
            agent.ensureInitialized();
            agent.destroy();
        }
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            String agentEvent = agentId + "_" + event.getValue();
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent))
                    .as("event %s must have no callbacks left after destroy", agentEvent)
                    .isEmpty();
        }
    }

    /**
     * destroy() must be idempotent: a second call (e.g. factory destroy
     * racing close()) must not throw or unregister another agent's rails.
     */
    @Test
    void destroyIsIdempotent() {
        DeepAgent agent = HarnessFactory
                .createDeepAgent(DeepAgentConfig.builder()
                        .workspacePath("./target/lifecycle-test-repo").rails(List.of(new CountingRail())).build());
        agent.ensureInitialized();
        agent.destroy();
        agent.destroy();
        for (AgentCallbackEvent event : AgentCallbackEvent.values()) {
            String agentEvent = agent.getCard().getId() + "_" + event.getValue();
            assertThat(Runner.callbackFramework().listCallbacks(agentEvent)).isEmpty();
        }
    }

    /**
     * Each enableTaskLoop DeepAgent starts one task-scheduler thread; destroy
     * must stop it, otherwise long-running services accumulate one thread
     * (and one registered executor) per create/destroy cycle.
     */
    @Test
    void destroyStopsTaskSchedulerThread() throws Exception {
        int before = countTaskSchedulerThreads();
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory
                    .createDeepAgent(DeepAgentConfig.builder()
                            .workspacePath("./target/lifecycle-test-repo").enableTaskLoop(true).build());
            agent.ensureInitialized();
            agent.destroy();
        }
        // Give stopped pools a moment to release their worker threads.
        long deadline = System.currentTimeMillis() + 5000L;
        int after;
        do {
            Thread.sleep(200L);
            after = countTaskSchedulerThreads();
        } while (after > before + 1 && System.currentTimeMillis() < deadline);
        assertThat(after).as("task-scheduler threads must not accumulate across create/destroy cycles")
                .isLessThanOrEqualTo(before + 1);
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

    /**
     * After destroy the whole agent object graph must be garbage
     * collectable. The rail pins the agent exactly like the business rails
     * in the DFX deployment; before the fix the global CallbackInfo chain
     * kept the whole agent graph (agent, sessions, HTTP clients, todo
     * state) alive after destroy.
     */
    @Test
    void destroyMakesAgentGraphCollectable() throws Exception {
        java.lang.ref.WeakReference<DeepAgent> reference;
        DeepAgent agent = HarnessFactory.createDeepAgent(DeepAgentConfig.builder()
                .workspacePath("./target/lifecycle-test-repo").rails(List.of(new AgentPinningRail())).build());
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
                .as("destroyed per-task DeepAgent must be garbage collected (global callback chain released)")
                .isTrue();
    }

    /**
     * All per-task agents share one agent id (EdpAgentFactory reuses the
     * startup card), so the global callback lists for the agent's event
     * names accumulate ACROSS agents: every rail registration re-sorts the
     * shared list (sortCallbacks, O(n log n)) inside the serialized creation
     * path — the mechanism that degraded EdpAgentFactory lock hold time
     * linearly and eventually blocked all 200 Tomcat threads. This test
     * pins the boundedness invariant: while one agent is live the count is
     * exactly its own registrations, and after destroy it returns to zero.
     */
    @Test
    void sharedEventCallbacksStayBoundedAcrossCycles() throws Exception {
        // EdpAgentFactory reuses the SAME startup AgentCard for every
        // per-task agent, so all instances share one agent id and their
        // event names collide in the global callback framework.
        com.openjiuwen.core.singleagent.schema.AgentCard sharedCard =
            com.openjiuwen.core.singleagent.schema.AgentCard.builder().id("issue-196-shared-agent")
                    .name("issue-196-shared-agent").description("shared card").build();
        String firstEvent = "issue-196-shared-agent_" + AgentCallbackEvent.BEFORE_INVOKE.getValue();
        int baselineLive = -1;
        for (int i = 0; i < CYCLES; i++) {
            DeepAgent agent = HarnessFactory.createDeepAgent(sharedCard, DeepAgentConfig.builder()
                    .workspacePath("./target/lifecycle-test-repo").rails(List.of(new CountingRail())).build(), null);
            agent.ensureInitialized();
            int live = Runner.callbackFramework().listCallbacks(firstEvent).size();
            if (baselineLive < 0) {
                baselineLive = live;
                assertThat(baselineLive).as("a live agent must register at least the counting rail callback")
                        .isGreaterThanOrEqualTo(1);
            }
            assertThat(live).as(
                    "cycle %d: live registrations must stay at the first cycle's count, not accumulate across agents",
                    i).isEqualTo(baselineLive);
            agent.destroy();
            assertThat(Runner.callbackFramework().listCallbacks(firstEvent))
                    .as("cycle %d: destroy must drain the shared event list back to zero", i).isEmpty();
        }
    }
}
