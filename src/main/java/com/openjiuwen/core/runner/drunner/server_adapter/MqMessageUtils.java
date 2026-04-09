  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

/**
 * Helpers for distributed MQ response construction.
 */
public final class MqMessageUtils {

    private MqMessageUtils() {
    }

    public static DmqResponseMessage buildStreamResponse(DmqRequestMessage request, String senderId,
                                                         Object payload, int seq, boolean last) {
        DmqResponseMessage response = new DmqResponseMessage();
        response.setType(DMessageType.OUTPUT);
        response.setMessageId(request.getMessageId());
        response.setBody(payload);
        response.setSenderId(senderId);
        response.setReceiverId(request.getSenderId());
        response.setRequestId(request.getRequestId());
        response.setSeq(seq);
        response.setLastChunk(last);
        return response;
    }

    public static DmqResponseMessage buildFinalResponse(DmqRequestMessage request, String senderId, int seq) {
        return buildStreamResponse(request, senderId, java.util.Map.of(), seq, true);
    }

    public static DmqResponseMessage buildBatchResponse(DmqRequestMessage request, String senderId, Object result) {
        DmqResponseMessage response = buildStreamResponse(request, senderId, result, 0, true);
        response.setResultType(ResultType.MESSAGE);
        return response;
    }

    public static DmqResponseMessage buildErrorResponse(DmqRequestMessage request, String senderId, Exception error) {
        DmqResponseMessage response = buildStreamResponse(request, senderId, java.util.Map.of(), 0, true);
        response.setResultType(ResultType.ERROR);
        int errorCode = -1;
        if (error instanceof BaseError baseError) {
            errorCode = baseError.getCode();
        }
        response.setErrorCode(errorCode);
        response.setErrorMsg(error.getMessage());
        return response;
    }
}
