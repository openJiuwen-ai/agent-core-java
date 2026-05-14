package com.openjiuwen.harness.tools.lsp;

import com.openjiuwen.harness.lsp.query.LspCallHierarchyItem;
import com.openjiuwen.harness.lsp.query.LspLocation;
import com.openjiuwen.harness.lsp.query.LspRange;
import com.openjiuwen.harness.lsp.query.LspSymbol;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python formatter expectations from
 * {@code tests.unit_tests.harness.tools.test_lsp_tool} for the current Java LSP payload helpers.
 */
class LspToolSupportFormattingTest {

    @Test
    void documentSymbolFlatListUsesFlatFormatter() {
        LspSymbol symbol = new LspSymbol(
                "MyClass",
                "Class",
                new LspLocation("/path/to/file.py", 1, 1),
                "",
                ""
        );

        String result = LspToolSupport.toToolPayload(LspOperation.DOCUMENT_SYMBOL, "/path/to/file.py", List.of(symbol))
                .typed().getResult();

        assertTrue(result.contains("/path/to/file.py:1: Class MyClass"));
    }

    @Test
    void documentSymbolTreeUsesIndentedTreeFormatter() {
        LspSymbol child = new LspSymbol(
                "my_method",
                "Method",
                new LspLocation("/path/to/file.py", 2, 1),
                "MyClass",
                "",
                List.of()
        );
        LspSymbol root = new LspSymbol(
                "MyClass",
                "Class",
                new LspLocation("/path/to/file.py", 1, 1),
                "",
                "public",
                List.of(child)
        );

        String result = LspToolSupport.toToolPayload(LspOperation.DOCUMENT_SYMBOL, "/path/to/file.py", List.of(root))
                .typed().getResult();

        assertTrue(result.contains("Class MyClass - public"));
        assertTrue(result.contains("  Method my_method"));
    }

    @Test
    void workspaceSymbolGroupsByFileAndIncludesContainer() {
        LspSymbol symbol = new LspSymbol(
                "my_func",
                "Function",
                new LspLocation("/path/to/file.py", 4, 1),
                "MyClass",
                ""
        );

        String result = LspToolSupport.toToolPayload(LspOperation.WORKSPACE_SYMBOL, null, List.of(symbol))
                .typed().getResult();

        assertTrue(result.contains("/path/to/file.py:"));
        assertTrue(result.contains("  4: Function MyClass.my_func"));
    }

    @Test
    void prepareCallHierarchyEmptyMessageMatchesPython() {
        String result = LspToolSupport.toToolPayload(LspOperation.PREPARE_CALL_HIERARCHY, "/path/to/file.py", List.of())
                .typed().getResult();

        assertEquals("No call hierarchy available.", result);
    }

    @Test
    void prepareCallHierarchySingleItemUsesSelectionRangeLine() {
        LspLocation location = new LspLocation("/path/to/file.py", 5, 1);
        LspCallHierarchyItem item = new LspCallHierarchyItem(
                "my_func",
                "Function",
                "",
                "/path/to/file.py",
                new LspRange(location, location),
                new LspRange(location, location)
        );

        String result = LspToolSupport.toToolPayload(LspOperation.PREPARE_CALL_HIERARCHY, "/path/to/file.py", List.of(item))
                .typed().getResult();

        assertEquals("/path/to/file.py:5: my_func", result);
    }
}
