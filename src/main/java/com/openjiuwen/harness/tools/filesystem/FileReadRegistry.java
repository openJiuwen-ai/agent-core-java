package com.openjiuwen.harness.tools.filesystem;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last file content observed through read-file operations.
 *
 * <p>Mirrors Python's in-memory read-before-write guard used by filesystem tools.
 */
public final class FileReadRegistry {

    private static final Map<String, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    private FileReadRegistry() {
    }

    public static void remember(String path, String content) {
        if (path == null || path.isBlank()) {
            return;
        }
        SNAPSHOTS.put(path, new Snapshot(path, content));
    }

    public static Optional<Snapshot> get(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(SNAPSHOTS.get(path));
    }

    public static void forget(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        SNAPSHOTS.remove(path);
    }

    public record Snapshot(String path, String content) {
    }
}
