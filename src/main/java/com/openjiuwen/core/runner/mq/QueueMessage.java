/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * Base message object for message queue communication.
 * Mirrors Python's {@code QueueMessage} in {@code message_queue_base.py}.
 * 
 * @since 0.1.7
 */
public class QueueMessage {
    private String messageId = "";
    private Object payload;
    private int errorCode = 0; // StatusCode.SUCCESS
    private String errorMsg = "";

    /**
     * QueueMessage.
     * 
     * @since 0.1.7
     */
    public QueueMessage() {
    }

    /**
     * QueueMessage.
     * 
     * @param messageId messageId
     * @param payload payload
     * @since 0.1.7
     */
    public QueueMessage(String messageId, Object payload) {
        this.messageId = messageId;
        this.payload = payload;
    }

    /**
     * getMessageId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * setMessageId.
     * 
     * @param messageId messageId
     * @since 0.1.7
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * getPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getPayload() {
        return payload;
    }

    /**
     * setPayload.
     * 
     * @param payload payload
     * @since 0.1.7
     */
    public void setPayload(Object payload) {
        this.payload = payload;
    }

    /**
     * getErrorCode.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int getErrorCode() {
        return errorCode;
    }

    /**
     * setErrorCode.
     * 
     * @param errorCode errorCode
     * @since 0.1.7
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * getErrorMsg.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * setErrorMsg.
     * 
     * @param errorMsg errorMsg
     * @since 0.1.7
     */
    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
