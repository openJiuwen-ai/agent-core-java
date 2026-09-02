/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link McpServerConfig} serverId defaulting.
 */
class McpServerConfigTest {

    @Test
    @DisplayName("serverId defaults to serverName when omitted")
    void serverIdDefaultsToServerNameWhenOmitted() {
        McpServerConfig config = McpServerConfig.builder()
                .serverName("my-lib")
                .serverPath("http://127.0.0.1:9/mcp")
                .clientType("sse")
                .build();

        assertThat(config.getServerId()).isEqualTo("my-lib");
    }

    @Test
    @DisplayName("explicit serverId is preserved")
    void explicitServerIdIsPreserved() {
        McpServerConfig config = McpServerConfig.builder()
                .serverId("custom-id")
                .serverName("my-lib")
                .serverPath("http://127.0.0.1:9/mcp")
                .build();

        assertThat(config.getServerId()).isEqualTo("custom-id");
        assertThat(config.getServerName()).isEqualTo("my-lib");
    }

    @Test
    @DisplayName("falls back to uuid when both serverId and serverName are missing")
    void fallsBackToUuidWhenBothMissing() {
        McpServerConfig config = McpServerConfig.builder()
                .serverPath("http://127.0.0.1:9/mcp")
                .build();

        assertThat(config.getServerId()).isNotBlank();
        assertThat(config.getServerId()).matches("[0-9a-f]{32}");
    }

    @Test
    @DisplayName("normalizeServerId is idempotent when serverId already set")
    void normalizeServerIdIsIdempotent() {
        McpServerConfig config = new McpServerConfig();
        config.setServerName("named");
        config.setServerId("kept");
        config.normalizeServerId();
        assertThat(config.getServerId()).isEqualTo("kept");
    }

    @Test
    @DisplayName("normalizeServerId fills blank serverId from serverName")
    void normalizeServerIdFillsBlankFromServerName() {
        McpServerConfig config = new McpServerConfig();
        config.setServerName("from-name");
        config.setServerId("  ");
        config.normalizeServerId();
        assertThat(config.getServerId()).isEqualTo("from-name");
    }
}
