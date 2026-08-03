/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.system_tests.harness.rail;

import com.openjiuwen.core.session.AgentSession;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.singleagent.interrupt.InterruptConstants;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestDeepAgentInterrupt.test_deepagent_stream_interrupt_resume} in
 * {@code tests/system_tests/harness/rail/test_deep_agent_interrupt.py}.
 */
class DeepAgentInterruptResumeSystemMissingTest {

    @Test
    void testDeepagentStreamInterruptResume() throws Exception {
        AgentSession session = new AgentSession("test_resume_1_" + uuid(), null, null);
        InterruptingReactAgent reactAgent = new InterruptingReactAgent();
        DeepAgent agent = agent(reactAgent);

        Map<String, Object> firstResult = agent.invoke(Map.of(
                "query", "Please write test.txt content hello world",
                "conversation_id", session.getSessionId()), session).get(10, TimeUnit.SECONDS);

        assertThat(firstResult).containsEntry("result_type", "interrupt");
        assertThat(firstResult.get("interrupt_ids")).isEqualTo(List.of("write-call-1"));
        assertThat(reactAgent.writeInvokeCount()).isZero();
        assertThat(reactAgent.calls()).hasSize(1);
        assertThat(reactAgent.calls().get(0).inputs()).containsEntry("is_follow_up", false);
        assertThat(reactAgent.calls().get(0).inputs()).containsKey("_steering_queue");

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("write-call-1", Map.of(
                "approved", true,
                "feedback", "Confirm",
                "auto_confirm", false));

        Map<String, Object> resumedResult = agent.invoke(Map.of(
                "query", interactiveInput,
                "conversation_id", session.getSessionId()), session).get(10, TimeUnit.SECONDS);

        assertThat(resumedResult).containsEntry("result_type", "answer");
        assertThat(resumedResult).containsEntry("output", "file written");
        assertThat(reactAgent.writeInvokeCount()).isEqualTo(1);
        assertThat(reactAgent.calls()).hasSize(2);
        assertThat(reactAgent.calls().get(1).inputs().get("query")).isSameAs(interactiveInput);
        assertThat(reactAgent.calls().get(1).inputs()).doesNotContainKeys("is_follow_up", "_steering_queue");
    }

    private static DeepAgent agent(InterruptingReactAgent reactAgent) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(true);
        config.setMaxIterations(5);

        DeepAgent agent = new DeepAgent(new AgentCard("deepagent_interrupt", "deepagent_interrupt", "system-test"));
        agent.configure(config);
        agent.setReactAgent(reactAgent, true);
        return agent;
    }

    private static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Invocation(Map<String, Object> inputs, AgentSessionApi session) {
    }

    private static final class InterruptingReactAgent {
        private final CopyOnWriteArrayList<Invocation> calls = new CopyOnWriteArrayList<>();
        private int writeInvokeCount;

        public CompletionStage<Map<String, Object>> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            calls.add(new Invocation(new LinkedHashMap<>(inputs), session));
            Object query = inputs.get("query");
            if (query instanceof InteractiveInput interactiveInput) {
                Object decision = interactiveInput.getUserInputs().get("write-call-1");
                assertThat(decision).isInstanceOf(Map.class);
                assertThat(((Map<?, ?>) decision).get("approved")).isEqualTo(true);
                writeInvokeCount++;
                return CompletableFuture.completedFuture(Map.of(
                        "output", "file written",
                        "result_type", "answer"));
            }
            return CompletableFuture.completedFuture(interruptResult());
        }

        private int writeInvokeCount() {
            return writeInvokeCount;
        }

        private List<Invocation> calls() {
            return calls;
        }

        private static Map<String, Object> interruptResult() {
            OutputSchema interaction = new OutputSchema(
                    InterruptConstants.INTERACTION,
                    0,
                    new InteractionOutput("write-call-1", Map.of(
                            "tool_name", "write",
                            "message", "Please approve or reject?")));
            return Map.of(
                    "result_type", "interrupt",
                    "interrupt_ids", List.of("write-call-1"),
                    "state", List.of(interaction));
        }
    }
}
