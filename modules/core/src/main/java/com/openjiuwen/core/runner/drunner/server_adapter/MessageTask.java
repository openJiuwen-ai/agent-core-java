/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;

import java.util.concurrent.Future;

/**
 * Associates a distributed request message with its in-flight execution task.
 * 
 * @since 0.1.7
 */
public class MessageTask {
    private final DmqRequestMessage message;
    private final Future<?> task;

    /**
     * MessageTask.
     * 
     * @param message message
     * @param task task
     * @since 0.1.7
     */
    public MessageTask(DmqRequestMessage message, Future<?> task) {
        this.message = message;
        this.task = task;
    }

    /**
     * getMessage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public DmqRequestMessage getMessage() {
        return message;
    }

    /**
     * getTask.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Future<?> getTask() {
        return task;
    }
}
