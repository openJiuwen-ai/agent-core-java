/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.tools.browser_move.test_file_uploads} in
 * {@code tests/unit_tests/harness/tools/browser_move/test_file_uploads.py}.</p>
 */
class ActionControllerFileUploadTest {

    @TempDir
    Path tempDir;

    @Test
    void listDirFilesReturnsFileEntriesWithMetadata() throws Exception {
        Files.write(tempDir.resolve("doc.pdf"), "hello".getBytes());
        Files.write(tempDir.resolve("img.png"), "xx".getBytes());

        List<Map<String, Object>> result = ActionController.listDirFiles(tempDir);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(entry -> "doc.pdf".equals(entry.get("name"))));
        assertTrue(result.stream().allMatch(entry -> entry.containsKey("path") && entry.containsKey("size_bytes")));
    }

    @Test
    void listDirFilesHandlesMissingDirectory() {
        List<Map<String, Object>> result = ActionController.listDirFiles(tempDir.resolve("missing"));
        assertEquals(List.of(), result);
    }

    @Test
    void buildSetInputFilesScriptEmbedsSelectorAndPaths() {
        String script = ActionController.buildSetInputFilesScript("#upload", List.of("/data/a.pdf", "/data/b.csv"));
        assertTrue(script.contains("#upload"));
        assertTrue(script.contains("/data/a.pdf"));
        assertTrue(script.contains("/data/b.csv"));
    }

    @Test
    void listUploadFilesReturnsErrorWhenEnvNotSet() {
        String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
        System.clearProperty("BROWSER_UPLOAD_ROOT");
        try {
            ActionController controller = controllerWithBuiltins();
            Map<String, Object> result = controller.runAction("list_upload_files", "", "", Map.of());
            assertFalse(Boolean.TRUE.equals(result.get("ok")));
            assertTrue(String.valueOf(result.get("error")).contains("BROWSER_UPLOAD_ROOT"));
            assertEquals(List.of(), result.get("files"));
        } finally {
            restoreProperty("BROWSER_UPLOAD_ROOT", previous);
        }
    }

    @Test
    void listUploadFilesReturnsErrorWhenDirMissing() {
        String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
        System.setProperty("BROWSER_UPLOAD_ROOT", tempDir.resolve("missing").toString());
        try {
            ActionController controller = controllerWithBuiltins();
            Map<String, Object> result = controller.runAction("list_upload_files", "", "", Map.of());
            assertFalse(Boolean.TRUE.equals(result.get("ok")));
            assertEquals(List.of(), result.get("files"));
        } finally {
            restoreProperty("BROWSER_UPLOAD_ROOT", previous);
        }
    }

    @Test
    void listUploadFilesReturnsFileListWhenDirExists() throws Exception {
        Files.write(tempDir.resolve("report.xlsx"), "data".getBytes());
        String previous = System.getProperty("BROWSER_UPLOAD_ROOT");
        System.setProperty("BROWSER_UPLOAD_ROOT", tempDir.toString());
        try {
            ActionController controller = controllerWithBuiltins();
            Map<String, Object> result = controller.runAction("list_upload_files", "", "", Map.of());
            assertTrue(Boolean.TRUE.equals(result.get("ok")));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> files = (List<Map<String, Object>>) result.get("files");
            assertTrue(files.stream().anyMatch(entry -> "report.xlsx".equals(entry.get("name"))));
        } finally {
            restoreProperty("BROWSER_UPLOAD_ROOT", previous);
        }
    }

    @Test
    void setInputFilesReturnsErrorWhenPathsEmpty() {
        ActionController controller = controllerWithBuiltins();
        Map<String, Object> result = controller.runAction("browser_set_input_files", "", "", Map.of("paths", List.of()));
        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertTrue(String.valueOf(result.get("error")).toLowerCase().contains("paths"));
    }

    @Test
    void setInputFilesUsesCodeExecutorWhenBound() {
        ActionController controller = controllerWithBuiltins();
        controller.bindCodeExecutor(jsCode -> CompletableFuture.completedFuture(
                Map.of("ok", true, "selector", "input[type=\"file\"]", "paths", List.of("/tmp/x.pdf"))));

        Map<String, Object> result = controller.runAction(
                "browser_set_input_files",
                "",
                "",
                Map.of("paths", List.of("/tmp/x.pdf")));

        assertTrue(Boolean.TRUE.equals(result.get("ok")));
    }

    @Test
    void setInputFilesDefaultsSelectorToFileInput() {
        ActionController controller = controllerWithBuiltins();
        final var captured = new java.util.ArrayList<String>();
        controller.bindCodeExecutor(jsCode -> {
            captured.add(jsCode);
            return CompletableFuture.completedFuture(
                    Map.of("ok", true, "selector", "input[type=\"file\"]", "paths", List.of("/tmp/f.txt")));
        });

        controller.runAction("browser_set_input_files", "", "", Map.of("paths", List.of("/tmp/f.txt")));

        assertFalse(captured.isEmpty());
        assertTrue(captured.getFirst().contains("input[type=\"file\"]"));
    }

    @Test
    void setInputFilesReturnsErrorWhenNoExecutorAndNoRunner() {
        ActionController controller = controllerWithBuiltins();
        controller.clearCodeExecutor();
        controller.clearRuntimeRunner();

        Map<String, Object> result = controller.runAction(
                "browser_set_input_files",
                "",
                "",
                Map.of("paths", List.of("/tmp/f.txt")));

        assertFalse(Boolean.TRUE.equals(result.get("ok")));
        assertTrue(String.valueOf(result.get("error")).contains("runtime_not_bound")
                || String.valueOf(result.get("error")).contains("bind_runtime"));
    }

    private static ActionController controllerWithBuiltins() {
        ActionController controller = new ActionController();
        controller.registerBuiltinActions();
        return controller;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
