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
                .thenThrow(new IllegalStateException("connection timeout"));

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
    }

    /**
     * 模拟长耗时（100秒）非流式回退场景，验证心跳机制是否在回退期间发送 progress 事件，
     * 防止客户端因长时间无响应而超时断开。
     *
     * 场景：流式返回空 → 回退非流式 → callModel 阻塞 100 秒 → 响应到达 → 包装为流式发送
     * 验证：在 callModel 前收到了 progress 类型的 SSE 心跳事件
     */
    @Test
    void heartbeatSentBeforeLongRunningNonStreamFallback() throws Exception {
        ReActAgent agent = newAgent("long-fallback");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .streamMaxRetries(0)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator());

        // 模拟 callModel 长耗时：用 latch 阻塞 invoke，直到测试释放
        java.util.concurrent.CountDownLatch invokeStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch invokeProceed = new java.util.concurrent.CountDownLatch(1);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    invokeStarted.countDown();
                    // 阻塞等待测试放行，模拟长耗时
                    invokeProceed.await();
                    return AssistantMessage.builder().content("delayed content").build();
                });
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("long-fallback-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        // 使用线程安全集合收集结果
        java.util.List<Object> collected = java.util.Collections.synchronizedList(new ArrayList<>());

        // 后台线程消费流输出（streamOutput 会阻塞直到流结束）
        java.util.concurrent.CountDownLatch consumerReady = new java.util.concurrent.CountDownLatch(1);
        Thread consumerThread = new Thread(() -> {
            consumerReady.countDown();
            session.streamOutput(collected::add);
        }, "test-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        // 确保 consumer 已开始等待
        consumerReady.await();

        // 触发流式执行（stream 方法会启动后台线程，并返回迭代器——但我们不消费它）
        agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));

        // 等待 callModel 被调用（说明心跳已经发送完毕）
        assertThat(invokeStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("callModel should be invoked within 10s").isTrue();

        // 给消费回调一点时间处理已入队的心跳事件
        Thread.sleep(500);

        // 在 callModel 阻塞期间，验证已收到 progress 心跳事件
        long heartbeatCount = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "progress".equals(output.getType()))
                .count();
        assertThat(heartbeatCount).as("应在 callModel 前发送至少一个 progress 心跳").isGreaterThanOrEqualTo(1);

        // 验证心跳内容包含非流式切换提示
        boolean hasFallbackMessage = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "progress".equals(output.getType()))
                .anyMatch(output -> String.valueOf(output.getPayload()).contains("非流式"));
        assertThat(hasFallbackMessage).as("心跳应包含非流式切换提示").isTrue();

        // 释放 callModel 阻塞，让流完成
        invokeProceed.countDown();

        // 等待流完成（轮询检查 answer 事件是否到达）
        long deadline = System.currentTimeMillis() + 10000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            found = collected.stream()
                    .filter(item -> item instanceof OutputSchema)
                    .map(item -> (OutputSchema) item)
                    .filter(output -> "answer".equals(output.getType()))
                    .anyMatch(output -> String.valueOf(output.getPayload()).contains("delayed content"));
            if (found) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(found).as("应在 10s 内收到最终 content").isTrue();

        // 验证最终 content 也被正确发送
        long answerCount = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "answer".equals(output.getType()))
                .filter(output -> String.valueOf(output.getPayload()).contains("delayed content"))
                .count();
        assertThat(answerCount).as("最终 content 应通过 answer 发送").isEqualTo(1);
    }

    /**
     * 模拟重试期间的心跳验证：流式返回空 → 心跳 → 重试 → 心跳 → 回退非流式 → 心跳。
     * 验证每次重试等待前和回退前都发送了 progress 心跳。
     */
    @Test
    void heartbeatSentDuringRetriesAndFallback() throws Exception {
        ReActAgent agent = newAgent("retry-heartbeat");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .streamMaxRetries(2)
                .streamRetryDelayMs(50)  // 短延迟，测试快速完成
                .build());
        Model model = mock(Model.class);
        // stream 始终返回空（触发重试 + 回退）
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator());
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(AssistantMessage.builder().content("final content").build());
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("retry-heartbeat-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        List<Object> collected = new ArrayList<>();
        StepVerifier.create(agent.streamAsync(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT)))
                .thenConsumeWhile(item -> {
                    collected.add(item);
                    return true;
                })
                .verifyComplete();

        // 提取所有 progress 心跳事件
        List<OutputSchema> heartbeats = collected.stream()
                .filter(item -> item instanceof OutputSchema)
                .map(item -> (OutputSchema) item)
                .filter(output -> "progress".equals(output.getType()))
                .toList();

        // streamMaxRetries=2，所以首次 + 2 次重试 = 3 次 stream 调用
        // 重试前心跳：attempt 0→1, attempt 1→2，共 2 次重试心跳
        // 回退前心跳：1 次
        // 总计：3 次 progress 事件
        assertThat(heartbeats).as("应有 3 个心跳（2 次重试 + 1 次回退）").hasSize(3);

        // 验证心跳内容
        String firstMsg = String.valueOf(heartbeats.get(0).getPayload());
        assertThat(firstMsg).contains("重试");
        String lastMsg = String.valueOf(heartbeats.get(2).getPayload());
        assertThat(lastMsg).contains("非流式");
    }

    /**
     * 模拟长耗时（12秒）非流式回退场景，验证心跳按 HEARTBEAT_INTERVAL_MS(5s) 周期性发送。
     *
     * 场景：流式返回空 → 回退非流式 → callModel 阻塞 12 秒 → 响应到达
     * 验证：阻塞期间收到 ≥2 个心跳，且相邻心跳间隔在 [3s, 8s] 范围内（容忍调度抖动）
     */
    @Test
    void heartbeatSentPeriodicallyDuringLongCallModel() throws Exception {
        ReActAgent agent = newAgent("periodic-heartbeat");
        agent.configure(ReActAgentConfig.builder()
                .maxIterations(3)
                .streamMaxRetries(0)
                .streamRetryDelayMs(0)
                .build());
        Model model = mock(Model.class);
        when(model.stream(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyIterator());

        // 用 latch 阻塞 invoke，模拟 12 秒长耗时
        java.util.concurrent.CountDownLatch invokeStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch invokeProceed = new java.util.concurrent.CountDownLatch(1);
        when(model.invoke(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    invokeStarted.countDown();
                    invokeProceed.await();
                    return AssistantMessage.builder().content("periodic content").build();
                });
        agent.setLlm(model);

        AgentSessionApi session =
            new AgentSessionApi("periodic-heartbeat-session", null, agent.getCard(), List.of(StreamMode.OUTPUT));

        // 线程安全收集：记录每个 progress 事件的接收时间戳
        java.util.List<Long> heartbeatTimestamps = java.util.Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch consumerReady = new java.util.concurrent.CountDownLatch(1);
        Thread consumerThread = new Thread(() -> {
            consumerReady.countDown();
            session.streamOutput(item -> {
                if (item instanceof OutputSchema output && "progress".equals(output.getType())) {
                    // 记录心跳到达的相对时间戳（毫秒）
                    heartbeatTimestamps.add(System.currentTimeMillis());
                }
            });
        }, "test-consumer-periodic");
        consumerThread.setDaemon(true);
        consumerThread.start();
        consumerReady.await();

        // 触发流式执行
        agent.stream(Map.of("query", "hello"), session, List.of(StreamMode.OUTPUT));

        // 等待 callModel 被调用（说明回退已开始，心跳已启动）
        assertThat(invokeStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
                .as("callModel should be invoked within 10s").isTrue();

        // 等待 12 秒，让周期性心跳发送 2-3 次（5s 间隔）
        Thread.sleep(12000);

        // 释放 callModel 阻塞，让流完成
        invokeProceed.countDown();

        // 等待流结束
        long deadline = System.currentTimeMillis() + 10000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            found = heartbeatTimestamps.size() >= 2;
            if (found) {
                break;
            }
            Thread.sleep(100);
        }

        // 验证至少收到 2 个心跳（12 秒内，5 秒间隔应发 2-3 次）
        assertThat(heartbeatTimestamps.size())
                .as("12秒内应收到至少2个心跳，实际收到 " + heartbeatTimestamps.size() + " 个")
                .isGreaterThanOrEqualTo(2);

        // 验证相邻心跳间隔在 [3s, 8s] 范围内（5s ± 调度抖动）
        for (int i = 1; i < heartbeatTimestamps.size(); i++) {
            long intervalMs = heartbeatTimestamps.get(i) - heartbeatTimestamps.get(i - 1);
            assertThat(intervalMs)
                    .as("第 " + i + " 个心跳与上一个的间隔应在 [3000, 8000]ms 范围内，实际 " + intervalMs + "ms")
                    .isBetween(3000L, 8000L);
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
