/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import com.openjiuwen.harness.lsp.query.LspDiagnostic;
import com.openjiuwen.harness.lsp.query.LspDiagnosticFile;
import com.openjiuwen.harness.lsp.query.LspLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimal LSP diagnostic registry.
 *
 * <p>Mirrors Python's diagnostic registry in {@code openjiuwen.harness.lsp.core.diagnostic_registry}.
 */
public final class LspDiagnosticRegistry {

    public static final int MAX_DIAG_PER_FILE = 10;
    public static final int MAX_DIAG_TOTAL = 30;

    private static LspDiagnosticRegistry instance;

    private final Map<String, List<LspDiagnostic>> pending = new LinkedHashMap<>();
    private final Map<String, Set<String>> delivered = new LinkedHashMap<>();
    private final Map<String, String> serverNames = new LinkedHashMap<>();

    private LspDiagnosticRegistry() {
    }

    public static synchronized LspDiagnosticRegistry getInstance() {
        if (instance == null) {
            instance = new LspDiagnosticRegistry();
        }
        return instance;
    }

    public static synchronized void reset() {
        instance = null;
    }

    public synchronized void push(String filePath, List<LspDiagnostic> diagnostics) {
        if (filePath == null || diagnostics == null || diagnostics.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(filePath, ignored -> new ArrayList<>()).addAll(diagnostics);
    }

    public synchronized String register(String serverName, String uri, List<Map<String, Object>> rawDiagnostics) {
        List<LspDiagnostic> diagnostics = parseRaw(uri, rawDiagnostics);
        if (diagnostics.isEmpty()) {
            return "";
        }
        String filePath = FileUriUtils.fileUriToPath(uri);
        String batchId = UUID.randomUUID().toString();
        pending.computeIfAbsent(filePath, ignored -> new ArrayList<>()).addAll(diagnostics);
        if (serverName != null && !serverName.isBlank()) {
            serverNames.put(filePath, serverName);
        }
        return batchId;
    }

    public synchronized List<LspDiagnosticFile> getAndClear(int maxPerFile, int maxTotal) {
        List<LspDiagnosticFile> result = new ArrayList<>();
        int remaining = maxTotal > 0 ? maxTotal : MAX_DIAG_TOTAL;
        int perFileLimit = maxPerFile > 0 ? maxPerFile : MAX_DIAG_PER_FILE;

        for (Map.Entry<String, List<LspDiagnostic>> entry : pending.entrySet()) {
            if (remaining <= 0) {
                break;
            }
            List<LspDiagnostic> diagnostics = dedup(entry.getKey(), entry.getValue());
            diagnostics.sort(Comparator.comparingInt(this::severityRank));
            int limit = Math.min(perFileLimit, Math.min(remaining, diagnostics.size()));
            if (limit <= 0) {
                continue;
            }
            List<LspDiagnostic> slice = new ArrayList<>(diagnostics.subList(0, limit));
            remaining -= slice.size();
            delivered.computeIfAbsent(entry.getKey(), ignored -> new HashSet<>())
                    .addAll(slice.stream().map(this::diagKey).toList());
            result.add(new LspDiagnosticFile(
                    entry.getKey(),
                    FileUriUtils.pathToFileUri(entry.getKey()),
                    serverNames.getOrDefault(entry.getKey(), ""),
                    slice
            ));
        }
        pending.clear();
        return result;
    }

    private List<LspDiagnostic> parseRaw(String uri, List<Map<String, Object>> rawDiagnostics) {
        if (rawDiagnostics == null || rawDiagnostics.isEmpty()) {
            return List.of();
        }
        String filePath = FileUriUtils.fileUriToPath(uri);
        List<LspDiagnostic> diagnostics = new ArrayList<>();
        for (Map<String, Object> item : rawDiagnostics) {
            if (item == null) {
                continue;
            }
            String message = item.get("message") != null ? String.valueOf(item.get("message")) : "";
            if (message.isBlank()) {
                continue;
            }
            String severity = mapSeverity(item.get("severity"));
            Map<String, Object> range = item.get("range") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            Map<String, Object> start = range.get("start") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
            int line = intValue(start.get("line")) + 1;
            int character = intValue(start.get("character")) + 1;
            diagnostics.add(new LspDiagnostic(severity, message, new LspLocation(filePath, line, character)));
        }
        return diagnostics;
    }

    private String mapSeverity(Object rawSeverity) {
        int severity = intValue(rawSeverity);
        return switch (severity) {
            case 1 -> "error";
            case 2 -> "warning";
            case 4 -> "hint";
            default -> "information";
        };
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value != null ? Integer.parseInt(String.valueOf(value)) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<LspDiagnostic> dedup(String filePath, List<LspDiagnostic> diagnostics) {
        Map<String, LspDiagnostic> unique = new LinkedHashMap<>();
        Set<String> alreadyDelivered = delivered.getOrDefault(filePath, Set.of());
        for (LspDiagnostic diagnostic : diagnostics) {
            String key = diagKey(diagnostic);
            if (alreadyDelivered.contains(key)) {
                continue;
            }
            unique.putIfAbsent(key, diagnostic);
        }
        return new ArrayList<>(unique.values());
    }

    private String diagKey(LspDiagnostic diagnostic) {
        if (diagnostic == null) {
            return "";
        }
        LspLocationLike location = new LspLocationLike(diagnostic.getLocation());
        return diagnostic.getMessage() + "|" + diagnostic.getSeverity() + "|"
                + location.line() + ":" + location.character();
    }

    private int severityRank(LspDiagnostic diagnostic) {
        if (diagnostic == null || diagnostic.getSeverity() == null) {
            return 3;
        }
        return switch (diagnostic.getSeverity().toLowerCase()) {
            case "error" -> 1;
            case "warning" -> 2;
            case "hint" -> 4;
            default -> 3;
        };
    }

    private record LspLocationLike(int line, int character) {
        private LspLocationLike(com.openjiuwen.harness.lsp.query.LspLocation location) {
            this(location != null ? location.getLine() : 0, location != null ? location.getCharacter() : 0);
        }
    }
}
