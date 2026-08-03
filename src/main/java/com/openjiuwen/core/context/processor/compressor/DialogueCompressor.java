/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

/**
 * Backward-compatible alias for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code DialogueCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/dialogue_compressor.py}.</p>
 */
public class DialogueCompressor
        extends com.openjiuwen.core.context_engine.processor.compressor.DialogueCompressor {
    public DialogueCompressor(Object config) {
        super(config instanceof DialogueCompressorConfig compatibleConfig ? compatibleConfig : config);
    }

    public DialogueCompressor(DialogueCompressorConfig config) {
        super(config);
    }
}
