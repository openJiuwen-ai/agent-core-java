/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.lsp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.harness.lsp.core.LspServerStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LspTypesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void initializeOptionsAndCustomServerConfigPreservePythonFieldNames() throws Exception {
        InitializeOptions options = mapper.readValue(
                """
                {
                  "cwd": "/repo",
                  "custom_servers": {
                    "pyright": {
                      "command": "pyright-langserver",
                      "args": ["--stdio"],
                      "env": {"PYTHONPATH": "."},
                      "extensions": [".py"],
                      "language_id": "python",
                      "initialization_options": {"a": 1},
                      "disabled": true
                    }
                  }
                }
                """,
                InitializeOptions.class
        );

        assertThat(options.getCwd()).isEqualTo("/repo");
        assertThat(options.getCustomServers()).containsKey("pyright");
        CustomServerConfig config = options.getCustomServers().get("pyright");
        assertThat(config.getLanguageId()).isEqualTo("python");
        assertThat(config.getInitializationOptions()).containsEntry("a", 1);
        assertThat(config.isDisabled()).isTrue();
    }

    @Test
    void initializeResultAndStatusExposeExpectedDefaults() {
        InitializeResult result = new InitializeResult();
        result.setSuccess(true);
        result.setServersLoaded(2);
        assertThat(result.getDurationMs()).isEqualTo(0.0d);

        LspServerStatus serverStatus = new LspServerStatus();
        serverStatus.setServerId("pyright");

        LspStatus status = new LspStatus();
        status.setInitialized(true);
        status.setServers(java.util.List.of(serverStatus));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getServersLoaded()).isEqualTo(2);
        assertThat(status.getServers()).hasSize(1);
        assertThat(status.getServers().get(0).getServerId()).isEqualTo("pyright");
    }
}
