/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.tools.filesystem.FileOpHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code test_file_op_history.py} from
 * {@code tests/unit_tests/harness/tools/test_file_op_history.py}.
 */
@DisplayName("FileOpHistory Tests")
class TestFileOpHistory {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<Map<String, Object>>>> HISTORY_TYPE =
            new TypeReference<>() {};

    @Test
    void testCreatesHistoryFile(@TempDir Path tempDir) {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "content");

        assertTrue(Files.exists(historyPath));
    }

    @Test
    void testEntryFields(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "hello");

        Map<String, Object> entry = load(historyPath).get("/foo/bar.py").get(0);
        assertEquals("write", entry.get("action"));
        assertNull(entry.get("old_content"));
        assertEquals("hello", entry.get("new_content"));
        assertNotNull(entry.get("timestamp"));
    }

    @Test
    void testOldContentNoneForCreate(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/new.py", "write", null, "body");

        assertNull(load(historyPath).get("/foo/new.py").get(0).get("old_content"));
    }

    @Test
    void testEditPreservesOldAndNew(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", "old", "new");

        Map<String, Object> entry = load(historyPath).get("/foo/bar.py").get(0);
        assertEquals("edit", entry.get("action"));
        assertEquals("old", entry.get("old_content"));
        assertEquals("new", entry.get("new_content"));
    }

    @Test
    void testMultipleEntriesAppendedInOrder(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "v1");
        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v1", "v2");
        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v2", "v3");

        List<Map<String, Object>> entries = load(historyPath).get("/foo/bar.py");
        assertEquals(3, entries.size());
        assertEquals(List.of("v1", "v2", "v3"), entries.stream().map(entry -> entry.get("new_content")).toList());
    }

    @Test
    void testMultipleFilesTrackedSeparately(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/a.py", "write", null, "a");
        FileOpHistory.appendOpHistory(historyPath, "/foo/b.py", "write", null, "b");

        Map<String, List<Map<String, Object>>> data = load(historyPath);
        assertEquals("a", data.get("/foo/a.py").get(0).get("new_content"));
        assertEquals("b", data.get("/foo/b.py").get(0).get("new_content"));
    }

    @Test
    void testAppendsToExistingHistory(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "v1");
        FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", "v1", "v2");

        assertEquals(2, load(historyPath).get("/foo/bar.py").size());
    }

    @Test
    void testExistingOtherFileEntriesPreserved(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        FileOpHistory.appendOpHistory(historyPath, "/foo/a.py", "write", null, "a");
        FileOpHistory.appendOpHistory(historyPath, "/foo/b.py", "write", null, "b");
        FileOpHistory.appendOpHistory(historyPath, "/foo/a.py", "edit", "a", "a2");

        Map<String, List<Map<String, Object>>> data = load(historyPath);
        assertEquals(2, data.get("/foo/a.py").size());
        assertEquals(1, data.get("/foo/b.py").size());
    }

    @Test
    void testEntriesCappedAtMax(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        for (int i = 0; i < FileOpHistory.MAX_HISTORY_PER_FILE + 10; i++) {
            FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", String.valueOf(i),
                    String.valueOf(i + 1));
        }

        assertEquals(FileOpHistory.MAX_HISTORY_PER_FILE, load(historyPath).get("/foo/bar.py").size());
    }

    @Test
    void testOldestEntriesDroppedWhenCapped(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);

        for (int i = 0; i < FileOpHistory.MAX_HISTORY_PER_FILE + 5; i++) {
            FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", String.valueOf(i),
                    String.valueOf(i + 1));
        }

        List<Map<String, Object>> entries = load(historyPath).get("/foo/bar.py");
        assertEquals("5", entries.get(0).get("old_content"));
        assertEquals(String.valueOf(FileOpHistory.MAX_HISTORY_PER_FILE + 4),
                entries.get(entries.size() - 1).get("old_content"));
    }

    @Test
    void testInvalidHistoryPathDoesNotRaise() {
        String badPath = String.valueOf((char) 0) + "bad";

        assertDoesNotThrow(() -> FileOpHistory.appendOpHistory(badPath, "/foo/bar.py", "write", null, "content"));
    }

    @Test
    void testCorruptedJsonDoesNotRaise(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);
        Files.createDirectories(historyPath.getParent());
        Files.writeString(historyPath, "not valid json{{{");

        assertDoesNotThrow(() -> FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "write", null, "content"));
    }

    @Test
    void testConcurrentCoroutinesDoNotCorrupt(@TempDir Path tempDir) throws Exception {
        Path historyPath = historyPath(tempDir);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                int current = i;
                tasks.add(() -> {
                    FileOpHistory.appendOpHistory(historyPath, "/foo/bar.py", "edit", String.valueOf(current),
                            String.valueOf(current + 1));
                    return null;
                });
            }
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        Map<String, List<Map<String, Object>>> data = load(historyPath);
        assertTrue(data.containsKey("/foo/bar.py"));
        assertTrue(data.get("/foo/bar.py").size() <= FileOpHistory.MAX_HISTORY_PER_FILE);
    }

    private static Path historyPath(Path tempDir) {
        return tempDir.resolve(".agent_history").resolve("file_ops_test.json");
    }

    private static Map<String, List<Map<String, Object>>> load(Path path) throws Exception {
        return MAPPER.readValue(path.toFile(), HISTORY_TYPE);
    }
}
