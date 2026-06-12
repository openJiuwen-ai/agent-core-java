/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Mock embedding provider for testing.
 * <p>
 * Mirrors Python's {@code MockEmbeddingProvider} in
 * {@code openjiuwen/core/memory/lite/embeddings.py}.
 */
public class MockEmbeddingProvider extends EmbeddingProvider {

    private static final int MOCK_DIMS = 128;

    public MockEmbeddingProvider() {
        this.id = "mock";
        this.model = "mock";
        this.dims = MOCK_DIMS;
    }

    @Override
    public CompletableFuture<List<Float>> embedQuery(String text) {
        return CompletableFuture.completedFuture(generateEmbedding(text));
    }

    @Override
    public CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts) {
        List<List<Float>> embeddings = new ArrayList<>();
        for (String text : texts) {
            embeddings.add(generateEmbedding(text));
        }
        return CompletableFuture.completedFuture(embeddings);
    }

    private List<Float> generateEmbedding(String text) {
        Random random = new Random(seedFromText(text == null ? "" : text));
        List<Float> embedding = new ArrayList<>(MOCK_DIMS);
        for (int index = 0; index < MOCK_DIMS; index++) {
            embedding.add(-1.0f + (2.0f * random.nextFloat()));
        }
        return embedding;
    }

    private long seedFromText(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(text.getBytes());
            return ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
        } catch (Exception exception) {
            return text.hashCode();
        }
    }
}
