// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.SubscriptionBase;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.ResultType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MqServerAdapter.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/server_adapter/test_mq_server_adapter.py
 */
@ExtendWith(MockitoExtension.class)
class MqServerAdapterTest {

    @Mock
    MessageQueueBase mockMq;

    @Mock
    SubscriptionBase mockSubscription;

    /**
     * 创建一个可测试的MqServerAdapter（绕过构造函数中的Runner.getDistPubsub()）
     */
    private MqServerAdapter createAdapter(
            Function<Map<String, Object>, Object> invokeHandler,
            Function<Map<String, Object>, Iterator<Object>> streamHandler) {
        // 使用反射或直接通过包可见字段设置
        when(mockMq.subscribe(anyString())).thenReturn(mockSubscription);

        MqServerAdapter adapter = new MqServerAdapter(
                "test-adapter", "test.topic", invokeHandler, streamHandler);
        adapter.mq = mockMq;
        return adapter;
    }

    // ==========================================
    // TestMqServerAdapterStart
    // ==========================================

    @Nested
    @DisplayName("start() 测试")
    class StartTests {

        @Test
        @DisplayName("start() 订阅 topic")
        void testStartSubscribesTopic() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            verify(mockMq).subscribe("test.topic");
        }

        @Test
        @DisplayName("start() 设置消息处理器")
        void testStartSetsMessageHandler() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            verify(mockSubscription).setMessageHandler(any());
        }

        @Test
        @DisplayName("start() 激活订阅")
        void testStartActivatesSubscription() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            verify(mockSubscription).activate();
        }

        @Test
        @DisplayName("start() 标记 active=True")
        void testStartSetsActiveTrue() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            assertFalse(adapter.isActive());
            adapter.start();
            assertTrue(adapter.isActive());
        }

        @Test
        @DisplayName("start() 幂等")
        void testStartIsIdempotent() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            adapter.start(); // second call
            verify(mockMq, times(1)).subscribe(anyString());
        }
    }

    // ==========================================
    // TestMqServerAdapterStop
    // ==========================================

    @Nested
    @DisplayName("stop() 测试")
    class StopTests {

        @Test
        @DisplayName("stop() 停用订阅")
        void testStopDeactivatesSubscription() {
            when(mockSubscription.deactivate()).thenReturn(CompletableFuture.completedFuture(null));
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            adapter.stop();
            verify(mockSubscription).deactivate();
        }

        @Test
        @DisplayName("stop() 标记 active=False")
        void testStopSetsActiveFalse() {
            when(mockSubscription.deactivate()).thenReturn(CompletableFuture.completedFuture(null));
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();
            assertTrue(adapter.isActive());
            adapter.stop();
            assertFalse(adapter.isActive());
        }

        @Test
        @DisplayName("stop() 取消所有运行中的任务")
        void testStopCancelsRunningTasks() throws Exception {
            when(mockSubscription.deactivate()).thenReturn(CompletableFuture.completedFuture(null));
            when(mockMq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();

            // Create a fake running task
            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("test-123");
            msg.setReplyTopic("reply.topic");
            msg.setPayload(Map.of());

            CompletableFuture<Void> longRunning = new CompletableFuture<>();
            adapter.runningTasks.put("test-123",
                    new MessageTask(msg, longRunning));

            adapter.stop();

            assertEquals(0, adapter.getRunningTasks().size());
        }

        @Test
        @DisplayName("stop() 幂等（未激活时直接返回）")
        void testStopIsIdempotent() {
            // Create adapter directly without mocking subscribe (not needed for stop-without-start)
            MqServerAdapter adapter = new MqServerAdapter(
                    "test-adapter", "test.topic", inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.mq = mockMq;
            // stop without start - should not throw
            assertDoesNotThrow(adapter::stop);
        }
    }

    // ==========================================
    // TestMqServerAdapterProcessMessage
    // ==========================================

    @Nested
    @DisplayName("_process_message() 测试")
    class ProcessMessageTests {

        @Test
        @DisplayName("非流式请求调用 invoke_handler 并发送 batch_response")
        void testProcessMessageInvokeSendsBatchResponse() {
            when(mockMq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            MqServerAdapter adapter = createAdapter(
                    inputs -> Map.of("result", inputs.getOrDefault("query", "default")),
                    inputs -> Collections.emptyIterator());
            adapter.start();

            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("invoke-123");
            msg.setReplyTopic("reply.topic");
            msg.setSenderId("client-1");
            msg.setPayload(Map.of("query", "hello"));
            msg.setEnableStream(false);

            adapter.processMessage(msg);

            // Verify response was sent
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(mockMq).produceMessage(eq("reply.topic"), captor.capture());
            DmqResponseMessage response = (DmqResponseMessage) captor.getValue();

            assertEquals("invoke-123", response.getMessageId());
            assertTrue(response.isLastChunk());
            assertEquals(Map.of("result", "hello"), response.getPayload());
        }

        @Test
        @DisplayName("流式请求调用 stream_handler 并逐个发送响应")
        void testProcessMessageStreamSendsChunks() {
            when(mockMq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            MqServerAdapter adapter = createAdapter(
                    inputs -> Map.of(),
                    inputs -> {
                        List<Object> chunks = new ArrayList<>();
                        for (int i = 0; i < 3; i++) {
                            chunks.add(Map.of("chunk", i, "data", inputs.getOrDefault("prompt", "default")));
                        }
                        return chunks.iterator();
                    });
            adapter.start();

            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("stream-123");
            msg.setReplyTopic("reply.topic");
            msg.setSenderId("client-1");
            msg.setPayload(Map.of("prompt", "generate"));
            msg.setEnableStream(true);

            adapter.processMessage(msg);

            // Should have sent 4 messages: 3 chunks + 1 final
            verify(mockMq, times(4)).produceMessage(eq("reply.topic"), any());

            // Check the last one is final
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(mockMq, times(4)).produceMessage(anyString(), captor.capture());
            List<Object> allResponses = captor.getAllValues();
            DmqResponseMessage finalResp = (DmqResponseMessage) allResponses.get(3);
            assertTrue(finalResp.isLastChunk());
        }
    }

    // ==========================================
    // TestMqServerAdapterTimeoutCancel
    // ==========================================

    @Nested
    @DisplayName("_timeout_cancel() 测试")
    class TimeoutCancelTests {

        @Test
        @DisplayName("任务存在且未完成时取消任务")
        void testTimeoutCancelCancelsTask() throws Exception {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();

            CompletableFuture<Void> longRunning = new CompletableFuture<>();
            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("timeout-123");
            msg.setReplyTopic("reply.topic");
            msg.setPayload(Map.of());

            adapter.runningTasks.put("timeout-123",
                    new MessageTask(msg, longRunning));

            adapter.timeoutCancel("timeout-123");

            assertTrue(longRunning.isCancelled());
        }

        @Test
        @DisplayName("任务不存在时不做任何操作")
        void testTimeoutCancelIgnoresNonexistent() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();

            // Should not throw
            assertDoesNotThrow(() -> adapter.timeoutCancel("nonexistent-123"));
        }
    }

    // ==========================================
    // TestMqServerAdapterCancelTask
    // ==========================================

    @Nested
    @DisplayName("_cancel_task() 测试")
    class CancelTaskTests {

        @Test
        @DisplayName("inner_cancel=True 时发送取消错误响应")
        void testCancelTaskSendsErrorWhenInnerCancel() {
            when(mockMq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();

            CompletableFuture<Void> longRunning = new CompletableFuture<>();
            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("cancel-123");
            msg.setReplyTopic("reply.topic");
            msg.setPayload(Map.of());

            adapter.runningTasks.put("cancel-123",
                    new MessageTask(msg, longRunning));

            adapter.cancelTask("cancel-123", true);

            // Verify error response was sent
            ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
            verify(mockMq).produceMessage(eq("reply.topic"), captor.capture());
            DmqResponseMessage response = (DmqResponseMessage) captor.getValue();
            assertEquals(ResultType.ERROR, response.getResultType());
            assertEquals(StatusCode.RUNNER_STOPPED.getCode(), response.getErrorCode());
        }

        @Test
        @DisplayName("inner_cancel=False 时不发送响应")
        void testCancelTaskNoResponseWhenNotInnerCancel() {
            MqServerAdapter adapter = createAdapter(inputs -> Map.of(), inputs -> Collections.emptyIterator());
            adapter.start();

            CompletableFuture<Void> longRunning = new CompletableFuture<>();
            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("cancel-456");
            msg.setReplyTopic("reply.topic");
            msg.setPayload(Map.of());

            adapter.runningTasks.put("cancel-456",
                    new MessageTask(msg, longRunning));

            adapter.cancelTask("cancel-456", false);

            // Should not send response
            verify(mockMq, never()).produceMessage(anyString(), any());
        }
    }

    // ==========================================
    // TestMessageTask
    // ==========================================

    @Nested
    @DisplayName("MessageTask 数据类测试")
    class MessageTaskTests {

        @Test
        @DisplayName("验证 MessageTask 创建")
        void testMessageTaskCreation() {
            DmqRequestMessage msg = new DmqRequestMessage();
            msg.setMessageId("test");
            msg.setReplyTopic("reply");
            msg.setPayload(Map.of());

            CompletableFuture<Void> task = new CompletableFuture<>();
            MessageTask mt = new MessageTask(msg, task);

            assertSame(msg, mt.getMessage());
            assertSame(task, mt.getTask());
        }
    }
}

