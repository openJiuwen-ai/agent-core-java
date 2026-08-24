/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Missing-test parity coverage for runtime trajectory registry behavior.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/agent_evolving/trajectory/test_trajectory_registry.py}.</p>
 */
class InMemoryTrajectoryRegistryTest {

    @Test
    void registryReturnsNullForEmptySession() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();

        Trajectory result = registry.getTrajectory("team-a", "missing", true);

        assertNull(result);
    }

    @Test
    void registryUsesLaterPublishOrderForSameTimestamp() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(snapshot("researcher", "session-a", "old_tool", 1L, 1000L));
        registry.publishMemberTrajectory(snapshot("researcher", "session-a", "new_tool", 1L, 1000L));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertNotNull(result);
        assertEquals(List.of("new_tool"), toolNames(result));
        assertEquals(1, result.getMeta().get("member_count"));
    }

    @Test
    void registryKeepsNewerRecordedAtWhenOlderArrivesLater() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(snapshot("writer", "session-a", "latest_tool", 1L, 2000L));
        registry.publishMemberTrajectory(snapshot("writer", "session-a", "stale_tool", 1L, 1000L));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertEquals(List.of("latest_tool"), toolNames(result));
    }

    @Test
    void registryAcceptsNewerRecordedAtSnapshot() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(snapshot("writer", "session-a", "before_restart", 1L, 1000L));
        registry.publishMemberTrajectory(snapshot("writer", "session-a", "after_restart", 1L, 2000L));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertEquals(List.of("after_restart"), toolNames(result));
    }

    @Test
    void registryMergesMembersInTimeOrder() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(snapshot("writer", "session-a", "send_message", 300L, 1000L, "teammate"));
        registry.publishMemberTrajectory(snapshot("leader", "session-a", "view_task", 100L, 1001L, "leader"));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertNotNull(result);
        assertEquals(List.of("view_task", "send_message"), toolNames(result));
        assertEquals(2, result.getMeta().get("member_count"));
    }

    @Test
    void registryClearSessionRemovesOnlyTargetSession() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(snapshot("leader", "session-a", "view_task", 1L, 1000L));
        registry.publishMemberTrajectory(snapshot("leader", "session-b", "view_task", 1L, 1000L));

        registry.clearSession("team-a", "session-a");

        assertNull(registry.getTrajectory("team-a", "session-a", true));
        assertNotNull(registry.getTrajectory("team-a", "session-b", true));
    }

    @Test
    void registryUsesSnapshotMemberMetadataForAggregation() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(new MemberTrajectorySnapshot(
                "team-a",
                "session-a",
                "leader",
                "leader",
                Trajectory.builder()
                        .executionId("random-execution-id")
                        .sessionId("session-a")
                        .source("online")
                        .steps(List.of(TrajectoryStep.builder()
                                .kind(StepKind.LLM)
                                .detail(LLMCallDetail.builder()
                                        .model("mock")
                                        .messages(List.of())
                                        .build())
                                .meta(Map.of("operator_id", "leader/llm_main"))
                                .build()))
                        .build(),
                1000L
        ));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertNotNull(result);
        assertEquals(1, result.getSteps().size());
        assertEquals("llm", result.getSteps().get(0).getKind());
        assertEquals(1, result.getMeta().get("member_count"));
    }

    @Test
    void registryKeepsLeaderLlmForRuntimeLeaderMemberId() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        String memberId = "jiuwen_team_a_team_leader";
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-a",
                memberId,
                Trajectory.builder()
                        .executionId("exec-leader")
                        .sessionId("session-a")
                        .source("online")
                        .steps(List.of(
                                TrajectoryStep.builder()
                                        .kind(StepKind.LLM)
                                        .detail(LLMCallDetail.builder()
                                                .model("mock")
                                                .messages(List.of())
                                                .build())
                                        .meta(Map.of("operator_id", memberId + "/llm_main"))
                                        .startTimeMs(1L)
                                        .build(),
                                toolStep("view_task", 2L)
                        ))
                        .meta(Map.of("member_id", memberId))
                        .build(),
                "leader",
                null,
                1000L
        ));

        Trajectory result = registry.getTrajectory("team-a", "session-a", true);

        assertNotNull(result);
        assertEquals(List.of("llm", "tool"), result.getSteps().stream().map(TrajectoryStep::getKind).toList());
    }

    @Test
    void memberTrajectorySnapshotMakeFillsRuntimeDefaults() {
        long before = InMemoryTrajectoryRegistry.nowMs();
        Trajectory trajectory = trajectory("writer", "session-a", "view_task", 1L);

        MemberTrajectorySnapshot snapshot = MemberTrajectorySnapshot.make(
                "team-a",
                "writer",
                trajectory,
                "teammate",
                null,
                null
        );
        long after = InMemoryTrajectoryRegistry.nowMs();

        assertEquals("team-a", snapshot.getTeamId());
        assertEquals("session-a", snapshot.getSessionId());
        assertEquals("writer", snapshot.getMemberId());
        assertEquals("teammate", snapshot.getMemberRole());
        assertSame(trajectory, snapshot.getTrajectory());
        assertTrue(snapshot.getRecordedAtMs() >= before && snapshot.getRecordedAtMs() <= after);
    }

    private static MemberTrajectorySnapshot snapshot(String memberId,
                                                     String sessionId,
                                                     String toolName,
                                                     long startTimeMs,
                                                     long recordedAtMs) {
        return snapshot(memberId, sessionId, toolName, startTimeMs, recordedAtMs, "leader");
    }

    private static MemberTrajectorySnapshot snapshot(String memberId,
                                                     String sessionId,
                                                     String toolName,
                                                     long startTimeMs,
                                                     long recordedAtMs,
                                                     String memberRole) {
        return MemberTrajectorySnapshot.make(
                "team-a",
                memberId,
                trajectory(memberId, sessionId, toolName, startTimeMs),
                memberRole,
                null,
                recordedAtMs
        );
    }

    private static Trajectory trajectory(String memberId, String sessionId, String toolName, long startTimeMs) {
        return Trajectory.builder()
                .executionId("exec-" + memberId + "-" + toolName)
                .sessionId(sessionId)
                .source("online")
                .steps(List.of(toolStep(toolName, startTimeMs)))
                .meta(Map.of("member_id", memberId))
                .build();
    }

    private static TrajectoryStep toolStep(String toolName, long startTimeMs) {
        return TrajectoryStep.builder()
                .kind(StepKind.TOOL)
                .operatorId(toolName)
                .detail(ToolCallDetail.builder().toolName(toolName).build())
                .startTimeMs(startTimeMs)
                .meta(Map.of())
                .build();
    }

    private static List<String> toolNames(Trajectory trajectory) {
        assertNotNull(trajectory);
        return trajectory.getSteps().stream()
                .map(TrajectoryStep::getDetail)
                .filter(ToolCallDetail.class::isInstance)
                .map(ToolCallDetail.class::cast)
                .map(ToolCallDetail::getToolName)
                .toList();
    }
}
