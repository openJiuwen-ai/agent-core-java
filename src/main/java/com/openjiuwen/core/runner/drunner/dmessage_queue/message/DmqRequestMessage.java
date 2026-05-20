/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

/**
 * Distributed request message.
 */
public class DmqRequestMessage extends DmqMessage {

    private DMessageType type = DMessageType.INPUT;

    private String replyTopic = "";

    private String requestId = "";

    private String senderId = "";

    private String receiverId = "";

    private boolean isEnableStream;

    private Double expireAt;

    /**
     * Auto-generated for codecheck compliance.
     */
    public DMessageType getType() {
        return type;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setType(DMessageType type) {
        this.type = type;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getReplyTopic() {
        return replyTopic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReplyTopic(String replyTopic) {
        this.replyTopic = replyTopic;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getReceiverId() {
        return receiverId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public boolean isEnableStream() {
        return isEnableStream;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setEnableStream(boolean isEnableStream) {
        this.isEnableStream = isEnableStream;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Double getExpireAt() {
        return expireAt;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setExpireAt(Double expireAt) {
        this.expireAt = expireAt;
    }
}
