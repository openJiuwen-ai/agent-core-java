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
 * TeamRuntime publish-subscribe communication example.
 *
 * <p>Mirrors Python's {@code runtime_pubsub} in {@code examples.multi_agent}.</p>
 */
public final class RuntimePubSubExample {

    public static final String PATTERN = "PUB_SUB";
    public static final String TOPIC_TASKS = "task_events";
    public static final String TOPIC_COMPLETIONS = "completion_events";

    private RuntimePubSubExample() {
        // Utility class
    }

    public static TeamRuntime createRuntime(MonitorAgent monitor) {
        TeamRuntime runtime = new TeamRuntime();
        AgentCard coordinatorCard = RuntimeP2PExample.card("coordinator", "Task coordinator");
        AgentCard worker1Card = RuntimeP2PExample.card("worker1", "Worker 1");
        AgentCard worker2Card = RuntimeP2PExample.card("worker2", "Worker 2");
        AgentCard worker3Card = RuntimeP2PExample.card("worker3", "Worker 3");
        AgentCard monitorCard = RuntimeP2PExample.card("monitor", "Task monitor");

        runtime.registerAgent(coordinatorCard, () -> new CoordinatorAgent(coordinatorCard));
        runtime.registerAgent(worker1Card, () -> new WorkerAgent(worker1Card, "1"));
        runtime.registerAgent(worker2Card, () -> new WorkerAgent(worker2Card, "2"));
        runtime.registerAgent(worker3Card, () -> new WorkerAgent(worker3Card, "3"));
        runtime.registerAgent(monitorCard, () -> monitor);
        runtime.subscribe("worker1", TOPIC_TASKS);
        runtime.subscribe("worker2", TOPIC_TASKS);
        runtime.subscribe("worker3", TOPIC_TASKS);
        runtime.subscribe("monitor", TOPIC_COMPLETIONS);
        return runtime;
    }

    public static Map<String, Object> runWorkflow(String task) {
        MonitorAgent monitor = new MonitorAgent(RuntimeP2PExample.card("monitor", "Task monitor"));
        TeamRuntime runtime = createRuntime(monitor);
        runtime.start();
        try {
            runtime.publish(Map.of("event", "new_task", "task", task, "priority", "high"),
                    TOPIC_TASKS, "coordinator").join();
            List<Map<String, Object>> completions = monitor.getCompletions();
            return Map.of("status", "completed", "completion_count", completions.size(),
                    "completions", completions);
        } finally {
            runtime.stop();
        }
    }

    public static final class CoordinatorAgent extends ExampleCommunicableAgent {
        public CoordinatorAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String task = RuntimeP2PExample.value(inputs, "task");
            publish(Map.of("event", "new_task", "task", task, "priority", "high"), TOPIC_TASKS, null).join();
            return Map.of("status", "task_published");
        }
    }

    public static final class WorkerAgent extends ExampleCommunicableAgent {
        private final String workerId;

        public WorkerAgent(AgentCard card, String workerId) {
            super(card);
            this.workerId = workerId;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            if (!(inputs instanceof Map<?, ?> map) || !"new_task".equals(map.get("event"))) {
                return Map.of("status", "ignored");
            }
            Object taskValue = map.get("task");
            String task = taskValue == null ? "" : String.valueOf(taskValue);
            String result = "Worker-" + workerId + " completed: " + task;
            publish(Map.of("event", "task_completed", "worker", workerId, "result", result),
                    TOPIC_COMPLETIONS, null).join();
            return Map.of("status", "processed", "worker", workerId);
        }
    }

    public static final class MonitorAgent extends ExampleCommunicableAgent {
        private final List<Map<String, Object>> completions = new ArrayList<>();

        public MonitorAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            if (inputs instanceof Map<?, ?> map && "task_completed".equals(map.get("event"))) {
                synchronized (completions) {
                    completions.add(Map.of(
                            "worker", String.valueOf(map.get("worker")),
                            "result", String.valueOf(map.get("result"))
                    ));
                }
            }
            return Map.of("status", "logged");
        }

        public List<Map<String, Object>> getCompletions() {
            synchronized (completions) {
                return List.copyOf(completions);
            }
        }
    }
}
