/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TeamRuntime P2P communication example.
 *
 * <p>Mirrors Python's {@code runtime_p2p} in {@code examples.multi_agent}.</p>
 */
public final class RuntimeP2PExample {

    public static final String PLANNER_AGENT = "planner";
    public static final String CODER_AGENT = "coder";
    public static final String REVIEWER_AGENT = "reviewer";
    public static final String PATTERN = "P2P_SEQUENTIAL";

    public static final AgentCard PLANNER_CARD = card(PLANNER_AGENT, "Task planner");
    public static final AgentCard CODER_CARD = card(CODER_AGENT, "Code implementer");
    public static final AgentCard REVIEWER_CARD = card(REVIEWER_AGENT, "Code reviewer");

    private RuntimeP2PExample() {
        // Utility class
    }

    public static TeamRuntime createRuntime() {
        TeamRuntime runtime = new TeamRuntime();
        runtime.registerAgent(PLANNER_CARD, () -> new PlannerAgent(PLANNER_CARD));
        runtime.registerAgent(CODER_CARD, () -> new CoderAgent(CODER_CARD));
        runtime.registerAgent(REVIEWER_CARD, () -> new ReviewerAgent(REVIEWER_CARD));
        return runtime;
    }

    public static Map<String, Object> runWorkflow(String task) {
        TeamRuntime runtime = createRuntime();
        runtime.start();
        try {
            Map<String, Object> plan = asMap(runtime.send(Map.of("task", task), PLANNER_AGENT, "user").join());
            Map<String, Object> code = asMap(runtime.send(plan, CODER_AGENT, PLANNER_AGENT).join());
            Map<String, Object> review = asMap(runtime.send(code, REVIEWER_AGENT, CODER_AGENT).join());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plan", plan);
            result.put("code", code);
            result.put("review", review);
            return result;
        } finally {
            runtime.stop();
        }
    }

    public static final class PlannerAgent extends ExampleCommunicableAgent {
        public PlannerAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String task = value(inputs, "task");
            return Map.of(
                    "task", task,
                    "steps", List.of("1. Analyze requirements", "2. Design solution", "3. Write code")
            );
        }
    }

    public static final class CoderAgent extends ExampleCommunicableAgent {
        public CoderAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            List<?> steps = inputs instanceof Map<?, ?> map && map.get("steps") instanceof List<?> list
                    ? list : List.of();
            return Map.of(
                    "code", "def solution():\n    # implementation code\n    pass",
                    "status", "completed",
                    "step_count", steps.size()
            );
        }
    }

    public static final class ReviewerAgent extends ExampleCommunicableAgent {
        public ReviewerAgent(AgentCard card) {
            super(card);
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            String code = value(inputs, "code");
            return Map.of(
                    "approved", true,
                    "comments", "Code structure is clear and follows conventions",
                    "code_length", code.length()
            );
        }
    }

    static AgentCard card(String id, String description) {
        return AgentCard.builder().id(id).name(id).description(description).build();
    }

    static String value(Object inputs, String key) {
        if (inputs instanceof Map<?, ?> map && map.get(key) != null) {
            return String.valueOf(map.get(key));
        }
        return inputs != null ? String.valueOf(inputs) : "";
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> asMap(Object value) {
        return new LinkedHashMap<>((Map<String, Object>) value);
    }
}
