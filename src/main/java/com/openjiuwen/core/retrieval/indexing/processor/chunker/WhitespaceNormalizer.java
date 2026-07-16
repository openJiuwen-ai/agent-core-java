/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Normalizes repeated whitespace.
 * 
 * @since 0.1.7
 */
public class WhitespaceNormalizer implements TextPreprocessor {
    /**
     * process.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String process(String text) {
        return text == null ? null : text.replaceAll("\\s+", " ").trim();
    }
}
