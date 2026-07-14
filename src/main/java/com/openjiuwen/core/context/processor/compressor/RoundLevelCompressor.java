/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.processor.compressor;

/**
 * Backward-compatible alias for the pre-0.1.14 compressor package.
 *
 * <p>Mirrors Python's {@code RoundLevelCompressor} in
 * {@code openjiuwen/core/context_engine/processor/compressor/round_level_compressor.py}.</p>
 */
public class RoundLevelCompressor
        extends com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressor {
    public static final String ROUND_LEVEL_FALLBACK_MARKER =
            com.openjiuwen.core.context_engine.processor.compressor.RoundLevelCompressor
                    .ROUND_LEVEL_FALLBACK_MARKER;

    public RoundLevelCompressor(Object config) {
        super(config instanceof RoundLevelCompressorConfig compatibleConfig ? compatibleConfig : config);
    }

    public RoundLevelCompressor(RoundLevelCompressorConfig config) {
        super(config);
    }
}
