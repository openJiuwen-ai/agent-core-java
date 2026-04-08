/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;

import java.util.concurrent.Future;

/**
 * Associates a distributed request message with its in-flight execution task.
 */
public class MessageTask {
    private final DmqRequestMessage message;
    private final Future<?> task;

    public MessageTask(DmqRequestMessage message, Future<?> task) {
        this.message = message;
        this.task = task;
    }

    public DmqRequestMessage getMessage() {
        return message;
    }

    public Future<?> getTask() {
        return task;
    }
}
