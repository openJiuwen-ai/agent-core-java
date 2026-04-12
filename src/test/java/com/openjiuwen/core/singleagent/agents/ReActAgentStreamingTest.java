/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.AbilityManager;
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

        String incrementalContent = outputs.stream()
                .filter(output -> "llm_output".equals(output.getType()))
                .map(ReActAgentStreamingTest::payload)
                .map(payload -> String.valueOf(payload.get("content")))
                .reduce("", String::concat);
        Map<String, Object> finalPayload = payload(outputs.get(2));
        assertThat(finalPayload.get("result_type")).isEqualTo("answer");
        assertThat(finalPayload).containsEntry("output", "你好");
        assertThat(finalPayload.get("output")).isEqualTo(incrementalContent);
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
                        null
                ),
                chunk("", "stop", null, null)
        ));
        AgentSessionApi session = AgentSessionApi.create("phase10-stream-hidden", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "A"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(3);
        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_reasoning", "llm_output", "answer");
        assertThat(payload(outputs.get(0)))
                .containsEntry("content", "hidden")
                .containsEntry("result_type", "answer")
                .doesNotContainKeys("output", "tool_calls", "reasoning_content");
        Map<String, Object> payload = payload(outputs.get(1));
        assertThat(payload).containsEntry("content", "A");
        assertThat(payload).containsEntry("result_type", "answer");
        assertThat(payload).doesNotContainKeys("tool_calls", "reasoning_content");

        Map<String, Object> finalPayload = payload(outputs.get(2));
        assertThat(finalPayload).doesNotContainKeys("tool_calls", "reasoning_content");
        assertThat(finalPayload).containsEntry("output", "A");
    }

    @Test
    void streamShouldEmitReasoningBeforeVisibleOutputWhenChunkContainsBoth() throws Exception {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModel(
                chunk("答案", null, "推理", null),
                chunk("", "stop", null, null)
        ));
        AgentSessionApi session = AgentSessionApi.create("phase17-stream-reasoning-first", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "为什么"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).hasSize(3);
        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_reasoning", "llm_output", "answer");
        assertThat(payload(outputs.get(0)))
                .containsEntry("content", "推理")
                .containsEntry("result_type", "answer")
                .doesNotContainKeys("output", "tool_calls", "reasoning_content");
        assertThat(payload(outputs.get(1)))
                .containsEntry("content", "答案")
                .containsEntry("result_type", "answer")
                .doesNotContainKeys("output", "tool_calls", "reasoning_content");
        assertThat(payload(outputs.get(2))).containsEntry("output", "答案");
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
        assertThat(outputs).hasSize(4);
        assertThat(outputs).extracting(OutputSchema::getType)
                .containsExactly("llm_reasoning", "llm_output", "llm_output", "answer");
        assertThat(payload(outputs.get(0)))
                .containsEntry("content", "internal-thinking")
                .containsEntry("result_type", "answer")
                .doesNotContainKeys("output", "tool_calls", "reasoning_content");
        assertPayload(outputs.get(1), "查", "answer");
        assertPayload(outputs.get(2), "到", "answer");

        Map<String, Object> finalPayload = payload(outputs.get(3));
        assertThat(finalPayload).containsEntry("output", "查到");
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
        assertThat(outputs).hasSize(5);
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

    @Test
    void streamShouldEndWithSingleFailureTerminalWhenToolFactIsError() throws Exception {
        ToolCall toolCall = ToolCall.builder().id("tc-failure").name("failing_tool").arguments("{}").build();
        StreamingProbeAgent agent = new StreamingProbeAgent(
                mockStreamingModelSequence(List.of(
                        List.of(chunk("查", null, null, List.of(toolCall)), chunk("", "stop", null, null))
                )),
                new StubAbilityManager(List.of(new AbilityManager.ToolExecutionEntry(
                        toolCall,
                        null,
                        ToolMessage.builder().content("tool failed").toolCallId("tc-failure").build(),
                        AbilityManager.ToolExecutionClassification.ERROR,
                        "tool failed"
                )))
        );
        AgentSessionApi session = AgentSessionApi.create("phase16-stream-tool-failure", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "失败"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).extracting(OutputSchema::getType).containsExactly("llm_output", "final");
        assertThat(payload(outputs.get(1)))
                .containsEntry("status", "failed")
                .containsEntry("message", "tool failed");
    }

    @Test
    void streamShouldEndWithSingleInterruptPendingTerminalWhenToolFactRequestsResume() throws Exception {
        ToolCall toolCall = ToolCall.builder().id("tc-interrupt").name("interrupt_tool").arguments("{}").build();
        StreamingProbeAgent agent = new StreamingProbeAgent(
                mockStreamingModelSequence(List.of(
                        List.of(chunk("等", null, null, List.of(toolCall)), chunk("", "stop", null, null))
                )),
                new StubAbilityManager(List.of(new AbilityManager.ToolExecutionEntry(
                        toolCall,
                        null,
                        ToolMessage.builder().content("waiting").toolCallId("tc-interrupt").build(),
                        AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE,
                        "waiting"
                )))
        );
        AgentSessionApi session = AgentSessionApi.create("phase16-stream-tool-interrupt", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "中断"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).extracting(OutputSchema::getType).containsExactly("llm_output", "final");
        assertThat(payload(outputs.get(1)))
                .containsEntry("status", "interrupt_pending")
                .containsEntry("result_type", "interrupt_pending")
                .containsEntry("message", "waiting");
    }

    @Test
    void streamInterruptPendingTerminalShouldExposeRecoverableResumePayload() throws Exception {
        ToolCall toolCall = ToolCall.builder().id("tc-interrupt-contract").name("interrupt_tool").arguments("{}").build();
        StreamingProbeAgent agent = new StreamingProbeAgent(
                mockStreamingModelSequence(List.of(
                        List.of(chunk("等", null, null, List.of(toolCall)), chunk("", "stop", null, null))
                )),
                new StubAbilityManager(List.of(new AbilityManager.ToolExecutionEntry(
                        toolCall,
                        null,
                        ToolMessage.builder().content("waiting").toolCallId("tc-interrupt-contract").build(),
                        AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE,
                        "waiting"
                )))
        );
        AgentSessionApi session = AgentSessionApi.create("phase18-stream-interrupt-contract", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(Map.of("query", "中断"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).extracting(OutputSchema::getType).containsExactly("llm_output", "final");
        assertThat(payload(outputs.get(1)))
                .containsOnlyKeys("message", "result_type", "status", "resume_supported", "conversation_id", "interaction")
                .containsEntry("message", "waiting")
                .containsEntry("result_type", "interrupt_pending")
                .containsEntry("status", "interrupt_pending")
                .containsEntry("resume_supported", true)
                .containsEntry("conversation_id", "phase18-stream-interrupt-contract");
        @SuppressWarnings("unchecked")
        Map<String, Object> interaction = (Map<String, Object>) payload(outputs.get(1)).get("interaction");
        assertThat(interaction)
                .containsEntry("id", "tc-interrupt-contract")
                .containsEntry("component_ids", List.of("tc-interrupt-contract"));
    }

    @Test
    void streamShouldResumeInterruptedToolCallOnSameConversationWithoutReplayingInterruptTerminal() throws Exception {
        ToolCall toolCall = ToolCall.builder().id("tc-resume").name("interrupt_tool").arguments("{}").build();
        AtomicInteger modelCalls = new AtomicInteger();
        StreamingProbeAgent agent = new StreamingProbeAgent(
                mockStreamingModelSequence(List.of(
                        List.of(chunk("等", null, null, List.of(toolCall)), chunk("", "stop", null, null)),
                        List.of(chunk("已", null, null, null), chunk("完成", null, null, null), chunk("", "stop", null, null))
                ), modelCalls),
                new StubAbilityManager(List.of(
                        new AbilityManager.ToolExecutionEntry(
                                toolCall,
                                null,
                                ToolMessage.builder().content("waiting").toolCallId("tc-resume").build(),
                                AbilityManager.ToolExecutionClassification.INTERRUPT_PENDING_CANDIDATE,
                                "waiting"
                        ),
                        new AbilityManager.ToolExecutionEntry(
                                toolCall,
                                "approved-result",
                                ToolMessage.builder().content("approved-result").toolCallId("tc-resume").build(),
                                AbilityManager.ToolExecutionClassification.SUCCESS,
                                null
                        )
                ))
        );
        AgentSessionApi session = AgentSessionApi.create("phase18-stream-resume", null, agent.getCard());

        List<OutputSchema> firstOutputs = collect(agent.stream(Map.of("query", "先中断"), session, List.of(StreamMode.OUTPUT)));
        List<OutputSchema> resumedOutputs = collect(agent.stream(new com.openjiuwen.core.session.interaction.InteractiveInput("approved"), session, List.of(StreamMode.OUTPUT)));

        assertThat(modelCalls.get()).isEqualTo(2);
        assertThat(firstOutputs).extracting(OutputSchema::getType).containsExactly("llm_output", "final");
        assertThat(payload(firstOutputs.get(1))).containsEntry("status", "interrupt_pending");
        assertThat(resumedOutputs).extracting(OutputSchema::getType).containsExactly("llm_output", "llm_output", "answer");
        assertThat(payload(resumedOutputs.get(2)))
                .containsEntry("output", "已完成")
                .containsEntry("result_type", "answer")
                .containsEntry("status", "completed");
        assertThat(resumedOutputs).noneMatch(output -> "final".equals(output.getType()));
    }

    @Test
    void streamShouldFailResumeWhenInterruptStateIsMissing() {
        StreamingProbeAgent agent = new StreamingProbeAgent(mockStreamingModelSequence(List.of()));
        AgentSessionApi session = AgentSessionApi.create("phase18-stream-resume-missing", null, agent.getCard());

        List<OutputSchema> outputs = collect(agent.stream(new com.openjiuwen.core.session.interaction.InteractiveInput("approved"), session, List.of(StreamMode.OUTPUT)));

        assertThat(outputs).singleElement().satisfies(output -> {
            assertThat(output.getType()).isEqualTo("final");
            assertThat(payload(output))
                    .containsEntry("status", "failed")
                    .containsEntry("message", "missing interrupt state for resume");
        });
    }

    private static void assertPayload(OutputSchema output, String expectedOutput, String expectedResultType) {
        assertThat(output.getPayload()).isInstanceOf(Map.class);
        Map<String, Object> payload = payload(output);
        assertThat(payload).containsEntry("content", expectedOutput);
        assertThat(payload).containsEntry("result_type", expectedResultType);
        assertThat(payload).doesNotContainKeys("output", "tool_calls", "reasoning_content");
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

    private static Model mockStreamingModelSequence(List<List<AssistantMessageChunk>> responses, AtomicInteger callCounter)
            throws Exception {
        Model model = mock(Model.class);
        AtomicInteger responseIndex = new AtomicInteger();
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), isNull(), any(), any()))
                .thenAnswer(invocation -> {
                    callCounter.incrementAndGet();
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
        private final AbilityManager abilityManager;

        private StreamingProbeAgent(Model model) {
            this(model, null);
        }

        private StreamingProbeAgent(Model model, AbilityManager abilityManager) {
            super(AgentCard.builder()
                    .id("streaming-probe-agent")
                    .name("streaming-probe-agent")
                    .description("streaming probe agent")
                    .build());
            this.model = model;
            this.abilityManager = abilityManager;
        }

        @Override
        public Object invoke(Object inputs, Session session) {
            throw new AssertionError("stream() must not call invoke()");
        }

        @Override
        protected Model getLlm() {
            return model;
        }

        @Override
        public AbilityManager getAbilityManager() {
            return abilityManager != null ? abilityManager : super.getAbilityManager();
        }
    }

    private static final class StubAbilityManager extends AbilityManager {
        private final List<ToolExecutionEntry> results;

        private StubAbilityManager(List<ToolExecutionEntry> results) {
            this.results = results;
        }

        @Override
        public List<ToolExecutionEntry> execute(
                com.openjiuwen.core.singleagent.rail.AgentCallbackContext ctx,
                Object toolCall,
                Session session,
                String tag
        ) {
            return results;
        }
    }
}
