/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON serializer for distributed-runner messages.
 */
public final class MessageSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageSerializer() {
    }

    public static byte[] serializeMessage(DmqMessage message) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("class", message.getClass().getSimpleName());
        data.put("message_id", message.getMessageId());
        data.put("body", message.getBody());
        data.put("error_code", message.getErrorCode());
        data.put("error_msg", message.getErrorMsg());
        if (message instanceof DmqRequestMessage request) {
            data.put("type", request.getType().name());
            data.put("reply_topic", request.getReplyTopic());
            data.put("request_id", request.getRequestId());
            data.put("sender_id", request.getSenderId());
            data.put("receiver_id", request.getReceiverId());
            data.put("enable_stream", request.isEnableStream());
            data.put("expire_at", request.getExpireAt());
        } else if (message instanceof DmqResponseMessage response) {
            data.put("type", response.getType().name());
            data.put("result_type", response.getResultType().name());
            data.put("request_id", response.getRequestId());
            data.put("sender_id", response.getSenderId());
            data.put("receiver_id", response.getReceiverId());
            data.put("seq", response.getSeq());
            data.put("last_chunk", response.isLastChunk());
            data.put("expire_at", response.getExpireAt());
        }
        return MAPPER.writeValueAsBytes(data);
    }

    @SuppressWarnings("unchecked")
    public static DmqMessage deserializeMessage(byte[] bytes) throws Exception {
        Map<String, Object> data = MAPPER.readValue(bytes, Map.class);
        String className = String.valueOf(data.get("class"));
        if ("DmqResponseMessage".equals(className)) {
            DmqResponseMessage response = new DmqResponseMessage();
            response.setResultType(ResultType.valueOf(String.valueOf(data.getOrDefault("result_type", "MESSAGE"))));
            response.setSeq(((Number) data.getOrDefault("seq", 0)).intValue());
            response.setLastChunk(Boolean.TRUE.equals(data.get("last_chunk")));
            populateCommonFields(response, data);
            return response;
        }
        DmqRequestMessage request = new DmqRequestMessage();
        request.setEnableStream(Boolean.TRUE.equals(data.get("enable_stream")));
        request.setReplyTopic(String.valueOf(data.getOrDefault("reply_topic", "")));
        populateCommonFields(request, data);
        return request;
    }

    private static void populateCommonFields(DmqMessage message, Map<String, Object> data) {
        message.setMessageId(String.valueOf(data.getOrDefault("message_id", "")));
        message.setBody(data.get("body"));
        if (message instanceof DmqRequestMessage request) {
            request.setType(DMessageType.valueOf(String.valueOf(data.getOrDefault("type", "INPUT"))));
            request.setRequestId(String.valueOf(data.getOrDefault("request_id", "")));
            request.setSenderId(String.valueOf(data.getOrDefault("sender_id", "")));
            request.setReceiverId(String.valueOf(data.getOrDefault("receiver_id", "")));
            if (data.get("expire_at") instanceof Number number) {
                request.setExpireAt(number.doubleValue());
            }
        }
        if (message instanceof DmqResponseMessage response) {
            response.setType(DMessageType.valueOf(String.valueOf(data.getOrDefault("type", "OUTPUT"))));
            response.setRequestId(String.valueOf(data.getOrDefault("request_id", "")));
            response.setSenderId(String.valueOf(data.getOrDefault("sender_id", "")));
            response.setReceiverId(String.valueOf(data.getOrDefault("receiver_id", "")));
            if (data.get("expire_at") instanceof Number number) {
                response.setExpireAt(number.doubleValue());
            }
        }
        if (data.get("error_code") instanceof Number number) {
            message.setErrorCode(number.intValue());
        }
        message.setErrorMsg(String.valueOf(data.getOrDefault("error_msg", "")));
    }
}
