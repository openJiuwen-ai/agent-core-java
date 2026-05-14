/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Distributed response message.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DmqResponseMessage extends DmqMessage {

    private DMessageType type = DMessageType.OUTPUT;

    private ResultType resultType = ResultType.MESSAGE;

    private String requestId = "";

    private String senderId = "";

    private String receiverId = "";

    private int seq;

    private boolean lastChunk;

    private Double expireAt;

    public String getRequestId() {
        return requestId;
    }

    public String getSenderId() {
        return senderId;
    }

    public ResultType getResultType() {
        return resultType;
    }

    public void setResultType(ResultType resultType) {
        this.resultType = resultType;
    }

    public boolean isLastChunk() {
        return lastChunk;
    }

    public void setLastChunk(boolean lastChunk) {
        this.lastChunk = lastChunk;
    }
}
