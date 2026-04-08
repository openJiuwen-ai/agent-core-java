/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

/**
 * Replaces control characters with spaces.
 */
public class SpecialCharacterNormalizer implements TextPreprocessor {

    @Override
    public String process(String text) {
        return text == null ? "" : text.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ");
    }
}
