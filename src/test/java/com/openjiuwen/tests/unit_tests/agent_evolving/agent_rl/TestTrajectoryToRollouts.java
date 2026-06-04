/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.tests.unit_tests.agent_evolving.agent_rl;

import com.openjiuwen.agent_evolving.agent_rl.RlSchemas;
import com.openjiuwen.agent_evolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agent_evolving.trajectory.LLMCallDetail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.agent_evolving.trajectory.TrajectoryStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TrajectoryToRollouts.
 * <p>
 * Mirrors Python's {@code test_trajectory_to_rollouts.py} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/}.
 */
@DisplayName("TrajectoryToRollouts Tests")
class TestTrajectoryToRollouts {

    @Test
    @DisplayName("converts assistant message response")
    void testTrajectoryToRolloutsConvertsAssistantMessageResponse() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("llm")
                        .detail(LLMCallDetail.builder()
                                .model("test-model")
                                .messages(List.of(Map.of("role", "user", "content", "hi")))
                                .response(Map.of("role", "assistant", "content", "hello"))
                                .build())
                        .build()))
                .build();

        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        assertThat(rollouts).hasSize(1);
        assertThat(rollouts.getFirst().getOutputResponse()).containsEntry("role", "assistant")
                .containsEntry("content", "hello");
        assertThat(rollouts.getFirst().getInputPrompt().get("message")).isInstanceOf(List.class);
        assertThat(((List<?>) rollouts.getFirst().getInputPrompt().get("message")).getFirst())
                .isEqualTo(Map.of("role", "user", "content", "hi"));
    }

    @Test
    @DisplayName("keeps dict response")
    void testTrajectoryToRolloutsKeepsDictResponse() {
        Trajectory trajectory = Trajectory.builder()
                .executionId("e2")
                .steps(List.of(TrajectoryStep.builder()
                        .kind("llm")
                        .detail(LLMCallDetail.builder()
                                .model("m")
                                .messages(List.of())
                                .response(Map.of("role", "assistant", "content", "ok"))
                                .build())
                        .build()))
                .build();

        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        assertThat(rollouts.getFirst().getOutputResponse()).isEqualTo(Map.of("role", "assistant", "content", "ok"));
    }
}
