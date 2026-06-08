/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.security;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code openjiuwen/core/common/security/user_config.py}.
 */
class UserConfigTest {

    @AfterEach
    void resetConfig() {
        UserConfig.resetForTests();
    }

    @Test
    void defaultsToSensitiveConfiguration() {
        UserConfig config = UserConfig.getConfig();

        assertTrue(UserConfig.isSensitive());
        assertEquals(UserConfig.DEFAULT_SENSITIVE_PATHS, config.getSensitivePathsList());
    }

    @Test
    void environmentOverrideCanDisableSensitivity() {
        UserConfig.setEnvReaderForTests(name -> "IS_SENSITIVE".equals(name) ? "false" : null);

        assertFalse(UserConfig.isSensitive());
    }

    @Test
    void readsIniConfigurationInsideWorkspace() throws IOException {
        Path configFile = createWorkspaceConfig("""
                [settings]
                is_sensitive = false
                sensitive_paths = /tmp/a, C:\\safe\\
                """);

        UserConfig.setConfigPath(configFile);

        assertFalse(UserConfig.isSensitive());
        assertEquals(List.of("/tmp/a", "C:\\safe\\"), UserConfig.getSensitivePaths());
    }

    @Test
    void rejectsConfigPathOutsideWorkspace(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        BaseError error = assertThrows(BaseError.class, () -> UserConfig.setConfigPath(tempDir.resolve("user.ini")));

        assertEquals(StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void rejectsReinitializingConfigPath() {
        UserConfig.getConfig();

        BaseError error = assertThrows(BaseError.class, () -> UserConfig.setConfigPath(Path.of("target", "user.ini")));

        assertEquals(StatusCode.COMMON_USER_CONFIG_PROCESS_ERROR, error.getStatus());
    }

    @Test
    void returnsCopyOfSensitivePaths() {
        List<String> first = UserConfig.getSensitivePaths();
        List<String> second = UserConfig.getSensitivePaths();

        assertNotSame(first, second);
        first.clear();
        assertEquals(UserConfig.DEFAULT_SENSITIVE_PATHS, UserConfig.getSensitivePaths());
    }

    @Test
    void canToggleSensitivityAtRuntime() {
        UserConfig.setIsSensitive(false);

        assertFalse(UserConfig.isSensitive());
    }

    private static Path createWorkspaceConfig(String content) throws IOException {
        Path dir = Path.of("target", "user-config-tests");
        Files.createDirectories(dir);
        Path file = dir.resolve("user-config.ini").toAbsolutePath().normalize();
        Files.writeString(file, content);
        return file;
    }
}
