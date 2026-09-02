/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

/**
 * Distributed request message.
 * 
 * @since 0.1.7
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
     * getType.
     * 
     * @return the result
     * @since 0.1.7
     */
    public DMessageType getType() {
        return type;
    }

    /**
     * setType.
     * 
     * @param type type
     * @since 0.1.7
     */
    public void setType(DMessageType type) {
        this.type = type;
    }

    /**
     * getReplyTopic.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReplyTopic() {
        return replyTopic;
    }

    /**
     * setReplyTopic.
     * 
     * @param replyTopic replyTopic
     * @since 0.1.7
     */
    public void setReplyTopic(String replyTopic) {
        this.replyTopic = replyTopic;
    }

    /**
     * getRequestId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * setRequestId.
     * 
     * @param requestId requestId
     * @since 0.1.7
     */
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * getSenderId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getSenderId() {
        return senderId;
    }

    /**
     * setSenderId.
     * 
     * @param senderId senderId
     * @since 0.1.7
     */
    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    /**
     * getReceiverId.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getReceiverId() {
        return receiverId;
    }

    /**
     * setReceiverId.
     * 
     * @param receiverId receiverId
     * @since 0.1.7
     */
    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    /**
     * isEnableStream.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean isEnableStream() {
        return isEnableStream;
    }

    /**
     * setEnableStream.
     * 
     * @param isEnableStream isEnableStream
     * @since 0.1.7
     */
    public void setEnableStream(boolean isEnableStream) {
        this.isEnableStream = isEnableStream;
    }

    /**
     * getExpireAt.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Double getExpireAt() {
        return expireAt;
    }

    /**
     * setExpireAt.
     * 
     * @param expireAt expireAt
     * @since 0.1.7
     */
    public void setExpireAt(Double expireAt) {
        this.expireAt = expireAt;
    }
}
