/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.regex.Pattern;

/**
 * Mirrors Python's {@code WhitespaceNormalizer} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_preprocessor.py}.
 */
public class WhitespaceNormalizer implements TextPreprocessor {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    @Override
    public String process(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }
}
