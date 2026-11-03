/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Deterministic local embedding implementation.
 *
 * <p>Mirrors Python's {@code Embedding} compatibility surface in
 * {@code openjiuwen/core/retrieval/embedding/base.py}.</p>
 */
public class HashEmbedding extends Embedding {

    private final int dimension;
    private final int maxBatchSize;

    public HashEmbedding() {
        this(32, 256);
    }

    public HashEmbedding(int dimension, int maxBatchSize) {
        this.dimension = Math.max(1, dimension);
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    @Override
    public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(embedText(text == null ? "" : text));
    }

    @Override
    public CompletableFuture<List<List<Double>>> embedDocuments(
            List<String> texts,
            Integer batchSize,
            Map<String, Object> kwargs
    ) {
        List<List<Double>> result = new ArrayList<>();
        if (texts == null) {
            return CompletableFuture.completedFuture(result);
        }
        for (String text : texts) {
            result.add(embedText(text == null ? "" : text));
        }
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    private List<Double> embedText(String text) {
        byte[] digest = digest(text);
        List<Double> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            int unsigned = digest[i % digest.length] & 0xff;
            vector.add((unsigned / 255.0d) * 2.0d - 1.0d);
        }
        return vector;
    }

    private static byte[] digest(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
