/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.schema;

/**
 * Factory helpers for loop events.
 *
 * <p>Mirrors Python's module-level {@code create_loop_event} helper in
 * {@code openjiuwen.harness.schema.loop_event}.
 */
public final class LoopEventFactory {

    private LoopEventFactory() {
    }

    public static DeepLoopEvent createLoopEvent(
            int seq,
            DeepLoopEventType eventType,
            String content) {
        return DeepLoopEvent.create(seq, eventType, content);
    }
}
