/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import com.openjiuwen.core.runner.mq.QueueMessage;

/**
 * Base distributed-runner queue message.
 *
 * <p>Overrides payload access so the in-memory MQ handler receives the whole message object,
 * while the actual business payload is stored in {@link #body}.
 */
public abstract class DmqMessage extends QueueMessage {

    private Object body;

    @Override
    public Object getPayload() {
        return this;
    }

    @Override
    public void setPayload(Object payload) {
        this.body = payload;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }
}
