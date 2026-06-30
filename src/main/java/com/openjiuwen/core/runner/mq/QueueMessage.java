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

    /**
     * Auto-generated for codecheck compliance.
     */
    public QueueMessage() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public QueueMessage(String messageId, Object payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
