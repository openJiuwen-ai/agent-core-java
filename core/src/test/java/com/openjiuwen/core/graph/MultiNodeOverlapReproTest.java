/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeState;
import com.openjiuwen.core.workflow.internal.WorkflowRuntimeSession;

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

@Timeout(30)
class MultiNodeOverlapReproTest {

    @Test
    void overlappingRun_secondNodeMustObserveItsOwnSession() throws Exception {
        PregelGraph graph = new PregelGraph();
        CountDownLatch runAInsideNode1 = new CountDownLatch(1);
        CountDownLatch releaseRunA = new CountDownLatch(1);
        List<String> node1Sessions = new CopyOnWriteArrayList<>();
        List<String> node2Sessions = new CopyOnWriteArrayList<>();
        graph.addNode("node1", new RecordingExecutable(
                "node1", node1Sessions, runAInsideNode1, releaseRunA));
        graph.addNode("node2", new RecordingExecutable(
                "node2", node2Sessions, null, null));
        graph.startNode("node1");
        graph.addEdge("node1", "node2");
        graph.endNode("node2");

        WorkflowRuntimeSession sessionA = createSession("session-A");
        WorkflowRuntimeSession sessionB = createSession("session-B");
        CompiledGraph compiledA = (CompiledGraph) graph.compile(sessionA, null);
        CompiledGraph compiledB = (CompiledGraph) graph.compile(sessionB, null);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> runA = executor.submit(
                    () -> compiledA.invoke(runInputs(), sessionA));
            assertThat(runAInsideNode1.await(10, TimeUnit.SECONDS)).isTrue();

            // A 仍在 node1 内时，B 初始化共享 Vertex 并完整执行。
            compiledB.invoke(runInputs(), sessionB);

            releaseRunA.countDown();
            runA.get(20, TimeUnit.SECONDS);

            System.out.println("node1 observed sessions: " + node1Sessions);
            System.out.println("node2 observed sessions: " + node2Sessions);
            assertThat(node2Sessions)
                    .as("each run's second node must observe its own session "
                            + "after an overlapping run")
                    .containsExactlyInAnyOrder("session-A", "session-B");
        } finally {
            releaseRunA.countDown();
            executor.shutdownNow();
        }
    }

    private static Map<String, Object> runInputs() {
        return Map.of("inputs", Map.of(), "config", Map.of());
    }

    private static WorkflowRuntimeSession createSession(String label) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        WorkflowRuntimeState state = WorkflowRuntimeState.create();
        return new WorkflowRuntimeSession(label, null, sessionId, state, null);
    }

    private static final class RecordingExecutable
            extends Executable<Map<String, Object>, Map<String, Object>> {
        private final String nodeName;
        private final List<String> observedSessions;
        private final CountDownLatch insideNode;
        private final CountDownLatch release;

        private RecordingExecutable(String nodeName,
                                    List<String> observedSessions,
                                    CountDownLatch insideNode,
                                    CountDownLatch release) {
            this.nodeName = nodeName;
            this.observedSessions = observedSessions;
            this.insideNode = insideNode;
            this.release = release;
        }

        @Override
        public Map<String, Object> onInvoke(Map<String, Object> inputs,
                                             BaseSession session,
                                             Object... kwargs) {
            if (session instanceof WorkflowRuntimeSession runtimeSession) {
                String label = runtimeSession.workflowId();
                observedSessions.add(label);
                if ("session-A".equals(label) && insideNode != null) {
                    insideNode.countDown();
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return Map.of(nodeName, true);
        }
    }
}
