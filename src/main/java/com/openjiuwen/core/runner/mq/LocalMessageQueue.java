/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * No-op local message queue stub.
 *
 * <p>Mirrors Python's {@code LocalMessageQueue} in
 * {@code openjiuwen/core/runner/message_queue_base.py}.
 */
public class LocalMessageQueue {

    public boolean start() {
        return true;
    }

    public boolean stop() {
        return true;
    }
}
