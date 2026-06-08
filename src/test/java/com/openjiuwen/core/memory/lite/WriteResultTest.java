package com.openjiuwen.core.memory.lite;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WriteResultTest {

    @Test
    void toDictMatchesPythonConditionalFields() {
        WriteResult result = new WriteResult(
                true,
                "a.md",
                WriteMode.CREATE,
                true,
                List.of("b.md"),
                "note",
                null,
                "user"
        );

        Map<String, Object> out = result.toDict();

        assertEquals(true, out.get("success"));
        assertEquals("a.md", out.get("path"));
        assertEquals("create", out.get("mode"));
        assertEquals("user", out.get("type"));
        assertEquals(true, out.get("conflict_detected"));
        assertEquals(List.of("b.md"), out.get("conflicting_files"));
        assertEquals("note", out.get("note"));
        assertFalse(out.containsKey("error"));
    }
}
