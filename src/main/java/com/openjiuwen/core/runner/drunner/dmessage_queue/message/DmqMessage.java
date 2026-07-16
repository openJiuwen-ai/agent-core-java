/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import com.openjiuwen.core.runner.mq.QueueMessage;

/**
 * Base distributed-runner queue message.
 * <p>
 * Overrides payload access so the in-memory MQ handler receives the whole message object,
 * while the actual business payload is stored in {@link #body}.
 * 
 * @since 0.1.7
 */
public abstract class DmqMessage extends QueueMessage {
    private Object body;

    /**
     * getPayload.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Object getPayload() {
        return this;
    }

    /**
     * setPayload.
     * 
     * @param payload payload
     * @since 0.1.7
     */
    @Override
    public void setPayload(Object payload) {
        this.body = payload;
    }

    /**
     * getBody.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Object getBody() {
        return body;
    }

    /**
     * setBody.
     * 
     * @param body body
     * @since 0.1.7
     */
    public void setBody(Object body) {
        this.body = body;
    }
}
