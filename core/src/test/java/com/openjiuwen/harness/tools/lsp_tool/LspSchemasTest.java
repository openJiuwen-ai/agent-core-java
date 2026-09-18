/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's schema behavior in
 * {@code openjiuwen/harness/tools/lsp_tool/_schemas.py}.
 */
class LspSchemasTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testOperationValues() throws Exception {
        assertEquals(LspOperation.WORKSPACE_SYMBOL, LspOperation.fromValue("workspaceSymbol"));
        assertEquals("\"incomingCalls\"", mapper.writeValueAsString(LspOperation.INCOMING_CALLS));
        assertNull(LspOperation.fromValue("unknown"));
    }

    @Test
    void testFindReferencesDefaults() throws Exception {
        FindReferencesInput input = mapper.readValue(
                """
                {
                  "operation": "findReferences",
                  "file_path": "src/app.py",
                  "line": 7
                }
                """,
                FindReferencesInput.class
        );
        assertEquals("findReferences", input.getOperation());
        assertEquals("src/app.py", input.getFilePath());
        assertEquals(7, input.getLine());
        assertEquals(1, input.getCharacter());
        assertTrue(input.isIncludeDeclaration());
    }

    @Test
    void testWorkspaceSymbolDefaults() throws Exception {
        WorkspaceSymbolInput input = mapper.readValue(
                """
                {
                  "operation": "workspaceSymbol"
                }
                """,
                WorkspaceSymbolInput.class
        );
        assertEquals("", input.getFilePath());
        assertEquals("", input.getQuery());
    }

    @Test
    void testOutputSnakeCaseRoundTrip() throws Exception {
        LspToolOutput output = mapper.readValue(
                """
                {
                  "operation": "documentSymbol",
                  "result": "ok",
                  "file_path": "src/app.py",
                  "result_count": 2
                }
                """,
                LspToolOutput.class
        );
        assertEquals(LspOperation.DOCUMENT_SYMBOL, output.getOperation());
        assertEquals("src/app.py", output.getFilePath());
        assertEquals(2, output.getResultCount());
    }
}
