package com.openjiuwen.harness.tools.filesystem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * File operation history persistence.
 *
 * <p>Mirrors Python's {@code _append_op_history} in
 * {@code openjiuwen.harness.tools.filesystem}.
 */
public final class FileOpHistory {
    public static final int MAX_HISTORY_PER_FILE = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, List<Map<String, Object>>>> HISTORY_TYPE =
            new TypeReference<>() {};
    private static final ReentrantLock HISTORY_LOCK = new ReentrantLock();
    private static final Logger LOGGER = Logger.getLogger(FileOpHistory.class.getName());

    private FileOpHistory() {
    }

    public static void appendOpHistory(String historyPath, String filePath, String action,
            String oldContent, String newContent) {
        try {
            appendOpHistory(Path.of(historyPath), filePath, action, oldContent, newContent);
        } catch (Exception exc) {
            LOGGER.warning("[appendOpHistory] Failed to persist file op history to "
                    + historyPath + ": " + exc.getMessage());
        }
    }

    public static void appendOpHistory(Path historyPath, String filePath, String action,
            String oldContent, String newContent) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("action", action);
        entry.put("timestamp", OffsetDateTime.now(ZoneOffset.UTC).toString());
        entry.put("old_content", oldContent);
        entry.put("new_content", newContent);

        try {
            HISTORY_LOCK.lock();
            Map<String, List<Map<String, Object>>> history = new LinkedHashMap<>();
            if (Files.exists(historyPath)) {
                history = MAPPER.readValue(historyPath.toFile(), HISTORY_TYPE);
            }
            List<Map<String, Object>> entries = history.computeIfAbsent(filePath, ignored -> new ArrayList<>());
            entries.add(entry);
            if (entries.size() > MAX_HISTORY_PER_FILE) {
                history.put(filePath, new ArrayList<>(entries.subList(entries.size() - MAX_HISTORY_PER_FILE,
                        entries.size())));
            }
            Path parent = historyPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Path.of(historyPath.toString() + ".tmp");
            Files.writeString(temp, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(history),
                    StandardCharsets.UTF_8);
            replace(temp, historyPath);
        } catch (Exception exc) {
            LOGGER.warning("[appendOpHistory] Failed to persist file op history to "
                    + historyPath + ": " + exc.getMessage());
        } finally {
            if (HISTORY_LOCK.isHeldByCurrentThread()) {
                HISTORY_LOCK.unlock();
            }
        }
    }

    private static void replace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | InvalidPathException exc) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
