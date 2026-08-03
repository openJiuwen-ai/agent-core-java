/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.application.workflow_agent.WorkflowAgent;
import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.config.WorkflowAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestReActAgentInterrupt} and {@code TestWorkflowAgentInterrupt} in
 * {@code tests/unit_tests/agent/llm_agent/test_llm_agent_with_interrupt.py}.
 */
class LLMAgentWithInterruptMissingTest {

    @Test
    void testRealReactAgentInvokeWithWorkflowInterrupt() {
        InterruptingLlmAgent agent = new InterruptingLlmAgent(llmConfig("react_agent_123", unique("questioner_workflow")));

        Map<String, Object> first = map(agent.invoke(Map.of(
                        "conversation_id", "12345",
                        "query", "query today's weather"
                ), null)
                .toCompletableFuture()
                .join());
        assertThat(first).containsEntry("result_type", "question");
        assertThat(first).containsEntry("component_id", "questioner");

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "hangzhou");
        Map<String, Object> second = map(agent.invoke(Map.of(
                        "conversation_id", "12345",
                        "query", input
                ), null)
                .toCompletableFuture()
                .join());
        assertThat(second).containsEntry("result_type", "answer");
        assertThat(second.get("output")).asString().contains("hangzhou").contains("today");
    }

    @Test
    void testRealWorkflowAgentInvokeWithWorkflowInterrupt() {
        DeterministicWorkflowController controller = new DeterministicWorkflowController();
        WorkflowAgent agent = workflowAgent(controller);
        RecordingSession session = new RecordingSession("12345");

        Object first = agent.invoke(Map.of("conversation_id", "12345", "query", "query today's weather"), session)
                .toCompletableFuture()
                .join();
        OutputSchema interaction = firstInteraction(first);
        assertThat(interaction.getType()).isEqualTo(Constant.INTERACTION);
        InteractionOutput payload = (InteractionOutput) interaction.getPayload();
        assertThat(payload.getId()).isEqualTo("questioner");

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "hangzhou");
        Map<String, Object> second = map(agent.invoke(Map.of("conversation_id", "12345", "query", input), session)
                .toCompletableFuture()
                .join());
        assertThat(second).containsEntry("result_type", "answer");
        assertThat(map(second.get("output"))).containsEntry("location", "hangzhou")
                .containsEntry("time", "today");
    }

    @Test
    void testRealWorkflowAgentStreamWithWorkflowInterrupt() {
        DeterministicWorkflowController controller = new DeterministicWorkflowController();
        WorkflowAgent agent = workflowAgent(controller);
        RecordingSession session = new RecordingSession("12345-stream");

        List<Object> firstChunks = drain(agent.stream(
                Map.of("conversation_id", "12345", "query", "query today's weather"),
                session
        ));
        OutputSchema interaction = onlyChunk(firstChunks);
        assertThat(interaction.getType()).isEqualTo(Constant.INTERACTION);
        InteractionOutput payload = (InteractionOutput) interaction.getPayload();
        assertThat(payload.getId()).isEqualTo("questioner");

        InteractiveInput input = new InteractiveInput();
        input.update("questioner", "hangzhou");
        List<Object> secondChunks = drain(agent.stream(Map.of("conversation_id", "12345", "query", input), session));
        OutputSchema finalChunk = onlyChunk(secondChunks);
        assertThat(finalChunk.getType()).isEqualTo("workflow_final");
        assertThat(map(finalChunk.getPayload())).containsEntry("location", "hangzhou")
                .containsEntry("time", "today");
    }

    private static LegacyReActAgentConfig llmConfig(String agentId, String workflowId) {
        WorkflowSchema workflowSchema = WorkflowSchema.builder()
                .id(workflowId)
                .name("questioner")
                .version("1.0")
                .description("questioner workflow")
                .inputs(Map.of("query", Map.of("type", "string")))
                .build();
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                "0.0.1",
                "AI assistant",
                List.of(workflowSchema),
                List.of(),
                new ModelConfig(),
                List.of(Map.of("role", "system", "content", "test"))
        );
    }

    private static WorkflowAgent workflowAgent(DeterministicWorkflowController controller) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId("write_agent");
        config.setVersion("0.1.0");
        config.setDescription("interrupt workflow single_agent");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(controller);
        return agent;
    }

    private static String unique(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static OutputSchema firstInteraction(Object result) {
        List<Object> items = list(result);
        assertThat(items).isNotEmpty();
        assertThat(items.get(0)).isInstanceOf(OutputSchema.class);
        return (OutputSchema) items.get(0);
    }

    private static OutputSchema onlyChunk(List<Object> chunks) {
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isInstanceOf(OutputSchema.class);
        return (OutputSchema) chunks.get(0);
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

    /**
     * Mirrors Python's patched ReAct LLM-agent interrupt scenario in
     * {@code tests/unit_tests/agent/llm_agent/test_llm_agent_with_interrupt.py}.
     */
    private static final class InterruptingLlmAgent extends LLMAgent {
        private final Map<String, Integer> phases = new LinkedHashMap<>();

        private InterruptingLlmAgent(LegacyReActAgentConfig agentConfig) {
            super(agentConfig);
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", "default_session"));
            Object query = inputs.get("query");
            if (!(query instanceof InteractiveInput input)) {
                phases.put(conversationId, 1);
                return Map.of(
                        "result_type", "question",
                        "component_id", "questioner",
                        "prompt", "Which city weather should be queried?"
                );
            }
            phases.put(conversationId, phases.getOrDefault(conversationId, 1) + 1);
            Object location = input.getUserInputs().getOrDefault("questioner", "");
            return Map.of(
                    "result_type", "answer",
                    "output", location + " | today"
            );
        }
    }

    /**
     * Mirrors Python's mocked WorkflowAgent interrupt and stream behavior in
     * {@code tests/unit_tests/agent/llm_agent/test_llm_agent_with_interrupt.py}.
     */
    public static final class DeterministicWorkflowController {
        public CompletionStage<Object> invoke(Map<String, Object> inputs, AgentSessionApi session) {
            Object query = inputs.get("query");
            if (query instanceof InteractiveInput input) {
                return CompletableFuture.completedFuture(answer(input));
            }
            return CompletableFuture.completedFuture(List.of(interaction("query weather city")));
        }

        public Iterator<Object> stream(Map<String, Object> inputs,
                                       AgentSessionApi session,
                                       List<StreamMode> streamModes) {
            Object query = inputs.get("query");
            if (query instanceof InteractiveInput input) {
                return List.of((Object) workflowFinal(answerPayload(input))).iterator();
            }
            return List.of((Object) interaction("query weather city")).iterator();
        }

        private static Map<String, Object> answer(InteractiveInput input) {
            return Map.of(
                    "result_type", "answer",
                    "output", answerPayload(input)
            );
        }

        private static Map<String, Object> answerPayload(InteractiveInput input) {
            return Map.of(
                    "location", input.getUserInputs().getOrDefault("questioner", ""),
                    "time", "today"
            );
        }

        private static OutputSchema interaction(String prompt) {
            return new OutputSchema(Constant.INTERACTION, 0,
                    new InteractionOutput("questioner", Map.of("prompt", prompt)));
        }

        private static OutputSchema workflowFinal(Map<String, Object> payload) {
            return new OutputSchema("workflow_final", 0, payload);
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
            if (data == null) {
                return;
            }
            state.putAll(data);
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
