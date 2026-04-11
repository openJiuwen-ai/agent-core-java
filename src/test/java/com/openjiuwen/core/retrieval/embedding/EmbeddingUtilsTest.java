/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.core.retrieval.embedding;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingUtilsTest {

    @Test
    void parseBase64EmbeddingDecodesFloat32Values() {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.putFloat(-2.5f);
        buffer.putFloat(3.25f);

        String encoded = Base64.getEncoder().encodeToString(buffer.array());
        List<Float> values = EmbeddingUtils.parseBase64Embedding(encoded);

        assertEquals(List.of(1.0f, -2.5f, 3.25f), values);
    }

    @Test
    void parseBase64EmbeddingRejectsBlankInput() {
        assertThrows(BaseError.class, () -> EmbeddingUtils.parseBase64Embedding(" "));
    }
}
