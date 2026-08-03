/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Mirrors Python's {@code TextPreprocessor} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_preprocessor.py}.
 */
@FunctionalInterface
public interface TextPreprocessor {

    String process(String text);

    default String call(String text) {
        return process(text);
    }
}
