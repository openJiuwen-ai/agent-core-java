/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import java.util.Map;

/**
 * Mirrors Python's module helpers in
 * {@code openjiuwen/core/runner/drunner/server_adapter/mq_message_utils.py}.
 */
public final class MqMessageUtils {

    private MqMessageUtils() {
    }

    public static DmqResponseMessage buildStreamResponse(
            DmqRequestMessage message,
            String senderId,
            Object payload,
            int seq,
            boolean last
    ) {
        DmqResponseMessage response = new DmqResponseMessage();
        response.setType(DMessageType.OUTPUT);
        response.setMessageId(message.getMessageId());
        response.setBody(payload);
        response.setSenderId(senderId);
        response.setReceiverId(message.getSenderId());
        response.setSeq(seq);
        response.setLastChunk(last);
        return response;
    }

    public static DmqResponseMessage buildFinalResponse(DmqRequestMessage message, String senderId, int seq) {
        return buildStreamResponse(message, senderId, Map.of(), seq, true);
    }

    public static DmqResponseMessage buildBatchResponse(DmqRequestMessage message, String senderId, Object result) {
        return buildStreamResponse(message, senderId, result, 0, true);
    }

    public static DmqResponseMessage buildErrorResponse(DmqRequestMessage message, String senderId, BaseError error) {
        DmqResponseMessage response = new DmqResponseMessage();
        response.setType(DMessageType.OUTPUT);
        response.setMessageId(message.getMessageId());
        response.setBody(Map.of());
        response.setResultType(ResultType.ERROR);
        response.setErrorCode(error.getCode());
        response.setErrorMsg(error.getMessage());
        response.setSenderId(senderId);
        response.setReceiverId(message.getSenderId());
        response.setSeq(0);
        response.setLastChunk(true);
        return response;
    }
}
