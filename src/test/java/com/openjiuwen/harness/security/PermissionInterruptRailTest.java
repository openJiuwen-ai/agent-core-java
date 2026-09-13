/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PermissionInterruptRail parity tests: ALLOW/DENY/ASK three-state, all-tool interception,
 * hosted/resume confirmation flow with persistence and session auto-confirm.
 *
 * @since 0.1.15
 */
class PermissionInterruptRailTest {
    @TempDir
    Path tempDir;

    private Map<String, Object> config() {
        Map<String, Object> tools = new LinkedHashMap<>();
        tools.put("read_file", "ask");
        tools.put("write_file", "deny");
        tools.put("bash", "ask");
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("*", "allow");
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", true);
        config.put("schema", "tiered_policy");
        config.put("permission_mode", "normal");
        config.put("tools", tools);
        config.put("defaults", defaults);
        config.put("rules", new ArrayList<>());
        config.put("approval_overrides", new ArrayList<>());
        return config;
    }

    private Map<String, Object> configDefaultsAsk() {
        Map<String, Object> config = config();
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("*", "ask");
        config.put("defaults", defaults);
        return config;
    }

    private AgentCallbackContext ctxFor(String toolName, Map<String, Object> toolArgs, Object resumeInput) {
        ToolCall toolCall = ToolCall.builder().id("tc-" + toolName).name(toolName).arguments("{}").build();
        ToolCallInputs inputs = ToolCallInputs.builder()
                .toolCall(toolCall).toolName(toolName).toolArgs(toolArgs).build();
        Map<String, Object> extra = new LinkedHashMap<>();
        if (resumeInput != null) {
            extra.put(ToolInterruptionState.RESUME_USER_INPUT_KEY, resumeInput);
        }
        return AgentCallbackContext.builder().inputs(inputs).extra(extra).build();
    }

    private PermissionInterruptRail rail(Map<String, Object> config, ToolPermissionHost host) {
        return new PermissionInterruptRail(new PermissionEngine(config, Path.of(".").toAbsolutePath()), host);
    }

    private PermissionInterruptRail defaultHostRail(Map<String, Object> config) {
        return rail(config, ToolPermissionHost.builder().build());
    }

    static final class RecordingHost extends ToolPermissionHost {
        final List<Map<String, Object>> persisted = new ArrayList<>();
        private final Map<String, Object> snapshot;

        RecordingHost(Map<String, Object> snapshot) {
            super();
            this.snapshot = snapshot;
        }

        @Override
        public Map<String, Object> getPermissionsSnapshot() {
            return new LinkedHashMap<>(snapshot);
        }

        @Override
        public boolean persistAllowRule(Map<String, Object> snapshot) {
            persisted.add(new LinkedHashMap<>(snapshot));
            return true;
        }
    }

    @Nested
    class AllowDecision {
        @Test
        void beforeToolCall_allowTool_approvesWithoutSkip() {
            PermissionInterruptRail rail = defaultHostRail(config());
            AgentCallbackContext ctx = ctxFor("list_files", Map.of(), null);

            rail.beforeToolCall(ctx);

            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
        }
    }

    @Nested
    class DenyDecision {
        @Test
        void beforeToolCall_denyTool_rejectsWithPermissionDenied() {
            PermissionInterruptRail rail = defaultHostRail(config());
            AgentCallbackContext ctx = ctxFor("write_file", Map.of("path", "a.txt"), null);

            rail.beforeToolCall(ctx);

            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
            Object toolResult = ((ToolCallInputs) ctx.getInputs()).getToolResult();
            assertThat(String.valueOf(toolResult)).contains("[PERMISSION_DENIED]");
        }
    }

    @Nested
    class DefaultsIntercept {
        @Test
        void beforeToolCall_unlistedToolWithDefaultsAsk_interrupts() {
            Map<String, Object> config = configDefaultsAsk();
            PermissionInterruptRail rail = defaultHostRail(config);
            AgentCallbackContext ctx = ctxFor("list_files", Map.of(), null);

            assertThatThrownBy(() -> rail.beforeToolCall(ctx))
                    .isInstanceOf(ToolInterruptException.class);

            PermissionCheckResult check = new PermissionEngine(config, Path.of(".").toAbsolutePath())
                    .checkPermission("list_files", Map.of());
            assertThat(check.isNeedsApproval()).isTrue();
        }
    }

    @Nested
    class AskInterrupt {
        @Test
        void beforeToolCall_askToolNoHost_interruptsWithToolInterruptException() {
            PermissionInterruptRail rail = defaultHostRail(config());
            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"), null);

            assertThatThrownBy(() -> rail.beforeToolCall(ctx))
                    .isInstanceOf(ToolInterruptException.class);
        }
    }

    @Nested
    class ResumePersist {
        @Test
        void resume_approvedAutoConfirmPersistAllow_persistsAndApproves() {
            Map<String, Object> config = config();
            RecordingHost host = new RecordingHost(config);
            PermissionInterruptRail rail = rail(config, host);
            PermissionConfirmResponse resp = PermissionConfirmResponse.builder()
                    .approved(true).autoConfirm(true).persistAllow(true).build();
            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"), resp);

            rail.beforeToolCall(ctx);

            assertThat(host.persisted).hasSize(1);
            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
            Map<?, ?> tools = (Map<?, ?>) host.persisted.get(0).get("tools");
            assertThat(tools.get("read_file")).isEqualTo("allow");
        }

        @Test
        void resume_approvedAutoConfirmPersistAllow_writesAllowRuleToYaml() throws Exception {
            Map<String, Object> config = config();
            Path yaml = tempDir.resolve("permissions.yaml");
            Files.writeString(yaml, "permissions: {}\n");
            ToolPermissionHost host = ToolPermissionHost.builder()
                    .permissionYamlPath(yaml)
                    .getPermissionsSnapshot(() -> new LinkedHashMap<>(config))
                    .build();
            PermissionInterruptRail rail = new PermissionInterruptRail(
                    new PermissionEngine(config, tempDir.toAbsolutePath()), host);
            PermissionConfirmResponse resp = PermissionConfirmResponse.builder()
                    .approved(true).autoConfirm(true).persistAllow(true).build();
            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"), resp);

            rail.beforeToolCall(ctx);

            assertThat(ctx.getExtra()).doesNotContainKey("_skip_tool");
            try (InputStream in = Files.newInputStream(yaml)) {
                Object loaded = new Yaml().load(in);
                assertThat(loaded).isInstanceOf(Map.class);
                Map<?, ?> data = (Map<?, ?>) loaded;
                Map<?, ?> perms = (Map<?, ?>) data.get("permissions");
                Map<?, ?> tools = (Map<?, ?>) perms.get("tools");
                assertThat(tools.get("read_file")).isEqualTo("allow");
            }
        }
    }

    @Nested
    class ResumeReject {
        @Test
        void resume_notApproved_rejectsWithFeedback() {
            PermissionInterruptRail rail = defaultHostRail(config());
            PermissionConfirmResponse resp = PermissionConfirmResponse.builder()
                    .approved(false).feedback("no way").build();
            AgentCallbackContext ctx = ctxFor("read_file", Map.of("path", "a.txt"), resp);

            rail.beforeToolCall(ctx);

            assertThat(ctx.getExtra()).containsEntry("_skip_tool", Boolean.TRUE);
            Object toolResult = ((ToolCallInputs) ctx.getInputs()).getToolResult();
            assertThat(String.valueOf(toolResult)).contains("no way");
        }
    }

    @Nested
    class SessionAutoConfirm {
        @Test
        void resume_autoConfirmSessionNonShell_thenSubsequentCallApprovesWithoutInterrupt() {
            PermissionInterruptRail rail = defaultHostRail(config());
            PermissionConfirmResponse resp = PermissionConfirmResponse.builder()
                    .approved(true).autoConfirm(true).persistAllow(false).build();
            AgentCallbackContext ctx1 = ctxFor("read_file", Map.of("path", "a.txt"), resp);
            rail.beforeToolCall(ctx1);
            assertThat(ctx1.getExtra()).doesNotContainKey("_skip_tool");

            AgentCallbackContext ctx2 = ctxFor("read_file", Map.of("path", "b.txt"), null);
            rail.beforeToolCall(ctx2);
            assertThat(ctx2.getExtra()).doesNotContainKey("_skip_tool");
        }

        @Test
        void resume_autoConfirmSessionShellSimple_thenSubsequentCallApprovesWithoutInterrupt() {
            PermissionInterruptRail rail = defaultHostRail(config());
            PermissionConfirmResponse resp = PermissionConfirmResponse.builder()
                    .approved(true).autoConfirm(true).persistAllow(false).build();
            AgentCallbackContext ctx1 = ctxFor("bash", Map.of("command", "ls"), resp);
            rail.beforeToolCall(ctx1);
            assertThat(ctx1.getExtra()).doesNotContainKey("_skip_tool");

            AgentCallbackContext ctx2 = ctxFor("bash", Map.of("command", "ls"), null);
            rail.beforeToolCall(ctx2);
            assertThat(ctx2.getExtra()).doesNotContainKey("_skip_tool");
        }
    }
}
