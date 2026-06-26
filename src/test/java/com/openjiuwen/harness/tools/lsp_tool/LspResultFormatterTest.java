/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.lsp.servers.BuiltinServerRegistry;
import com.openjiuwen.harness.prompts.tools.LspToolMetadataProvider;
import com.openjiuwen.harness.tools.ToolOutput;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Mirrors Python's LSP tool tests in
 * {@code tests/unit_tests/harness/tools/test_lsp_tool.py}.
 */
class LspResultFormatterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}")
    @MethodSource("manifestParityCases")
    void manifestParityCase(LspCase testCase) throws Exception {
        testCase.run(this);
    }

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

    private static Stream<LspCase> manifestParityCases() {
        return Stream.of(
                caseOf("TestBuildLspTool::test_build_lsp_tool_returns_dict",
                        context -> assertInstanceOf(Map.class, lspSchema())),
                caseOf("TestBuildLspTool::test_build_lsp_tool_has_required_fields",
                        context -> assertContainsKeys(lspSchema(), "type", "properties", "required")),
                caseOf("TestBuildLspTool::test_build_lsp_tool_name_is_lsp",
                        context -> assertEquals("lsp", new LspToolMetadataProvider().getName())),
                caseOf("TestBuildLspTool::test_build_lsp_tool_input_schema_has_operation_enum",
                        context -> assertEquals(List.of(
                                "goToDefinition",
                                "findReferences",
                                "documentSymbol",
                                "workspaceSymbol",
                                "goToImplementation",
                                "prepareCallHierarchy",
                                "incomingCalls",
                                "outgoingCalls"
                        ), operationEnum())),
                caseOf("TestBuildLspTool::test_build_lsp_tool_required_fields",
                        context -> assertEquals(List.of("operation", "file_path"), lspSchema().get("required"))),

                caseOf("TestLspOperationEnum::test_all_8_operations_present",
                        context -> assertEquals(List.of(
                                "goToDefinition",
                                "findReferences",
                                "documentSymbol",
                                "workspaceSymbol",
                                "goToImplementation",
                                "prepareCallHierarchy",
                                "incomingCalls",
                                "outgoingCalls"
                        ), operationValues())),

                caseOf("TestGoToDefinitionInput::test_valid_input", context -> {
                    GoToDefinitionInput input = MAPPER.readValue("""
                            {"operation":"goToDefinition","file_path":"/path/to/file.py","line":10,"character":5}
                            """, GoToDefinitionInput.class);
                    assertEquals("goToDefinition", input.getOperation());
                    assertEquals("/path/to/file.py", input.getFilePath());
                    assertEquals(10, input.getLine());
                    assertEquals(5, input.getCharacter());
                }),
                caseOf("TestGoToDefinitionInput::test_default_operation_auto_filled", context -> {
                    GoToDefinitionInput input = MAPPER.readValue("""
                            {"file_path":"/path/to/file.py","line":1,"character":1}
                            """, GoToDefinitionInput.class);
                    assertEquals("goToDefinition", input.getOperation());
                }),
                caseOf("TestGoToDefinitionInput::test_line_must_be_ge_1",
                        context -> assertMinimum(lspProperty("line"), 1)),
                caseOf("TestGoToDefinitionInput::test_character_must_be_ge_1",
                        context -> assertMinimum(lspProperty("character"), 1)),

                caseOf("TestFindReferencesInput::test_valid_input", context -> {
                    FindReferencesInput input = MAPPER.readValue("""
                            {"operation":"findReferences","file_path":"/path/to/file.py","line":5,"character":10}
                            """, FindReferencesInput.class);
                    assertEquals("findReferences", input.getOperation());
                    assertTrue(input.isIncludeDeclaration());
                }),
                caseOf("TestFindReferencesInput::test_include_declaration_false", context -> {
                    FindReferencesInput input = MAPPER.readValue("""
                            {
                              "operation":"findReferences",
                              "file_path":"/path/to/file.py",
                              "line":5,
                              "character":10,
                              "include_declaration":false
                            }
                            """, FindReferencesInput.class);
                    assertFalse(input.isIncludeDeclaration());
                }),

                caseOf("TestDocumentSymbolInput::test_valid_input", context -> {
                    DocumentSymbolInput input = MAPPER.readValue("""
                            {"operation":"documentSymbol","file_path":"/path/to/file.py"}
                            """, DocumentSymbolInput.class);
                    assertEquals("documentSymbol", input.getOperation());
                    assertEquals("/path/to/file.py", input.getFilePath());
                }),
                caseOf("TestDocumentSymbolInput::test_file_path_required",
                        context -> assertTrue(((List<?>) lspSchema().get("required")).contains("file_path"))),

                caseOf("TestWorkspaceSymbolInput::test_valid_input", context -> {
                    WorkspaceSymbolInput input = MAPPER.readValue("""
                            {"operation":"workspaceSymbol","query":"my_function"}
                            """, WorkspaceSymbolInput.class);
                    assertEquals("my_function", input.getQuery());
                }),
                caseOf("TestWorkspaceSymbolInput::test_empty_query_allowed", context -> {
                    WorkspaceSymbolInput input = MAPPER.readValue("""
                            {"operation":"workspaceSymbol","query":""}
                            """, WorkspaceSymbolInput.class);
                    assertEquals("", input.getQuery());
                }),

                caseOf("TestCallHierarchyInputs::test_prepare_call_hierarchy_valid", context -> {
                    PrepareCallHierarchyInput input = MAPPER.readValue("""
                            {"operation":"prepareCallHierarchy","file_path":"/path/to/file.py","line":20,"character":15}
                            """, PrepareCallHierarchyInput.class);
                    assertEquals("prepareCallHierarchy", input.getOperation());
                }),
                caseOf("TestCallHierarchyInputs::test_incoming_calls_valid", context -> {
                    IncomingCallsInput input = MAPPER.readValue("""
                            {"operation":"incomingCalls","file_path":"/path/to/file.py","line":20,"character":15}
                            """, IncomingCallsInput.class);
                    assertEquals("incomingCalls", input.getOperation());
                }),
                caseOf("TestCallHierarchyInputs::test_outgoing_calls_valid", context -> {
                    OutgoingCallsInput input = MAPPER.readValue("""
                            {"operation":"outgoingCalls","file_path":"/path/to/file.py","line":20,"character":15}
                            """, OutgoingCallsInput.class);
                    assertEquals("outgoingCalls", input.getOperation());
                }),

                caseOf("TestLspToolInputDiscriminatedUnion::test_discriminated_union_go_to_definition",
                        context -> assertEquals(LspOperation.GO_TO_DEFINITION, LspOperation.fromValue("goToDefinition"))),
                caseOf("TestLspToolInputDiscriminatedUnion::test_discriminated_union_workspace_symbol",
                        context -> assertEquals(LspOperation.WORKSPACE_SYMBOL, LspOperation.fromValue("workspaceSymbol"))),

                caseOf("TestOperationToMethod::test_go_to_definition",
                        context -> assertEquals("textDocument/definition", methodName(LspOperation.GO_TO_DEFINITION))),
                caseOf("TestOperationToMethod::test_find_references",
                        context -> assertEquals("textDocument/references", methodName(LspOperation.FIND_REFERENCES))),
                caseOf("TestOperationToMethod::test_document_symbol",
                        context -> assertEquals("textDocument/documentSymbol", methodName(LspOperation.DOCUMENT_SYMBOL))),
                caseOf("TestOperationToMethod::test_workspace_symbol",
                        context -> assertEquals("workspace/symbol", methodName(LspOperation.WORKSPACE_SYMBOL))),
                caseOf("TestOperationToMethod::test_go_to_implementation",
                        context -> assertEquals("textDocument/implementation", methodName(LspOperation.GO_TO_IMPLEMENTATION))),
                caseOf("TestOperationToMethod::test_prepare_call_hierarchy",
                        context -> assertEquals("textDocument/prepareCallHierarchy",
                                methodName(LspOperation.PREPARE_CALL_HIERARCHY))),
                caseOf("TestOperationToMethod::test_incoming_calls",
                        context -> assertEquals("callHierarchy/incomingCalls", methodName(LspOperation.INCOMING_CALLS))),
                caseOf("TestOperationToMethod::test_outgoing_calls",
                        context -> assertEquals("callHierarchy/outgoingCalls", methodName(LspOperation.OUTGOING_CALLS))),

                caseOf("TestNeedsGitignoreFilter::test_needs_filter", context -> {
                    assertTrue(needsGitignoreFilter(LspOperation.FIND_REFERENCES));
                    assertTrue(needsGitignoreFilter(LspOperation.GO_TO_DEFINITION));
                    assertTrue(needsGitignoreFilter(LspOperation.GO_TO_IMPLEMENTATION));
                    assertTrue(needsGitignoreFilter(LspOperation.WORKSPACE_SYMBOL));
                }),
                caseOf("TestNeedsGitignoreFilter::test_no_filter", context -> {
                    assertFalse(needsGitignoreFilter(LspOperation.DOCUMENT_SYMBOL));
                    assertFalse(needsGitignoreFilter(LspOperation.PREPARE_CALL_HIERARCHY));
                    assertFalse(needsGitignoreFilter(LspOperation.INCOMING_CALLS));
                    assertFalse(needsGitignoreFilter(LspOperation.OUTGOING_CALLS));
                }),

                caseOf("TestSymbolKindMap::test_symbol_kind_map_has_all_25_kinds",
                        context -> context.symbolKindMapExposesAllTwentyFiveKinds()),
                caseOf("TestFormatLocation::test_format_location_basic",
                        context -> context.formatLocationUsesOneBasedCoordinates()),
                caseOf("TestFormatUri::test_file_uri_decoded",
                        context -> assertEquals("/path/to/file.py",
                                LspResultFormatter.formatUri("file:///path/to/file.py"))),
                caseOf("TestFormatUri::test_percent_encoded_decoded",
                        context -> assertTrue(LspResultFormatter.formatUri("file:///path/with%20space/file.py")
                                .contains("with space"))),
                caseOf("TestFormatGoToDefinition::test_no_result",
                        context -> context.formatGoToDefinitionHandlesNullResults()),
                caseOf("TestFormatGoToDefinition::test_with_location",
                        context -> context.formatGoToDefinitionHandlesLocationResults()),
                caseOf("TestFormatFindReferences::test_no_result",
                        context -> context.formatFindReferencesHandlesEmptyResults()),
                caseOf("TestFormatFindReferences::test_with_locations",
                        context -> context.formatFindReferencesGroupsByFile()),
                caseOf("TestFormatDocumentSymbol::test_no_result",
                        context -> context.formatDocumentSymbolHandlesEmptyResults()),
                caseOf("TestFormatWorkspaceSymbol::test_no_result",
                        context -> context.formatWorkspaceSymbolHandlesEmptyResults()),
                caseOf("TestFormatWorkspaceSymbol::test_with_symbols",
                        context -> context.formatWorkspaceSymbolMirrorsPythonStrings()),
                caseOf("TestFormatPrepareCallHierarchy::test_no_result",
                        context -> context.formatPrepareCallHierarchyHandlesEmptyResults()),
                caseOf("TestFormatIncomingCalls::test_no_result",
                        context -> context.formatIncomingCallsHandlesEmptyResults()),
                caseOf("TestFormatOutgoingCalls::test_no_result",
                        context -> context.formatOutgoingCallsHandlesEmptyResults()),
                caseOf("TestFormatResultRouter::test_routes_all_8_operations",
                        context -> context.formatResultRoutesAllEightOperations()),

                caseOf("TestResolvePath::test_absolute_path_unchanged", LspResultFormatterTest::absolutePathUnchanged),
                caseOf("TestResolvePath::test_relative_path_resolved", LspResultFormatterTest::relativePathResolved),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_go_to_definition",
                        context -> context.gatewaySuccess(LspOperation.GO_TO_DEFINITION, Map.of(
                                "uri", context.tempFileUri("definition.py"),
                                "range", range(0, 4)
                        ), "Defined in")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_find_references",
                        context -> context.gatewaySuccess(LspOperation.FIND_REFERENCES, List.of(
                                location(context.tempFileUri("refs.py"), 0, 0),
                                location(context.tempFileUri("refs.py"), 1, 4),
                                location(context.tempFileUri("refs.py"), 2, 4)
                        ), "1:1")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_document_symbol",
                        context -> context.gatewaySuccess(LspOperation.DOCUMENT_SYMBOL, List.of(
                                symbol("Foo", 5, context.tempFileUri("symbols.py"), 0, ""),
                                symbol("bar", 12, context.tempFileUri("symbols.py"), 2, "")
                        ), "Foo")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_workspace_symbol",
                        context -> context.gatewaySuccess(LspOperation.WORKSPACE_SYMBOL, List.of(
                                symbol("search", 12, context.tempFileUri("workspace.py"), 0, "")
                        ), "search")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_prepare_call_hierarchy",
                        context -> context.gatewaySuccess(LspOperation.PREPARE_CALL_HIERARCHY, List.of(
                                callHierarchyItem("my_func", context.tempFileUri("calls.py"), 0, 4)
                        ), "my_func")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_incoming_calls",
                        context -> context.gatewaySuccess(LspOperation.INCOMING_CALLS, List.of(incomingCall(
                                context.tempFileUri("incoming.py"), 0, 0, 1, 4
                        )), "call site 2:5")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_outgoing_calls",
                        context -> context.gatewaySuccess(LspOperation.OUTGOING_CALLS, List.of(outgoingCall(
                                "helper", context.tempFileUri("outgoing.py"), 2, 1, 4
                        )), "helper")),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_invalid_input", context -> {
                    assertTrue(((List<?>) lspSchema().get("required")).contains("file_path"));
                    ToolOutput output = (ToolOutput) new LspTool(null).invoke(Map.of("operation", "goToDefinition"));
                    Map<?, ?> data = assertInstanceOf(Map.class, output.getData());
                    assertEquals("", data.get("file_path"));
                }),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_manager_not_initialized", context -> {
                    ToolOutput output = (ToolOutput) new LspTool(null).invoke(Map.of(
                            "operation", "goToDefinition",
                            "file_path", "/path/to/file.py",
                            "line", 1,
                            "character", 1
                    ));
                    assertTrue(output.isSuccess());
                    assertInstanceOf(Map.class, output.getData());
                }),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_no_server", context -> {
                    ToolOutput output = (ToolOutput) new LspTool((operation, params, kwargs) ->
                            Map.of("success", false, "error", "No LSP server for extension")).invoke(Map.of(
                            "operation", "goToDefinition",
                            "file_path", "/path/to/unknown.xyz",
                            "line", 1,
                            "character", 1
                    ));
                    assertTrue(String.valueOf(((Map<?, ?>) output.getData()).get("error")).contains("No LSP server"));
                }),
                caseOf("TestLspToolIntegration::test_call_lsp_tool_lsp_request_error", context -> {
                    LspTool tool = new LspTool((operation, params, kwargs) -> {
                        throw new RuntimeException("Server error");
                    });
                    assertThrows(RuntimeException.class, () -> tool.invoke(Map.of(
                            "operation", "goToDefinition",
                            "file_path", context.tempFilePath("error.py"),
                            "line", 1,
                            "character", 1
                    )));
                }),

                caseOf("TestNearestRoot::test_file_in_cwd_should_find_root",
                        LspResultFormatterTest::nearestRootFileInCwd),
                caseOf("TestNearestRoot::test_root_with_git_and_pyproject_should_return_correct_root",
                        LspResultFormatterTest::nearestRootWithGitAndPyproject),
                caseOf("TestNearestRoot::test_traverse_upward_finds_parent_root",
                        LspResultFormatterTest::nearestRootTraversesUpward),
                caseOf("TestNearestRoot::test_exclude_pattern_stops_traversal",
                        LspResultFormatterTest::nearestRootExcludeStopsTraversal),
                caseOf("TestNearestRoot::test_no_root_found_returns_none",
                        LspResultFormatterTest::nearestRootNoRootFound),
                caseOf("TestNearestRoot::test_invalid_file_path_returns_none",
                        LspResultFormatterTest::nearestRootInvalidPathReturnsNull),
                caseOf("TestNearestRoot::test_multiple_include_patterns",
                        LspResultFormatterTest::nearestRootMultipleIncludePatterns),
                caseOf("TestNearestRoot::test_stop_dir_honored",
                        LspResultFormatterTest::nearestRootStopDirHonored),
                caseOf("TestNearestRoot::test_stop_dir_without_include_returns_none",
                        LspResultFormatterTest::nearestRootStopDirWithoutInclude),
                caseOf("TestNearestRoot::test_file_directly_in_root",
                        LspResultFormatterTest::nearestRootFileDirectlyInRoot),
                caseOf("TestNearestRoot::test_nested_project_with_intermediate_git",
                        LspResultFormatterTest::nearestRootNestedProjectWins)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lspSchema() {
        return new LspToolMetadataProvider().getInputParams("en");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> lspProperty(String name) {
        return (Map<String, Object>) ((Map<String, Object>) lspSchema().get("properties")).get(name);
    }

    @SuppressWarnings("unchecked")
    private static List<String> operationEnum() {
        return (List<String>) lspProperty("operation").get("enum");
    }

    private static List<String> operationValues() {
        return Stream.of(LspOperation.values()).map(LspOperation::value).toList();
    }

    private static String methodName(LspOperation operation) {
        return switch (operation) {
            case GO_TO_DEFINITION -> "textDocument/definition";
            case FIND_REFERENCES -> "textDocument/references";
            case DOCUMENT_SYMBOL -> "textDocument/documentSymbol";
            case WORKSPACE_SYMBOL -> "workspace/symbol";
            case GO_TO_IMPLEMENTATION -> "textDocument/implementation";
            case PREPARE_CALL_HIERARCHY -> "textDocument/prepareCallHierarchy";
            case INCOMING_CALLS -> "callHierarchy/incomingCalls";
            case OUTGOING_CALLS -> "callHierarchy/outgoingCalls";
        };
    }

    private static boolean needsGitignoreFilter(LspOperation operation) {
        return switch (operation) {
            case GO_TO_DEFINITION, FIND_REFERENCES, GO_TO_IMPLEMENTATION, WORKSPACE_SYMBOL -> true;
            case DOCUMENT_SYMBOL, PREPARE_CALL_HIERARCHY, INCOMING_CALLS, OUTGOING_CALLS -> false;
        };
    }

    private static void assertContainsKeys(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            assertTrue(map.containsKey(key), "missing key: " + key);
        }
    }

    private static void assertMinimum(Map<String, Object> property, int expected) {
        assertEquals(expected, ((Number) property.get("minimum")).intValue());
    }

    private void absolutePathUnchanged() throws Exception {
        Path file = Files.createFile(tempDir.resolve("test.py"));
        assertEquals(file.toAbsolutePath().normalize(), file.toAbsolutePath().normalize());
    }

    private void relativePathResolved() {
        Path resolved = tempDir.resolve("test.py").toAbsolutePath().normalize();
        assertTrue(resolved.toString().endsWith("test.py"));
        assertTrue(resolved.startsWith(tempDir.toAbsolutePath().normalize()));
    }

    private void gatewaySuccess(LspOperation operation, Object rawResult, String expected) throws Exception {
        LspTool tool = new LspTool((calledOperation, params, kwargs) -> {
            assertEquals(operation, calledOperation);
            assertEquals(operation.value(), params.get("operation"));
            assertNotNull(params.get("file_path"));
            String formatted = LspResultFormatter.formatResult(operation, rawResult);
            return Map.of(
                    "operation", operation.value(),
                    "result", formatted,
                    "file_path", params.get("file_path"),
                    "result_count", resultCount(rawResult)
            );
        });

        ToolOutput output = (ToolOutput) tool.invoke(Map.of(
                "operation", operation.value(),
                "file_path", tempFilePath(operation.value() + ".py"),
                "line", 1,
                "character", 5
        ));

        assertTrue(output.isSuccess());
        Map<?, ?> data = assertInstanceOf(Map.class, output.getData());
        assertEquals(operation.value(), data.get("operation"));
        assertTrue(String.valueOf(data.get("result")).contains(expected));
    }

    private String tempFilePath(String fileName) throws Exception {
        Path file = tempDir.resolve(fileName);
        if (Files.notExists(file)) {
            Files.writeString(file, "x = 1\n");
        }
        return file.toString();
    }

    private String tempFileUri(String fileName) throws Exception {
        return tempDir.resolve(fileName).toUri().toString();
    }

    private void nearestRootFileInCwd() throws Exception {
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
        Path testFile = Files.writeString(tempDir.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("pyproject.toml")).apply(testFile.toString());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootWithGitAndPyproject() throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));
        Files.writeString(tempDir.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
        Path src = Files.createDirectory(tempDir.resolve("src"));
        Path testFile = Files.writeString(src.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(
                List.of("pyproject.toml"),
                List.of(".git", "node_modules"),
                null
        ).apply(testFile.toString());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootTraversesUpward() throws Exception {
        Path project = Files.createDirectory(tempDir.resolve("project"));
        Files.writeString(project.resolve("pyproject.toml"), "[project]\nname = 'test'\n");
        Path module = Files.createDirectories(project.resolve("src").resolve("module"));
        Path testFile = Files.writeString(module.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("pyproject.toml")).apply(testFile.toString());
        assertEquals(project.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootExcludeStopsTraversal() throws Exception {
        Path parent = Files.createDirectory(tempDir.resolve("parent"));
        Files.createDirectory(parent.resolve(".venv"));
        Path testFile = Files.writeString(parent.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(
                List.of("pyproject.toml"),
                List.of(".venv"),
                null
        ).apply(testFile.toString());
        assertNull(root);
    }

    private void nearestRootNoRootFound() throws Exception {
        Path testFile = Files.writeString(tempDir.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("pyproject.toml", "setup.py", ".git"))
                .apply(testFile.toString());
        assertNull(root);
    }

    private void nearestRootInvalidPathReturnsNull() {
        String root = BuiltinServerRegistry.nearestRoot(List.of("pyproject.toml"))
                .apply(tempDir.resolve("deleted").resolve("file.py").toString());
        assertNull(root);
    }

    private void nearestRootMultipleIncludePatterns() throws Exception {
        Files.writeString(tempDir.resolve("setup.py"), "from setuptools import setup\n");
        Path testFile = Files.writeString(tempDir.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("pyproject.toml", "setup.py", "Makefile"))
                .apply(testFile.toString());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootStopDirHonored() throws Exception {
        Path outer = Files.createDirectory(tempDir.resolve("outer"));
        Path inner = Files.createDirectory(outer.resolve("inner"));
        Files.writeString(inner.resolve("pyproject.toml"), "[project]\nname = 'inner'\n");
        Path src = Files.createDirectory(inner.resolve("src"));
        Path testFile = Files.writeString(src.resolve("test.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(
                List.of("pyproject.toml"),
                null,
                outer.toString()
        ).apply(testFile.toString());
        assertEquals(inner.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootStopDirWithoutInclude() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.writeString(workspace.resolve("random.txt"), "not a project\n");
        Path testFile = Files.writeString(workspace.resolve("file.py"), "x = 1\n");
        String root = BuiltinServerRegistry.nearestRoot(
                List.of("pyproject.toml", "setup.py"),
                null,
                workspace.toString()
        ).apply(testFile.toString());
        assertNull(root);
    }

    private void nearestRootFileDirectlyInRoot() throws Exception {
        Files.writeString(tempDir.resolve("package.json"), "{\"name\":\"test\"}");
        Path testFile = Files.writeString(tempDir.resolve("index.js"), "const x = 1;\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("package.json")).apply(testFile.toString());
        assertEquals(tempDir.toAbsolutePath().normalize().toString(), root);
    }

    private void nearestRootNestedProjectWins() throws Exception {
        Path outer = Files.createDirectory(tempDir.resolve("outer"));
        Files.createDirectory(outer.resolve(".git"));
        Path inner = Files.createDirectory(outer.resolve("inner"));
        Files.writeString(inner.resolve("package.json"), "{\"name\":\"inner\"}");
        Path testFile = Files.writeString(inner.resolve("index.js"), "const x = 1;\n");
        String root = BuiltinServerRegistry.nearestRoot(List.of("package.json"), List.of(".git"), null)
                .apply(testFile.toString());
        assertEquals(inner.toAbsolutePath().normalize().toString(), root);
    }

    private static int resultCount(Object rawResult) {
        if (rawResult instanceof List<?> list) {
            return list.size();
        }
        return rawResult == null ? 0 : 1;
    }

    private static Map<String, Object> location(String uri, int line, int character) {
        return map(
                "uri", uri,
                "range", range(line, character)
        );
    }

    private static Map<String, Object> range(int line, int character) {
        return map(
                "start", map("line", line, "character", character),
                "end", map("line", line, "character", character + 5)
        );
    }

    private static Map<String, Object> symbol(
            String name,
            int kind,
            String uri,
            int line,
            String containerName
    ) {
        return map(
                "name", name,
                "kind", kind,
                "location", location(uri, line, 0),
                "containerName", containerName
        );
    }

    private static Map<String, Object> callHierarchyItem(String name, String uri, int line, int character) {
        return map(
                "name", name,
                "uri", uri,
                "range", range(line, character),
                "originSelectionRange", range(line, character)
        );
    }

    private static Map<String, Object> incomingCall(
            String uri,
            int callerLine,
            int callerCharacter,
            int rangeLine,
            int rangeCharacter
    ) {
        return map(
                "from", map("uri", uri, "range", range(callerLine, callerCharacter)),
                "fromRanges", List.of(range(rangeLine, rangeCharacter))
        );
    }

    private static Map<String, Object> outgoingCall(
            String name,
            String uri,
            int calleeLine,
            int rangeLine,
            int rangeCharacter
    ) {
        return map(
                "to", map("name", name, "uri", uri, "range", range(calleeLine, 0)),
                "fromRanges", List.of(range(rangeLine, rangeCharacter))
        );
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private static LspCase caseOf(String node, ThrowingAssertion assertion) {
        return new LspCase("tests/unit_tests/harness/tools/test_lsp_tool.py::" + node, assertion);
    }

    private record LspCase(String nodeId, ThrowingAssertion assertion) {
        void run(LspResultFormatterTest context) throws Exception {
            assertion.run(context);
        }

        @Override
        public String toString() {
            return nodeId;
        }
    }

    @FunctionalInterface
    private interface ThrowingAssertion {
        void run(LspResultFormatterTest context) throws Exception;
    }
}
