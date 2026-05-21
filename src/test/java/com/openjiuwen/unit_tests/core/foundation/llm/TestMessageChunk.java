/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.llm;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for message chunk handling.
 * <p>
 * Mirrors Python's {@code test_message_chunk.py} from
 * {@code tests/unit_tests/core/foundation/llm/test_message_chunk.py}.
 * Tests streaming message chunk creation and aggregation.
 */
class TestMessageChunk {

    // ---------------------------------------------------------------------------
    // Tests - Level 0 (Basic existence checks)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level0")
    void testAssistantMessageChunkClassExists() {
        assertNotNull(AssistantMessageChunk.class);
    }

    @Test
    @Tag("level0")
    void testBaseMessageClassExists() {
        assertNotNull(BaseMessage.class);
    }

    @Test
    @Tag("level0")
    void testUserMessageClassExists() {
        assertNotNull(UserMessage.class);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 1 (Chunk creation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level1")
    void testAssistantMessageChunkCreation() {
        AssistantMessageChunk chunk = new AssistantMessageChunk("test content");
        assertNotNull(chunk);
        assertEquals("test content", chunk.getContent());
    }

    @Test
    @Tag("level1")
    void testAssistantMessageChunkRole() {
        AssistantMessageChunk chunk = new AssistantMessageChunk("test");
        assertEquals("assistant", chunk.getRole());
    }

    @Test
    @Tag("level1")
    void testEmptyChunk() {
        AssistantMessageChunk chunk = new AssistantMessageChunk("");
        assertEquals("", chunk.getContent());
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 2 (Chunk content operations)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level2")
    void testChunkContentModification() {
        AssistantMessageChunk chunk = new AssistantMessageChunk("initial");
        chunk.setContent("modified");
        assertEquals("modified", chunk.getContent());
    }

    @Test
    @Tag("level2")
    void testChunkConcatenation() {
        AssistantMessageChunk chunk1 = new AssistantMessageChunk("Hello");
        AssistantMessageChunk chunk2 = new AssistantMessageChunk(" World");
        String combined = chunk1.getContent() + chunk2.getContent();
        assertEquals("Hello World", combined);
    }

    // ---------------------------------------------------------------------------
    // Tests - Level 3 (Streaming simulation)
    // ---------------------------------------------------------------------------

    @Test
    @Tag("level3")
    void testMultipleChunks() {
        java.util.List<AssistantMessageChunk> chunks = new java.util.ArrayList<>();
        String[] parts = {"Hello", ", ", "world", "!"};
        for (String part : parts) {
            chunks.add(new AssistantMessageChunk(part));
        }
        assertEquals(4, chunks.size());
    }

    @Test
    @Tag("level3")
    void testChunkAggregation() {
        java.util.List<AssistantMessageChunk> chunks = new java.util.ArrayList<>();
        chunks.add(new AssistantMessageChunk("Hello"));
        chunks.add(new AssistantMessageChunk(" World"));

        StringBuilder sb = new StringBuilder();
        for (AssistantMessageChunk chunk : chunks) {
            sb.append(chunk.getContent());
        }
        assertEquals("Hello World", sb.toString());
    }
}