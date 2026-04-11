/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.foundation.tool.mcp.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialSdkBoundaryTest {

    @Test
    @DisplayName("Official SDK imports stay out of public runtime facade files")
    void officialSdkImportsStayOutOfPublicRuntimeFacadeFiles() throws IOException {
        assertFalse(readSource("src/main/java/com/openjiuwen/core/runner/resourcemanager/ResourceMgr.java")
                .contains("io.modelcontextprotocol.sdk"));
        assertFalse(readSource("src/main/java/com/openjiuwen/core/runner/Runner.java")
                .contains("io.modelcontextprotocol.sdk"));
        assertFalse(readSource("src/main/java/com/openjiuwen/core/singleagent/agents/ReActAgent.java")
                .contains("io.modelcontextprotocol.sdk"));
    }

    @Test
    @DisplayName("ToolMgr is the public seam that attaches the official SDK factory")
    void toolMgrIsTheOnlyPublicSeamForOfficialSdkFactory() throws IOException {
        assertTrue(readSource("src/main/java/com/openjiuwen/core/runner/resourcemanager/ToolMgr.java")
                .contains("OfficialMcpClientFactory"));
        assertFalse(readSource("src/main/java/com/openjiuwen/core/foundation/tool/mcp/McpServerConfig.java")
                .contains("OfficialMcpClientFactory"));
    }

    private String readSource(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
