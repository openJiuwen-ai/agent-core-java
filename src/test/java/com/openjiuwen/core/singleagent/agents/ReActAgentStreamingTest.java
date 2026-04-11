/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReActAgentStreamingTest {

    @Test
    void streamShouldEmitIncrementalLlmOutputBeforeCloseOnlyAnswer() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModel(
                chunk("你", null, null, null),
                chunk("好", null, null, null),
                chunk("", "stop", null, null)
        ));
        AgentSessionApi session = AgentSessionApi.create("phase10-stream", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "你好"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(3);
        assertThat(outputs.get(0).getType()).isEqualTo("llm_output");
        assertThat(outputs.get(1).getType()).isEqualTo("llm_output");
        assertThat(outputs.get(2).getType()).isEqualTo("answer");

        assertPayload(outputs.get(0), "你", "answer");
        assertPayload(outputs.get(1), "好", "answer");

        Map<String, Object> finalPayload = payload(outputs.get(2));
        assertThat(finalPayload.get("result_type")).isEqualTo("answer");
        assertThat(finalPayload).containsEntry("output", "");
        assertThat(finalPayload).containsEntry("status", "completed");
        assertThat(finalPayload.get("output")).isNotEqualTo("你好");
        assertThat(finalPayload.get("output")).isNotInstanceOf(Map.class);
    }

    @Test
    void streamShouldHideToolCallsAndReasoningContentFromOutwardPayload() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModel(
                chunk(
                        "A",
                        null,
                        "hidden",
                        List.of(ToolCall.builder().id("tc-1").name("secret_tool").arguments("{}").build())
                ),
                chunk("", "stop", null, null)
        ));
        AgentSessionApi session = AgentSessionApi.create("phase10-stream-hidden", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "A"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(2);
        assertThat(outputs.get(0).getType()).isEqualTo("llm_output");
        Map<String, Object> payload = payload(outputs.get(0));
        assertThat(payload).containsEntry("output", "A");
        assertThat(payload).containsEntry("result_type", "answer");
        assertThat(payload).doesNotContainKeys("tool_calls", "reasoning_content");

        Map<String, Object> finalPayload = payload(outputs.get(1));
        assertThat(finalPayload).doesNotContainKeys("tool_calls", "reasoning_content");
        assertThat(finalPayload).containsEntry("output", "");
    }

    private static void assertPayload(OutputSchema output, String expectedOutput, String expectedResultType) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        Map<String, Object> payload = payload(output);
        assertThat(payload).containsEntry("output", expectedOutput);
        assertThat(payload).containsEntry("result_type", expectedResultType);
        assertThat(payload).doesNotContainKeys("tool_calls", "reasoning_content");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(OutputSchema output) {
        return (Map<String, Object>) output.getPayload();
    }

    private static List<OutputSchema> collect(Iterator<Object> iterator) {
        List<OutputSchema> results = new ArrayList<>();
        while (iterator.hasNext()) {
            Object next = iterator.next();
            assertThat(next).isInstanceOf(OutputSchema.class);
            results.add((OutputSchema) next);
        }
        return results;
    }

    private static Model mockStreamingModel(AssistantMessageChunk... chunks) throws Exception {
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenReturn(List.of(chunks).iterator());
        return model;
    }

    private static AssistantMessageChunk chunk(
            String content,
            String finishReason,
            String reasoningContent,
            List<ToolCall> toolCalls
    ) {
        return AssistantMessageChunk.builder()
                .content(content)
                .finishReason(finishReason)
                .reasoningContent(reasoningContent)
                .toolCalls(toolCalls)
                .build();
    }

    private static final class StreamingProbeAgent extends ReActAgent {
        private final Model model;

        private StreamingProbeAgent(Model model) {
            super(AgentCard.builder()
                    .id("streaming-probe-agent")
                    .name("streaming-probe-agent")
                    .description("streaming probe agent")
                    .build());
            this.model = model;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            throw new AssertionError("stream() must not call invoke()");
        }

        @Override
        protected Model getLlm() {
            return model;
        }
    }
}
