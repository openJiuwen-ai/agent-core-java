/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

import com.openjiuwen.core.runner.mq.QueueMessage;

/**
 * Base distributed-runner queue message.
 *
 * <p>Mirrors Python's {@code DmqMessage} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message.py}.
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
