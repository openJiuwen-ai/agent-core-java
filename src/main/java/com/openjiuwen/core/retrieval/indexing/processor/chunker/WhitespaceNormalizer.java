/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Normalizes repeated whitespace.
 */
public class WhitespaceNormalizer implements TextPreprocessor {

    @Override
    public String process(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
