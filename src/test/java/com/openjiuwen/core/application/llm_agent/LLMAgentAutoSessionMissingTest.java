/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.llm_agent;

import com.openjiuwen.core.foundation.llm.schema.ModelConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.config.LegacyReActAgentConfig;
import com.openjiuwen.core.singleagent.legacy.schema.WorkflowSchema;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestLLMAgentRefactorInvokeAutoSession} and
 * {@code TestLLMAgentRefactorStreamAutoSession} in
 * {@code tests/unit_tests/agent/llm_agent/test_mock_llm_agent_auto_session.py}.
 */
class LLMAgentAutoSessionMissingTest {

    @Test
    void testInvokeAutoSessionWorkflowInterruptAndResume() {
        AutoSessionAgent agent = new AutoSessionAgent(config("agent_invoke_auto", "wf_auto"));
        String conversationId = "conv_invoke_auto";

        Object first = agent.invoke(Map.of(
                        "query", "collect info",
                        "conversation_id", conversationId
                ), null)
                .toCompletableFuture()
                .join();

        List<?> interactionResult = assertList(first);
        assertThat(interactionResult).isNotEmpty();
        OutputSchema interaction = assertOutput(interactionResult.getFirst());
        assertThat(interaction.getType()).isEqualTo("__interaction__");
        assertThat(((InteractionOutput) interaction.getPayload()).getId()).isEqualTo("questioner");
        assertThat(agent.seenSessionIds).contains(conversationId);

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Shanghai");
        Object second = agent.invoke(Map.of(
                        "query", userInput,
                        "conversation_id", conversationId
                ), null)
                .toCompletableFuture()
                .join();

        Map<String, Object> answer = assertMap(second);
        assertThat(answer).containsEntry("result_type", "answer");
        assertThat(answer.get("output")).asString().contains("Shanghai");
    }

    @Test
    void testStreamAutoSessionWorkflowInterruptAndResume() {
        AutoSessionAgent agent = new AutoSessionAgent(config("agent_stream_auto", "wf_stream"));
        String conversationId = "conv_stream_auto";

        List<OutputSchema> first = drain(agent.stream(Map.of(
                "query", "weather query",
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));
        List<OutputSchema> firstInteractions = interactions(first);

        assertThat(firstInteractions).hasSize(1);
        assertThat(((InteractionOutput) firstInteractions.getFirst().getPayload()).getId()).isEqualTo("questioner");

        InteractiveInput userInput = new InteractiveInput();
        userInput.update("questioner", "Beijing");
        List<OutputSchema> second = drain(agent.stream(Map.of(
                "query", userInput,
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));

        assertThat(interactions(second)).isEmpty();
        assertThat(second).isNotEmpty();
        assertThat(String.valueOf(second.getLast().getPayload())).contains("Beijing");
        assertThat(agent.seenSessionIds).contains(conversationId);
    }

    private static LegacyReActAgentConfig config(String agentId, String workflowId) {
        WorkflowSchema workflow = WorkflowSchema.builder()
                .id(workflowId)
                .name(workflowId)
                .version("1.0")
                .description("auto-session workflow")
                .inputs(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string")),
                        "required", List.of("query")
                ))
                .build();
        return LLMAgentFactory.createLlmAgentConfig(
                agentId,
                "1.0",
                "test",
                List.of(workflow),
                List.of(),
                new ModelConfig(),
                List.of(Map.of("role", "system", "content", "You are a test assistant."))
        );
    }

    private static List<OutputSchema> drain(Iterator<Object> iterator) {
        List<OutputSchema> chunks = new ArrayList<>();
        while (iterator.hasNext()) {
            Object item = iterator.next();
            if (item instanceof OutputSchema outputSchema) {
                chunks.add(outputSchema);
            }
        }
        return chunks;
    }

    private static List<OutputSchema> interactions(List<OutputSchema> chunks) {
        return chunks.stream()
                .filter(chunk -> "__interaction__".equals(chunk.getType()))
                .toList();
    }

    private static List<?> assertList(Object value) {
        assertThat(value).isInstanceOf(List.class);
        return (List<?>) value;
    }

    private static OutputSchema assertOutput(Object value) {
        assertThat(value).isInstanceOf(OutputSchema.class);
        return (OutputSchema) value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assertMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's mocked LLM/workflow auto-session fixture in
     * {@code tests/unit_tests/agent/llm_agent/test_mock_llm_agent_auto_session.py}.
     */
    private static final class AutoSessionAgent extends LLMAgent {
        private final List<String> seenSessionIds = new ArrayList<>();

        private AutoSessionAgent(LegacyReActAgentConfig agentConfig) {
            super(agentConfig);
        }

        @Override
        protected Object invokeController(Map<String, Object> inputs, AgentSessionApi session) {
            seenSessionIds.add(session.getSessionId());
            Object query = inputs.get("query");
            if (!(query instanceof InteractiveInput interactiveInput)) {
                OutputSchema interaction = new OutputSchema(
                        "__interaction__",
                        0,
                        new InteractionOutput("questioner", Map.of("prompt", "What is your location?"))
                );
                session.writeStream(interaction);
                return List.of(interaction);
            }

            Object answer = interactiveInput.getUserInputs().getOrDefault("questioner", "");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("result_type", "answer");
            payload.put("output", "Collected info: " + answer + ". Task complete.");
            session.writeStream(new OutputSchema("answer", 0, payload));
            return payload;
        }
    }
}
