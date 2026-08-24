/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Tests for {@link ToolPermissionHost} Task 9: hosted confirmation callback
 * and merged-to-disk allow rule persistence.
 */
class ToolPermissionHostTest {

    private static PermissionConfirmationRequest sampleRequest() {
        return PermissionConfirmationRequest.builder()
                .toolName("bash")
                .toolArgs(Map.of("command", "ls"))
                .result(PermissionResult.builder()
                        .permission(PermissionLevel.ASK)
                        .matchedRule("tools.bash")
                        .build())
                .autoConfirmKey("bash:ls")
                .build();
    }

    private static PermissionConfirmResponse approvedPersist() {
        return PermissionConfirmResponse.builder()
                .approved(true)
                .autoConfirm(true)
                .persistAllow(true)
                .build();
    }

    @Nested
    class RequestPermissionConfirmation {
        @Test
        void requestPermissionConfirmation_withCallback_returnsResponse() {
            Function<PermissionConfirmationRequest, PermissionConfirmResponse> fn =
                    req -> approvedPersist();
            ToolPermissionHost host = ToolPermissionHost.builder()
                    .requestPermissionConfirmationFn(fn)
                    .build();

            PermissionConfirmResponse resp = host.requestPermissionConfirmation(sampleRequest());

            assertThat(resp).isNotNull();
            assertThat(resp.isApproved()).isTrue();
            assertThat(resp.isAutoConfirm()).isTrue();
            assertThat(resp.isPersistAllow()).isTrue();
        }

        @Test
        void requestPermissionConfirmation_withoutCallback_returnsNull() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();

            PermissionConfirmResponse resp = host.requestPermissionConfirmation(sampleRequest());

            assertThat(resp).isNull();
        }

        @Test
        void requestPermissionConfirmation_setterInjectsCallback_returnsResponse() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();
            host.setRequestPermissionConfirmationFn(req -> approvedPersist());

            PermissionConfirmResponse resp = host.requestPermissionConfirmation(sampleRequest());

            assertThat(resp).isNotNull();
            assertThat(resp.isApproved()).isTrue();
        }
    }

    @Nested
    class PersistAllowRuleSnapshot {
        @Test
        void persistAllowRule_snapshot_writesNewRuleAndReturnsTrue(@TempDir Path tmp) throws Exception {
            Path yaml = tmp.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      enabled: true
                      tools:
                        read_file: ask
                        write_file: deny
                    other_key: preserved
                    """, StandardCharsets.UTF_8);

            Map<String, Object> tools = new LinkedHashMap<>();
            tools.put("bash", "allow");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("enabled", true);
            snapshot.put("tools", tools);
            snapshot.put("approval_overrides", new ArrayList<>());

            ToolPermissionHost host = ToolPermissionHost.builder()
                    .permissionYamlPath(yaml)
                    .build();

            boolean ok = host.persistAllowRule(snapshot);

            assertThat(ok).isTrue();
            Map<String, Object> root = loadYaml(yaml);
            Map<String, Object> perms = (Map<String, Object>) root.get("permissions");
            assertThat(perms).isNotNull();
            Map<String, Object> writtenTools = (Map<String, Object>) perms.get("tools");
            assertThat(writtenTools.get("bash")).isEqualTo("allow");
            assertThat(root.get("other_key")).isEqualTo("preserved");
        }

        @Test
        void persistAllowRule_nullYamlPath_returnsFalse() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("tools", Map.of("bash", "allow"));
            ToolPermissionHost host = ToolPermissionHost.builder().build();

            boolean ok = host.persistAllowRule(snapshot);

            assertThat(ok).isFalse();
        }

        @Test
        void persistAllowRule_missingFile_returnsFalse(@TempDir Path tmp) {
            Path missing = tmp.resolve("does-not-exist.yaml");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("tools", Map.of("bash", "allow"));
            ToolPermissionHost host = ToolPermissionHost.builder()
                    .permissionYamlPath(missing)
                    .build();

            boolean ok = host.persistAllowRule(snapshot);

            assertThat(ok).isFalse();
        }

        private Map<String, Object> loadYaml(Path file) throws Exception {
            try (InputStream in = Files.newInputStream(file)) {
                Object loaded = new Yaml().load(in);
                if (loaded instanceof Map<?, ?> m) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        out.put(String.valueOf(e.getKey()), e.getValue());
                    }
                    return out;
                }
                return new LinkedHashMap<>();
            }
        }
    }

    @Nested
    class PersistAllowRuleLegacy {
        @Test
        void persistAllowRule_toolNameAndArgs_returnsMergedSnapshotWithAllow() {
            ToolPermissionHost host = ToolPermissionHost.builder()
                    .getPermissionsSnapshot(() -> {
                        Map<String, Object> snap = new LinkedHashMap<>();
                        Map<String, Object> tools = new LinkedHashMap<>();
                        tools.put("read_file", "ask");
                        snap.put("tools", tools);
                        return snap;
                    })
                    .build();

            Map<String, Object> persisted = host.persistAllowRule("bash", Map.of("command", "ls"));

            assertThat(((Map<?, ?>) persisted.get("tools")).get("bash")).isEqualTo("allow");
            assertThat(((Map<?, ?>) persisted.get("tools")).get("read_file")).isEqualTo("ask");
        }
    }
}
