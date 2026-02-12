// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.serveradapter;

import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;

import java.util.concurrent.Future;

/**
 * 正在运行的消息任务记录
 * 
 * 对应Python: drunner/server_adapter/mq_server_adapter.py - MessageTask
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

