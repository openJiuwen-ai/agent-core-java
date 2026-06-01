/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.unit_tests.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code tests.unit_tests.core.retrieval.embedding.test_openai_embedding}.
 *
 * <p>The package-local OpenAI suite contains the one-to-one parity assertions. This translated
 * unit-test path exposes the same 19 methods explicitly so count-based audits see the mapping.</p>
 */
class TestOpenAIEmbedding extends com.openjiuwen.core.retrieval.embedding.OpenAIEmbeddingTest {

    @Test
    @Override
    public void testInitWithApiKey() {
        super.testInitWithApiKey();
    }

    @Test
    @Override
    public void testInitWithoutApiKey() {
        super.testInitWithoutApiKey();
    }

    @Test
    @Override
    public void testInitWithExtraHeaders() {
        super.testInitWithExtraHeaders();
    }

    @Test
    @Override
    public void testInitWithCustomParams() {
        super.testInitWithCustomParams();
    }

    @Test
    @Override
    public void testEmbedQuerySuccessEmbeddingFormat() throws Exception {
        super.testEmbedQuerySuccessEmbeddingFormat();
    }

    @Test
    @Override
    public void testEmbedQuerySuccessEmbeddingBase64() throws Exception {
        super.testEmbedQuerySuccessEmbeddingBase64();
    }

    @Test
    @Override
    public void testEmbedQuerySuccessEmbeddingsFormat() throws Exception {
        super.testEmbedQuerySuccessEmbeddingsFormat();
    }

    @Test
    @Override
    public void testEmbedQuerySuccessDataFormat() throws Exception {
        super.testEmbedQuerySuccessDataFormat();
    }

    @Test
    @Override
    public void testEmbedQueryEmptyText() {
        super.testEmbedQueryEmptyText();
    }

    @Test
    @Override
    public void testEmbedQueryRetryOnFailure() throws Exception {
        super.testEmbedQueryRetryOnFailure();
    }

    @Test
    @Override
    public void testEmbedQueryMaxRetriesExceeded() throws Exception {
        super.testEmbedQueryMaxRetriesExceeded();
    }

    @Test
    @Override
    public void testEmbedQueryInvalidResponseFormat() throws Exception {
        super.testEmbedQueryInvalidResponseFormat();
    }

    @Test
    @Override
    public void testEmbedDocumentsSuccess() throws Exception {
        super.testEmbedDocumentsSuccess();
    }

    @Test
    @Override
    public void testEmbedDocumentsWithBatching() throws Exception {
        super.testEmbedDocumentsWithBatching();
    }

    @Test
    @Override
    public void testEmbedDocumentsRespectsMaxBatchSize() throws Exception {
        super.testEmbedDocumentsRespectsMaxBatchSize();
    }

    @Test
    @Override
    public void testEmbedDocumentsEmptyList() {
        super.testEmbedDocumentsEmptyList();
    }

    @Test
    @Override
    public void testEmbedDocumentsWithEmptyTexts() {
        super.testEmbedDocumentsWithEmptyTexts();
    }

    @Test
    @Override
    public void testEmbedDocumentsAllEmpty() {
        super.testEmbedDocumentsAllEmpty();
    }

    @Test
    @Override
    public void testDimensionFromResponse() throws Exception {
        super.testDimensionFromResponse();
    }
}
