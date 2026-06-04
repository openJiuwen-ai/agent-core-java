/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.config.TeamConfig;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * BaseTeam hybrid communication example.
 *
 * <p>Mirrors Python's {@code team_hybrid} in {@code examples.multi_agent}.</p>
 */
public final class TeamHybridExample {

    public static final String TEAM_ID = "task_execution_team";

    private TeamHybridExample() {
        // Utility class
    }

    public static TaskExecutionTeam createTeam() {
        TeamCard teamCard = TeamCard.builder()
                .id(TEAM_ID)
                .name(TEAM_ID)
                .description("Task execution team")
                .build();
        return new TaskExecutionTeam(teamCard, new TeamConfig());
    }

    public static Map<String, Object> runWorkflow(String task) {
        return RuntimeP2PExample.asMap(createTeam().invoke(Map.of("task", task)).join());
    }

    /**
     * Task execution team that combines P2P orchestration with Pub-Sub fan-out.
     */
    public static final class TaskExecutionTeam extends BaseTeam {
        public static final int EXECUTOR_COUNT = 3;
        public static final int EXTRA_PUBLISH_COUNT = 1;

        private final RuntimeHybridExample.AggregatorAgent aggregator;
        private boolean subscriptionsReady;

        public TaskExecutionTeam(TeamCard card, TeamConfig config) {
            super(card, config);
            AgentCard orchestratorCard = RuntimeP2PExample.card("orchestrator", "orchestrator");
            AgentCard executor1Card = RuntimeP2PExample.card("executor1", "executor1");
            AgentCard executor2Card = RuntimeP2PExample.card("executor2", "executor2");
            AgentCard executor3Card = RuntimeP2PExample.card("executor3", "executor3");
            AgentCard aggregatorCard = RuntimeP2PExample.card("aggregator", "aggregator");
            AgentCard reporterCard = RuntimeP2PExample.card("reporter", "reporter");

            this.aggregator = new RuntimeHybridExample.AggregatorAgent(aggregatorCard, expectedResultCount());

            addAgent(orchestratorCard, () -> new RuntimeHybridExample.OrchestratorAgent(orchestratorCard));
            addAgent(executor1Card, () -> new RuntimeHybridExample.ExecutorAgent(executor1Card, 1));
            addAgent(executor2Card, () -> new RuntimeHybridExample.ExecutorAgent(executor2Card, 2));
            addAgent(executor3Card, () -> new RuntimeHybridExample.ExecutorAgent(executor3Card, 3));
            addAgent(aggregatorCard, () -> aggregator);
            addAgent(reporterCard, () -> new RuntimeHybridExample.ReporterAgent(reporterCard));
        }

        public static int expectedResultCount() {
            return EXECUTOR_COUNT * (1 + EXTRA_PUBLISH_COUNT);
        }

        @Override
        public CompletableFuture<Object> invoke(Object input) {
            List<Map<String, Object>> events = new ArrayList<>();
            Map<String, Object> result = runWorkflow(input, events);
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Stream<Object> stream(Object input) {
            List<Map<String, Object>> events = new ArrayList<>();
            runWorkflow(input, events);
            return events.stream().map(event -> (Object) event);
        }

        private Map<String, Object> runWorkflow(Object input, List<Map<String, Object>> events) {
            String task = RuntimeP2PExample.value(input, "task");
            aggregator.reset(expectedResultCount());
            setupSubscriptions();
            runtime.start();
            try {
                events.add(event("team_started", null, Map.of("task", task)));

                Map<String, Object> orchestration = RuntimeP2PExample.asMap(
                        runtime.send(Map.of("task", task), "orchestrator", "main_process").join());
                events.add(event("orchestrator_received", "orchestrator", Map.of("task", task)));
                events.add(event("orchestrator_published", "orchestrator", orchestration));

                runtime.publish(Map.of("event", "execution_request", "task", task + " [direct publish]",
                        "source", "main_process"), RuntimeHybridExample.TOPIC_EXECUTION, "main_process").join();
                events.add(event("team_direct_publish", null, Map.of("task", task + " [direct publish]")));

                List<String> results = aggregator.getResults();
                for (int i = 0; i < results.size(); i++) {
                    String source = i % EXECUTOR_COUNT == 0 ? "executor1"
                            : i % EXECUTOR_COUNT == 1 ? "executor2" : "executor3";
                    events.add(event("executor_started", source, Map.of("task", task)));
                    events.add(event("aggregator_progress", "aggregator",
                            Map.of("count", i + 1, "total", expectedResultCount(), "result", results.get(i))));
                }

                Map<String, Object> report = RuntimeP2PExample.asMap(
                        runtime.send(Map.of("results", results), "reporter", "main_process").join());
                events.add(event("reporter_completed", "reporter", report));

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("orchestration", orchestration);
                result.put("report", report);
                events.add(event("team_completed", null, Map.of("status", "completed", "result", result)));
                return result;
            } finally {
                runtime.stop();
            }
        }

        private void setupSubscriptions() {
            if (subscriptionsReady) {
                return;
            }
            runtime.subscribe("executor1", RuntimeHybridExample.TOPIC_EXECUTION);
            runtime.subscribe("executor2", RuntimeHybridExample.TOPIC_EXECUTION);
            runtime.subscribe("executor3", RuntimeHybridExample.TOPIC_EXECUTION);
            runtime.subscribe("aggregator", RuntimeHybridExample.TOPIC_COMPLETION);
            subscriptionsReady = true;
        }

        private Map<String, Object> event(String event, String sourceAgentId, Map<String, Object> values) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", event);
            if (sourceAgentId != null) {
                payload.put("source_agent_id", sourceAgentId);
            }
            payload.putAll(values);
            return payload;
        }
    }
}
