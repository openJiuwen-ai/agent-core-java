// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试分布式消息协议类。
 * 
 * 对应Python: test_message.py - TestMessageUsagePatterns
 */
class DmqMessageTest {

    @Nested
    @DisplayName("消息的典型使用模式")
    class TestMessageUsagePatterns {

        @Test
        @DisplayName("测试非流式请求-响应流程")
        void testInvokeRequestAndResponseFlow() {
            // 构建请求
            DmqRequestMessage request = DmqRequestMessage.builder()
                    .type(DMessageType.INPUT)
                    .messageId("invoke-123")
                    .replyTopic("reply.runner.instance1")
                    .senderId("reply.runner.instance1")
                    .receiverId("agent-abc")
                    .enableStream(false)
                    .payload(Map.of("query", "hello"))
                    .expireAt(1700000000.0)
                    .build();
            assertFalse(request.isEnableStream());

            // 构建响应
            DmqResponseMessage response = DmqResponseMessage.builder()
                    .type(DMessageType.OUTPUT)
                    .messageId("invoke-123")
                    .senderId("agent-abc")
                    .receiverId("reply.runner.instance1")
                    .resultType(ResultType.MESSAGE)
                    .payload(Map.of("answer", "world"))
                    .lastChunk(true)
                    .build();
            assertEquals(request.getMessageId(), response.getMessageId());
            assertTrue(response.isLastChunk());
        }

        @Test
        @DisplayName("测试流式请求-响应流程")
        void testStreamRequestAndResponseFlow() {
            // 构建流式请求
            DmqRequestMessage request = DmqRequestMessage.builder()
                    .type(DMessageType.INPUT)
                    .messageId("stream-456")
                    .replyTopic("reply.runner.instance2")
                    .senderId("reply.runner.instance2")
                    .receiverId("agent-xyz")
                    .enableStream(true)
                    .payload(Map.of("prompt", "Generate story"))
                    .build();
            assertTrue(request.isEnableStream());

            // 构建流式响应序列
            java.util.List<DmqResponseMessage> chunks = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                DmqResponseMessage chunk = DmqResponseMessage.builder()
                        .type(DMessageType.OUTPUT)
                        .messageId("stream-456")
                        .senderId("agent-xyz")
                        .seq(i)
                        .payload(Map.of("chunk", "part_" + i))
                        .lastChunk(false)
                        .build();
                chunks.add(chunk);
            }

            // 最终响应
            DmqResponseMessage finalResp = DmqResponseMessage.builder()
                    .type(DMessageType.OUTPUT)
                    .messageId("stream-456")
                    .senderId("agent-xyz")
                    .seq(3)
                    .payload(Map.of())
                    .lastChunk(true)
                    .build();
            chunks.add(finalResp);

            // 验证序列完整性
            assertEquals(4, chunks.size());
            for (int i = 0; i < chunks.size() - 1; i++) {
                assertEquals(i, chunks.get(i).getSeq());
                assertFalse(chunks.get(i).isLastChunk());
            }
            assertTrue(chunks.getLast().isLastChunk());
        }

        @Test
        @DisplayName("测试取消请求流程")
        void testCancelRequestFlow() {
            // 原始请求
            String originalMsgId = "task-to-cancel";
            DmqRequestMessage request = DmqRequestMessage.builder()
                    .type(DMessageType.INPUT)
                    .messageId(originalMsgId)
                    .enableStream(true)
                    .payload(Map.of("prompt", "long task"))
                    .build();

            // 发送取消请求
            DmqRequestMessage stopMsg = DmqRequestMessage.builder()
                    .type(DMessageType.STOP)
                    .messageId(originalMsgId)
                    .senderId("client")
                    .payload(Map.of())
                    .build();
            assertEquals(DMessageType.STOP.getValue(), stopMsg.getType());
            assertEquals(request.getMessageId(), stopMsg.getMessageId());
        }

        @Test
        @DisplayName("测试错误处理流程")
        void testErrorHandlingFlow() {
            DmqRequestMessage request = DmqRequestMessage.builder()
                    .type(DMessageType.INPUT)
                    .messageId("error-test-123")
                    .payload(Map.of("query", "invalid"))
                    .build();

            // 服务端返回错误响应
            DmqResponseMessage errorResponse = DmqResponseMessage.builder()
                    .type(DMessageType.OUTPUT)
                    .messageId(request.getMessageId())
                    .resultType(ResultType.ERROR)
                    .payload(Map.of())
                    .errorCode(400)
                    .errorMsg("Invalid request format")
                    .lastChunk(true)
                    .build();

            assertEquals(ResultType.ERROR, errorResponse.getResultType());
            assertEquals(400, errorResponse.getErrorCode());
            assertEquals(request.getMessageId(), errorResponse.getMessageId());
        }
    }
}

