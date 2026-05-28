/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Mock embedding provider for testing and development.
 * <p>
 * Mirrors Python's {@code MockEmbeddingProvider} from
 * {@code core/memory/lite/embeddings.py}.
 */
public class MockEmbeddingProvider extends EmbeddingProvider {

    private static final int DEFAULT_DIMS = 1024;
    private final Random random = new Random(42);

    public MockEmbeddingProvider() {
        this.id = "mock";
        this.model = "mock";
        this.dims = DEFAULT_DIMS;
    }

    public MockEmbeddingProvider(int dims) {
        this.id = "mock";
        this.model = "mock";
        this.dims = dims;
    }

    @Override
    public CompletableFuture<List<Float>> embedQuery(String text) {
        return CompletableFuture.completedFuture(randomEmbedding());
    }

    @Override
    public CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            result.add(randomEmbedding());
        }
        return CompletableFuture.completedFuture(result);
    }

    private List<Float> randomEmbedding() {
        List<Float> vec = new ArrayList<>(dims);
        for (int i = 0; i < dims; i++) {
            vec.add(random.nextFloat());
        }
        return vec;
    }
}
