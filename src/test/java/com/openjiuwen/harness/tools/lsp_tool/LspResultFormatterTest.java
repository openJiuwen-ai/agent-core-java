/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's formatter coverage in
 * {@code tests/unit_tests/harness/tools/test_lsp_tool.py}.
 */
class LspResultFormatterTest {

    @Test
    void symbolKindMapExposesAllTwentyFiveKinds() {
        assertEquals(25, LspResultFormatter.SYMBOL_KIND_MAP.size());
        assertEquals("File", LspResultFormatter.SYMBOL_KIND_MAP.get(1));
        assertEquals("Class", LspResultFormatter.SYMBOL_KIND_MAP.get(5));
        assertEquals("Function", LspResultFormatter.SYMBOL_KIND_MAP.get(12));
        assertEquals("TypeParameter", LspResultFormatter.SYMBOL_KIND_MAP.get(25));
    }

    @Test
    void formatLocationUsesOneBasedCoordinates() {
        String result = LspResultFormatter.formatLocation(Map.of(
                "uri", "file:///path/to/file.py",
                "range", Map.of("start", Map.of("line", 4, "character", 2))
        ));
        assertTrue(result.contains("/path/to/file.py"));
        assertTrue(result.contains(":5:3"));
    }

    @Test
    void isWindowsDrivePathRecognizesDrivePrefix() {
        assertTrue(LspResultFormatter.isWindowsDrivePath("/C:/workspace/file.py"));
    }

    @Test
    void formatUriDecodesFileUris() {
        assertEquals("/path/to/file.py", LspResultFormatter.formatUri("file:///path/to/file.py"));
        assertTrue(LspResultFormatter.formatUri("file:///path/with%20space/file.py").contains("with space"));
    }

    @Test
    void formatUriPassesThroughNonFileUris() {
        assertEquals("https://example.com/file.py", LspResultFormatter.formatUri("https://example.com/file.py"));
    }

    @Test
    void formatGoToDefinitionHandlesNullResults() {
        assertEquals("No definition found.", LspResultFormatter.formatGoToDefinition(null));
    }

    @Test
    void formatGoToDefinitionHandlesLocationResults() {
        String result = LspResultFormatter.formatGoToDefinition(Map.of(
                "uri", "file:///path/to/file.py",
                "range", Map.of("start", Map.of("line", 0, "character", 0))
        ));
        assertTrue(result.contains("Defined in"));
        assertTrue(result.contains("/path/to/file.py"));
    }

    @Test
    void formatFindReferencesHandlesEmptyResults() {
        assertEquals("No references found.", LspResultFormatter.formatFindReferences(List.of()));
    }

    @Test
    void formatFindReferencesGroupsByFile() {
        String result = LspResultFormatter.formatFindReferences(List.of(
                Map.of("uri", "file:///a.py", "range", Map.of("start", Map.of("line", 0, "character", 0))),
                Map.of("uri", "file:///a.py", "range", Map.of("start", Map.of("line", 1, "character", 0))),
                Map.of("uri", "file:///b.py", "range", Map.of("start", Map.of("line", 2, "character", 0)))
        ));
        assertTrue(result.contains("/a.py:"));
        assertTrue(result.contains("/b.py:"));
        assertTrue(result.contains("1:1"));
        assertTrue(result.contains("3:1"));
    }

    @Test
    void groupByFilePreservesInsertionOrder() {
        Map<String, List<Map<String, Object>>> grouped = LspResultFormatter.groupByFile(List.of(
                Map.of("uri", "file:///a.py", "range", Map.of("start", Map.of("line", 0, "character", 0))),
                Map.of("uri", "file:///b.py", "range", Map.of("start", Map.of("line", 1, "character", 0))),
                Map.of("uri", "file:///a.py", "range", Map.of("start", Map.of("line", 2, "character", 0)))
        ));
        assertEquals(List.of("/a.py", "/b.py"), grouped.keySet().stream().toList());
        assertEquals(2, grouped.get("/a.py").size());
    }

    @Test
    void formatDocumentSymbolHandlesEmptyResults() {
        assertEquals("No symbols found.", LspResultFormatter.formatDocumentSymbol(List.of()));
    }

    @Test
    void formatDocumentSymbolSupportsFlatSymbols() {
        String flat = LspResultFormatter.formatDocumentSymbol(List.of(
                Map.of(
                        "name", "MyClass",
                        "kind", 5,
                        "location", Map.of(
                                "uri", "file:///path/to/file.py",
                                "range", Map.of("start", Map.of("line", 0, "character", 0))
                        ),
                        "containerName", ""
                )
        ));
        assertTrue(flat.contains("Class MyClass"));
    }

    @Test
    void formatDocumentSymbolSupportsSymbolTrees() {
        String tree = LspResultFormatter.formatDocumentSymbol(List.of(
                Map.of(
                        "name", "MyClass",
                        "kind", 5,
                        "detail", "public",
                        "children", List.of(Map.of("name", "my_method", "kind", 6, "detail", "", "children", List.of()))
                )
        ));
        assertTrue(tree.contains("Class MyClass - public"));
        assertTrue(tree.contains("Method my_method"));
    }

    @Test
    void formatWorkspaceSymbolHandlesEmptyResults() {
        assertEquals("No symbols found.", LspResultFormatter.formatWorkspaceSymbol(List.of()));
    }

    @Test
    void formatWorkspaceSymbolMirrorsPythonStrings() {
        String workspace = LspResultFormatter.formatWorkspaceSymbol(List.of(
                Map.of(
                        "name", "my_func",
                        "kind", 12,
                        "location", Map.of(
                                "uri", "file:///path/to/file.py",
                                "range", Map.of("start", Map.of("line", 3, "character", 0))
                        ),
                        "containerName", "MyClass"
                )
        ));
        assertTrue(workspace.contains("Function MyClass.my_func"));
    }

    @Test
    void formatPrepareCallHierarchyHandlesEmptyResults() {
        assertEquals("No call hierarchy available.", LspResultFormatter.formatPrepareCallHierarchy(List.of()));
    }

    @Test
    void formatPrepareCallHierarchyFormatsSingleItems() {
        String hierarchy = LspResultFormatter.formatPrepareCallHierarchy(List.of(
                Map.of(
                        "name", "my_func",
                        "uri", "file:///path/to/file.py",
                        "range", Map.of("start", Map.of("line", 0, "character", 0)),
                        "originSelectionRange", Map.of("start", Map.of("line", 0, "character", 0))
                )
        ));
        assertTrue(hierarchy.contains("my_func"));
        assertTrue(hierarchy.contains("1:"));
    }

    @Test
    void formatPrepareCallHierarchyFormatsMultipleItems() {
        String hierarchy = LspResultFormatter.formatPrepareCallHierarchy(List.of(
                Map.of(
                        "name", "alpha",
                        "uri", "file:///path/to/file.py",
                        "range", Map.of("start", Map.of("line", 0, "character", 0))
                ),
                Map.of(
                        "name", "beta",
                        "uri", "file:///path/to/file.py",
                        "range", Map.of("start", Map.of("line", 2, "character", 0))
                )
        ));
        assertTrue(hierarchy.contains("2 call hierarchy items:"));
        assertTrue(hierarchy.contains("alpha"));
        assertTrue(hierarchy.contains("beta"));
    }

    @Test
    void formatIncomingCallsHandlesEmptyResults() {
        assertEquals("No incoming calls found.", LspResultFormatter.formatIncomingCalls(List.of()));
    }

    @Test
    void formatIncomingCallsFormatsPopulatedResults() {
        String incoming = LspResultFormatter.formatIncomingCalls(List.of(
                Map.of(
                        "from", Map.of(
                                "uri", "file:///path/to/file.py",
                                "range", Map.of("start", Map.of("line", 0, "character", 0))
                        ),
                        "fromRanges", List.of(Map.of("start", Map.of("line", 1, "character", 4)))
                )
        ));
        assertTrue(incoming.contains("call site 2:5"));
    }

    @Test
    void formatOutgoingCallsHandlesEmptyResults() {
        assertEquals("No outgoing calls found.", LspResultFormatter.formatOutgoingCalls(List.of()));
    }

    @Test
    void formatOutgoingCallsFormatsPopulatedResults() {
        String outgoing = LspResultFormatter.formatOutgoingCalls(List.of(
                Map.of(
                        "to", Map.of(
                                "name", "callee",
                                "uri", "file:///path/to/file.py",
                                "range", Map.of("start", Map.of("line", 3, "character", 0))
                        ),
                        "fromRanges", List.of(Map.of("start", Map.of("line", 1, "character", 2)))
                )
        ));
        assertTrue(outgoing.contains("callee (/path/to/file.py:4)"));
    }

    @Test
    void formatResultRoutesAllEightOperations() {
        for (LspOperation operation : LspOperation.values()) {
            assertTrue(LspResultFormatter.formatResult(operation, null) instanceof String);
        }
    }
}
