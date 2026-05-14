/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp;

import com.openjiuwen.harness.lsp.query.LspDiagnostic;
import com.openjiuwen.harness.lsp.query.LspDiagnosticFile;
import com.openjiuwen.harness.lsp.query.LspCallHierarchyItem;
import com.openjiuwen.harness.lsp.query.LspIncomingCall;
import com.openjiuwen.harness.lsp.query.LspLocation;
import com.openjiuwen.harness.lsp.query.LspOutgoingCall;
import com.openjiuwen.harness.lsp.query.LspRange;
import com.openjiuwen.harness.lsp.query.LspSymbol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal formatter and payload helper for Java harness LSP tools.
 *
 * <p>Mirrors Python's formatter/result flow in
 * {@code openjiuwen.harness.tools.lsp_tool._formatter} and
 * {@code openjiuwen.harness.tools.lsp_tool._tool}.
 */
public final class LspToolSupport {

    private LspToolSupport() {
    }

    public static ToolPayload toToolPayload(LspOperation operation, String filePath, Object data) {
        String result = format(operation, data);
        LspToolResult typed = new LspToolResult();
        typed.setOperation(operation);
        typed.setResult(result);
        typed.setFilePath(filePath == null || filePath.isBlank() ? null : filePath);
        Integer resultCount = countOf(data);
        typed.setResultCount(resultCount);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", operation != null ? operation.getValue() : null);
        payload.put("result", result);
        payload.put("file_path", typed.getFilePath());
        if (resultCount != null) {
            payload.put("result_count", resultCount);
        }
        payload.put("data", data);
        payload.put("typed", typed);
        return new ToolPayload(typed, payload);
    }

    public static ToolPayload toDiagnosticsPayload(String filePath, Object data, boolean pending) {
        LspToolResult typed = new LspToolResult();
        typed.setResult(formatDiagnostics(data));
        typed.setFilePath(filePath == null || filePath.isBlank() ? null : filePath);
        typed.setResultCount(countOf(data));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operation", "lsp_diagnostics");
        payload.put("result", typed.getResult());
        payload.put("file_path", typed.getFilePath());
        payload.put("result_count", typed.getResultCount());
        payload.put("data", data);
        if (pending) {
            payload.put("pending", true);
            payload.put("diagnostic_files", data);
        } else {
            payload.put("diagnostics", data);
        }
        payload.put("typed", typed);
        return new ToolPayload(typed, payload);
    }

    public static String format(LspOperation operation, Object data) {
        if (operation == null) {
            return data == null ? "" : String.valueOf(data);
        }
        return switch (operation) {
            case GO_TO_DEFINITION, GO_TO_IMPLEMENTATION -> formatLocationResult(data, "No definition found.");
            case FIND_REFERENCES -> formatLocationList(castList(data, LspLocation.class), "No references found.", false);
            case DOCUMENT_SYMBOL -> formatDocumentSymbols(castList(data, LspSymbol.class), "No symbols found.");
            case WORKSPACE_SYMBOL -> formatSymbols(castList(data, LspSymbol.class), "No symbols found.", true);
            case PREPARE_CALL_HIERARCHY -> formatPrepareCallHierarchy(castList(data, LspCallHierarchyItem.class));
            case INCOMING_CALLS -> formatIncomingCalls(castList(data, LspIncomingCall.class));
            case OUTGOING_CALLS -> formatOutgoingCalls(castList(data, LspOutgoingCall.class));
        };
    }

    public static String formatDiagnostics(Object data) {
        if (data instanceof Collection<?> collection && !collection.isEmpty()) {
            Object first = collection.iterator().next();
            if (first instanceof LspDiagnosticFile) {
                @SuppressWarnings("unchecked")
                List<LspDiagnosticFile> files = (List<LspDiagnosticFile>) data;
                return formatPendingDiagnostics(files);
            }
            if (first instanceof LspDiagnostic) {
                @SuppressWarnings("unchecked")
                List<LspDiagnostic> diagnostics = (List<LspDiagnostic>) data;
                return formatDiagnosticsList(diagnostics);
            }
        }
        if (data instanceof List<?> list && list.isEmpty()) {
            return "No diagnostics found.";
        }
        return data == null ? "No diagnostics found." : String.valueOf(data);
    }

    private static String formatLocationResult(Object data, String emptyMessage) {
        if (!(data instanceof LspLocation location)) {
            return emptyMessage;
        }
        return "Defined in " + formatLocation(location);
    }

    private static String formatLocationList(List<LspLocation> locations, String emptyMessage, boolean singleLineForOne) {
        if (locations.isEmpty()) {
            return emptyMessage;
        }
        if (singleLineForOne && locations.size() == 1) {
            return formatLocation(locations.get(0));
        }
        Map<String, List<LspLocation>> groups = new LinkedHashMap<>();
        for (LspLocation location : locations) {
            groups.computeIfAbsent(location.getFilePath(), key -> new ArrayList<>()).add(location);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<LspLocation>> entry : groups.entrySet()) {
            lines.add(entry.getKey() + ":");
            for (LspLocation location : entry.getValue()) {
                lines.add("  " + location.getLine() + ":" + location.getCharacter());
            }
        }
        return String.join("\n", lines);
    }

    private static String formatSymbols(List<LspSymbol> symbols, String emptyMessage, boolean withKind) {
        if (symbols.isEmpty()) {
            return emptyMessage;
        }
        Map<String, List<LspSymbol>> groups = new LinkedHashMap<>();
        for (LspSymbol symbol : symbols) {
            String filePath = symbol.getLocation() != null ? symbol.getLocation().getFilePath() : "";
            groups.computeIfAbsent(filePath, key -> new ArrayList<>()).add(symbol);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<LspSymbol>> entry : groups.entrySet()) {
            lines.add(entry.getKey() + ":");
            for (LspSymbol symbol : entry.getValue()) {
                int line = symbol.getLocation() != null ? symbol.getLocation().getLine() : 0;
                String qualifiedName = symbol.getContainerName().isBlank()
                        ? symbol.getName()
                        : symbol.getContainerName() + "." + symbol.getName();
                if (withKind) {
                    lines.add("  " + line + ": " + symbol.getKind() + " " + qualifiedName);
                } else {
                    lines.add("  " + line + ": " + qualifiedName);
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String formatDocumentSymbols(List<LspSymbol> symbols, String emptyMessage) {
        if (symbols.isEmpty()) {
            return emptyMessage;
        }
        boolean hasChildren = symbols.stream().anyMatch(symbol -> symbol.getChildren() != null && !symbol.getChildren().isEmpty());
        if (hasChildren) {
            return formatSymbolTree(symbols, 0);
        }
        List<String> lines = new ArrayList<>();
        for (LspSymbol symbol : symbols) {
            LspLocation location = symbol.getLocation();
            String path = location != null ? location.getFilePath() : "";
            int line = location != null ? location.getLine() : 0;
            String qualifiedName = symbol.getContainerName().isBlank()
                    ? symbol.getName()
                    : symbol.getContainerName() + "." + symbol.getName();
            lines.add(path + ":" + line + ": " + symbol.getKind() + " " + qualifiedName);
        }
        return String.join("\n", lines);
    }

    private static String formatSymbolTree(List<LspSymbol> symbols, int indent) {
        List<String> lines = new ArrayList<>();
        for (LspSymbol symbol : symbols) {
            lines.add("  ".repeat(indent) + symbol.getKind() + " " + symbol.getName()
                    + (symbol.getDetail().isBlank() ? "" : " - " + symbol.getDetail()));
            if (symbol.getChildren() != null && !symbol.getChildren().isEmpty()) {
                lines.add(formatSymbolTree(symbol.getChildren(), indent + 1));
            }
        }
        return String.join("\n", lines);
    }

    private static String formatPrepareCallHierarchy(List<LspCallHierarchyItem> items) {
        if (items.isEmpty()) {
            return "No call hierarchy available.";
        }
        if (items.size() == 1) {
            LspCallHierarchyItem item = items.get(0);
            LspRange selectionRange = item.getSelectionRange();
            LspLocation location = selectionRange != null && selectionRange.getStart() != null
                    ? selectionRange.getStart()
                    : item.getSelectionLocation() != null ? item.getSelectionLocation() : item.getLocation();
            return location.getFilePath() + ":" + location.getLine() + ": " + item.getName()
                    + (item.getDetail().isBlank() ? "" : " - " + item.getDetail());
        }
        List<String> lines = new ArrayList<>();
        lines.add(items.size() + " call hierarchy items:");
        for (LspCallHierarchyItem item : items) {
            LspRange selectionRange = item.getSelectionRange();
            LspLocation location = selectionRange != null && selectionRange.getStart() != null
                    ? selectionRange.getStart()
                    : item.getSelectionLocation() != null ? item.getSelectionLocation() : item.getLocation();
            lines.add("  " + location.getFilePath() + ":" + location.getLine() + ": " + item.getName()
                    + (item.getDetail().isBlank() ? "" : " - " + item.getDetail()));
        }
        return String.join("\n", lines);
    }

    private static String formatIncomingCalls(List<LspIncomingCall> calls) {
        if (calls.isEmpty()) {
            return "No incoming calls found.";
        }
        Map<String, List<LspIncomingCall>> groups = new LinkedHashMap<>();
        for (LspIncomingCall call : calls) {
            String filePath = call.getFrom() != null && call.getFrom().getLocation() != null
                    ? call.getFrom().getLocation().getFilePath() : "";
            groups.computeIfAbsent(filePath, key -> new ArrayList<>()).add(call);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<LspIncomingCall>> entry : groups.entrySet()) {
            lines.add(entry.getKey() + ":");
            for (LspIncomingCall call : entry.getValue()) {
                LspCallHierarchyItem from = call.getFrom();
                LspRange fromRange = from != null ? from.getRange() : null;
                LspLocation fromLocation = fromRange != null && fromRange.getStart() != null ? fromRange.getStart()
                        : from != null ? from.getLocation() : null;
                for (LspRange range : call.getFromRanges()) {
                    LspLocation start = range != null ? range.getStart() : null;
                    int callerLine = fromLocation != null ? fromLocation.getLine() : 0;
                    lines.add("  " + entry.getKey() + ":" + callerLine + " -> call site "
                            + (start != null ? start.getLine() : 0) + ":" + (start != null ? start.getCharacter() : 0));
                }
            }
        }
        return String.join("\n", lines);
    }

    private static String formatOutgoingCalls(List<LspOutgoingCall> calls) {
        if (calls.isEmpty()) {
            return "No outgoing calls found.";
        }
        List<String> lines = new ArrayList<>();
        for (LspOutgoingCall call : calls) {
            LspCallHierarchyItem to = call.getTo();
            LspRange calleeRange = to != null ? to.getRange() : null;
            LspLocation calleeLocation = calleeRange != null && calleeRange.getStart() != null ? calleeRange.getStart()
                    : to != null ? to.getLocation() : null;
            String calleeName = to != null ? to.getName() : "?";
            String calleePath = calleeLocation != null ? calleeLocation.getFilePath() : "";
            int calleeLine = calleeLocation != null ? calleeLocation.getLine() : 0;
            for (LspRange range : call.getFromRanges()) {
                LspLocation start = range != null ? range.getStart() : null;
                lines.add("  call site " + (start != null ? start.getLine() : 0) + ":" + (start != null ? start.getCharacter() : 0)
                        + " -> " + calleeName + " (" + calleePath + ":" + calleeLine + ")");
            }
        }
        return String.join("\n", lines);
    }

    private static String formatDiagnosticsList(List<LspDiagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return "No diagnostics found.";
        }
        List<String> lines = new ArrayList<>();
        for (LspDiagnostic diagnostic : diagnostics) {
            LspLocation location = diagnostic.getLocation();
            String prefix = location == null ? "" : formatLocation(location) + ": ";
            lines.add(prefix + diagnostic.getSeverity() + " - " + diagnostic.getMessage());
        }
        return String.join("\n", lines);
    }

    private static String formatPendingDiagnostics(List<LspDiagnosticFile> files) {
        if (files.isEmpty()) {
            return "No diagnostics found.";
        }
        List<String> lines = new ArrayList<>();
        for (LspDiagnosticFile file : files) {
            lines.add(file.getFilePath() + ":");
            for (LspDiagnostic diagnostic : file.getDiagnostics()) {
                LspLocation location = diagnostic.getLocation();
                String position = location == null ? "" : "  " + location.getLine() + ":" + location.getCharacter() + " ";
                lines.add(position + diagnostic.getSeverity() + " - " + diagnostic.getMessage());
            }
        }
        return String.join("\n", lines);
    }

    private static String formatLocation(LspLocation location) {
        return location.getFilePath() + ":" + location.getLine() + ":" + location.getCharacter();
    }

    private static Integer countOf(Object data) {
        if (data == null) {
            return 0;
        }
        if (data instanceof Collection<?> collection) {
            return collection.size();
        }
        return 1;
    }

    private static <T> List<T> castList(Object data, Class<T> type) {
        if (!(data instanceof Collection<?> collection)) {
            return List.of();
        }
        List<T> items = new ArrayList<>();
        for (Object value : collection) {
            if (type.isInstance(value)) {
                items.add(type.cast(value));
            }
        }
        return items;
    }

    public record ToolPayload(LspToolResult typed, Map<String, Object> payload) {
    }
}
