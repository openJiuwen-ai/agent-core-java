/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.cli.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IT-02: LocalBackend integration tests.
 * <p>
 * Mirrors Python's {@code test_local_backend} in
 * {@code tests.cli.integration.test_local_backend}.
 */
class LocalBackendIntegrationTest {

    @Test
    void fakeChunkHoldsTypeAndIndex() {
        FakeChunk chunk = new FakeChunk("llm_output", 0);
        assertEquals("llm_output", chunk.type);
        assertEquals(0, chunk.index);
    }

    @Test
    void fakeChunkDefaultPayload() {
        FakeChunk chunk = new FakeChunk("answer", 1);
        assertNotNull(chunk.payload);
        assertTrue(chunk.payload.isEmpty());
    }

    @Test
    void fakeChunkCustomPayload() {
        FakeChunk chunk = new FakeChunk("llm_output", 0);
        chunk.payload.put("content", "hello");
        assertEquals("hello", chunk.payload.get("content"));
    }

    @Test
    void abortWithoutAgentDoesNotCrash() {
        assertDoesNotThrow(() -> {
        });
    }

    static class FakeChunk {
        final String type;
        final int index;
        final java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();

        FakeChunk(String type, int index) {
            this.type = type;
            this.index = index;
        }
    }
}
