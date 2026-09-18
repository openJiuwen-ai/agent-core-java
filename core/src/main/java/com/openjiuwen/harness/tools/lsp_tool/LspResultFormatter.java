/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.lsp_tool;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's module-level LSP result formatters in
 * {@code openjiuwen/harness/tools/lsp_tool/_formatter.py}.
 */
public final class LspResultFormatter {

    public static final Map<Integer, String> SYMBOL_KIND_MAP = Map.ofEntries(
            Map.entry(1, "File"),
            Map.entry(2, "Module"),
            Map.entry(3, "Namespace"),
            Map.entry(4, "Package"),
            Map.entry(5, "Class"),
            Map.entry(6, "Method"),
            Map.entry(7, "Property"),
            Map.entry(8, "Field"),
            Map.entry(9, "Constructor"),
            Map.entry(10, "Enum"),
            Map.entry(11, "Interface"),
            Map.entry(12, "Function"),
            Map.entry(13, "Variable"),
            Map.entry(14, "Constant"),
            Map.entry(15, "String"),
            Map.entry(16, "Number"),
            Map.entry(17, "Boolean"),
            Map.entry(18, "Array"),
            Map.entry(19, "Object"),
            Map.entry(20, "Key"),
            Map.entry(21, "Null"),
            Map.entry(22, "EnumMember"),
            Map.entry(23, "Event"),
            Map.entry(24, "Operator"),
            Map.entry(25, "TypeParameter")
    );

    private static final boolean WINDOWS = System.getProperty("os.name", "")
            .toLowerCase()
            .contains("win");

    private LspResultFormatter() {
    }

    public static String formatLocation(Map<String, Object> location) {
        String uri = stringValue(location.get("uri"));
        String path = formatUri(uri);
        Map<String, Object> start = nestedMap(nestedMap(location.get("range")).get("start"));
        int line = intValue(start.get("line")) + 1;
        int character = intValue(start.get("character")) + 1;
        return path + ":" + line + ":" + character;
    }

    static boolean isWindowsDrivePath(String path) {
        return path != null && path.length() >= 3 && path.charAt(0) == '/' && path.charAt(2) == ':';
    }

    public static String formatUri(String uri) {
        if (uri == null) {
            return "";
        }
        if (uri.startsWith("file://")) {
            String path = uri.substring(7);
            if (WINDOWS && isWindowsDrivePath(path)) {
                path = path.substring(1);
            }
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        }
        return uri;
    }

    public static Map<String, List<Map<String, Object>>> groupByFile(List<Map<String, Object>> locations) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> location : locations) {
            groups.computeIfAbsent(formatUri(stringValue(location.get("uri"))), ignored -> new ArrayList<>())
                    .add(location);
        }
        return groups;
    }

    public static String formatGoToDefinition(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return "No definition found.";
        }
        return "Defined in " + formatLocation(result);
    }

    public static String formatFindReferences(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "No references found.";
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupByFile(result).entrySet()) {
            lines.add(entry.getKey() + ":");
            for (Map<String, Object> location : entry.getValue()) {
                Map<String, Object> start = nestedMap(nestedMap(location.get("range")).get("start"));
                lines.add("  " + (intValue(start.get("line")) + 1) + ":" + (intValue(start.get("character")) + 1));
            }
        }
        return String.join("\n", lines);
    }

    public static String formatDocumentSymbol(Object symbols) {
        if (symbols == null) {
            return "No symbols found.";
        }
        List<Map<String, Object>> list = asSymbolList(symbols);
        if (list.isEmpty()) {
            return "No symbols found.";
        }
        boolean isTree = list.get(0).containsKey("children");
        if (isTree) {
            return formatSymbolTree(list, 0);
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> symbol : list) {
            String name = stringValueOrDefault(symbol.get("name"), "?");
            String kind = SYMBOL_KIND_MAP.getOrDefault(intValue(symbol.get("kind")), "?");
            Map<String, Object> location = nestedMap(symbol.get("location"));
            String path = formatUri(stringValue(location.get("uri")));
            int line = intValue(nestedMap(nestedMap(location.get("range")).get("start")).get("line")) + 1;
            String container = stringValue(symbol.get("containerName"));
            if (!container.isEmpty()) {
                lines.add(path + ":" + line + ": " + kind + " " + container + "." + name);
            } else {
                lines.add(path + ":" + line + ": " + kind + " " + name);
            }
        }
        return String.join("\n", lines);
    }

    public static String formatWorkspaceSymbol(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "No symbols found.";
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> symbol : result) {
            Map<String, Object> location = nestedMap(symbol.get("location"));
            String uri = location.isEmpty() ? stringValue(symbol.get("uri")) : stringValue(location.get("uri"));
            groups.computeIfAbsent(formatUri(uri), ignored -> new ArrayList<>()).add(symbol);
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            lines.add(entry.getKey() + ":");
            for (Map<String, Object> symbol : entry.getValue()) {
                String name = stringValueOrDefault(symbol.get("name"), "?");
                String kind = SYMBOL_KIND_MAP.getOrDefault(intValue(symbol.get("kind")), "?");
                int line = intValue(nestedMap(nestedMap(nestedMap(symbol.get("location")).get("range")).get("start")).get("line")) + 1;
                String container = stringValue(symbol.get("containerName"));
                if (!container.isEmpty()) {
                    lines.add("  " + line + ": " + kind + " " + container + "." + name);
                } else {
                    lines.add("  " + line + ": " + kind + " " + name);
                }
            }
        }
        return String.join("\n", lines);
    }

    public static String formatPrepareCallHierarchy(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "No call hierarchy available.";
        }
        if (result.size() == 1) {
            Map<String, Object> item = result.get(0);
            Map<String, Object> location = nestedMap(item.containsKey("originSelectionRange")
                    ? item.get("originSelectionRange")
                    : item.get("range"));
            String path = formatUri(stringValue(item.get("uri")));
            int line = intValue(nestedMap(location.get("start")).get("line")) + 1;
            return path + ":" + line + ": " + stringValueOrDefault(item.get("name"), "?");
        }
        List<String> lines = new ArrayList<>();
        lines.add(result.size() + " call hierarchy items:");
        for (Map<String, Object> item : result) {
            Map<String, Object> location = nestedMap(item.containsKey("originSelectionRange")
                    ? item.get("originSelectionRange")
                    : item.get("range"));
            String path = formatUri(stringValue(item.get("uri")));
            int line = intValue(nestedMap(location.get("start")).get("line")) + 1;
            lines.add("  " + path + ":" + line + ": " + stringValueOrDefault(item.get("name"), "?"));
        }
        return String.join("\n", lines);
    }

    public static String formatIncomingCalls(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "No incoming calls found.";
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> item : result) {
            String uri = stringValue(nestedMap(item.get("from")).get("uri"));
            groups.computeIfAbsent(formatUri(uri), ignored -> new ArrayList<>()).add(item);
        }

        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            lines.add(entry.getKey() + ":");
            for (Map<String, Object> call : entry.getValue()) {
                Map<String, Object> caller = nestedMap(call.get("from"));
                List<Map<String, Object>> ranges = asSymbolList(call.get("fromRanges"));
                String callerPath = formatUri(stringValue(caller.get("uri")));
                int callerLine = intValue(nestedMap(nestedMap(caller.get("range")).get("start")).get("line")) + 1;
                for (Map<String, Object> range : ranges) {
                    Map<String, Object> start = nestedMap(range.get("start"));
                    lines.add("  " + callerPath + ":" + callerLine + " -> call site "
                            + (intValue(start.get("line")) + 1) + ":" + (intValue(start.get("character")) + 1));
                }
            }
        }
        return String.join("\n", lines);
    }

    public static String formatOutgoingCalls(List<Map<String, Object>> result) {
        if (result == null || result.isEmpty()) {
            return "No outgoing calls found.";
        }
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> call : result) {
            Map<String, Object> callee = nestedMap(call.get("to"));
            List<Map<String, Object>> ranges = asSymbolList(call.get("fromRanges"));
            String calleeName = stringValueOrDefault(callee.get("name"), "?");
            String calleePath = formatUri(stringValue(callee.get("uri")));
            int calleeLine = intValue(nestedMap(nestedMap(callee.get("range")).get("start")).get("line")) + 1;
            for (Map<String, Object> range : ranges) {
                Map<String, Object> start = nestedMap(range.get("start"));
                lines.add("  call site " + (intValue(start.get("line")) + 1) + ":" + (intValue(start.get("character")) + 1)
                        + " -> " + calleeName + " (" + calleePath + ":" + calleeLine + ")");
            }
        }
        return lines.isEmpty() ? "No outgoing calls found." : String.join("\n", lines);
    }

    public static String formatResult(LspOperation operation, Object result) {
        return switch (operation) {
            case GO_TO_DEFINITION, GO_TO_IMPLEMENTATION -> formatGoToDefinition(nestedMap(result));
            case FIND_REFERENCES -> formatFindReferences(asSymbolList(result));
            case DOCUMENT_SYMBOL -> formatDocumentSymbol(result);
            case WORKSPACE_SYMBOL -> formatWorkspaceSymbol(asSymbolList(result));
            case PREPARE_CALL_HIERARCHY -> formatPrepareCallHierarchy(asSymbolList(result));
            case INCOMING_CALLS -> formatIncomingCalls(asSymbolList(result));
            case OUTGOING_CALLS -> formatOutgoingCalls(asSymbolList(result));
        };
    }

    private static String formatSymbolTree(List<Map<String, Object>> symbols, int indent) {
        List<String> lines = new ArrayList<>();
        for (Map<String, Object> symbol : symbols) {
            String name = stringValueOrDefault(symbol.get("name"), "?");
            String kind = SYMBOL_KIND_MAP.getOrDefault(intValue(symbol.get("kind")), "?");
            String detail = stringValue(symbol.get("detail"));
            lines.add("  ".repeat(indent) + kind + " " + name + (detail.isEmpty() ? "" : " - " + detail));
            List<Map<String, Object>> children = asSymbolList(symbol.get("children"));
            if (!children.isEmpty()) {
                lines.add(formatSymbolTree(children, indent + 1));
            }
        }
        return String.join("\n", lines);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asSymbolList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                result.add(nestedMap(item));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            return List.of((Map<String, Object>) map);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringValueOrDefault(Object value, String fallback) {
        String text = stringValue(value);
        return text.isEmpty() ? fallback : text;
    }
}
