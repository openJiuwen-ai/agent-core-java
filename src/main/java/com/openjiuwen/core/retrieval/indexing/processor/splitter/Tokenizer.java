/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import java.util.List;

/**
 * Tokenizer interface for text tokenization.
 * 
 * <p>Provides encode and decode methods for converting between text and tokens.
 * Used by Splitter for accurate token counting.</p>
 * 
 * <p>Mirrors Python's tokenizer interface in splitter/base.py.</p>
 */
public interface Tokenizer {

    /**
     * Encode text into a list of token IDs.
     *
     * @param text Text to encode
     * @return List of token IDs
     */
    List<Integer> encode(String text);

    /**
     * Decode token IDs back to text.
     *
     * @param tokens List of token IDs
     * @return Decoded text
     */
    String decode(List<Integer> tokens);

    /**
     * Check if this tokenizer supports decode operation.
     *
     * @return true if decode is supported
     */
    default boolean canDecode() {
        return true;
    }
}
