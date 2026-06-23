/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.application.workflow_agent;

import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.single_agent.legacy.config.WorkflowAgentConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code TestWorkflowAgentInterruptStream} in
 * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_stream.py}.
 */
class WorkflowAgentInterruptStreamMissingTest {

    @Test
    void testStreamInterruptAndResume() {
        WorkflowAgent agent = agent("interrupt_stream_agent");
        String conversationId = "test_interrupt_stream";

        List<OutputSchema> first = drain(agent.stream(Map.of(
                "conversation_id", conversationId,
                "query", "check weather"
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> interactions = chunksOfType(first, "__interaction__");
        assertThat(interactions).isNotEmpty();

        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "shanghai");
        List<OutputSchema> second = drain(agent.stream(Map.of(
                "conversation_id", conversationId,
                "query", interactiveInput
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> finalChunks = chunksOfType(second, "workflow_final");
        assertThat(finalChunks).hasSize(1);
        Map<String, Object> payload = assertMap(finalChunks.getFirst().getPayload());
        assertThat(payload).containsEntry("response", "shanghai");
    }

    @Test
    void testStreamDictInterruptAndResume() {
        WorkflowAgent agent = agent("interrupt_dict_stream_agent");
        String conversationId = "test_interrupt_dict_stream";

        List<OutputSchema> first = drain(agent.stream(Map.of(
                "query", "check weather",
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> interactionChunks = chunksOfType(first, "__interaction__");
        assertThat(interactionChunks).isNotEmpty();
        assertThat(interactionChunks.getFirst().getType()).isEqualTo("__interaction__");

        InteractiveInput interactiveInput = new InteractiveInput();
        for (OutputSchema chunk : interactionChunks) {
            InteractionOutput payload = (InteractionOutput) chunk.getPayload();
            interactiveInput.update(payload.getId(), Map.of("location", "shanghai"));
        }

        List<OutputSchema> second = drain(agent.stream(Map.of(
                "query", interactiveInput,
                "conversation_id", conversationId
        ), null, List.of(StreamMode.OUTPUT)));

        List<OutputSchema> finalChunks = chunksOfType(second, "workflow_final");
        assertThat(finalChunks).hasSize(1);
        Map<String, Object> payload = assertMap(finalChunks.getFirst().getPayload());
        assertThat(payload).containsKey("response");
        assertThat(payload.get("response")).isInstanceOf(Map.class);
    }

    private static WorkflowAgent agent(String agentId) {
        WorkflowAgentConfig config = new WorkflowAgentConfig();
        config.setId(agentId);
        config.setVersion("1.0");
        config.setDescription("interrupt stream test agent");
        config.setWorkflows(List.of());
        WorkflowAgent agent = new WorkflowAgent(config);
        agent.setController(new DeterministicStreamController());
        return agent;
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

    private static List<OutputSchema> chunksOfType(List<OutputSchema> chunks, String type) {
        return chunks.stream()
                .filter(chunk -> type.equals(chunk.getType()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assertMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /**
     * Mirrors Python's {@code MockWorkflowAgent} stream-mode interrupt fixture in
     * {@code tests/unit_tests/agent/workflow_agent/test_mock_workflow_agent_interrupt_stream.py}.
     */
    public static final class DeterministicStreamController {
        private final Map<String, Integer> phases = new LinkedHashMap<>();

        public Iterator<Object> stream(Map<String, Object> inputs,
                                       AgentSessionApi session,
                                       List<StreamMode> streamModes) {
            String conversationId = String.valueOf(inputs.getOrDefault("conversation_id", session.getSessionId()));
            Object query = inputs.get("query");
            int phase = phases.getOrDefault(conversationId, 0);
            if (!(query instanceof InteractiveInput interactiveInput) && phase == 0) {
                phases.put(conversationId, 1);
                return List.<Object>of(interaction()).iterator();
            }
            phases.put(conversationId, phase + 1);
            Object response = query instanceof InteractiveInput interactiveInput
                    ? interactiveInput.getUserInputs().get("questioner")
                    : query;
            return List.<Object>of(workflowFinal(response)).iterator();
        }

        private static OutputSchema interaction() {
            return new OutputSchema(
                    "__interaction__",
                    0,
                    new InteractionOutput("questioner", Map.of("prompt", "What is your location?"))
            );
        }

        private static OutputSchema workflowFinal(Object response) {
            return new OutputSchema("workflow_final", 0, Map.of("response", response));
        }
    }
}
