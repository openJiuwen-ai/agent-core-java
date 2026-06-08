/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.trajectory;

import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TrajectoryBuilder} member-id support.
 * <p>
 * Mirrors Python's
 * {@code tests/unit_tests/agent_evolving/trajectory/test_trajectory_builder_member_id.py}.
 * </p>
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
                .detail(ToolCallDetail.builder()
                        .toolName("read_file")
                        .callArgs("test.txt")
                        .build())
                .meta(new HashMap<>())
                .build());

        Trajectory trajectory = builder.build();

        assertEquals("agent-001", trajectory.getMeta().get("member_id"));
    }

    @Test
    void buildWithoutMemberId() {
        TrajectoryBuilder builder = TrajectoryBuilder.builder()
                .sessionId("test-session")
                .source("online")
                .build();

        Trajectory trajectory = builder.build();

        assertTrue(trajectory.getMeta().isEmpty());
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
