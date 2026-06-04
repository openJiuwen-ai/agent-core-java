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
import com.openjiuwen.harness.lsp.servers.LspRootResolver;
import com.openjiuwen.harness.lsp.servers.LspRootResolvers;
import com.openjiuwen.harness.tools.LspTool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
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

        @Test
        void relativePathCannotEscapeWorkspace(@TempDir Path tempDir) {
            assertNull(LspToolSupport.resolvePath("../outside.py", tempDir));
        }

        @Test
        void invalidFilePathReturnsNull(@TempDir Path tempDir) {
            assertNull(LspToolSupport.resolvePath(String.valueOf((char) 0) + "bad", tempDir));
        }
    }

    @Nested
    class TestLspOperationEnum {

        @Test
        void allEightOperationsPresent() {
            assertEquals("goToDefinition", LspOperation.GO_TO_DEFINITION.getValue());
            assertEquals("findReferences", LspOperation.FIND_REFERENCES.getValue());
            assertEquals("documentSymbol", LspOperation.DOCUMENT_SYMBOL.getValue());
            assertEquals("workspaceSymbol", LspOperation.WORKSPACE_SYMBOL.getValue());
            assertEquals("goToImplementation", LspOperation.GO_TO_IMPLEMENTATION.getValue());
            assertEquals("prepareCallHierarchy", LspOperation.PREPARE_CALL_HIERARCHY.getValue());
            assertEquals("incomingCalls", LspOperation.INCOMING_CALLS.getValue());
            assertEquals("outgoingCalls", LspOperation.OUTGOING_CALLS.getValue());
        }
    }

    @Nested
    class TestInputSchemaContracts {

        @Test
        void goToDefinitionValidInput() {
            assertEquals(LspOperation.GO_TO_DEFINITION, LspOperation.fromValue("goToDefinition"));
            assertTrue(operationEnum().contains("goToDefinition"));
        }

        @Test
        void goToDefinitionDefaultOperationAutoFilled() {
            assertEquals(LspOperation.GO_TO_DEFINITION, LspOperation.fromValue("goToDefinition"));
        }

        @Test
        void lineMustBeGeOne() {
            assertEquals(1, property("line").get("minimum"));
        }

        @Test
        void characterMustBeGeOne() {
            assertEquals(1, property("character").get("minimum"));
        }

        @Test
        void findReferencesValidInput() {
            assertEquals(LspOperation.FIND_REFERENCES, LspOperation.fromValue("findReferences"));
            assertTrue(operationEnum().contains("findReferences"));
        }

        @Test
        void findReferencesIncludeDeclarationFalse() {
            assertEquals("boolean", property("include_declaration").get("type"));
        }

        @Test
        void documentSymbolValidInput() {
            assertEquals(LspOperation.DOCUMENT_SYMBOL, LspOperation.fromValue("documentSymbol"));
            assertTrue(operationEnum().contains("documentSymbol"));
        }

        @Test
        void documentSymbolFilePathRequired() {
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) LspTool.buildInputSchema().get("required");

            assertTrue(required.contains("file_path"));
        }

        @Test
        void workspaceSymbolValidInput() {
            assertEquals(LspOperation.WORKSPACE_SYMBOL, LspOperation.fromValue("workspaceSymbol"));
            assertTrue(operationEnum().contains("workspaceSymbol"));
        }

        @Test
        void workspaceSymbolEmptyQueryAllowed() {
            assertEquals("string", property("query").get("type"));
        }

        @Test
        void prepareCallHierarchyValid() {
            assertEquals(LspOperation.PREPARE_CALL_HIERARCHY, LspOperation.fromValue("prepareCallHierarchy"));
        }

        @Test
        void incomingCallsValid() {
            assertEquals(LspOperation.INCOMING_CALLS, LspOperation.fromValue("incomingCalls"));
        }

        @Test
        void outgoingCallsValid() {
            assertEquals(LspOperation.OUTGOING_CALLS, LspOperation.fromValue("outgoingCalls"));
        }

        @Test
        void discriminatedUnionGoToDefinition() {
            assertEquals(LspOperation.GO_TO_DEFINITION, LspOperation.fromValue("goToDefinition"));
        }

        @Test
        void discriminatedUnionWorkspaceSymbol() {
            assertEquals(LspOperation.WORKSPACE_SYMBOL, LspOperation.fromValue("workspaceSymbol"));
        }

        @Test
        void normalizeOperationAcceptsPythonSnakeCaseAliases() {
            assertEquals(LspOperation.GO_TO_DEFINITION, LspTool.normalizeOperation("goto_definition"));
            assertEquals(LspOperation.FIND_REFERENCES, LspTool.normalizeOperation("find_references"));
            assertEquals(LspOperation.DOCUMENT_SYMBOL, LspTool.normalizeOperation("document_symbol"));
            assertEquals(LspOperation.PREPARE_CALL_HIERARCHY, LspTool.normalizeOperation("prepare_call_hierarchy"));
            assertEquals(LspOperation.OUTGOING_CALLS, LspTool.normalizeOperation("outgoing_calls"));
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

        @Test
        void needsFilterForDefinitionReferenceImplementationAndWorkspace() {
            assertTrue(LspToolSupport.needsGitignoreFilter(LspOperation.FIND_REFERENCES));
            assertTrue(LspToolSupport.needsGitignoreFilter(LspOperation.GO_TO_DEFINITION));
            assertTrue(LspToolSupport.needsGitignoreFilter(LspOperation.GO_TO_IMPLEMENTATION));
            assertTrue(LspToolSupport.needsGitignoreFilter(LspOperation.WORKSPACE_SYMBOL));
        }

        @Test
        void noFilterForDocumentAndCallHierarchyOperations() {
            assertFalse(LspToolSupport.needsGitignoreFilter(LspOperation.DOCUMENT_SYMBOL));
            assertFalse(LspToolSupport.needsGitignoreFilter(LspOperation.PREPARE_CALL_HIERARCHY));
            assertFalse(LspToolSupport.needsGitignoreFilter(LspOperation.INCOMING_CALLS));
            assertFalse(LspToolSupport.needsGitignoreFilter(LspOperation.OUTGOING_CALLS));
        }
    }

    @Nested
    class TestFormatterFunctions {

        @Test
        void routesAllEightOperationsReturnString() {
            for (LspOperation operation : LspOperation.values()) {
                assertNotNull(LspToolSupport.format(operation, null));
            }
        }

        @Test
        void formatGoToDefinitionNoResult() {
            assertEquals("No definition found.", LspToolSupport.format(LspOperation.GO_TO_DEFINITION, null));
        }

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
        void formatFindReferencesNoResult() {
            assertEquals("No references found.", LspToolSupport.format(LspOperation.FIND_REFERENCES, List.of()));
        }

        @Test
        void formatFindReferencesGroupsMultipleFiles() {
            String result = LspToolSupport.format(
                    LspOperation.FIND_REFERENCES,
                    List.of(location("/a.py", 1, 1), location("/b.py", 3, 1)));

            assertTrue(result.contains("/a.py:"));
            assertTrue(result.contains("/b.py:"));
            assertTrue(result.contains("3:1"));
        }

        @Test
        void formatDocumentSymbolReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.DOCUMENT_SYMBOL,
                    List.of(new LspSymbol("Foo", "Class", location("/tmp/Foo.java", 1, 1))));

            assertEquals("/tmp/Foo.java:1: Class Foo", result);
        }

        @Test
        void formatDocumentSymbolNoResult() {
            assertEquals("No symbols found.", LspToolSupport.format(LspOperation.DOCUMENT_SYMBOL, List.of()));
        }

        @Test
        void formatDocumentSymbolTree() {
            LspSymbol child = new LspSymbol("my_method", "Method", location("/tmp/Foo.java", 2, 1),
                    "MyClass", "", List.of());
            LspSymbol root = new LspSymbol("MyClass", "Class", location("/tmp/Foo.java", 1, 1),
                    "", "public", List.of(child));

            String result = LspToolSupport.format(LspOperation.DOCUMENT_SYMBOL, List.of(root));

            assertTrue(result.contains("Class MyClass - public"));
            assertTrue(result.contains("Method my_method"));
        }

        @Test
        void formatWorkspaceSymbolReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.WORKSPACE_SYMBOL,
                    List.of(new LspSymbol("bar", "Function", location("/tmp/Foo.java", 5, 1), "Foo", "")));

            assertEquals("/tmp/Foo.java:\n  5: Function Foo.bar", result);
        }

        @Test
        void formatWorkspaceSymbolNoResult() {
            assertEquals("No symbols found.", LspToolSupport.format(LspOperation.WORKSPACE_SYMBOL, List.of()));
        }

        @Test
        void formatPrepareCallHierarchyReturnsCorrectFormat() {
            String result = LspToolSupport.format(
                    LspOperation.PREPARE_CALL_HIERARCHY,
                    List.of(new LspCallHierarchyItem("work", location("/tmp/Foo.java", 7, 2))));

            assertEquals("/tmp/Foo.java:7: work", result);
        }

        @Test
        void formatPrepareCallHierarchyNoResult() {
            assertEquals("No call hierarchy available.",
                    LspToolSupport.format(LspOperation.PREPARE_CALL_HIERARCHY, List.of()));
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
        void formatIncomingCallsNoResult() {
            assertEquals("No incoming calls found.", LspToolSupport.format(LspOperation.INCOMING_CALLS, List.of()));
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

        @Test
        void formatOutgoingCallsNoResult() {
            assertEquals("No outgoing calls found.", LspToolSupport.format(LspOperation.OUTGOING_CALLS, List.of()));
        }
    }

    @Test
    void symbolKindMapContainsExpectedKinds() {
        assertEquals(25, LspToolSupport.symbolKindMap().size());
        assertEquals("File", LspToolSupport.symbolKindMap().get(1));
        assertEquals("Class", LspToolSupport.symbolKindMap().get(5));
        assertEquals("Function", LspToolSupport.symbolKindName(12));
        assertEquals("TypeParameter", LspToolSupport.symbolKindMap().get(25));
        assertEquals("?", LspToolSupport.symbolKindName(999));
    }

    @Nested
    class TestToolInvocationEdges {

        @Test
        void callLspToolInvalidInput() {
            ToolOutput output = (ToolOutput) new LspTool().invoke(Map.of("operation", "goToDefinition"), Map.of());

            assertFalse(output.isSuccess());
            assertTrue(output.getError().contains("goToDefinition"));
        }

        @Test
        void callLspToolNoServer() {
            ToolOutput output = (ToolOutput) new LspTool().invoke(Map.of(
                    "operation", "goToDefinition",
                    "file_path", "/path/to/unknown.xyz",
                    "line", 1,
                    "character", 1), Map.of());

            assertFalse(output.isSuccess());
        }

        @Test
        void callLspToolUnsupportedOperation() {
            ToolOutput output = (ToolOutput) new LspTool().invoke(Map.of(
                    "operation", "unknown",
                    "file_path", "/tmp/a.py"), Map.of());

            assertFalse(output.isSuccess());
            assertTrue(output.getError().contains("unsupported"));
        }
    }

    @Nested
    class TestNearestRoot {

        @Test
        void fileInCwdShouldFindRoot(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
            Path testFile = tempDir.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("pyproject.toml"), null, tempDir.toString());

            assertEquals(tempDir.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void rootWithGitAndPyprojectShouldReturnCorrectRoot(@TempDir Path tempDir) throws Exception {
            Files.createDirectory(tempDir.resolve(".git"));
            Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
            Path src = Files.createDirectory(tempDir.resolve("src"));
            Path testFile = src.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(
                    List.of("pyproject.toml"), List.of(".git", "node_modules"), tempDir.toString());

            assertEquals(tempDir.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void traverseUpwardFindsParentRoot(@TempDir Path tempDir) throws Exception {
            Path root = Files.createDirectory(tempDir.resolve("project"));
            Files.writeString(root.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
            Path module = Files.createDirectories(root.resolve("src/module"));
            Path testFile = module.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("pyproject.toml"), null, tempDir.toString());

            assertEquals(root.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void excludePatternStopsTraversal(@TempDir Path tempDir) throws Exception {
            Path parent = Files.createDirectory(tempDir.resolve("parent"));
            Files.createDirectory(parent.resolve(".venv"));
            Path testFile = parent.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(
                    List.of("pyproject.toml"), List.of(".venv"), tempDir.toString());

            assertNull(find.resolve(testFile.toString()));
        }

        @Test
        void noRootFoundReturnsNone(@TempDir Path tempDir) throws Exception {
            Path testFile = tempDir.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(
                    List.of("pyproject.toml", "setup.py", ".git"), null, tempDir.toString());

            assertNull(find.resolve(testFile.toString()));
        }

        @Test
        void invalidFilePathReturnsNone() {
            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("pyproject.toml"), null, null);

            assertNull(find.resolve(String.valueOf((char) 0) + "bad"));
        }

        @Test
        void multipleIncludePatterns(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("setup.py"), "from setuptools import setup\n");
            Path testFile = tempDir.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(
                    List.of("pyproject.toml", "setup.py", "Makefile"), null, tempDir.toString());

            assertEquals(tempDir.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void stopDirHonored(@TempDir Path tempDir) throws Exception {
            Path outer = Files.createDirectory(tempDir.resolve("outer"));
            Path inner = Files.createDirectory(outer.resolve("inner"));
            Files.writeString(inner.resolve("pyproject.toml"), "[project]\nname = 'inner'\n");
            Path src = Files.createDirectory(inner.resolve("src"));
            Path testFile = src.resolve("test.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("pyproject.toml"), null, outer.toString());

            assertEquals(inner.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void stopDirWithoutIncludeReturnsNone(@TempDir Path tempDir) throws Exception {
            Path stop = Files.createDirectory(tempDir.resolve("workspace"));
            Files.writeString(stop.resolve("random.txt"), "not a project\n");
            Path testFile = stop.resolve("file.py");
            Files.writeString(testFile, "x = 1\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("pyproject.toml", "setup.py"), null,
                    stop.toString());

            assertNull(find.resolve(testFile.toString()));
        }

        @Test
        void fileDirectlyInRoot(@TempDir Path tempDir) throws Exception {
            Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"test\"}");
            Path testFile = tempDir.resolve("index.js");
            Files.writeString(testFile, "const x = 1;\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("package.json"), null, tempDir.toString());

            assertEquals(tempDir.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }

        @Test
        void nestedProjectWithIntermediateGit(@TempDir Path tempDir) throws Exception {
            Path outer = Files.createDirectory(tempDir.resolve("outer"));
            Files.createDirectory(outer.resolve(".git"));
            Path inner = Files.createDirectory(outer.resolve("inner"));
            Files.writeString(inner.resolve("package.json"), "{\"name\":\"inner\"}");
            Path testFile = inner.resolve("index.js");
            Files.writeString(testFile, "const x = 1;\n");

            LspRootResolver find = LspRootResolvers.nearestRoot(List.of("package.json"), List.of(".git"),
                    tempDir.toString());

            assertEquals(inner.toAbsolutePath().normalize().toString(), find.resolve(testFile.toString()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> property(String name) {
        Map<String, Object> schema = LspTool.buildInputSchema();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        return (Map<String, Object>) properties.get(name);
    }

    @SuppressWarnings("unchecked")
    private List<String> operationEnum() {
        return (List<String>) property("operation").get("enum");
    }

    private LspLocation location(String filePath, int line, int character) {
        return new LspLocation(filePath, line, character);
    }
}
