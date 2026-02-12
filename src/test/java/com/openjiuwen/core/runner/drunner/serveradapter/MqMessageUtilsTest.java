// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.ResultType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MqMessageUtils.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/server_adapter/test_mq_message_utils.py
 */
class MqMessageUtilsTest {

    private DmqRequestMessage requestMessage;

    @BeforeEach
    void setUp() {
        requestMessage = new DmqRequestMessage();
        requestMessage.setMessageId("test-msg-123");
        requestMessage.setReplyTopic("reply.topic.client");
        requestMessage.setSenderId("client-sender-456");
        requestMessage.setReceiverId("server-receiver-789");
        requestMessage.setPayload(Map.of("query", "test"));
    }

    // ==========================================
    // TestBuildStreamResponse
    // ==========================================

    @Nested
    @DisplayName("build_stream_response 测试")
    class BuildStreamResponseTests {

        @Test
        @DisplayName("设置正确的 seq 序号")
        void testSetsSeq() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of("chunk", "data"), 5);
            assertEquals(5, response.getSeq());
        }

        @Test
        @DisplayName("默认 last_chunk=False")
        void testSetsLastChunkFalse() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of("chunk", "data"), 0);
            assertFalse(response.isLastChunk());
        }

        @Test
        @DisplayName("可设置 last=True")
        void testSetsLastChunkTrue() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of("chunk", "data"), 0, true);
            assertTrue(response.isLastChunk());
        }

        @Test
        @DisplayName("设置 payload")
        void testSetsPayload() {
            Map<String, Object> payload = Map.of("chunk", "hello", "index", 1);
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", payload, 0);
            assertEquals(payload, response.getPayload());
        }

        @Test
        @DisplayName("从请求消息复制 message_id")
        void testSetsMessageId() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of(), 0);
            assertEquals("test-msg-123", response.getMessageId());
        }

        @Test
        @DisplayName("设置 sender_id 为 adapter_id")
        void testSetsSenderId() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "my-adapter", Map.of(), 0);
            assertEquals("my-adapter", response.getSenderId());
        }

        @Test
        @DisplayName("设置 receiver_id 为请求的 sender_id")
        void testSetsReceiverId() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of(), 0);
            assertEquals("client-sender-456", response.getReceiverId());
        }

        @Test
        @DisplayName("type 为 OUTPUT")
        void testTypeOutput() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of(), 0);
            assertEquals(DMessageType.OUTPUT.getValue(), response.getType());
        }

        @Test
        @DisplayName("返回 DmqResponseMessage 实例")
        void testReturnsDmqResponseMessage() {
            DmqResponseMessage response = MqMessageUtils.buildStreamResponse(
                    requestMessage, "adapter-1", Map.of(), 0);
            assertNotNull(response);
            assertInstanceOf(DmqResponseMessage.class, response);
        }
    }

    // ==========================================
    // TestBuildFinalResponse
    // ==========================================

    @Nested
    @DisplayName("build_final_response 测试")
    class BuildFinalResponseTests {

        @Test
        @DisplayName("设置正确的 seq 序号")
        void testSetsSeq() {
            DmqResponseMessage response = MqMessageUtils.buildFinalResponse(
                    requestMessage, "adapter-1", 10);
            assertEquals(10, response.getSeq());
        }

        @Test
        @DisplayName("last_chunk=True")
        void testSetsLastChunkTrue() {
            DmqResponseMessage response = MqMessageUtils.buildFinalResponse(
                    requestMessage, "adapter-1", 5);
            assertTrue(response.isLastChunk());
        }

        @Test
        @DisplayName("payload 为空字典")
        void testSetsEmptyPayload() {
            DmqResponseMessage response = MqMessageUtils.buildFinalResponse(
                    requestMessage, "adapter-1", 0);
            assertEquals(Map.of(), response.getPayload());
        }

        @Test
        @DisplayName("从请求消息复制 message_id")
        void testSetsMessageId() {
            DmqResponseMessage response = MqMessageUtils.buildFinalResponse(
                    requestMessage, "adapter-1", 0);
            assertEquals("test-msg-123", response.getMessageId());
        }

        @Test
        @DisplayName("内部调用 build_stream_response")
        void testCallsBuildStreamResponse() {
            DmqResponseMessage response = MqMessageUtils.buildFinalResponse(
                    requestMessage, "adapter-1", 7);
            assertInstanceOf(DmqResponseMessage.class, response);
            assertEquals(7, response.getSeq());
            assertTrue(response.isLastChunk());
        }
    }

    // ==========================================
    // TestBuildBatchResponse
    // ==========================================

    @Nested
    @DisplayName("build_batch_response 测试")
    class BuildBatchResponseTests {

        @Test
        @DisplayName("设置 payload 为 result")
        void testSetsPayload() {
            Map<String, Object> result = Map.of("answer", "hello world", "score", 0.95);
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", result);
            assertEquals(result, response.getPayload());
        }

        @Test
        @DisplayName("last_chunk=True")
        void testSetsLastChunkTrue() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of("result", "ok"));
            assertTrue(response.isLastChunk());
        }

        @Test
        @DisplayName("seq=0")
        void testSetsSeqZero() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of("result", "ok"));
            assertEquals(0, response.getSeq());
        }

        @Test
        @DisplayName("从请求消息复制 message_id")
        void testSetsMessageId() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of());
            assertEquals("test-msg-123", response.getMessageId());
        }

        @Test
        @DisplayName("设置 sender_id 为 adapter_id")
        void testSetsSenderId() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "my-batch-adapter", Map.of());
            assertEquals("my-batch-adapter", response.getSenderId());
        }

        @Test
        @DisplayName("设置 receiver_id 为请求的 sender_id")
        void testSetsReceiverId() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of());
            assertEquals("client-sender-456", response.getReceiverId());
        }

        @Test
        @DisplayName("type 为 OUTPUT")
        void testTypeOutput() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of());
            assertEquals(DMessageType.OUTPUT.getValue(), response.getType());
        }

        @Test
        @DisplayName("返回 DmqResponseMessage 实例")
        void testReturnsDmqResponseMessage() {
            DmqResponseMessage response = MqMessageUtils.buildBatchResponse(
                    requestMessage, "adapter-1", Map.of());
            assertInstanceOf(DmqResponseMessage.class, response);
        }
    }

    // ==========================================
    // TestBuildErrorResponse
    // ==========================================

    @Nested
    @DisplayName("build_error_response 测试")
    class BuildErrorResponseTests {

        @Test
        @DisplayName("result_type 为 ERROR")
        void testSetsResultTypeError() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Internal error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals(ResultType.ERROR, response.getResultType());
        }

        @Test
        @DisplayName("设置 error_code")
        void testSetsErrorCode() {
            JiuWenBaseException error = new JiuWenBaseException(503, "Service unavailable");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals(503, response.getErrorCode());
        }

        @Test
        @DisplayName("设置 error_msg")
        void testSetsErrorMsg() {
            JiuWenBaseException error = new JiuWenBaseException(404, "Not found");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals("Not found", response.getErrorMsg());
        }

        @Test
        @DisplayName("payload 为空字典")
        void testSetsEmptyPayload() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals(Map.of(), response.getPayload());
        }

        @Test
        @DisplayName("last_chunk=True")
        void testSetsLastChunkTrue() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertTrue(response.isLastChunk());
        }

        @Test
        @DisplayName("seq=0")
        void testSetsSeqZero() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals(0, response.getSeq());
        }

        @Test
        @DisplayName("从请求消息复制 message_id")
        void testSetsMessageId() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals("test-msg-123", response.getMessageId());
        }

        @Test
        @DisplayName("设置 sender_id 为 adapter_id")
        void testSetsSenderId() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "error-adapter", error);
            assertEquals("error-adapter", response.getSenderId());
        }

        @Test
        @DisplayName("设置 receiver_id 为请求的 sender_id")
        void testSetsReceiverId() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals("client-sender-456", response.getReceiverId());
        }

        @Test
        @DisplayName("type 为 OUTPUT")
        void testTypeOutput() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertEquals(DMessageType.OUTPUT.getValue(), response.getType());
        }

        @Test
        @DisplayName("返回 DmqResponseMessage 实例")
        void testReturnsDmqResponseMessage() {
            JiuWenBaseException error = new JiuWenBaseException(500, "Error");
            DmqResponseMessage response = MqMessageUtils.buildErrorResponse(
                    requestMessage, "adapter-1", error);
            assertInstanceOf(DmqResponseMessage.class, response);
        }
    }

    // ==========================================
    // TestMessageUtilsIntegration
    // ==========================================

    @Nested
    @DisplayName("消息构建工具集成测试")
    class IntegrationTests {

        @Test
        @DisplayName("验证流式响应序列的正确性")
        void testStreamResponseSequence() {
            String adapterId = "stream-adapter";

            DmqResponseMessage[] responses = new DmqResponseMessage[4];
            for (int i = 0; i < 3; i++) {
                responses[i] = MqMessageUtils.buildStreamResponse(
                        requestMessage, adapterId,
                        Map.of("chunk", "data_" + i), i, false);
            }
            responses[3] = MqMessageUtils.buildFinalResponse(requestMessage, adapterId, 3);

            assertEquals(4, responses.length);
            for (int i = 0; i < 3; i++) {
                assertEquals(i, responses[i].getSeq());
                assertFalse(responses[i].isLastChunk());
            }
            assertEquals(3, responses[3].getSeq());
            assertTrue(responses[3].isLastChunk());
            assertEquals(Map.of(), responses[3].getPayload());
        }

        @Test
        @DisplayName("对比错误响应和批量响应的差异")
        void testErrorResponseVsBatchResponse() {
            String adapterId = "test-adapter";

            DmqResponseMessage batchResp = MqMessageUtils.buildBatchResponse(
                    requestMessage, adapterId, Map.of("result", "success"));

            JiuWenBaseException error = new JiuWenBaseException(500, "Something went wrong");
            DmqResponseMessage errorResp = MqMessageUtils.buildErrorResponse(
                    requestMessage, adapterId, error);

            // 共同点
            assertEquals(DMessageType.OUTPUT.getValue(), batchResp.getType());
            assertEquals(DMessageType.OUTPUT.getValue(), errorResp.getType());
            assertTrue(batchResp.isLastChunk());
            assertTrue(errorResp.isLastChunk());
            assertEquals(0, batchResp.getSeq());
            assertEquals(0, errorResp.getSeq());
            assertEquals(batchResp.getMessageId(), errorResp.getMessageId());

            // 差异点
            assertNotEquals(batchResp.getResultType(), errorResp.getResultType());
            assertEquals(ResultType.ERROR, errorResp.getResultType());
            assertEquals(500, errorResp.getErrorCode());
            assertEquals("Something went wrong", errorResp.getErrorMsg());
        }
    }
}

