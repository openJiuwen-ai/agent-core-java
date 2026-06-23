/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentMultiInterrupt} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_multi_interrupt.py}.
 */
class WorkflowAgentMultiInterruptMissingTest {

    @Test
    void testMultiInterruptSequentialResume() {
        DeterministicMultiInterruptController controller = new DeterministicMultiInterruptController();
        WorkflowAgent agent = agentWith(controller);
        RecordingSession session = new RecordingSession("test_multi_interrupt_seq");

        List<Object> firstChunks = drain(agent.stream(query("check weather", session.getSessionId()), session));
        assertThat(firstChunks).hasSize(1);
        OutputSchema first = output(firstChunks.getFirst());
        assertThat(first.getType()).isEqualTo(Constant.INTERACTION);
        String firstId = interaction(first).getId();

        InteractiveInput firstResume = new InteractiveInput();
        String expectedSecond;
        if ("interactive".equals(firstId)) {
            firstResume.update("interactive", "confirmed");
            expectedSecond = "questioner";
        } else {
            firstResume.update("questioner", "shanghai");
            expectedSecond = "interactive";
        }

        List<Object> secondChunks = drain(agent.stream(query(firstResume, session.getSessionId()), session));
        assertThat(secondChunks).hasSize(1);
        OutputSchema second = output(secondChunks.getFirst());
        assertThat(second.getType()).isEqualTo(Constant.INTERACTION);
        assertThat(interaction(second).getId()).isEqualTo(expectedSecond);

        InteractiveInput secondResume = new InteractiveInput();
        if ("interactive".equals(expectedSecond)) {
            secondResume.update("interactive", "confirmed");
        } else {
            secondResume.update("questioner", "shanghai");
        }

        List<Object> finalChunks = drain(agent.stream(query(secondResume, session.getSessionId()), session));
        assertThat(finalChunks).hasSize(1);
        OutputSchema finalChunk = output(finalChunks.getFirst());
        assertThat(finalChunk.getType()).isEqualTo("workflow_final");
        assertThat(map(finalChunk.getPayload())).containsKey("response");
        assertThat(controller.roles(session.getSessionId()))
                .containsExactly("user", "assistant", "user", "assistant", "user", "assistant");
    }

    @Test
    void testMultiInterruptResumeAllAtOnce() {
        DeterministicMultiInterruptController controller = new DeterministicMultiInterruptController();
        WorkflowAgent agent = agentWith(controller);
        RecordingSession session = new RecordingSession("test_multi_interrupt_all");

        List<Object> firstResult = list(agent.invoke(query("check weather", session.getSessionId()), session)
                .toCompletableFuture()
                .join());
        assertThat(firstResult).hasSize(1);
        assertThat(output(firstResult.getFirst()).getType()).isEqualTo(Constant.INTERACTION);

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("interactive", "confirmed");
        interactiveInput.update("questioner", "shanghai");
        Map<String, Object> secondResult = map(agent.invoke(query(interactiveInput, session.getSessionId()), session)
                .toCompletableFuture()
                .join());

        assertThat(secondResult).containsEntry("result_type", "answer");
        WorkflowOutput output = workflowOutput(secondResult.get("output"));
        assertThat(output.getState()).isEqualTo(WorkflowExecutionState.COMPLETED);
        assertThat(map(output.getResult())).containsKey("response");
        assertThat(controller.roles(session.getSessionId()))
                .containsExactly("user", "assistant", "user", "assistant");
    }

    private static WorkflowAgent agentWith(DeterministicMultiInterruptController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId("workflow-agent-multi-interrupt-missing-test");
        config.setVersion("1.0");
        config.setDescription("workflow agent multi interrupt missing-test parity");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(controller);
        return agent;
    }

    private static Map<String, Object> query(Object query, String conversationId) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("query", query);
        inputs.put("conversation_id", conversationId);
        return inputs;
    }

    private static List<Object> drain(Iterator<Object> iterator) {
        List<Object> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static OutputSchema output(Object value) {
        assertThat(value).isInstanceOf(OutputSchema.class);
        return (OutputSchema) value;
    }

    private static WorkflowOutput workflowOutput(Object value) {
        assertThat(value).isInstanceOf(WorkflowOutput.class);
        return (WorkflowOutput) value;
    }

    private static InteractionOutput interaction(OutputSchema value) {
        assertThat(value.getPayload()).isInstanceOf(InteractionOutput.class);
        return (InteractionOutput) value.getPayload();
    }

    public static final class DeterministicMultiInterruptController {
        private final ConcurrentMap<String, List<String>> chatRoles = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Integer> streamPhase = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, Integer> invokePhase = new ConcurrentHashMap<>();

        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            String sessionId = session.getSessionId();
            Object query = inputs.get("query");
            if (query instanceof InteractiveInput interactiveInput) {
                appendExchange(sessionId);
                invokePhase.remove(sessionId);
                return CompletableFuture.completedFuture(answer(interactiveInput));
            }
            appendExchange(sessionId);
            invokePhase.put(sessionId, 1);
            return CompletableFuture.completedFuture(List.of(interaction("questioner", "What is your location?")));
        }

        public Iterator<Object> stream(Map<String, Object> inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            String sessionId = session.getSessionId();
            Object query = inputs.get("query");
            int phase = streamPhase.getOrDefault(sessionId, 0);
            if (!(query instanceof InteractiveInput)) {
                appendExchange(sessionId);
                streamPhase.put(sessionId, 1);
                return List.of((Object) interaction("questioner", "What is your location?")).iterator();
            }
            appendExchange(sessionId);
            if (phase == 1) {
                streamPhase.put(sessionId, 2);
                return List.of((Object) interaction("interactive", "Please confirm the operation")).iterator();
            }
            streamPhase.remove(sessionId);
            return List.of((Object) workflowFinal(answerPayload((InteractiveInput) query))).iterator();
        }

        List<String> roles(String sessionId) {
            return List.copyOf(chatRoles.getOrDefault(sessionId, List.of()));
        }

        private Map<String, Object> answer(InteractiveInput input) {
            return Map.of(
                    "result_type", "answer",
                    "output", new WorkflowOutput(answerPayload(input), WorkflowExecutionState.COMPLETED)
            );
        }

        private static Map<String, Object> answerPayload(InteractiveInput input) {
            Object questioner = input.getUserInputs().getOrDefault("questioner", "shanghai");
            Object interactive = input.getUserInputs().getOrDefault("interactive", "confirmed");
            return Map.of("response", questioner + " | confirm=" + interactive);
        }

        private static OutputSchema interaction(String nodeId, String prompt) {
            return new OutputSchema(Constant.INTERACTION, 0, new InteractionOutput(nodeId, Map.of("prompt", prompt)));
        }

        private static OutputSchema workflowFinal(Map<String, Object> payload) {
            return new OutputSchema("workflow_final", 0, payload);
        }

        private void appendExchange(String sessionId) {
            appendRole(sessionId, "user");
            appendRole(sessionId, "assistant");
        }

        private void appendRole(String sessionId, String role) {
            chatRoles.compute(sessionId, (ignored, roles) -> {
                List<String> updated = roles == null ? new ArrayList<>() : new ArrayList<>(roles);
                updated.add(role);
                return updated;
            });
        }
    }

    private static final class RecordingSession implements AgentSessionApi {
        private final String sessionId;
        private final Map<String, Object> state = new LinkedHashMap<>();
        private final List<Object> stream = new ArrayList<>();

        private RecordingSession(String sessionId) {
            this.sessionId = sessionId;
        }

        @Override
        public String getSessionId() {
            return sessionId;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            if (data != null) {
                state.putAll(data);
            }
        }

        @Override
        public void writeStream(Object data) {
            stream.add(data);
        }

        @Override
        public Iterator<Object> streamIterator() {
            return stream.iterator();
        }
    }
}
