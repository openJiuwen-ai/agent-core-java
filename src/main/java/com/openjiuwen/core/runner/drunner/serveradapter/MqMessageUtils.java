// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.ResultType;

import java.util.Map;

/**
 * MQ消息构建工具类
 * 
 * <p>提供构建流式响应、最终响应、批量响应和错误响应的静态工厂方法。
 * 
 * 对应Python: drunner/server_adapter/mq_message_utils.py
 */
public final class MqMessageUtils {

    private MqMessageUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * 构建流式响应消息
     *
     * @param message   原始请求消息
     * @param senderId  发送者ID（adapter_id）
     * @param payload   响应载荷
     * @param seq       序列号
     * @param last      是否为最后一个chunk
     * @return DmqResponseMessage
     */
    public static DmqResponseMessage buildStreamResponse(DmqRequestMessage message,
                                                          String senderId,
                                                          Object payload,
                                                          int seq,
                                                          boolean last) {
        return DmqResponseMessage.builder()
                .type(DMessageType.OUTPUT)
                .messageId(message.getMessageId())
                .payload(payload)
                .senderId(senderId)
                .receiverId(message.getSenderId())
                .seq(seq)
                .lastChunk(last)
                .build();
    }

    /**
     * 构建流式响应消息（默认 last=false）
     */
    public static DmqResponseMessage buildStreamResponse(DmqRequestMessage message,
                                                          String senderId,
                                                          Object payload,
                                                          int seq) {
        return buildStreamResponse(message, senderId, payload, seq, false);
    }

    /**
     * 构建最终响应消息（空payload + last=true）
     *
     * @param message   原始请求消息
     * @param senderId  发送者ID
     * @param seq       序列号
     * @return DmqResponseMessage
     */
    public static DmqResponseMessage buildFinalResponse(DmqRequestMessage message,
                                                         String senderId,
                                                         int seq) {
        return buildStreamResponse(message, senderId, Map.of(), seq, true);
    }

    /**
     * 构建批量响应消息（一次性完整结果）
     *
     * @param message   原始请求消息
     * @param senderId  发送者ID
     * @param result    结果载荷
     * @return DmqResponseMessage
     */
    public static DmqResponseMessage buildBatchResponse(DmqRequestMessage message,
                                                         String senderId,
                                                         Object result) {
        return DmqResponseMessage.builder()
                .type(DMessageType.OUTPUT)
                .messageId(message.getMessageId())
                .payload(result)
                .senderId(senderId)
                .receiverId(message.getSenderId())
                .seq(0)
                .lastChunk(true)
                .build();
    }

    /**
     * 构建错误响应消息
     *
     * @param message   原始请求消息
     * @param senderId  发送者ID
     * @param error     异常信息
     * @return DmqResponseMessage
     */
    public static DmqResponseMessage buildErrorResponse(DmqRequestMessage message,
                                                         String senderId,
                                                         JiuWenBaseException error) {
        return DmqResponseMessage.builder()
                .type(DMessageType.OUTPUT)
                .messageId(message.getMessageId())
                .payload(Map.of())
                .resultType(ResultType.ERROR)
                .errorCode(error.getErrorCode())
                .errorMsg(error.getErrorMessage())
                .senderId(senderId)
                .receiverId(message.getSenderId())
                .seq(0)
                .lastChunk(true)
                .build();
    }
}

