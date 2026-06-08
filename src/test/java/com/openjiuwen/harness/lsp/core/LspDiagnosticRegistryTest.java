/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's registry-focused LSP diagnostic behaviors in
 * {@code tests/unit_tests/harness/tools/test_lsp_diagnostics.py}.
 */
class LspDiagnosticRegistryTest {

    @BeforeEach
    void setUp() {
        LspDiagnosticRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        LspDiagnosticRegistry.reset();
    }

    @Test
    void parseRawDropsInvalidEntriesAndNormalizesOptionalFields() {
        List<LspDiagnosticItem> items = LspDiagnosticRegistry.parseRaw(List.of(
                "not-a-map",
                Map.of("message", "", "severity", 1),
                diagnostic("first", 1, 4, 2, "pyright", 1001),
                diagnostic("second", 0, 7, 9, "", null)
        ));

        assertEquals(2, items.size());
        assertEquals("first", items.get(0).getMessage());
        assertEquals(1, items.get(0).getSeverity());
        assertEquals("pyright", items.get(0).getSource());
        assertEquals(1001, items.get(0).getCode());
        assertNull(items.get(1).getSource());
        assertEquals(0, items.get(1).getSeverity());
    }

    @Test
    void registerReturnsEmptyForInvalidPayloadsAndTracksPendingCount() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();

        assertEquals("", registry.register("pyright", "file:///workspace/a.py", List.of("bad")));
        assertEquals(0, registry.getPendingCount());

        String batchId = registry.register("pyright", "file:///workspace/a.py", List.of(diagnostic("err", 1, 0, 0)));

        assertNotEquals("", batchId);
        assertEquals(1, registry.getPendingCount());
    }

    @Test
    void getAndClearDeduplicatesWithinAndAcrossRounds() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        Map<String, Object> duplicate = diagnostic("same", 1, 0, 0);

        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));
        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));

        List<LspDiagnosticFile> first = registry.getAndClear(10, 30);
        assertEquals(1, first.size());
        assertEquals(1, first.get(0).getDiagnostics().size());
        assertEquals(0, registry.getPendingCount());

        registry.register("pyright", "file:///workspace/a.py", List.of(duplicate));
        assertTrue(registry.getAndClear(10, 30).isEmpty());
    }

    @Test
    void getAndClearSortsBySeverityAndAppliesPerFileAndGlobalCaps() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();

        registry.register("pyright", "file:///workspace/a.py", List.of(
                diagnostic("hint", 4, 3, 0),
                diagnostic("warning", 2, 1, 0),
                diagnostic("error", 1, 0, 0),
                diagnostic("info", 3, 2, 0)
        ));
        registry.register("ruff", "file:///workspace/b.py", List.of(
                diagnostic("b1", 2, 0, 0),
                diagnostic("b2", 2, 1, 0)
        ));

        List<LspDiagnosticFile> files = registry.getAndClear(2, 3);

        assertEquals(2, files.size());
        assertEquals(List.of("error", "warning"), files.get(0).getDiagnostics().stream()
                .map(LspDiagnosticItem::getMessage)
                .toList());
        assertEquals(1, files.get(1).getDiagnostics().size());
    }

    @Test
    void getAndClearPreservesServerNameAndResolvesLocalPath() {
        LspDiagnosticRegistry registry = LspDiagnosticRegistry.getInstance();
        String uri = "file:///workspace/example.py";

        registry.register("my-lsp", uri, List.of(diagnostic("err", 1, 5, 7)));

        LspDiagnosticFile file = registry.getAndClear(10, 30).get(0);

        assertEquals(uri, file.getUri());
        assertEquals("my-lsp", file.getServerName());
        assertEquals(
                com.openjiuwen.harness.lsp.core.utils.FileUriUtils.fileUriToPath(uri),
                file.getLocalPath()
        );
        assertEquals("err|1|5:7|null", LspDiagnosticRegistry.diagKey(file.getDiagnostics().get(0)));
    }

    private static Map<String, Object> diagnostic(String message, int severity, int line, int character) {
        return diagnostic(message, severity, line, character, null, null);
    }

    private static Map<String, Object> diagnostic(
            String message,
            int severity,
            int line,
            int character,
            String source,
            Object code
    ) {
        Map<String, Object> start = new LinkedHashMap<>();
        start.put("line", line);
        start.put("character", character);
        Map<String, Object> end = new LinkedHashMap<>();
        end.put("line", line);
        end.put("character", character + 1);
        Map<String, Object> range = new LinkedHashMap<>();
        range.put("start", start);
        range.put("end", end);
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("message", message);
        diagnostic.put("severity", severity);
        diagnostic.put("range", range);
        if (source != null) {
            diagnostic.put("source", source);
        }
        if (code != null) {
            diagnostic.put("code", code);
        }
        return diagnostic;
    }
}
