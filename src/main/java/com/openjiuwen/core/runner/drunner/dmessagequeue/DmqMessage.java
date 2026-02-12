// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.openjiuwen.core.runner.QueueMessage;

/**
 * Base class for distributed message queue messages.
 * 
 * 对应Python: drunner/dmessage_queue/message.py - DmqMessage
 */
public class DmqMessage extends QueueMessage {

    public DmqMessage() {
        super();
    }

    public DmqMessage(String messageId, Object payload) {
        super(messageId, payload);
    }
}

