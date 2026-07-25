/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiuwenBoxClientTest {

    @Test
    @DisplayName("deleteSandbox is no-op when sandboxId is null or empty")
    void testDeleteSandboxNoOp() {
        JiuwenBoxClient client = new JiuwenBoxClient("http://localhost:0", 1);
        assertThatCode(() -> client.deleteSandbox(null)).doesNotThrowAnyException();
        assertThatCode(() -> client.deleteSandbox("")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("setIdleTimeout is no-op when both params are null")
    void testSetIdleTimeoutNoOp() {
        JiuwenBoxClient client = new JiuwenBoxClient("http://localhost:0", 1);
        assertThatCode(() -> client.setIdleTimeout(null, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("createSandbox throws on invalid base URL")
    void testCreateSandboxInvalidUrl() {
        JiuwenBoxClient client = new JiuwenBoxClient("http://localhost:0", 1);
        assertThatThrownBy(() -> client.createSandbox(Map.of()))
                .isInstanceOf(Exception.class);
    }
}
