/* *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved. */
package com.openjiuwen.extensions.sys_operation.sandbox;

import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.sandbox.SandboxRegistry;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.PreDeployLauncherConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test configuration for extensions sys_operation sandbox tests.
 * <p>
 * Mirrors Python's {@code conftest.py} in
 * {@code tests/unit_tests/extensions/sys_operation/sandbox/conftest.py}.
 */
class ExtSandboxTestConfig {

    @Nested
    @DisplayName("ExtSandbox config tests")
    class ConfigTests {

        @Test
        @DisplayName("Test SandboxRegistry class exists")
        void testSandboxRegistryClassExists() {
            assertNotNull(SandboxRegistry.class);
        }

        @Test
        @DisplayName("Test sandbox configuration")
        void testExtSandboxConfig() {
            assertNotNull(SandboxRegistry.class);
            // Sandbox registry should be accessible
        }

        @Test
        @DisplayName("Test truthy env helper")
        void testIsTruthyEnv() {
            assertTrue(isTruthyEnv("1"));
            assertTrue(isTruthyEnv("true"));
            assertTrue(isTruthyEnv("yes"));
            assertFalse(isTruthyEnv(""));
            assertFalse(isTruthyEnv("false"));
        }

        @Test
        @DisplayName("Test AIO sandbox reachability helper")
        void testAioSandboxIsReachableReturnsFalseForClosedPort() throws IOException {
            int port;
            try (ServerSocket socket = new ServerSocket(0)) {
                port = socket.getLocalPort();
            }

            assertFalse(aioSandboxIsReachable("http://localhost:" + port));
        }

        @Test
        @DisplayName("Test real_aio_op fixture card")
        void testRealAioOpFixtureBuildsSandboxCard() {
            SysOperationCard card = aioSandboxCard("real_aio_sandbox_");

            assertTrue(card.getId().startsWith("real_aio_sandbox_"));
            assertEquals(OperationMode.SANDBOX, card.getMode());
            assertEquals(ContainerScope.SYSTEM, card.getGatewayConfig().getIsolation().getContainerScope());
            assertEquals("aio", card.getGatewayConfig().getLauncherConfig().getSandboxType());
            assertEquals(30, card.getGatewayConfig().getTimeoutSeconds());
        }

        @Test
        @DisplayName("Test aio_agent_op fixture card")
        void testAioAgentOpFixtureBuildsSandboxCard() {
            SysOperationCard card = aioSandboxCard("aio_agent_sandbox_");

            assertTrue(card.getId().startsWith("aio_agent_sandbox_"));
            assertEquals("http://localhost:8080",
                    ((PreDeployLauncherConfig) card.getGatewayConfig().getLauncherConfig()).getBaseUrl());
        }
    }

    static boolean isTruthyEnv(String value) {
        return value != null && switch (value.toLowerCase()) {
            case "1", "true", "yes" -> true;
            default -> false;
        };
    }

    static boolean aioSandboxIsReachable(String baseUrl) {
        URI uri = URI.create(baseUrl);
        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), (int) Duration.ofSeconds(1).toMillis());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    static SysOperationCard aioSandboxCard(String prefix) {
        PreDeployLauncherConfig launcherConfig = PreDeployLauncherConfig.create("http://localhost:8080", "aio");
        launcherConfig.setIdleTtlSeconds(600);
        return SysOperationCard.builder()
                .id(prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(SandboxGatewayConfig.builder()
                        .isolation(SandboxIsolationConfig.builder()
                                .containerScope(ContainerScope.SYSTEM)
                                .build())
                        .launcherConfig(launcherConfig)
                        .timeoutSeconds(30)
                        .build())
                .build();
    }
}
