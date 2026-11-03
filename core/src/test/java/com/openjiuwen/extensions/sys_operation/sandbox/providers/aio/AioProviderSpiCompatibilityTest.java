/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.aio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Map;

class AioProviderSpiCompatibilityTest {
    private SandboxEndpoint endpoint;
    private SandboxGatewayConfig config;
    private AioFSProvider fsProvider;
    private AioShellProvider shellProvider;
    private AioCodeProvider codeProvider;

    @BeforeEach
    void setUp() {
        endpoint = Mockito.mock(SandboxEndpoint.class);
        Mockito.when(endpoint.getBaseUrl()).thenReturn("http://localhost:8080");
        Mockito.when(endpoint.getSandboxId()).thenReturn("sbx-test");
        config = Mockito.mock(SandboxGatewayConfig.class);
        Mockito.when(config.getTimeoutSeconds()).thenReturn(30);
        fsProvider = new AioFSProvider(endpoint, config);
        shellProvider = new AioShellProvider(endpoint, config);
        codeProvider = new AioCodeProvider(endpoint, config);
    }

    @Test
    void testAioFSProviderThrowsUnsupported() {
        assertThatThrownBy(() -> fsProvider.readFile("/path", "r", null, null, null, "utf-8", 1024, Map.of()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("readFile");
    }

    @Test
    void testAioShellProviderThrowsUnsupported() {
        assertThatThrownBy(() -> shellProvider.executeCmd("ls", "/", 10, Map.of(), Map.of()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("executeCmd");
    }

    @Test
    void testAioCodeProviderThrowsUnsupported() {
        assertThatThrownBy(() -> codeProvider.executeCode("print(1)", "python", 10, Map.of(), Map.of()))
                .isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("executeCode");
    }

    @Test
    void testAioProviderConstructorSignature() {
        AioFSProvider provider = new AioFSProvider(endpoint, config);
        assertThat(provider).isInstanceOf(AioFSProvider.class);
    }

    @Test
    void testBaseAioProviderMixinInterface() {
        boolean hasGetEndpoint = false;
        boolean hasGetConfig = false;
        for (Method m : BaseAioProviderMixin.class.getMethods()) {
            if (m.getName().equals("getEndpoint") && m.getReturnType() == SandboxEndpoint.class) {
                hasGetEndpoint = true;
            }
            if (m.getName().equals("getConfig") && m.getReturnType() == SandboxGatewayConfig.class) {
                hasGetConfig = true;
            }
        }
        assertThat(hasGetEndpoint).isTrue();
        assertThat(hasGetConfig).isTrue();
    }
}
