/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's tests.unit_tests.agent_evolving.trajectory.test_trajectory_builder_member_id.
 * Unit tests for TrajectoryBuilder with member_id support.
 */
class TrajectoryBuilderMemberIdTest {

    @Test
    void buildWithMemberId() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("test-session")
                .source("online")
                .memberId("agent-001")
                .build();

        builder.recordStep(TrajectoryStep.builder()
                .kind("tool")
                .inputs(ToolCallDetail.builder()
                        .toolName("read_file")
                        .callArgs("test.txt")
                        .build())
                .meta(new java.util.HashMap<>())
                .build());

        Trajectory traj = builder.buildTrajectory();

        assertEquals("agent-001", traj.getMeta().get("member_id"));
    }

    @Test
    void buildWithoutMemberId() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("test-session")
                .source("online")
                .build();

        Trajectory traj = builder.buildTrajectory();

        assertTrue(traj.getMeta().isEmpty());
    }

    @Test
    void memberIdStoredOnBuilder() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("test-session")
                .source("online")
                .memberId("leader-1")
                .build();

        assertEquals("leader-1", builder.getMemberId());
    }

    @Test
    void memberIdNoneDefault() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("test-session")
                .source("online")
                .build();

        assertNull(builder.getMemberId());
    }
}