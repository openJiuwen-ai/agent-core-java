// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

/**
 * Distributed response message.
 * 
 * 对应Python: drunner/dmessage_queue/message.py - DmqResponseMessage
 */
public class DmqResponseMessage extends DmqMessage {

    private String type = DMessageType.OUTPUT.getValue();
    private ResultType resultType = ResultType.MESSAGE;
    private String requestId = "";
    private String senderId = "";
    private String receiverId = "";
    private int seq = 0;
    private boolean lastChunk = false;
    private Double expireAt = null;

    public DmqResponseMessage() {
        super();
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setType(DMessageType type) {
        this.type = type.getValue();
    }

    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
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

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public boolean isLastChunk() {
        return lastChunk;
    }

    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
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
        private final DmqResponseMessage msg = new DmqResponseMessage();

        public Builder type(String type) { msg.type = type; return this; }
        public Builder type(DMessageType type) { msg.type = type.getValue(); return this; }
        public Builder messageId(String messageId) { msg.setMessageId(messageId); return this; }
        public Builder resultType(ResultType resultType) { msg.resultType = resultType; return this; }
        public Builder requestId(String requestId) { msg.requestId = requestId; return this; }
        public Builder senderId(String senderId) { msg.senderId = senderId; return this; }
        public Builder receiverId(String receiverId) { msg.receiverId = receiverId; return this; }
        public Builder seq(int seq) { msg.seq = seq; return this; }
        public Builder lastChunk(boolean lastChunk) { msg.lastChunk = lastChunk; return this; }
        public Builder expireAt(Double expireAt) { msg.expireAt = expireAt; return this; }
        public Builder payload(Object payload) { msg.setPayload(payload); return this; }
        public Builder errorCode(int errorCode) { msg.setErrorCode(errorCode); return this; }
        public Builder errorMsg(String errorMsg) { msg.setErrorMsg(errorMsg); return this; }

        public DmqResponseMessage build() { return msg; }
    }
}

