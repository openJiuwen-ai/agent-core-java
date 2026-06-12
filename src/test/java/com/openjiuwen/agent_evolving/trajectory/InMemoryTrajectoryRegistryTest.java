/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mirrors Python's {@code openjiuwen/agent_evolving/trajectory/registry.py}.
 */
class InMemoryTrajectoryRegistryTest {

    @Test
    void laterRecordedSnapshotReplacesOlderOne() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-1",
                "member-1",
                trajectory("session-1", "first-step", 1L),
                "leader",
                null,
                100L
        ));
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-1",
                "member-1",
                trajectory("session-1", "second-step", 2L),
                "leader",
                null,
                200L
        ));

        Trajectory combined = registry.getTrajectory("team-1", "session-1", true);

        assertNotNull(combined);
        assertEquals(1, combined.getSteps().size());
        assertEquals("second-step", combined.getSteps().get(0).getOperatorId());
        assertEquals(1, combined.getMeta().get("member_count"));
    }

    @Test
    void sameTimestampFallsBackToSequenceOrder() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-1",
                "member-1",
                trajectory("session-1", "first-step", 1L),
                "leader",
                null,
                100L
        ));
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-1",
                "member-1",
                trajectory("session-1", "second-step", 2L),
                "leader",
                null,
                100L
        ));

        Trajectory combined = registry.getTrajectory("team-1", "session-1", false);

        assertNotNull(combined);
        assertEquals("second-step", combined.getSteps().get(0).getOperatorId());
        assertEquals(1, combined.getMeta().get("member_count"));
    }

    @Test
    void clearSessionRemovesPublishedSnapshots() {
        InMemoryTrajectoryRegistry registry = new InMemoryTrajectoryRegistry();
        registry.publishMemberTrajectory(MemberTrajectorySnapshot.make(
                "team-1",
                "member-1",
                trajectory("session-1", "first-step", 1L),
                null,
                null,
                100L
        ));

        registry.clearSession("team-1", "session-1");

        assertNull(registry.getTrajectory("team-1", "session-1", true));
    }

    private static Trajectory trajectory(String sessionId, String operatorId, long startTimeMs) {
        return Trajectory.builder()
                .executionId("exec-" + operatorId)
                .sessionId(sessionId)
                .source("online")
                .steps(List.of(TrajectoryStep.builder()
                        .kind(StepKind.AGENT)
                        .operatorId(operatorId)
                        .startTimeMs(startTimeMs)
                        .meta(Map.of())
                        .build()))
                .meta(Map.of())
                .build();
    }
}
