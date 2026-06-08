/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import com.openjiuwen.harness.lsp.core.utils.FileUriUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mirrors Python's {@code LspDiagnosticRegistry} in
 * {@code openjiuwen/harness/lsp/core/diagnostic_registry.py}.
 */
public final class LspDiagnosticRegistry {

    public static final int MAX_DIAG_PER_FILE = 10;
    public static final int MAX_DIAG_TOTAL = 30;

    private static LspDiagnosticRegistry instance;

    private final Map<String, PendingBatch> pending = new LinkedHashMap<>();
    private final Map<String, Set<String>> delivered = new LinkedHashMap<>();

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

    public int getPendingCount() {
        return pending.size();
    }

    public String register(String serverName, String uri, List<?> rawDiagnostics) {
        List<LspDiagnosticItem> items = parseRaw(rawDiagnostics);
        if (items.isEmpty()) {
            return "";
        }
        String batchId = UUID.randomUUID().toString();
        pending.put(batchId, new PendingBatch(serverName == null ? "" : serverName, uri, items));
        return batchId;
    }

    public List<LspDiagnosticFile> getAndClear(int maxPerFile, int maxTotal) {
        if (pending.isEmpty() || maxPerFile <= 0 || maxTotal <= 0) {
            pending.clear();
            return List.of();
        }

        Map<String, UriBuffer> byUri = new LinkedHashMap<>();
        for (PendingBatch batch : pending.values()) {
            UriBuffer buffer = byUri.computeIfAbsent(
                    batch.uri(),
                    ignored -> new UriBuffer(batch.serverName(), new LinkedHashMap<>())
            );
            for (LspDiagnosticItem item : batch.items()) {
                String key = diagKey(item);
                buffer.uniqueByKey().putIfAbsent(key, item);
            }
        }
        pending.clear();

        Map<String, FreshDiagnostics> freshByUri = new LinkedHashMap<>();
        for (Map.Entry<String, UriBuffer> entry : byUri.entrySet()) {
            String uri = entry.getKey();
            UriBuffer buffer = entry.getValue();
            Set<String> deliveredKeys = delivered.getOrDefault(uri, Set.of());
            List<LspDiagnosticItem> freshItems = new ArrayList<>();
            for (Map.Entry<String, LspDiagnosticItem> diagnosticEntry : buffer.uniqueByKey().entrySet()) {
                if (!deliveredKeys.contains(diagnosticEntry.getKey())) {
                    freshItems.add(diagnosticEntry.getValue());
                }
            }
            if (!freshItems.isEmpty()) {
                freshItems.sort((left, right) -> Integer.compare(left.getSeverity(), right.getSeverity()));
                if (freshItems.size() > maxPerFile) {
                    freshItems = new ArrayList<>(freshItems.subList(0, maxPerFile));
                }
                freshByUri.put(uri, new FreshDiagnostics(buffer.serverName(), freshItems));
            }
        }

        if (freshByUri.isEmpty()) {
            return List.of();
        }

        List<LspDiagnosticFile> result = new ArrayList<>();
        int total = 0;
        for (Map.Entry<String, FreshDiagnostics> entry : freshByUri.entrySet()) {
            if (total >= maxTotal) {
                break;
            }
            List<LspDiagnosticItem> items = entry.getValue().items();
            int remaining = maxTotal - total;
            List<LspDiagnosticItem> clipped = items.size() > remaining
                    ? new ArrayList<>(items.subList(0, remaining))
                    : new ArrayList<>(items);
            if (clipped.isEmpty()) {
                continue;
            }
            String uri = entry.getKey();
            result.add(new LspDiagnosticFile(
                    uri,
                    clipped,
                    entry.getValue().serverName(),
                    FileUriUtils.fileUriToPath(uri)
            ));
            Set<String> seen = delivered.computeIfAbsent(uri, ignored -> new LinkedHashSet<>());
            for (LspDiagnosticItem item : clipped) {
                seen.add(diagKey(item));
            }
            total += clipped.size();
        }

        return result;
    }

    public void clearAll() {
        pending.clear();
        delivered.clear();
    }

    public static List<LspDiagnosticItem> parseRaw(List<?> rawList) {
        List<LspDiagnosticItem> items = new ArrayList<>();
        if (rawList == null) {
            return items;
        }
        for (Object entry : rawList) {
            if (!(entry instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object rawMessage = rawMap.get("message");
            String message = rawMessage == null ? "" : String.valueOf(rawMessage);
            if (message.isEmpty()) {
                continue;
            }
            int severity = intValue(rawMap.get("severity"), 3);
            Map<String, Object> range = mapValue(rawMap.get("range"));
            Object rawSource = rawMap.get("source");
            String source = rawSource == null ? null : String.valueOf(rawSource);
            if (source != null && source.isEmpty()) {
                source = null;
            }
            items.add(new LspDiagnosticItem(message, severity, range, source, rawMap.get("code")));
        }
        return items;
    }

    public static String diagKey(LspDiagnosticItem item) {
        if (item == null) {
            return "";
        }
        Map<String, Object> start = mapValue(item.getRange().get("start"));
        int line = intValue(start.get("line"), 0);
        int character = intValue(start.get("character"), 0);
        return item.getMessage() + "|" + item.getSeverity() + "|" + line + ":" + character + "|" + item.getCode();
    }

    private static Map<String, Object> mapValue(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return result;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record PendingBatch(String serverName, String uri, List<LspDiagnosticItem> items) {
    }

    private record UriBuffer(String serverName, Map<String, LspDiagnosticItem> uniqueByKey) {
    }

    private record FreshDiagnostics(String serverName, List<LspDiagnosticItem> items) {
    }
}
