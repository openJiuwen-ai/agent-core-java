// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

/**
 * Distributed request message.
 * 
 * 对应Python: drunner/dmessage_queue/message.py - DmqRequestMessage
 */
public class DmqRequestMessage extends DmqMessage {

    private String type = DMessageType.INPUT.getValue();
    private String replyTopic = "";
    private String requestId = "";
    private String senderId = "";
    private String receiverId = "";
    private boolean enableStream = false;
    private Double expireAt = null;

    public DmqRequestMessage() {
        super();
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * Set type from DMessageType enum.
     */
    public void setType(DMessageType type) {
        this.type = type.getValue();
    }

    public String getReplyTopic() {
        return replyTopic;
    }

    public void setReplyTopic(String replyTopic) {
        this.replyTopic = replyTopic != null ? replyTopic : "";
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId != null ? requestId : "";
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId != null ? senderId : "";
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId != null ? receiverId : "";
    }

    public boolean isEnableStream() {
        return enableStream;
    }

    public void setEnableStream(boolean enableStream) {
        this.enableStream = enableStream;
    }

    public Double getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Double expireAt) {
        this.expireAt = expireAt;
    }

    /**
     * Builder pattern for fluent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final DmqRequestMessage msg = new DmqRequestMessage();

        public Builder type(String type) { msg.type = type; return this; }
        public Builder type(DMessageType type) { msg.type = type.getValue(); return this; }
        public Builder messageId(String messageId) { msg.setMessageId(messageId); return this; }
        public Builder replyTopic(String replyTopic) { msg.replyTopic = replyTopic; return this; }
        public Builder requestId(String requestId) { msg.requestId = requestId; return this; }
        public Builder senderId(String senderId) { msg.senderId = senderId; return this; }
        public Builder receiverId(String receiverId) { msg.receiverId = receiverId; return this; }
        public Builder enableStream(boolean enableStream) { msg.enableStream = enableStream; return this; }
        public Builder expireAt(Double expireAt) { msg.expireAt = expireAt; return this; }
        public Builder payload(Object payload) { msg.setPayload(payload); return this; }

        public DmqRequestMessage build() { return msg; }
    }
}

