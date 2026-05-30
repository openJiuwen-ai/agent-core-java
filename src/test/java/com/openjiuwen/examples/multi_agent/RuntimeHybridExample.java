/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TeamRuntime hybrid communication example.
 *
 * <p>Mirrors Python's {@code runtime_hybrid} in {@code examples.multi_agent}.</p>
 */
public final class RuntimeHybridExample {

    public static final String PATTERN = "HYBRID";
    public static final String TOPIC_EXECUTION = "execution_events";
    public static final String TOPIC_COMPLETION = "completion_events";

    private RuntimeHybridExample() {
        // Utility class
    }

    public static TeamRuntime createRuntime(AggregatorAgent aggregator) {
        TeamRuntime runtime = new TeamRuntime();
        AgentCard orchestratorCard = RuntimeP2PExample.card("orchestrator", "orchestrator");
        AgentCard executor1Card = RuntimeP2PExample.card("executor1", "executor1");
        AgentCard executor2Card = RuntimeP2PExample.card("executor2", "executor2");
        AgentCard executor3Card = RuntimeP2PExample.card("executor3", "executor3");
        AgentCard aggregatorCard = RuntimeP2PExample.card("aggregator", "aggregator");
        AgentCard reporterCard = RuntimeP2PExample.card("reporter", "reporter");

        runtime.registerAgent(orchestratorCard, () -> new OrchestratorAgent(orchestratorCard));
        runtime.registerAgent(executor1Card, () -> new ExecutorAgent(executor1Card, 1));
        runtime.registerAgent(executor2Card, () -> new ExecutorAgent(executor2Card, 2));
        runtime.registerAgent(executor3Card, () -> new ExecutorAgent(executor3Card, 3));
        runtime.registerAgent(aggregatorCard, () -> aggregator);
        runtime.registerAgent(reporterCard, () -> new ReporterAgent(reporterCard));
        runtime.subscribe("executor1", TOPIC_EXECUTION);
        runtime.subscribe("executor2", TOPIC_EXECUTION);
        runtime.subscribe("executor3", TOPIC_EXECUTION);
        runtime.subscribe("aggregator", TOPIC_COMPLETION);
        return runtime;
    }

    public static Map<String, Object> runWorkflow(String task) {
        AggregatorAgent aggregator = new AggregatorAgent(RuntimeP2PExample.card("aggregator", "aggregator"), 3);
        TeamRuntime runtime = createRuntime(aggregator);
        runtime.start();
        try {
            Map<String, Object> orchestration = RuntimeP2PExample.asMap(
                    runtime.send(Map.of("task", task), "orchestrator", "main").join());
            Map<String, Object> report = RuntimeP2PExample.asMap(
                    runtime.send(Map.of("results", aggregator.getResults()), "reporter", "main").join());
            return Map.of("orchestration", orchestration, "report", report, "results", aggregator.getResults());
        } finally {
            runtime.stop();
        }
    }

    public static final class OrchestratorAgent extends ExampleCommunicableAgent {
        public OrchestratorAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String task = RuntimeP2PExample.value(inputs, "task");
            publish(Map.of("event", "execution_request", "task", task), TOPIC_EXECUTION, null).join();
            return Map.of("status", "broadcast_done", "task", task);
        }
    }

    public static final class ExecutorAgent extends ExampleCommunicableAgent {
        private final int executorId;

        public ExecutorAgent(AgentCard card, int executorId) {
            super(card);
            this.executorId = executorId;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            if (!(inputs instanceof Map<?, ?> map) || !"execution_request".equals(map.get("event"))) {
                return Map.of("status", "ignored");
            }
            Object taskValue = map.get("task");
            String task = taskValue == null ? "" : String.valueOf(taskValue);
            String result = "executor-" + executorId + " done: " + task;
            publish(Map.of("event", "task_completed", "executor", executorId, "result", result),
                    TOPIC_COMPLETION, null).join();
            return Map.of("status", "executed", "executor", executorId, "result", result);
        }
    }

    public static final class AggregatorAgent extends ExampleCommunicableAgent {
        private final List<String> results = new ArrayList<>();
        private int expected;

        public AggregatorAgent(AgentCard card, int expected) {
            super(card);
            this.expected = expected;
        }

        public void reset(int expected) {
            synchronized (results) {
                this.expected = expected;
                this.results.clear();
            }
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            if (!(inputs instanceof Map<?, ?> map) || !"task_completed".equals(map.get("event"))) {
                return Map.of("status", "ignored");
            }
            synchronized (results) {
                results.add(String.valueOf(map.get("result")));
                return Map.of("status", "aggregated", "count", results.size(), "total", expected);
            }
        }

        public List<String> getResults() {
            synchronized (results) {
                return List.copyOf(results);
            }
        }
    }

    public static final class ReporterAgent extends ExampleCommunicableAgent {
        public ReporterAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            List<?> results = inputs instanceof Map<?, ?> map && map.get("results") instanceof List<?> list
                    ? list : List.of();
            return Map.of("status", "report_generated", "total", results.size());
        }
    }
}
