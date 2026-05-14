/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.lsp.core.LspServerManager;
import com.openjiuwen.harness.tools.lsp.LspOperation;
import com.openjiuwen.harness.tools.lsp.LspToolSupport;

import java.util.Map;

/**
 * Unified Java harness LSP tool surface.
 *
 * <p>Mirrors Python's LSP tool surface in {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public class LspTool extends AbstractHarnessTool {

    public LspTool() {
        super(toolCard("harness.lsp", "lsp", "Language Server Protocol tool for diagnostics and code navigation."), null);
    }

    @Override
    public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String operation = stringValue(inputs.get("operation"));
        String filePath = stringValue(inputs.get("file_path"));
        int line = intValue(inputs.get("line"), 1);
        int character = intValue(inputs.get("character"), 1);
        String query = stringValue(inputs.get("query"));
        boolean includeDeclaration = boolValue(inputs.get("include_declaration"), true);
        int limit = intValue(inputs.get("limit"), 50);
        String severity = stringValue(inputs.get("severity"));
        boolean pending = boolValue(inputs.get("pending"), false);
        int maxPerFile = intValue(inputs.get("max_per_file"), 10);
        int maxTotal = intValue(inputs.get("max_total"), 30);

        LspOperation normalizedOperation = normalizeOperation(operation);
        LspServerManager manager = LspServerManager.getInstance();
        String effectiveFilePath = filePath;
        if (normalizedOperation == LspOperation.WORKSPACE_SYMBOL && effectiveFilePath.isBlank()) {
            effectiveFilePath = manager.getWorkspaceRoot();
        }
        if (!pending && normalizedOperation != LspOperation.WORKSPACE_SYMBOL && !effectiveFilePath.isBlank()) {
            Object resolvedPath = manager.fromFileUri(manager.toFileUri(effectiveFilePath));
            effectiveFilePath = resolvedPath == null ? effectiveFilePath : String.valueOf(resolvedPath);
        }
        Object data = switch (operation) {
            case "goToDefinition", "goto_definition" -> manager.gotoDefinition(effectiveFilePath, line, character);
            case "findReferences", "find_references" -> manager.findReferences(effectiveFilePath, line, character, includeDeclaration);
            case "documentSymbol", "document_symbol" -> manager.getDocumentSymbols(effectiveFilePath);
            case "workspaceSymbol", "workspace_symbol" -> manager.getWorkspaceSymbols(query, limit);
            case "goToImplementation", "goto_implementation" -> manager.gotoImplementation(effectiveFilePath, line, character);
            case "prepareCallHierarchy", "prepare_call_hierarchy" -> manager.prepareCallHierarchy(effectiveFilePath, line, character);
            case "incomingCalls", "incoming_calls" -> manager.incomingCalls(effectiveFilePath, line, character);
            case "outgoingCalls", "outgoing_calls" -> manager.outgoingCalls(effectiveFilePath, line, character);
            case "diagnostics", "lsp_diagnostics" -> pending
                    ? manager.getPendingDiagnostics(maxPerFile, maxTotal)
                    : manager.getDiagnostics(effectiveFilePath, severity);
            case "status", "lsp_status" -> manager.getLspStatus();
            default -> null;
        };

        if (data == null) {
            return new ToolOutput(false, null, "unsupported lsp operation: " + operation);
        }
        LspToolSupport.ToolPayload payload = ("diagnostics".equals(operation) || "lsp_diagnostics".equals(operation))
                ? LspToolSupport.toDiagnosticsPayload(effectiveFilePath, data, pending)
                : normalizedOperation == null
                ? new LspToolSupport.ToolPayload(null, Map.of("operation", operation, "data", data))
                : LspToolSupport.toToolPayload(normalizedOperation, effectiveFilePath, data);
        return new ToolOutput(true, payload.payload(), null);
    }

    static LspOperation normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return null;
        }
        return switch (operation) {
            case "goToDefinition", "goto_definition" -> LspOperation.GO_TO_DEFINITION;
            case "findReferences", "find_references" -> LspOperation.FIND_REFERENCES;
            case "documentSymbol", "document_symbol" -> LspOperation.DOCUMENT_SYMBOL;
            case "workspaceSymbol", "workspace_symbol" -> LspOperation.WORKSPACE_SYMBOL;
            case "goToImplementation", "goto_implementation" -> LspOperation.GO_TO_IMPLEMENTATION;
            case "prepareCallHierarchy", "prepare_call_hierarchy" -> LspOperation.PREPARE_CALL_HIERARCHY;
            case "incomingCalls", "incoming_calls" -> LspOperation.INCOMING_CALLS;
            case "outgoingCalls", "outgoing_calls" -> LspOperation.OUTGOING_CALLS;
            default -> null;
        };
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
