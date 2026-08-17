/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java-Python parity checks for {@link ToolPermissionHost}.
 *
 * <p>Mirrors Python {@code openjiuwen.harness.security.host.ToolPermissionHost}. Verifies the
 * public API surface ({@code requestPermissionConfirmation}, {@code persistAllowRule} overloads,
 * {@code permissionYamlPath}, {@code getPermissionsSnapshot}, {@code resolveWorkspaceDir}) and the
 * disk-writing contract of {@code persistAllowRule(Map)}: the {@code permissions} section is
 * rewritten atomically while other top-level keys survive.
 *
 * @since 0.1.15
 */
class ToolPermissionHostCompatibilityTest {

    @Nested
    class ApiSurface {
        @Test
        void publicClass_andCoreMethods_existAligningPython() throws Exception {
            assertThat(Modifier.isPublic(ToolPermissionHost.class.getModifiers())).isTrue();

            Method request = ToolPermissionHost.class.getDeclaredMethod(
                    "requestPermissionConfirmation", PermissionConfirmationRequest.class);
            assertThat(request.getReturnType()).isEqualTo(PermissionConfirmResponse.class);

            Method persistMap = ToolPermissionHost.class.getDeclaredMethod(
                    "persistAllowRule", Map.class);
            assertThat(persistMap.getReturnType()).isEqualTo(boolean.class);

            Method persistLegacy = ToolPermissionHost.class.getDeclaredMethod(
                    "persistAllowRule", String.class, Map.class);
            assertThat(persistLegacy.getReturnType()).isEqualTo(Map.class);

            Method yamlPath = ToolPermissionHost.class.getDeclaredMethod("permissionYamlPath");
            assertThat(yamlPath.getReturnType()).isEqualTo(Path.class);

            Method snapshot = ToolPermissionHost.class.getDeclaredMethod("getPermissionsSnapshot");
            assertThat(snapshot.getReturnType()).isEqualTo(Map.class);

            Method workspace = ToolPermissionHost.class.getDeclaredMethod("resolveWorkspaceDir");
            assertThat(workspace.getReturnType()).isEqualTo(Path.class);
        }
    }

    @Nested
    class RequestPermissionConfirmation {
        @Test
        void withoutCallback_returnsNullSignallingInterruptFallback() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();
            assertThat(host.requestPermissionConfirmation(
                    PermissionConfirmationRequest.builder().toolName("bash").build())).isNull();
        }

        @Test
        void withCallback_invokesAndReturnsResponse() {
            ToolPermissionHost host = ToolPermissionHost.builder().build();
            PermissionConfirmResponse expected = PermissionConfirmResponse.builder()
                    .approved(true).build();
            host.setRequestPermissionConfirmationFn(request -> expected);

            PermissionConfirmResponse actual = host.requestPermissionConfirmation(
                    PermissionConfirmationRequest.builder().toolName("bash").build());

            assertThat(actual).isSameAs(expected);
        }
    }

    @Nested
    class PersistAllowRuleDiskContract {
        @Test
        void mapOverload_writesPermissionsSectionAndPreservesOthers(@TempDir Path dir) throws Exception {
            Path yaml = dir.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      tools: {bash: ask}
                      approval_overrides: []
                    meta:
                      author: carol
                    """);

            Map<String, Object> tools = new LinkedHashMap<>();
            tools.put("bash", "ask");
            tools.put("read_file", "allow");
            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("tools", tools);
            merged.put("approval_overrides", java.util.List.of());

            ToolPermissionHost host = ToolPermissionHost.builder().permissionYamlPath(yaml).build();

            assertThat(host.persistAllowRule(merged)).isTrue();

            Map<String, Object> reloaded = loadYaml(yaml);
            Map<String, Object> permissions = castMap(reloaded.get("permissions"));
            assertThat(castMap(permissions.get("tools")).get("read_file")).isEqualTo("allow");
            assertThat(castMap(permissions.get("tools")).get("bash")).isEqualTo("ask");
            assertThat(castMap(reloaded.get("meta")).get("author")).isEqualTo("carol");
        }

        @Test
        void legacyOverload_mergesAllowAndPersistsToDisk(@TempDir Path dir) throws Exception {
            Path yaml = dir.resolve("agent.yaml");
            Files.writeString(yaml, """
                    permissions:
                      tools: {bash: ask}
                    meta:
                      env: test
                    """);
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("tools", new LinkedHashMap<>(Map.of("bash", "ask")));

            ToolPermissionHost host = ToolPermissionHost.builder()
                    .permissionYamlPath(yaml)
                    .getPermissionsSnapshot(() -> new LinkedHashMap<>(snapshot))
                    .build();

            Map<String, Object> persisted = host.persistAllowRule("read_file", Map.of());
            assertThat(castMap(persisted.get("tools")).get("read_file")).isEqualTo("allow");

            Map<String, Object> reloaded = loadYaml(yaml);
            assertThat(castMap(castMap(reloaded.get("permissions")).get("tools")).get("read_file"))
                    .isEqualTo("allow");
            assertThat(castMap(reloaded.get("meta")).get("env")).isEqualTo("test");
        }

        @Test
        void missingPath_returnsFalseNoThrow() {
            ToolPermissionHost host = ToolPermissionHost.builder()
                    .permissionYamlPath(Path.of("/no-such-dir-xyz/agent.yaml")).build();
            assertThatCode(() -> host.persistAllowRule(new LinkedHashMap<>()))
                    .doesNotThrowAnyException();
            assertThat(host.persistAllowRule(new LinkedHashMap<>())).isFalse();
        }
    }

    private static Map<String, Object> loadYaml(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            Object data = new Yaml().load(in);
            assertThat(data).isInstanceOf(Map.class);
            return castMap(data);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object data) {
        assertThat(data).isInstanceOf(Map.class);
        return (Map<String, Object>) data;
    }
}
