/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.lite;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for embedding providers.
 * <p>
 * Mirrors Python's {@code EmbeddingProvider} in
 * {@code openjiuwen/core/memory/lite/embeddings.py}.
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

    public abstract CompletableFuture<List<Float>> embedQuery(String text);

    public abstract CompletableFuture<List<List<Float>>> embedDocuments(List<String> texts);
}
