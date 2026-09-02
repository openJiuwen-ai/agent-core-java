/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Text preprocessor abstraction.
 * 
 * @since 0.1.7
 */
public interface TextPreprocessor {
    /**
     * process.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    String process(String text);
}
