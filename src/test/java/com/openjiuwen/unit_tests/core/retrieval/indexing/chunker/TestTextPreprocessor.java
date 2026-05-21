/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.retrieval.indexing.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.chunker.TextPreprocessor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Text preprocessor test cases.
 * Mirrors Python's tests/unit_tests/core/retrieval/indexing/processor/chunker/test_text_preprocessor.py
 */
class TestTextPreprocessor {

    @Test
    void testTextPreprocessorExists() {
        // Test that TextPreprocessor class exists
        assertNotNull(TextPreprocessor.class);
    }

    @Test
    void testPreprocess() {
        // Test preprocessing text
        TextPreprocessor preprocessor = new TextPreprocessor();
        String text = "  This  is   a  test  with  extra  spaces.  ";
        String result = preprocessor.preprocess(text);
        assertNotNull(result);
        assertFalse(result.startsWith("  "));
    }
}