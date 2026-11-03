/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agentevolving.agent_rl.schemas.Rollout;
import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RlSchemasTest {

    @Test
    void trajectoryToRolloutsExtractsOnlyLlmSteps() {
        LLMCallDetail detail = new LLMCallDetail();
        detail.setMessages(List.of(
                Map.of("role", "user", "content", "hello"),
                new DumpableMessage(Map.of("role", "system", "content", "ctx"))
        ));
        detail.setTools(List.of(Map.of("name", "tool-1")));
        detail.setResponse(new DumpableMessage("assistant-reply"));

        TrajectoryStep llmStep = new TrajectoryStep();
        llmStep.setKind("llm");
        llmStep.setDetail(detail);
        llmStep.setMeta(Map.of("llm_config", Map.of("model", "gpt")));
        llmStep.setPromptTokenIds(List.of(1, 2));
        llmStep.setCompletionTokenIds(List.of(3, 4));

        TrajectoryStep toolStep = new TrajectoryStep();
        toolStep.setKind("tool");

        Trajectory trajectory = new Trajectory();
        trajectory.setSteps(List.of(toolStep, llmStep));

        List<Rollout> rollouts = RlSchemas.trajectoryToRollouts(trajectory);

        assertThat(rollouts).hasSize(1);
        Rollout rollout = rollouts.get(0);
        assertThat(rollout.getTurnId()).isZero();
        assertThat(rollout.getInputPrompt()).containsKey("message").containsKey("tools");
        assertThat((List<?>) rollout.getInputPrompt().get("message")).hasSize(2);
        assertThat(rollout.getOutputResponse()).containsEntry("role", "assistant")
                .containsEntry("content", "assistant-reply");
        assertThat(rollout.getLlmConfig()).containsEntry("model", "gpt");
        assertThat(rollout.getInputPromptIds()).containsExactly(1, 2);
        assertThat(rollout.getOutputResponseIds()).containsExactly(3, 4);
    }

    private static final class DumpableMessage {
        private final Object value;

        private DumpableMessage(Object value) {
            this.value = value;
        }

        public Object modelDump() {
            return value;
        }
    }
}
