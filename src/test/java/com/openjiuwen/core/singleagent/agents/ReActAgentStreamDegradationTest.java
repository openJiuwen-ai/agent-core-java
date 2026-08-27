/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Tests for ReActAgent streaming degradation removal and retry behavior.
 * <p>
 * Verifies that:
 * <ul>
 * <li>Stream failures retry instead of silently degrading to non-stream</li>
 * <li>Empty stream responses fall back to non-stream with content wrapped as stream chunks</li>
 * <li>Stream exceptions (network errors) return errors without falling back</li>
 * <li>Normal streaming behavior is unaffected</li>
 * </ul>
 */
class ReActAgentStreamDegradationTest {

    @Test
    void streamRetryOnEmptyStreamThenSucceeds() throws Exception {
        ReActAgent agent = newAgent("retry-empty-then-success");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .streamMaxRetries(1)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        // 第一次返回空 Iterator（触发重试），第二次返回正常 chunks
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator())
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("recovered content").build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("retry-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .thenConsumeWhile(item -> !(item instanceof OutputSchema output
                        && "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("recovered content")))
                .expectNextMatches(item -> item instanceof OutputSchema output
                        && "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("recovered content"))
                .verifyComplete();

        // 验证 model.invoke() 从未被调用（不降级）
        verify(model, never()).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void streamEmptyResponseFallsBackToNonStreamWithWrapping() throws Exception {
        ReActAgent agent = newAgent("empty-fallback");
        Model model = mock(Model.class);
        // stream 始终返回空（模拟模型返回非 SSE 格式）
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator());
        // invoke 返回有 content 的响应（作为回退）
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("non-stream content").build());
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("empty-fallback-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        List<Object> collected = new ArrayList<>();
        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .thenConsumeWhile(item -> {
                    collected.add(item);
                    return true;
                })
                .verifyComplete();

        // 验证 content 通过 answer 类型发送了一次
        long answerCount = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "answer".equals(output.getType()))
                .filter(output -> String.valueOf(output.getPayload()).contains("non-stream content"))
                .count();
        assertThat(answerCount).isEqualTo(1);

        // 验证 content 没有通过 llm_output 类型重复发送
        long llmOutputCount = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "llm_output".equals(output.getType()))
                .filter(output -> String.valueOf(output.getPayload()).contains("non-stream content"))
                .count();
        assertThat(llmOutputCount).isEqualTo(0);

        // 验证 model.invoke() 被调用了一次（作为回退）
        verify(model, times(1))
                .invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void streamAllRetriesExhaustedWithError() throws Exception {
        ReActAgent agent = newAgent("retries-exhausted");
        Model model = mock(Model.class);
        // stream 抛异常（异常时不重试，避免部分 chunk 重复发送）
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection timeout"));

        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("exhausted-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .expectNextMatches(item -> item instanceof OutputSchema output
                        && "answer".equals(output.getType())
                        && String.valueOf(output.getPayload()).contains("failed"))
                .verifyComplete();

        // 验证 model.invoke() 从未被调用
        verify(model, never()).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // 验证 stream 仅被调用一次（异常时不重试）
        verify(model, times(1))
                .stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void normalStreamNotAffected() throws Exception {
        ReActAgent agent = newAgent("normal-stream");
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder().content("Hello ").build(),
                        AssistantMessageChunk.builder().content("World").build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("normal-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        // 收集所有流式输出
        List<Object> collected = new ArrayList<>();
        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .thenConsumeWhile(item -> {
                    collected.add(item);
                    return true;
                })
                .verifyComplete();

        // 验证流式输出了 content chunks
        String allContent = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .map(output -> String.valueOf(output.getPayload()))
                .reduce("", (a, b) -> a + b);
        assertThat(allContent).contains("Hello");
        assertThat(allContent).contains("World");

        // 验证 model.invoke() 从未被调用
        verify(model, never()).invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void streamWithContentAndToolCallsSendsContentFirst() throws Exception {
        ReActAgent agent = newAgent("content-then-tools");
        Model model = mock(Model.class);
        // 返回包含 content 和 tool_calls 的单 chunk
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(
                        AssistantMessageChunk.builder()
                                .content("analysis result")
                                .toolCalls(List.of(ToolCall.builder()
                                        .name("todo_modify")
                                        .arguments("{\"status\": \"completed\"}")
                                        .build()))
                                .build()
                ).iterator());
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("content-tools-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        List<Object> collected = new ArrayList<>();
        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .thenConsumeWhile(item -> {
                    collected.add(item);
                    return true;
                })
                .verifyComplete();

        // 验证 content 在 tool_calls 之前被发送
        int contentIndex = -1;
        int toolCallIndex = -1;
        for (int i = 0; i < collected.size(); i++) {
            if (collected.get(i) instanceof OutputSchema output) {
                String payloadStr = String.valueOf(output.getPayload());
                if (payloadStr.contains("analysis result") && contentIndex == -1) {
                    contentIndex = i;
                }
                if (payloadStr.contains("tool_calls") && toolCallIndex == -1) {
                    toolCallIndex = i;
                }
            }
        }
        assertThat(contentIndex).as("content should be streamed").isGreaterThanOrEqualTo(0);
        // tool_calls 可能不存在于最终输出中（因为工具执行可能失败），但如果存在，应在 content 之后
        if (toolCallIndex >= 0) {
            assertThat(toolCallIndex).as("tool_calls should come after content").isGreaterThan(contentIndex);
        }
    }

    /**
     * 模拟非流式响应（包含 content 和 tool_call），验证 content 被正确包装为流式 SSE 发送。
     *
     * 场景：流式请求返回空 → 回退非流式 → 第一次 invoke 返回 content + todo_modify →
     * content 通过 llm_output 分块发送 → tool_call 通过 llm_output 发送 → 工具执行后继续 →
     * 第二次 invoke 返回最终答案 → 通过 answer 类型发送
     */
    @Test
    void nonStreamResponseWithContentAndToolCallWrappedAsStream() throws Exception {
        ReActAgent agent = newAgent("non-stream-content-toolcall");
        LocalFunction todoModifyTool = new LocalFunction(
                ToolCard.builder().id("todo_modify").name("todo_modify").description("stub").build(),
                inputs -> Map.of("ok", true));
        Runner.resourceMgr().addTool(todoModifyTool, null);
        agent.getAbilityManager().add(todoModifyTool.getCard());
        try {
            Model model = mock(Model.class);

            // stream 始终返回空（模拟模型不支持流式）
            when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyIterator());

            // 第一次 invoke：返回对比正文 + todo_modify 工具调用
            String comparisonContent = "乔丹 vs 詹姆斯：乔丹6冠6FMVP，詹姆斯4冠4FMVP。梅西 vs C罗：梅西8金球，C罗5金球。";
            AssistantMessage firstResponse = AssistantMessage.builder()
                    .content(comparisonContent)
                    .toolCalls(List.of(ToolCall.builder()
                            .name("todo_modify")
                            .arguments("{\"task_id\": \"5\", \"status\": \"completed\"}")
                            .build()))
                    .build();

            // 第二次 invoke：返回最终摘要（无工具调用）
            AssistantMessage secondResponse = AssistantMessage.builder()
                    .content("对比分析任务已完成。")
                    .build();

            when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(firstResponse)
                    .thenReturn(secondResponse);
            agent.setLlm(model);

            AgentSessionApi session =
                new AgentSessionApi("content-toolcall-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

            List<Object> collected = new ArrayList<>();
            StepVerifier.create(agent.streamAsync(Map.of("query", "GOAT对比"), session, List.of(StreamMode.OUTPUT)))
                    .thenConsumeWhile(item -> {
                        collected.add(item);
                        return true;
                    })
                    .verifyComplete();

            // 提取所有 OutputSchema
            List<OutputSchema> outputs = collected.stream()
                    .filter(item -> item instanceof OutputSchema)
                    .map(item -> (OutputSchema) item)
                    .toList();

            // 1. 验证对比正文通过 llm_output 类型发送（被包装为流式）
            String llmOutputContent = outputs.stream()
                    .filter(o -> "llm_output".equals(o.getType()))
                    .map(o -> String.valueOf(o.getPayload()))
                    .filter(s -> s.contains("乔丹") || s.contains("梅西"))
                    .reduce("", (a, b) -> a + b);
            assertThat(llmOutputContent).as("对比正文应通过 llm_output 流式发送").contains("乔丹");
            assertThat(llmOutputContent).as("对比正文应通过 llm_output 流式发送").contains("梅西");

            // 2. 验证 todo_modify 工具调用通过 llm_output 类型发送
            String toolCallPayload = outputs.stream()
                    .filter(o -> "llm_output".equals(o.getType()))
                    .map(o -> String.valueOf(o.getPayload()))
                    .filter(s -> s.contains("tool_calls"))
                    .reduce("", (a, b) -> a + b);
            assertThat(toolCallPayload).as("tool_call 应通过 llm_output 发送").contains("tool_calls");
            assertThat(toolCallPayload).as("tool_call 应包含 ToolCall 对象").contains("ToolCall");

            // 3. 验证 content 在 tool_call 之前发送
            int contentIdx = -1;
            int toolCallIdx = -1;
            for (int i = 0; i < outputs.size(); i++) {
                String payload = String.valueOf(outputs.get(i).getPayload());
                if (contentIdx == -1 && payload.contains("乔丹")) {
                    contentIdx = i;
                }
                if (toolCallIdx == -1 && payload.contains("tool_calls") && payload.contains("ToolCall")) {
                    toolCallIdx = i;
                }
            }
            assertThat(contentIdx).as("对比正文应被发送").isGreaterThanOrEqualTo(0);
            assertThat(toolCallIdx).as("tool_call 应被发送").isGreaterThanOrEqualTo(0);
            assertThat(toolCallIdx).as("content 必须在 tool_call 之前发送").isGreaterThan(contentIdx);

            // 4. 验证最终答案通过 answer 类型发送
            String answerPayload = outputs.stream()
                    .filter(o -> "answer".equals(o.getType()))
                    .map(o -> String.valueOf(o.getPayload()))
                    .reduce("", (a, b) -> a + b);
            assertThat(answerPayload).as("最终答案应通过 answer 发送").contains("对比分析任务已完成");

            // 5. 验证对比正文没有被 answer 类型重复发送
            boolean answerHasComparison = outputs.stream()
                    .filter(o -> "answer".equals(o.getType()))
                    .anyMatch(o -> String.valueOf(o.getPayload()).contains("乔丹"));
            assertThat(answerHasComparison).as("对比正文不应通过 answer 重复发送").isFalse();

            // 6. 验证 model.invoke 被调用两次（第一次 content+tool_call，第二次最终答案）
            verify(model, times(2))
                    .invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        } finally {
            agent.getAgentCallbackManager().clear(null);
            Runner.resourceMgr().removeTool("todo_modify", null, TagMatchStrategy.ALL, true);
        }
    }

    private static ReActAgent newAgent(String id) {
        ReActAgent agent = new ReActAgent(AgentCard.builder().id(id).name(id).description(id).build());
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .streamMaxRetries(0)
                .streamRetryDelayMs(0)
                .build());
        return agent;
    }
}
