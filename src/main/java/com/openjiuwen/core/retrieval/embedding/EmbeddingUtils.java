/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Mirrors Python's {@code parse_base64_embedding} in
 * {@code openjiuwen/core/retrieval/embedding/utils.py}.
 */
public final class EmbeddingUtils {

    private EmbeddingUtils() {
    }

    public static List<Float> parseBase64Embedding(String base64Embedding) {
        byte[] decodedBytes = Base64.getDecoder().decode(base64Embedding);
        ByteBuffer buffer = ByteBuffer.wrap(decodedBytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Float> values = new ArrayList<>(decodedBytes.length / Float.BYTES);
        while (buffer.remaining() >= Float.BYTES) {
            values.add(buffer.getFloat());
        }
        return values;
    }
}
