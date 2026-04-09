/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.foundation.store.base_embedding;

import java.util.List;

/**
 * Embedding model abstract base class.
 * <p>
 * Mirrors Python's {@code Embedding} ABC.
 * Java synchronous equivalent of Python's async interface.
 */
public abstract class Embedding {

    /**
     * Embed query text into a vector.
     *
     * @param text the query text
     * @return embedding vector
     */
    public abstract List<Float> embedQuery(String text);

    /**
     * Embed document texts into vectors.
     *
     * @param texts     the document texts
     * @param batchSize optional batch size (null for default)
     * @return list of embedding vectors
     */
    public abstract List<List<Float>> embedDocuments(List<String> texts, Integer batchSize);

    /**
     * Embed document texts into vectors with default batch size.
     */
    public List<List<Float>> embedDocuments(List<String> texts) {
        return embedDocuments(texts, null);
    }

    /**
     * Return the embedding dimension.
     */
    public abstract int getDimension();
}
