/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

/**
 * Backward-compatible alias for the pre-0.1.14 summary offloader package.
 *
 * <p>Mirrors Python's {@code MessageSummaryOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_summary_offloader.py}.</p>
 */
public class MessageSummaryOffloader
        extends com.openjiuwen.core.context_engine.processor.offloader.MessageSummaryOffloader {
    public MessageSummaryOffloader(Object config) {
        super(config instanceof MessageSummaryOffloaderConfig compatibleConfig ? compatibleConfig : config);
    }

    public MessageSummaryOffloader(MessageSummaryOffloaderConfig config) {
        super(config);
    }
}
