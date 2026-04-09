/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Base message object for message queue communication.
 * Mirrors Python's {@code QueueMessage} in {@code message_queue_base.py}.
 */
public class QueueMessage {

    private String messageId = "";
    private Object payload;
    private int errorCode = 0; // StatusCode.SUCCESS
    private String errorMsg = "";

    public QueueMessage() {
    }

    public QueueMessage(String messageId, Object payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
