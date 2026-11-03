/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MqMessageUtilsTest {

    @Test
    void buildStreamResponseMirrorsStreamChunkRouting() {
        DmqRequestMessage request = requestMessage();
        Map<String, Object> payload = Map.of("chunk", "hello");

        DmqResponseMessage response = MqMessageUtils.buildStreamResponse(request, "adapter-1", payload, 7, false);

        assertThat(response.getType()).isEqualTo(DMessageType.OUTPUT);
        assertThat(response.getMessageId()).isEqualTo("msg-1");
        assertThat(response.getBody()).isEqualTo(payload);
        assertThat(response.getSenderId()).isEqualTo("adapter-1");
        assertThat(response.getReceiverId()).isEqualTo("caller-1");
        assertThat(response.getSeq()).isEqualTo(7);
        assertThat(response.isLastChunk()).isFalse();
        assertThat(response.getResultType()).isEqualTo(ResultType.MESSAGE);
        assertThat(response.getRequestId()).isEmpty();
    }

    @Test
    void buildFinalResponseUsesEmptyPayloadAndTerminalFlag() {
        DmqRequestMessage request = requestMessage();

        DmqResponseMessage response = MqMessageUtils.buildFinalResponse(request, "adapter-1", 3);

        assertThat(response.getBody()).isEqualTo(Map.of());
        assertThat(response.getSeq()).isEqualTo(3);
        assertThat(response.isLastChunk()).isTrue();
        assertThat(response.getReceiverId()).isEqualTo("caller-1");
    }

    @Test
    void buildBatchResponseUsesTerminalBatchDefaults() {
        DmqRequestMessage request = requestMessage();
        Map<String, Object> result = Map.of("answer", 42);

        DmqResponseMessage response = MqMessageUtils.buildBatchResponse(request, "adapter-1", result);

        assertThat(response.getBody()).isEqualTo(result);
        assertThat(response.getSeq()).isZero();
        assertThat(response.isLastChunk()).isTrue();
        assertThat(response.getResultType()).isEqualTo(ResultType.MESSAGE);
    }

    @Test
    void buildErrorResponseCopiesErrorMetadataAndUsesEmptyPayload() {
        DmqRequestMessage request = requestMessage();
        BaseError error = ErrorHelper.buildError(
                StatusCode.MESSAGE_QUEUE_MESSAGE_PROCESS_EXECUTION_ERROR,
                "reason",
                "boom"
        );

        DmqResponseMessage response = MqMessageUtils.buildErrorResponse(request, "adapter-1", error);

        assertThat(response.getType()).isEqualTo(DMessageType.OUTPUT);
        assertThat(response.getMessageId()).isEqualTo("msg-1");
        assertThat(response.getBody()).isEqualTo(Map.of());
        assertThat(response.getResultType()).isEqualTo(ResultType.ERROR);
        assertThat(response.getErrorCode()).isEqualTo(error.getCode());
        assertThat(response.getErrorMsg()).isEqualTo(error.getMessage());
        assertThat(response.getSenderId()).isEqualTo("adapter-1");
        assertThat(response.getReceiverId()).isEqualTo("caller-1");
        assertThat(response.getSeq()).isZero();
        assertThat(response.isLastChunk()).isTrue();
    }

    private static DmqRequestMessage requestMessage() {
        DmqRequestMessage request = new DmqRequestMessage();
        request.setMessageId("msg-1");
        request.setSenderId("caller-1");
        request.setReplyTopic("topic-1");
        request.setRequestId("req-1");
        request.setPayload(Map.of("prompt", "hello"));
        return request;
    }
}
