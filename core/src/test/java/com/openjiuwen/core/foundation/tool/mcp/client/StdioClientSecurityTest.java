/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void supportsAllowlistedAbsoluteLauncherOutsidePath() throws Exception {
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String launcherName = isWindows ? "python3.exe" : "python3";
        Path launcher = tempDir.resolve(launcherName);
        Files.copy(javaExecutable("java"), launcher);
        launcher.toFile().setExecutable(true);
        assertTrue(Files.isExecutable(launcher));

        McpServerConfig config = McpServerConfig.builder()
                .serverPath("")
                .params(Map.of("command", launcher.toString()))
                .build();

        assertEquals(launcher.toAbsolutePath().normalize().toString(), StdioClient.resolveAllowedCommand(config));
    }

    @Test
    void preservesSymbolicLinkForAllowlistedAbsoluteLauncher() throws Exception {
        assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        Path binDir = Files.createDirectories(tempDir.resolve("venv").resolve("bin"));
        Path launcher = Files.createSymbolicLink(binDir.resolve("python3"), javaExecutable("java"));
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("")
                .params(Map.of("command", launcher.toString()))
                .build();

        String resolvedCommand = StdioClient.resolveAllowedCommand(config);

        assertEquals(launcher.toAbsolutePath().normalize().toString(), resolvedCommand);
        assertNotEquals(launcher.toRealPath().toString(), resolvedCommand);

        Path alias = Files.createSymbolicLink(binDir.resolve("python"), javaExecutable("java"));
        McpServerConfig explicitlyAllowlisted = McpServerConfig.builder()
                .serverPath(launcher.toString())
                .params(Map.of("command", alias.toString()))
                .build();
        assertEquals(launcher.toAbsolutePath().normalize().toString(),
                StdioClient.resolveAllowedCommand(explicitlyAllowlisted));

        Path brokenLauncher = Files.createSymbolicLink(binDir.resolve("java"), tempDir.resolve("missing-java"));
        McpServerConfig brokenConfig = McpServerConfig.builder()
                .serverPath("")
                .params(Map.of("command", brokenLauncher.toString()))
                .build();
        assertThrows(IOException.class, () -> StdioClient.resolveAllowedCommand(brokenConfig));
    }

    @Test
    void rejectsBlankAndNonExecutableCommands() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> StdioClient.resolveAllowedCommand(McpServerConfig.builder().serverPath(" ").build()));

        assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"),
                "Windows may treat extensionless scripts as executable");
        Path nonExecutable = Files.writeString(tempDir.resolve("server"), "#!/bin/sh\n");
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
