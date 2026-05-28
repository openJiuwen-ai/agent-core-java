/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for embedding providers.
 * <p>
 * Mirrors Python's {@code EmbeddingProvider} ABC from
 * {@code core/memory/lite/embeddings.py}.
 */
public abstract class EmbeddingProvider {

    protected String id = "base";
    protected String model = "base";
    protected int dims = 0;

    public String getId() {
        return id;
    }

    public String getModel() {
        return model;
    }

    public int getDims() {
        return dims;
    }

    /**
     * Generate embedding for a query text.
     */
    public abstract CompletableFuture<List<Float>> embedQuery(String text);

    /**
     * Generate embeddings for multiple documents.
     */
    public abstract CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts);
}
