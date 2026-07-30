/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class StdioClientSecurityTest {
    @TempDir
    Path tempDir;

    @Test
    void allowsOnlyCommandDeclaredByServerPath() throws Exception {
        Path javaExecutable = javaExecutable("java");
        McpServerConfig config = McpServerConfig.builder()
                .serverPath(javaExecutable.toString())
                .params(Map.of("command", javaExecutable.toString()))
                .build();

        assertEquals(javaExecutable.toString(), StdioClient.resolveAllowedCommand(config));

        McpServerConfig mismatched = McpServerConfig.builder()
                .serverPath(javaExecutable.toString())
                .params(Map.of("command", javaExecutable("javac").toString()))
                .build();
        assertThrows(SecurityException.class, () -> StdioClient.resolveAllowedCommand(mismatched));
    }

    @Test
    void supportsLegacyBlankServerPathOnlyForDefaultAllowlist() throws Exception {
        Path javaExecutable = javaExecutable("java");
        McpServerConfig compatible = McpServerConfig.builder()
                .serverPath("")
                .params(Map.of("command", javaExecutable.toString()))
                .build();
        assertEquals(javaExecutable.toString(), StdioClient.resolveAllowedCommand(compatible));

        McpServerConfig arbitrary = McpServerConfig.builder()
                .serverPath("")
                .params(Map.of("command", javaExecutable("javac").toString()))
                .build();
        assertThrows(SecurityException.class, () -> StdioClient.resolveAllowedCommand(arbitrary));
    }

    @Test
    void rejectsBlankAndNonExecutableCommands() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> StdioClient.resolveAllowedCommand(McpServerConfig.builder().serverPath(" ").build()));

        // Prefer a directory: on Windows Files.isExecutable often returns true for plain files.
        Path nonExecutable = Files.createDirectory(tempDir.resolve("not-a-binary"));
        McpServerConfig config = McpServerConfig.builder()
                .serverPath(nonExecutable.toString())
                .params(Map.of("command", nonExecutable.toString()))
                .build();
        assertThrows(SecurityException.class, () -> StdioClient.resolveAllowedCommand(config));
    }

    private static Path javaExecutable(String command) throws Exception {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? command + ".exe"
                : command;
        return Path.of(System.getProperty("java.home"), "bin", executableName).toRealPath();
    }
}
