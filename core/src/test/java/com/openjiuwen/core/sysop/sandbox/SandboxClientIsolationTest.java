/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.ContainerScope;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.config.SandboxIsolationConfig;
import com.openjiuwen.core.sysop.config.SandboxLauncherConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGatewayClient;
import com.openjiuwen.core.sysop.sandbox.launchers.LaunchedSandbox;
import com.openjiuwen.core.sysop.sandbox.launchers.SandboxLauncher;
import com.openjiuwen.core.sysop.sandbox.providers.BaseShellProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;

/**
 * Verifies that {@code new SandboxClient(config)} honors the isolation config.
 *
 * <p>agent-runtime-java builds one SandboxClient per configured sandbox server. Each client must be
 * routed by its own isolation key; otherwise every client shares the first client's sandbox and
 * provider. A recording launcher captures launched isolation keys, and a KV shell provider keeps
 * one store per sandbox ({@code set k v} / {@code get k}) and prefixes its output with the sandbox
 * type it was created for.</p>
 */
class SandboxClientIsolationTest {
    private static final String LAUNCHER = "client-isolation-launcher";

    private static final String TYPE_A = "client-isolation-a";

    private static final String TYPE_B = "client-isolation-b";

    private static final List<String> LAUNCHED_KEYS = new CopyOnWriteArrayList<>();

    private static final Map<String, Map<String, String>> SANDBOX_DATA = new ConcurrentHashMap<>();

    /**
     * Launcher that records the isolation key of every launch.
     */
    public static class RecordingLauncher extends SandboxLauncher {
        @Override
        public CompletableFuture<LaunchedSandbox> launch(SandboxLauncherConfig config, int timeoutSeconds,
                String isolationKey) {
            LAUNCHED_KEYS.add(isolationKey);
            String sandboxId = "sbx-" + LAUNCHED_KEYS.size();
            return CompletableFuture.completedFuture(new LaunchedSandbox(config.getBaseUrl(), sandboxId));
        }
    }

    /**
     * Shell provider backed by an in-memory KV store per sandbox.
     */
    public static class KvShellProvider extends BaseShellProvider {
        private final String sandboxType;

        /**
         * Creates the provider for one sandbox endpoint.
         *
         * @param endpoint sandbox endpoint
         * @param config gateway config the provider was created for
         */
        public KvShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(endpoint, config);
            this.sandboxType = config.getLauncherConfig().getSandboxType();
        }

        @Override
        public CompletableFuture<ExecuteCmdResult> executeCmd(String command, String cwd, Integer timeoutSeconds,
                Map<String, String> environment, Map<String, Object> options) {
            Map<String, String> store = SANDBOX_DATA.computeIfAbsent(getEndpoint().sandboxId(),
                    ignored -> new ConcurrentHashMap<>());
            String[] parts = command.split(" ");
            String output = "set".equals(parts[0]) ? "ok" : store.getOrDefault(parts[1], "<none>");
            if ("set".equals(parts[0])) {
                store.put(parts[1], parts[2]);
            }
            ExecuteCmdResult result = new ExecuteCmdResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setData(ExecuteCmdData.builder().command(command).exitCode(0)
                    .stdout(sandboxType + ":" + output).build());
            return CompletableFuture.completedFuture(result);
        }

        @Override
        public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd,
                Integer timeoutSeconds, Map<String, String> environment, Map<String, Object> options) {
            throw new UnsupportedOperationException("stream is not used by this test");
        }
    }

    @BeforeEach
    void setUp() {
        LAUNCHED_KEYS.clear();
        SANDBOX_DATA.clear();
        SandboxRegistry.registerLauncher(LAUNCHER, RecordingLauncher.class);
        SandboxRegistry.registerProvider(TYPE_A, "shell", KvShellProvider.class);
        SandboxRegistry.registerProvider(TYPE_B, "shell", KvShellProvider.class);
    }

    @AfterEach
    void tearDown() {
        // The gateway is process-wide: drop this test's records so tests stay independent.
        for (String key : LAUNCHED_KEYS) {
            SandboxGatewayClient.release(key, "keep").handle((ignored, error) -> null).join();
        }
        SandboxRegistry.unregisterLauncher(LAUNCHER);
        SandboxRegistry.unregisterProvider(TYPE_A, "shell");
        SandboxRegistry.unregisterProvider(TYPE_B, "shell");
    }

    @Test
    void constructor_differentSandboxTypes_usesSeparateSandboxes() {
        // Given: two clients for two servers of different sandbox types
        SandboxClient serverA = new SandboxClient(config(TYPE_A, "http://server-a", "types-a"));
        SandboxClient serverB = new SandboxClient(config(TYPE_B, "http://server-b", "types-b"));

        // When: server A writes a value and server B reads the same key
        String writeByA = exec(serverA, "set secret A-password");
        String readByB = exec(serverB, "get secret");

        // Then: server B is served by its own provider and sandbox
        assertEquals(TYPE_A + ":ok", writeByA);
        assertEquals(TYPE_B + ":<none>", readByB, "launched keys = " + LAUNCHED_KEYS);
        assertEquals(2, LAUNCHED_KEYS.size(), "launched keys = " + LAUNCHED_KEYS);
    }

    @Test
    void constructor_differentCustomIds_usesSeparateSandboxes() {
        // Given: two clients of the same sandbox type with different custom ids
        SandboxClient teamA = new SandboxClient(config(TYPE_A, "http://server-a", "team-a"));
        SandboxClient teamB = new SandboxClient(config(TYPE_A, "http://server-a", "team-b"));

        // When: team A writes a value and team B reads the same key
        exec(teamA, "set secret A-password");
        String readByB = exec(teamB, "get secret");

        // Then: team B cannot see team A's data
        assertEquals(TYPE_A + ":<none>", readByB, "launched keys = " + LAUNCHED_KEYS);
        assertEquals(2, LAUNCHED_KEYS.size(), "launched keys = " + LAUNCHED_KEYS);
    }

    @Test
    void constructor_customScopeWithoutCustomId_fallsBackToSharedKey() {
        // Given: an invalid isolation config (CUSTOM scope without custom id)
        SandboxGatewayConfig invalid = config(TYPE_A, "http://server-a", null);

        // When: the client is created and used
        String output = exec(new SandboxClient(invalid), "set secret value");

        // Then: construction does not fail and the legacy shared key is used
        assertEquals(TYPE_A + ":ok", output);
        assertEquals(1, LAUNCHED_KEYS.size());
        assertNull(LAUNCHED_KEYS.get(0));
    }

    private static SandboxGatewayConfig config(String sandboxType, String baseUrl, String customId) {
        return SandboxGatewayConfig.builder()
                .isolation(SandboxIsolationConfig.builder()
                        .containerScope(ContainerScope.CUSTOM).customId(customId).build())
                .launcherConfig(SandboxLauncherConfig.builder()
                        .launcherType(LAUNCHER).sandboxType(sandboxType).baseUrl(baseUrl).build())
                .build();
    }

    private static String exec(SandboxClient client, String command) {
        return client.shell().executeCmd(command, null, 30, null, null).getData().getStdout();
    }
}
