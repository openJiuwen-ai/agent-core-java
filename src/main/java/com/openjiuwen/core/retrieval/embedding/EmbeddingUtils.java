/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.retrieval.common.RetrievalExceptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Helpers for embedding model implementations.
 */
public final class EmbeddingUtils {

    private EmbeddingUtils() {
    }

    public static List<Float> parseBase64Embedding(String base64Embedding) {
        if (base64Embedding == null || base64Embedding.isBlank()) {
            throw RetrievalExceptions.validation("base64 embedding is required");
        }
        byte[] bytes = Base64.getDecoder().decode(base64Embedding);
        if (bytes.length % Float.BYTES != 0) {
            throw RetrievalExceptions.validation("base64 embedding length is not aligned to float32");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Float> values = new ArrayList<>(bytes.length / Float.BYTES);
        while (buffer.remaining() >= Float.BYTES) {
            values.add(buffer.getFloat());
        }
        return values;
    }
}
