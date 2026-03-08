/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic local embedding based on SHA-256 hashing.
 */
public class HashEmbedding implements Embedding {

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
    public List<Float> embedQuery(String text) {
        return embedText(text == null ? "" : text);
    }

    @Override
    public List<List<Float>> embedDocuments(List<String> texts, Integer batchSize) {
        List<List<Float>> result = new ArrayList<>();
        if (texts == null) {
            return result;
        }
        for (String text : texts) {
            result.add(embedText(text == null ? "" : text));
        }
        return result;
    }

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    private List<Float> embedText(String text) {
        byte[] digest = digest(text);
        List<Float> vector = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            int unsigned = digest[i % digest.length] & 0xff;
            vector.add((unsigned / 255.0f) * 2.0f - 1.0f);
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
