/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.offloader;

/**
 * Backward-compatible alias for the pre-0.1.14 offloader package.
 *
 * <p>Mirrors Python's {@code MessageOffloader} in
 * {@code openjiuwen/core/context_engine/processor/offloader/message_offloader.py}.</p>
 */
public class MessageOffloader
        extends com.openjiuwen.core.context_engine.processor.offloader.MessageOffloader {
    public MessageOffloader(Object config) {
        super(config instanceof MessageOffloaderConfig compatibleConfig ? compatibleConfig : config);
    }

    public MessageOffloader(MessageOffloaderConfig config) {
        super(config);
    }
}
