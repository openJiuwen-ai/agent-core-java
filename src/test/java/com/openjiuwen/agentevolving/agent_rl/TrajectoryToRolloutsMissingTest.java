/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code test_trajectory_to_rollouts} in
 * {@code tests/unit_tests/agent_evolving/agent_rl/test_trajectory_to_rollouts.py}.
 */
class TrajectoryToRolloutsMissingTest {

    @Test
    void convertsAssistantMessageResponse() {
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("test-model")
                .messages(List.of(new UserMessage("hi")))
                .response(new AssistantMessage("hello"))
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("e1")
                .steps(List.of(step))
                .build();

        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        assertThat(rollouts).hasSize(1);
        Rollout rollout = rollouts.get(0);
        assertThat(rollout.getOutputResponse())
                .containsEntry("role", "assistant")
                .containsEntry("content", "hello");
        assertThat(rollout.getInputPrompt()).containsKey("message");
        Object rawMessages = rollout.getInputPrompt().get("message");
        assertThat(rawMessages).isInstanceOf(List.class);
        List<?> messages = (List<?>) rawMessages;
        assertThat(messages).hasSize(1);
        Map<?, ?> message = (Map<?, ?>) messages.get(0);
        assertThat(message.get("role")).isEqualTo("user");
        assertThat(message.get("content")).isEqualTo("hi");
    }

    @Test
    void keepsDictResponse() {
        Map<String, Object> response = Map.of("role", "assistant", "content", "ok");
        LLMCallDetail detail = LLMCallDetail.builder()
                .model("m")
                .messages(List.of())
                .response(response)
                .build();
        TrajectoryStep step = TrajectoryStep.builder()
                .kind("llm")
                .detail(detail)
                .build();
        Trajectory trajectory = Trajectory.builder()
                .executionId("e2")
                .steps(List.of(step))
                .build();

        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        assertThat(rollouts.get(0).getOutputResponse()).containsExactlyInAnyOrderEntriesOf(response);
    }
}
