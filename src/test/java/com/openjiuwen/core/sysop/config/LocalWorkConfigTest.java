/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/
package com.openjiuwen.core.sysop.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for LocalWorkConfig.
 */
class LocalWorkConfigTest {

    @Test
    @DisplayName("default shell allowlist is populated")
    void testDefaultAllowlist() {
        LocalWorkConfig config = new LocalWorkConfig();
        List<String> allowlist = config.getShellAllowlist();
        assertNotNull(allowlist);
        assertTrue(allowlist.contains("ls"));
        assertTrue(allowlist.contains("cat"));
        assertTrue(allowlist.contains("python"));
    }

    @Test
    @DisplayName("builder sets workDir correctly")
    void testBuilderWorkDir() {
        LocalWorkConfig config = LocalWorkConfig.builder()
                .workDir("/workspace")
                .build();
        assertEquals("/workspace", config.getWorkDir());
    }

    @Test
    @DisplayName("builder with custom allowlist")
    void testBuilderCustomAllowlist() {
        List<String> custom = List.of("echo", "ping");
        LocalWorkConfig config = LocalWorkConfig.builder()
                .shellAllowlist(custom)
                .build();
        assertEquals(2, config.getShellAllowlist().size());
        assertTrue(config.getShellAllowlist().contains("echo"));
    }
}
