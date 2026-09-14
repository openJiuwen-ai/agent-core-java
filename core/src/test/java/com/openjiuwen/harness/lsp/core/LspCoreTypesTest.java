/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's core LSP type defaults in
 * {@code openjiuwen/harness/lsp/core/types.py}.
 */
class LspCoreTypesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testServerStateValues() throws Exception {
        assertEquals(LspServerState.RUNNING, LspServerState.fromValue("running"));
        assertEquals("\"error\"", mapper.writeValueAsString(LspServerState.ERROR));
    }

    @Test
    void testSpawnHandleDefaults() throws Exception {
        SpawnHandle handle = mapper.readValue(
                """
                {
                  "command": "gopls"
                }
                """,
                SpawnHandle.class
        );
        assertEquals("gopls", handle.getCommand());
        assertTrue(handle.getArgs().isEmpty());
        assertTrue(handle.getEnv().isEmpty());
        assertEquals(45_000, handle.getStartupTimeout());
    }

    @Test
    void testScopedConfigAndStatusDefaults() throws Exception {
        ScopedLspServerConfig config = mapper.readValue(
                """
                {
                  "server_id": "pyright",
                  "command": "pyright-langserver",
                  "workspace_folder": "/repo",
                  "extension_to_language": {".py": "python"}
                }
                """,
                ScopedLspServerConfig.class
        );
        assertEquals("pyright", config.getServerId());
        assertEquals(Map.of(".py", "python"), config.getExtensionToLanguage());
        assertEquals(45_000, config.getStartupTimeout());

        LspServerStatus status = new LspServerStatus();
        status.setServerId("pyright");
        status.setName("Pyright");
        status.setRunning(true);
        assertEquals(LspServerState.STOPPED, status.getState());
    }
}
