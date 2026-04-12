/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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
        assertThat(finalPayload).containsEntry("output", "你好");
        assertThat(finalPayload).containsEntry("status", "completed");
        assertThat(finalPayload.get("output")).isNotInstanceOf(Map.class);
    }

    @Test
    void streamShouldStayOnStreamingEntryPathInsteadOfWrappingInvoke() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModel(
                chunk("直", null, null, null),
                chunk("播", null, null, null),
                chunk("", "stop", null, null)
        ));
        AgentSessionApi session = AgentSessionApi.create("phase15-stream-entry-path", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "直播"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_output", "llm_output", "answer");
        assertPayload(outputs.get(0), "直", "answer");
        assertPayload(outputs.get(1), "播", "answer");
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

    @Test
    void streamShouldPauseForToolRoundThenResumeAnswerOutput() throws Exception {
        AtomicInteger toolExecutions = new AtomicInteger();
        String toolName = uniqueToolName("lookup_tool");
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModelSequence(
                List.of(
                        List.of(
                                chunk(
                                        "查",
                                        null,
                                        "internal-thinking",
                                        List.of(ToolCall.builder().id("tc-1").name(toolName).arguments("{}" ).build())
                                ),
                                chunk("", "stop", null, null)
                        ),
                        List.of(
                                chunk("到", null, null, null),
                                chunk("", "stop", null, null)
                        )
                )
        ));
        registerTool(agent, toolName, toolExecutions);
        AgentSessionApi session = AgentSessionApi.create("phase11-stream-tool-round", null, agent.getCard());

        List<OutputSchema> outputs;
        try {
            outputs = collect(agent.stream(Map.of("query", "查到什么了"), session, List.of(StreamMode.OUTPUT)));
        } finally {
            unregisterTool(toolName);
        }

        assertThat(toolExecutions).hasValue(1);
        assertThat(outputs).hasSize(3);
        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_output", "llm_output", "answer");
        assertPayload(outputs.get(0), "查", "answer");
        assertPayload(outputs.get(1), "到", "answer");

        Map<String, Object> finalPayload = payload(outputs.get(2));
        assertThat(finalPayload).containsEntry("output", "到");
        assertThat(finalPayload).containsEntry("result_type", "answer");
        assertThat(finalPayload).containsEntry("status", "completed");
        assertThat(finalPayload).doesNotContainKeys("tool_calls", "reasoning_content");
    }

    @Test
    void streamToolRoundShouldNeverExposeToolCallsOrReasoningContent() throws Exception {
        AtomicInteger toolExecutions = new AtomicInteger();
        String toolName = uniqueToolName("hidden_lookup_tool");
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModelSequence(
                List.of(
                        List.of(
                                chunk(
                                        "查",
                                        null,
                                        "first-round-hidden",
                                        List.of(ToolCall.builder().id("tc-hidden").name(toolName).arguments("{}" ).build())
                                ),
                                chunk("", "stop", null, null)
                        ),
                        List.of(
                                chunk("到", null, "second-round-hidden", null),
                                chunk("", "stop", null, null)
                        )
                )
        ));
        registerTool(agent, toolName, toolExecutions);
        AgentSessionApi session = AgentSessionApi.create("phase11-stream-tool-hidden", null, agent.getCard());

        List<OutputSchema> outputs;
        try {
            outputs = collect(agent.stream(Map.of("query", "查到什么了"), session, List.of(StreamMode.OUTPUT)));
        } finally {
            unregisterTool(toolName);
        }

        assertThat(toolExecutions).hasValue(1);
        assertThat(outputs).hasSize(3);
        for (OutputSchema output : outputs) {
            assertThat(payload(output)).doesNotContainKeys("tool_calls", "reasoning_content");
        }
    }

    @Test
    void streamShouldEmitSingleFinalFrameWhenModelStreamThrows() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingFailure(new RuntimeException("stream boom")));
        AgentSessionApi session = AgentSessionApi.create("phase11-stream-runtime-failure", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "boom"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(1);
        OutputSchema terminal = outputs.get(0);
        assertThat(terminal.getType()).isEqualTo("final");
        Map<String, Object> payload = payload(terminal);
        assertThat(payload).containsEntry("error", true);
        assertThat(payload).containsEntry("status", "failed");
        assertThat(payload).containsEntry("message", "stream boom");
        assertThat(String.valueOf(payload.get("message"))).isNotBlank();
        assertThat(payload).doesNotContainKeys("tool_calls", "reasoning_content");
    }

    @Test
    void streamShouldEmitSingleFinalFrameWhenMaxIterationsReached() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModel(
                chunk(
                        "查",
                        null,
                        null,
                        List.of(ToolCall.builder().id("tc-max").name(uniqueToolName("never-run-tool")).arguments("{}").build())
                ),
                chunk("", "stop", null, null)
        ));
        agent.configure(ReActAgentConfig.builder().maxIterations(1).build());
        AgentSessionApi session = AgentSessionApi.create("phase11-stream-max-iterations", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "查到哪了"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(2);
        assertThat(outputs.get(0).getType()).isEqualTo("llm_output");
        assertPayload(outputs.get(0), "查", "answer");

        OutputSchema terminal = outputs.get(1);
        assertThat(terminal.getType()).isEqualTo("final");
        Map<String, Object> payload = payload(terminal);
        assertThat(payload).containsEntry("error", true);
        assertThat(payload).containsEntry("status", "failed");
        assertThat(String.valueOf(payload.get("message"))).isNotBlank();
        assertThat(payload).doesNotContainKeys("tool_calls", "reasoning_content");
        assertThat(outputs).filteredOn(output -> "final".equals(output.getType())).hasSize(1);
        assertThat(outputs).noneMatch(output -> "error".equals(output.getType()));
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
        return assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            List<OutputSchema> results = new ArrayList<>();
            while (iterator.hasNext()) {
                Object next = iterator.next();
                assertThat(next).isInstanceOf(OutputSchema.class);
                results.add((OutputSchema) next);
            }
            return results;
        });
    }

    private static Model mockStreamingModel(AssistantMessageChunk... chunks) throws Exception {
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenReturn(List.of(chunks).iterator());
        return model;
    }

    private static Model mockStreamingModelSequence(List<List<AssistantMessageChunk>> responses) throws Exception {
        Model model = mock(Model.class);
        AtomicInteger responseIndex = new AtomicInteger();
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenAnswer(invocation -> {
                    int index = responseIndex.getAndIncrement();
                    if (index >= responses.size()) {
                        return List.<AssistantMessageChunk>of().iterator();
                    }
                    return responses.get(index).iterator();
                });
        return model;
    }

    private static Model mockStreamingFailure(RuntimeException failure) throws Exception {
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenThrow(failure);
        return model;
    }

    private static void registerTool(StreamingProbeAgent agent, String toolName, AtomicInteger toolExecutions) {
        ToolCard card = ToolCard.builder()
                .id(toolName)
                .name(toolName)
                .description("固定返回测试结果的工具")
                .inputParams(Map.of("type", "object", "properties", Map.of()))
                .build();
        LocalFunction tool = new LocalFunction(card, inputs -> {
            toolExecutions.incrementAndGet();
            return "lookup-result";
        });
        agent.getAbilityManager().add(card);
        Runner.resourceMgr().addTool(tool, null);
    }

    private static void unregisterTool(String toolName) {
        Runner.resourceMgr().removeTool(toolName, null, TagMatchStrategy.ALL, true);
    }

    private static String uniqueToolName(String prefix) {
        return prefix + "-" + UUID.randomUUID();
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
