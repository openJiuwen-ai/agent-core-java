/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.message;

/**
 * Distributed message type.
 *
 * <p>Mirrors Python's {@code DMessageType} in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message.py}.
 */
public enum DMessageType {
    INPUT,
    STOP,
    OUTPUT
}
