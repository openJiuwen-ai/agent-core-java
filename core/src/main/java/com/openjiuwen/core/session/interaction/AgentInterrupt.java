/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

/**
 * Agent interruption raised when interaction input pauses execution.
 *
 * <p>Mirrors Python's {@code AgentInterrupt} in
 * {@code openjiuwen/core/session/interaction/base.py}.</p>
 */
public class AgentInterrupt extends RuntimeException {

    public final String message;

    public AgentInterrupt(String message) {
        super(message);
        this.message = message;
    }

    public AgentInterrupt(String message, Throwable cause) {
        super(message, cause);
        this.message = message;
    }
}
