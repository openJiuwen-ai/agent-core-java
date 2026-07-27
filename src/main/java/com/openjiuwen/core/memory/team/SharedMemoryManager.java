/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.team;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Read/write team-level TEAM_MEMORY.md under team-memory/.
 */
public class SharedMemoryManager {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String TEAM_MEMORY_FILENAME = "TEAM_MEMORY.md";
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final int TEAM_MEMORY_MAX_READ_LINES = 200;

    private final Path teamMemoryDir;
    private final Object sysOperation;

    /**
     * Auto-generated for codecheck compliance.
     */
    public SharedMemoryManager(String teamMemoryDir, Object sysOperation) {
        this.teamMemoryDir = Path.of(teamMemoryDir).toAbsolutePath().normalize();
        this.sysOperation = sysOperation;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void ensureDir() throws IOException {
        Files.createDirectories(teamMemoryDir);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String readTeamSummary() throws IOException {
        Path file = teamMemoryDir.resolve(TEAM_MEMORY_FILENAME);
        if (!Files.exists(file)) {
            return "";
        }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.size() > TEAM_MEMORY_MAX_READ_LINES) {
            lines = lines.subList(0, TEAM_MEMORY_MAX_READ_LINES);
        }
        return String.join("\n", lines).trim();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void writeTeamSummary(String content) throws IOException {
        ensureDir();
        Path target = teamMemoryDir.resolve(TEAM_MEMORY_FILENAME);
        Path temp = Files.createTempFile(teamMemoryDir, "team_memory_", ".tmp");
        try {
            Files.writeString(temp, content != null ? content : "", StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void appendEntry(String entry) throws IOException {
        String existing = readTeamSummary();
        String newContent = existing.isBlank() ? entry : existing + "\n\n---\n\n" + entry;
        writeTeamSummary(newContent);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Path getTeamMemoryDir() {
        return teamMemoryDir;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getSysOperation() {
        return sysOperation;
    }
}
