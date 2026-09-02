/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local embedding based on SHA-256 hashing.
 * 
 * @since 0.1.7
 */
public class HashEmbedding implements Embedding {
    private final int dimension;
    private final int maxBatchSize;

    /**
     * HashEmbedding.
     * 
     * @since 0.1.7
     */
    public HashEmbedding() {
        this(32, 256);
    }

    /**
     * HashEmbedding.
     * 
     * @param dimension dimension
     * @param maxBatchSize maxBatchSize
     * @since 0.1.7
     */
    public HashEmbedding(int dimension, int maxBatchSize) {
        this.dimension = Math.max(1, dimension);
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    /**
     * embedQuery.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Float> embedQuery(String text) {
        return embedText(text == null ? "" : text);
    }

    /**
     * embedDocuments.
     * 
     * @param texts texts
     * @param batchSize batchSize
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<List<Float>> embedDocuments(List<?> texts, Integer batchSize) {
        List<List<Float>> result = new ArrayList<>();
        if (texts == null) {
            return result;
        }
        for (Object text : texts) {
            result.add(embedText(text == null ? "" : String.valueOf(text)));
        }
        return result;
    }

    /**
     * getDimension.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int getDimension() {
        return dimension;
    }

    /**
     * getMaxBatchSize.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    /**
     * embedText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private List<Float> embedText(String text) {
        byte[] digest = digest(text);
        List<Float> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            int unsigned = digest[i % digest.length] & 0xff;
            vector.add((unsigned / 255.0f) * 2.0f - 1.0f);
        }
        return vector;
    }

    /**
     * digest.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    private static byte[] digest(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
