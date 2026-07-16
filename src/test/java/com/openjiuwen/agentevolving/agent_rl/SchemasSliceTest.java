
package com.openjiuwen.agentevolving.agent_rl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agentevolving.trajectory.LLMCallDetail;
import com.openjiuwen.agentevolving.trajectory.StepKind;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.agentevolving.trajectory.TrajectoryStep;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class SchemasSliceTest {
    @Test
    void rolloutMessageDefaultsUseIndependentMutableLists() {
        RolloutMessage first = new RolloutMessage();
        RolloutMessage second = new RolloutMessage();

        assertNotSame(first.getRollout_info(), second.getRollout_info());
        assertNotSame(first.getReward_list(), second.getReward_list());
        assertTrue(first.getRollout_info().isEmpty());
        assertTrue(first.getReward_list().isEmpty());

        first.getRollout_info().add(new Rollout(0, Map.of("message", List.of()), null, null, null, null));
        first.getReward_list().add(1.0);

        assertTrue(second.getRollout_info().isEmpty());
        assertTrue(second.getReward_list().isEmpty());
    }

    @Test
    void rlTaskDefaultsMatchPythonBaseline() {
        RLTask task = new RLTask("t1", "o1");

        assertEquals("t1", task.getTask_id());
        assertEquals("o1", task.getOrigin_task_id());
        assertEquals(0, task.getRound_num());
        assertTrue(task.getTask_sample().isEmpty());
    }

    @Test
    void rlTaskRejectsMissingRequiredIds() {
        assertThrows(NullPointerException.class, () -> new RLTask(null, "o1"));
        assertThrows(NullPointerException.class, () -> new RLTask("t1", null));
    }

    @Test
    void rolloutWithRewardRequiresTokenIdLists() {
        RolloutWithReward reward = new RolloutWithReward(List.of(1, 2), List.of(3, 4));
        assertEquals(List.of(1, 2), reward.getInput_prompt_ids());
        assertEquals(List.of(3, 4), reward.getOutput_response_ids());

        assertThrows(NullPointerException.class, () -> new RolloutWithReward(null, List.of(1)));
        assertThrows(NullPointerException.class, () -> new RolloutWithReward(List.of(1), null));
    }

    @Test
    void rolloutRetainsPythonShapedPayloads() {
        Map<String, Object> inputPrompt = new LinkedHashMap<>();
        inputPrompt.put("message", new ArrayList<>(List.of(Map.of("role", "user", "content", "hi"))));
        inputPrompt.put("tools", new ArrayList<>(List.of(Map.of("name", "lookup"))));

        Map<String, Object> outputResponse = new LinkedHashMap<>();
        outputResponse.put("role", "assistant");
        outputResponse.put("content", "hello");

        Rollout rollout =
            new Rollout(0, inputPrompt, outputResponse, Map.of("temperature", 0.7), List.of(10, 11), List.of(12));

        assertEquals("assistant", rollout.getOutput_response().get("role"));
        assertEquals("hello", rollout.getOutput_response().get("content"));
        assertEquals(List.of(10, 11), rollout.getInput_prompt_ids());
        assertEquals(List.of(12), rollout.getOutput_response_ids());
    }

    @Test
    void trajectoryToRolloutsConvertsAssistantMessageResponse() {
        Trajectory trajectory = Trajectory.builder().executionId("e1")
                .steps(List.of(TrajectoryStep.builder().kind(StepKind.LLM).detail(new LLMCallDetail("test-model",
                        List.<Object>of(new UserMessage("hi")), new AssistantMessage("hello"), null, null, null))
                        .build()))
                .build();

        List<Rollout> rollouts = AgentRlSchemas.trajectoryToRollouts(trajectory);

        assertEquals(1, rollouts.size());
        assertEquals("assistant", rollouts.get(0).getOutput_response().get("role"));
        assertEquals("hello", rollouts.get(0).getOutput_response().get("content"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
            (List<Map<String, Object>>) rollouts.get(0).getInput_prompt().get("message");
        assertEquals("user", messages.get(0).get("role"));
        assertEquals("hi", messages.get(0).get("content"));
    }

    @Test
    void trajectoryToRolloutsKeepsDictResponseAndPassesTokenIdsAndConfig() {
        Trajectory trajectory = Trajectory.builder().executionId("e2")
                .steps(List.of(
                        TrajectoryStep.builder().kind("llm")
                                .detail(new LLMCallDetail("m", List.of(Map.of("role", "user", "content", "hi")),
                                        Map.of("role", "assistant", "content", "ok"), List.of(Map.of("name", "lookup")),
                                        null, null))
                                .promptTokenIds(List.of(10, 11)).completionTokenIds(List.of(12))
                                .meta(Map.of("llm_config", Map.of("temperature", 0.7))).build(),
                        TrajectoryStep.builder().kind(StepKind.TOOL).build(),
                        TrajectoryStep.builder().kind(StepKind.LLM).detail(null).build()))
                .build();

        List<Rollout> rollouts = AgentRlSchemas.trajectoryToRollouts(trajectory);

        assertEquals(1, rollouts.size());
        assertEquals(Map.of("role", "assistant", "content", "ok"), rollouts.get(0).getOutput_response());
        assertEquals(List.of(10, 11), rollouts.get(0).getInput_prompt_ids());
        assertEquals(List.of(12), rollouts.get(0).getOutput_response_ids());
        assertEquals(Map.of("temperature", 0.7), rollouts.get(0).getLlm_config());
        assertEquals(List.of(Map.of("name", "lookup")), rollouts.get(0).getInput_prompt().get("tools"));
    }

    @Test
    void trajectoryToRolloutsWrapsStringResponsesAndNormalizesEmptyTokenListsToNull() {
        Trajectory trajectory =
            Trajectory.builder().executionId("e3")
                    .steps(List.of(TrajectoryStep.builder().kind(StepKind.LLM)
                            .detail(new LLMCallDetail("m", List.<Object>of(new UserMessage("prompt")), "plain text",
                                    null, null, null))
                            .promptTokenIds(List.of()).completionTokenIds(List.of()).build()))
                    .build();

        List<Rollout> rollouts = AgentRlSchemas.trajectoryToRollouts(trajectory);

        assertEquals(1, rollouts.size());
        assertEquals(Map.of("role", "assistant", "content", "plain text"), rollouts.get(0).getOutput_response());
        assertNull(rollouts.get(0).getInput_prompt_ids());
        assertNull(rollouts.get(0).getOutput_response_ids());
    }
}
