/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.multi_agent;

import java.util.List;
import java.util.Map;

/**
 * Team hybrid stream check example.
 *
 * <p>Mirrors Python's {@code team_hybrid_stream_check} in
 * {@code examples.multi_agent}.</p>
 */
public final class TeamHybridStreamCheckExample {

    private TeamHybridStreamCheckExample() {
        // Utility class
    }

    public static List<Map<String, Object>> runStreamCheck() {
        TeamHybridExample.TaskExecutionTeam team = TeamHybridExample.createTeam();
        List<Map<String, Object>> payloads = team.stream(Map.of("task", "build new feature module"))
                .map(TeamHybridStreamCheckExample::payload)
                .toList();

        if (payloads.size() <= 1) {
            throw new AssertionError("expected multiple stream chunks, got " + payloads.size());
        }
        requireEvent(payloads, "team_started", null);
        requireEvent(payloads, "orchestrator_received", "orchestrator");
        requireEvent(payloads, "executor_started", "executor1");
        requireEvent(payloads, "aggregator_progress", "aggregator");
        requireEvent(payloads, "reporter_completed", "reporter");

        Map<String, Object> finalPayload = payloads.stream()
                .filter(payload -> "team_completed".equals(payload.get("event")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing team_completed event"));
        Object result = finalPayload.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            throw new AssertionError("unexpected final result payload: " + result);
        }
        Object orchestration = resultMap.get("orchestration");
        Object report = resultMap.get("report");
        if (!(orchestration instanceof Map<?, ?> orchestrationMap)
                || !"broadcast_done".equals(orchestrationMap.get("status"))) {
            throw new AssertionError("unexpected orchestration result: " + orchestration);
        }
        if (!(report instanceof Map<?, ?> reportMap)
                || !"report_generated".equals(reportMap.get("status"))
                || !Integer.valueOf(TeamHybridExample.TaskExecutionTeam.expectedResultCount())
                        .equals(reportMap.get("total"))) {
            throw new AssertionError("unexpected report result: " + report);
        }
        return payloads;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> payload(Object chunk) {
        if (!(chunk instanceof Map<?, ?> map)) {
            throw new AssertionError("unexpected chunk payload: " + chunk);
        }
        return (Map<String, Object>) map;
    }

    public static boolean hasEvent(List<Map<String, Object>> payloads, String event, String sourceAgentId) {
        return payloads.stream().anyMatch(payload ->
                event.equals(payload.get("event"))
                        && (sourceAgentId == null || sourceAgentId.equals(payload.get("source_agent_id"))));
    }

    private static void requireEvent(List<Map<String, Object>> payloads, String event, String sourceAgentId) {
        if (!hasEvent(payloads, event, sourceAgentId)) {
            throw new AssertionError("missing " + event + " event");
        }
    }
}
