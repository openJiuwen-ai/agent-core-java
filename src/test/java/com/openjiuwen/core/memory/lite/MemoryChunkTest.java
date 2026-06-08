package com.openjiuwen.core.memory.lite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemoryChunkTest {

    @Test
    void constructorMatchesPythonDataclassFields() {
        MemoryChunk chunk = new MemoryChunk("hello", 2, 5);

        assertEquals("hello", chunk.getText());
        assertEquals(2, chunk.getStartLine());
        assertEquals(5, chunk.getEndLine());
    }
}
