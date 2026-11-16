/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.graph.CompiledGraph;
import com.openjiuwen.core.graph.Executable;
import com.openjiuwen.core.graph.PregelGraph;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.RouterSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Verifies that conditional routing reads the state of the run being executed
 * instead of the session of whichever run compiled last.
 *
 * <p>A conditional edge is a shared graph definition. When its router resolves
 * its session from state captured at registration time, a second compile of the
 * same graph replaces that session for every run, so a run still in flight
 * routes on another run's state.
 */
@Timeout(30)
class RouterIsolationTest {

    @Test
    void overlappingRun_branchRouterMustRouteOnItsOwnState() throws Exception {
        List<String> leftVisits = new CopyOnWriteArrayList<>();
        List<String> rightVisits = new CopyOnWriteArrayList<>();
        CountDownLatch runAInsideStart = new CountDownLatch(1);
        CountDownLatch releaseRunA = new CountDownLatch(1);

        PregelGraph graph = new PregelGraph();
        graph.addNode("start", new BlockingStart(runAInsideStart, releaseRunA));
        graph.addNode("left", new RecordingNode("left", leftVisits));
        graph.addNode("right", new RecordingNode("right", rightVisits));
        graph.startNode("start");
        graph.endNode("left");
        graph.endNode("right");

        BranchRouter router = new BranchRouter();
        router.addBranch(new RunLabelCondition("session-A"), "left", "left-branch");
        router.addBranch(new RunLabelCondition("session-B"), "right", "right-branch");
        graph.addConditionalEdges("start", router);

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");
        CompiledGraph compiledA = (CompiledGraph) graph.compile(sessionA, null);
        CompiledGraph compiledB = (CompiledGraph) graph.compile(sessionB, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> runA = executor.submit(() -> compiledA.invoke(runInputs(), sessionA));
            assertThat(runAInsideStart.await(10, TimeUnit.SECONDS)).isTrue();

            // A is still inside "start" while B compiles and runs to completion.
            compiledB.invoke(runInputs(), sessionB);

            releaseRunA.countDown();
            runA.get(20, TimeUnit.SECONDS);

            System.out.println("run A visited: " + leftVisits + rightVisits);
            assertThat(leftVisits)
                    .as("run A must route on its own session, not on run B's")
                    .containsExactly("left");
            assertThat(rightVisits)
                    .as("run B must route on its own session")
                    .containsExactly("right");
        } finally {
            releaseRunA.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void routeEvaluationMustResolveTheGivenRunAndIgnoreStoredSession() {
        BranchRouter router = new BranchRouter();
        router.addBranch(new RunLabelCondition("session-A"), "left", "left-branch");
        router.addBranch(new RunLabelCondition("session-B"), "right", "right-branch");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");
        // A stored definition-level session must never win over the run the
        // route is evaluated for.
        router.setSession(sessionB);

        assertThat(router.routeWith(sessionA)).isEqualTo(List.of("left"));
        assertThat(router.routeWith(sessionB)).isEqualTo(List.of("right"));
    }

    @Test
    void functionRouterMustResolveTheRunBeingDispatched() {
        List<String> routed = new CopyOnWriteArrayList<>();
        FunctionRouterAdapter adapter = new FunctionRouterAdapter(
                state -> {
                    RouterSession routerSession = (RouterSession) state;
                    String label = routerSession.sessionId();
                    routed.add(label);
                    return label;
                },
                () -> null);

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");

        assertThat(adapter.routeWith(sessionA)).isEqualTo(sessionA.sessionId());
        assertThat(adapter.routeWith(sessionB)).isEqualTo(sessionB.sessionId());
        assertThat(routed).containsExactly(sessionA.sessionId(), sessionB.sessionId());
    }

    private static Map<String, Object> runInputs() {
        return Map.of("inputs", Map.of(), "config", Map.of());
    }

    private static WorkflowRuntimeSession createSession(String label) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        WorkflowRuntimeState state = WorkflowRuntimeState.create();
        return new WorkflowRuntimeSession(label, null, sessionId, state, null);
    }

    /**
     * Matches only the run whose workflow id equals the expected label.
     *
     * <p>Reads the session handed to {@code evaluate}, so a route can only match
     * when the router resolved the session of the run actually executing.
     */
    private static final class RunLabelCondition implements Branch.BranchCondition {
        private final String expectedLabel;

        private RunLabelCondition(String expectedLabel) {
            this.expectedLabel = expectedLabel;
        }

        @Override
        public boolean evaluate(BaseSession session) {
            return session instanceof WorkflowRuntimeSession runtimeSession
                    && expectedLabel.equals(runtimeSession.workflowId());
        }

        @Override
        public Object traceInfo(BaseSession session) {
            return expectedLabel;
        }
    }

    private static final class BlockingStart extends Executable<Map<String, Object>, Map<String, Object>> {
        private final CountDownLatch inside;
        private final CountDownLatch release;

        private BlockingStart(CountDownLatch inside, CountDownLatch release) {
            this.inside = inside;
            this.release = release;
        }

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                            BaseSession session,
                                            Object... kwargs) {
            if (session instanceof WorkflowRuntimeSession runtimeSession
                    && "session-A".equals(runtimeSession.workflowId())) {
                inside.countDown();
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return Map.of("start", true);
        }
    }

    private static final class RecordingNode extends Executable<Map<String, Object>, Map<String, Object>> {
        private final String name;
        private final List<String> visits;

        private RecordingNode(String name, List<String> visits) {
            this.name = name;
            this.visits = visits;
        }

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                            BaseSession session,
                                            Object... kwargs) {
            visits.add(name);
            return Map.of(name, true);
        }
    }
}
