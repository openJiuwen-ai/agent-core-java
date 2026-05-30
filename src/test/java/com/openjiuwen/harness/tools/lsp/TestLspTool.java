/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

import com.openjiuwen.harness.lsp.query.LspCallHierarchyItem;
import com.openjiuwen.harness.lsp.query.LspIncomingCall;
import com.openjiuwen.harness.lsp.query.LspLocation;
import com.openjiuwen.harness.lsp.query.LspOutgoingCall;
import com.openjiuwen.harness.lsp.query.LspRange;
import com.openjiuwen.harness.lsp.query.LspSymbol;
import com.openjiuwen.harness.tools.LspTool;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests: LSP Tool and the eight LSP operations.
 *
 * <p>Mirrors Python's {@code test_lsp_tool.py} in
 * {@code tests.unit_tests.harness.tools}.
 */
class TestLspTool {

    @Nested
    class TestBuildLspTool {

        @Test
        void buildLspToolReturnsDict() {
            assertInstanceOf(Map.class, LspTool.buildInputSchema());
        }

        @Test
        void buildLspToolHasRequiredFields() {
            LspTool tool = new LspTool();

            assertEquals("lsp", tool.getCard().getName());
            assertNotNull(tool.getCard().getDescription());
            assertFalse(tool.getCard().getInputParams().isEmpty());
        }

        @Test
        void buildLspToolNameIsLsp() {
            assertEquals("lsp", new LspTool().getCard().getName());
        }

        @Test
        void buildLspToolInputSchemaHasOperationEnum() {
            Map<String, Object> schema = LspTool.buildInputSchema();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            @SuppressWarnings("unchecked")
            Map<String, Object> operation = (Map<String, Object>) properties.get("operation");
            @SuppressWarnings("unchecked")
            List<String> opEnum = (List<String>) operation.get("enum");

            assertTrue(opEnum.contains("goToDefinition"));
            assertTrue(opEnum.contains("findReferences"));
            assertTrue(opEnum.contains("documentSymbol"));
            assertTrue(opEnum.contains("workspaceSymbol"));
            assertTrue(opEnum.contains("goToImplementation"));
            assertTrue(opEnum.contains("prepareCallHierarchy"));
            assertTrue(opEnum.contains("incomingCalls"));
            assertTrue(opEnum.contains("outgoingCalls"));
            assertEquals(8, opEnum.size());
        }

        @Test
        void buildLspToolRequiredFields() {
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) LspTool.buildInputSchema().get("required");

            assertTrue(required.contains("operation"));
            assertTrue(required.contains("file_path"));
        }
    }

    @Nested
    class TestResolvePath {

        @Test
        void resolvePathHandlesRelativePath(@TempDir Path tempDir) {
            String resolved = LspToolSupport.resolvePath("src/App.java", tempDir);

            assertEquals(tempDir.resolve("src").resolve("App.java").normalize().toString(), resolved);
        }

        @Test
        void resolvePathHandlesAbsolutePath(@TempDir Path tempDir) {
            Path file = tempDir.resolve("App.java");

            String resolved = LspToolSupport.resolvePath(file.toString(), tempDir);

            assertEquals(file.normalize().toString(), resolved);
        }

        @Test
        void resolvePathHandlesNullPath(@TempDir Path tempDir) {
            assertNull(LspToolSupport.resolvePath(null, tempDir));
        }
    }

    @Nested
    class TestOperationToMethod {

        @Test
        void goToDefinitionMapsToTextDocumentDefinition() {
            assertEquals("textDocument/definition",
                    LspToolSupport.operationToMethod(LspOperation.GO_TO_DEFINITION));
        }

        @Test
        void findReferencesMapsToTextDocumentReferences() {
            assertEquals("textDocument/references",
                    LspToolSupport.operationToMethod(LspOperation.FIND_REFERENCES));
        }

        @Test
        void documentSymbolMapsToTextDocumentDocumentSymbol() {
            assertEquals("textDocument/documentSymbol",
                    LspToolSupport.operationToMethod(LspOperation.DOCUMENT_SYMBOL));
        }

        @Test
        void workspaceSymbolMapsToWorkspaceSymbol() {
            assertEquals("workspace/symbol",
                    LspToolSupport.operationToMethod(LspOperation.WORKSPACE_SYMBOL));
        }

        @Test
        void goToImplementationMapsToTextDocumentImplementation() {
            assertEquals("textDocument/implementation",
                    LspToolSupport.operationToMethod(LspOperation.GO_TO_IMPLEMENTATION));
        }

        @Test
        void prepareCallHierarchyMapsToTextDocumentPrepareCallHierarchy() {
            assertEquals("textDocument/prepareCallHierarchy",
                    LspToolSupport.operationToMethod(LspOperation.PREPARE_CALL_HIERARCHY));
        }

        @Test
        void incomingCallsMapsToCallHierarchyIncomingCalls() {
            assertEquals("callHierarchy/incomingCalls",
                    LspToolSupport.operationToMethod(LspOperation.INCOMING_CALLS));
        }

        @Test
        void outgoingCallsMapsToCallHierarchyOutgoingCalls() {
            assertEquals("callHierarchy/outgoingCalls",
                    LspToolSupport.operationToMethod(LspOperation.OUTGOING_CALLS));
        }
    }

    @Nested
    class TestNeedsGitignoreFilter {

        @Test
        void needsGitignoreFilterForWorkspaceSymbol() {
            assertTrue(LspToolSupport.needsGitignoreFilter(LspOperation.WORKSPACE_SYMBOL));
        }

        @Test
        void doesNotNeedGitignoreFilterForDocumentSymbol() {
            assertFalse(LspToolSupport.needsGitignoreFilter(LspOperation.DOCUMENT_SYMBOL));
        }
    }

    @Nested
    class TestFormatterFunctions {

        @Test
        void formatLocationReturnsCorrectString() {
            assertEquals("/tmp/Foo.java:10:5", LspToolSupport.formatLocation(location("/tmp/Foo.java", 10, 5)));
        }

        @Test
        void formatUriHandlesFileUri() {
            assertEquals("/tmp/a b.java", LspToolSupport.formatUri("file:///tmp/a%20b.java"));
        }

        @Test
        void formatGoToDefinitionReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.GO_TO_DEFINITION,
                    location("/tmp/Foo.java", 10, 5));

            assertEquals("Defined in /tmp/Foo.java:10:5", result);
        }

        @Test
        void formatFindReferencesReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.FIND_REFERENCES,
                    List.of(location("/tmp/Foo.java", 10, 5), location("/tmp/Foo.java", 20, 3)));

            assertEquals("/tmp/Foo.java:\n  10:5\n  20:3", result);
        }

        @Test
        void formatDocumentSymbolReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.DOCUMENT_SYMBOL,
                    List.of(new LspSymbol("Foo", "Class", location("/tmp/Foo.java", 1, 1))));

            assertEquals("/tmp/Foo.java:1: Class Foo", result);
        }

        @Test
        void formatWorkspaceSymbolReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.WORKSPACE_SYMBOL,
                    List.of(new LspSymbol("bar", "Function", location("/tmp/Foo.java", 5, 1), "Foo", "")));

            assertEquals("/tmp/Foo.java:\n  5: Function Foo.bar", result);
        }

        @Test
        void formatPrepareCallHierarchyReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.PREPARE_CALL_HIERARCHY,
                    List.of(new LspCallHierarchyItem("work", location("/tmp/Foo.java", 7, 2))));

            assertEquals("/tmp/Foo.java:7: work", result);
        }

        @Test
        void formatIncomingCallsReturnsCorrectFormat() {
            LspCallHierarchyItem caller = new LspCallHierarchyItem("caller", location("/tmp/Foo.java", 3, 1));
            LspRange callSite = new LspRange(location("/tmp/Foo.java", 8, 4), location("/tmp/Foo.java", 8, 8));

            String result = LspToolSupport.format(
                    LspOperation.INCOMING_CALLS,
                    List.of(new LspIncomingCall(caller, List.of(callSite))));

            assertEquals("/tmp/Foo.java:\n  /tmp/Foo.java:3 -> call site 8:4", result);
        }

        @Test
        void formatOutgoingCallsReturnsCorrectFormat() {
            LspCallHierarchyItem callee = new LspCallHierarchyItem("callee", location("/tmp/Bar.java", 11, 1));
            LspRange callSite = new LspRange(location("/tmp/Foo.java", 8, 4), location("/tmp/Foo.java", 8, 8));

            String result = LspToolSupport.format(
                    LspOperation.OUTGOING_CALLS,
                    List.of(new LspOutgoingCall(callee, List.of(callSite))));

            assertEquals("  call site 8:4 -> callee (/tmp/Bar.java:11)", result);
        }
    }

    @Test
    void symbolKindMapContainsExpectedKinds() {
        assertEquals("Class", LspToolSupport.symbolKindMap().get(5));
        assertEquals("Function", LspToolSupport.symbolKindName(12));
        assertEquals("?", LspToolSupport.symbolKindName(999));
    }

    private LspLocation location(String filePath, int line, int character) {
        return new LspLocation(filePath, line, character);
    }
}
