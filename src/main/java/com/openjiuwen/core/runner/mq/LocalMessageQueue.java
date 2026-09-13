/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.mq;

/**
 * No-op local message queue stub.
 * Mirrors Python's {@code LocalMessageQueue} in {@code message_queue_base.py}.
 * 
 * @since 0.1.7
 */
public class LocalMessageQueue {
    /**
     * start.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean start() {
        return true;
    }

    /**
     * stop.
     * 
     * @return the result
     * @since 0.1.7
     */
    public boolean stop() {
        return true;
    }
}
