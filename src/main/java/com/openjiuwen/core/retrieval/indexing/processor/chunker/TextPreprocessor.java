/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Text preprocessor abstraction.
 */
public interface TextPreprocessor {

    String process(String text);
}
