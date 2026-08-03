/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

/**
 * Backward-compatible alias for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code CurrentRoundCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/current_round_compressor.py}.</p>
 */
public class CurrentRoundCompressor
        extends com.openjiuwen.core.context_engine.processor.compressor.CurrentRoundCompressor {
    public CurrentRoundCompressor(Object config) {
        super(config instanceof CurrentRoundCompressorConfig compatibleConfig
                ? compatibleConfig
                : config);
    }

    public CurrentRoundCompressor(CurrentRoundCompressorConfig config) {
        super(config);
    }
}
