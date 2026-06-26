/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation;

import com.openjiuwen.core.common.logging.LoggingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ShellProcessRegistryTest {

    @AfterEach
    void tearDown() {
        ShellProcessRegistry.resetShellSessionId(new ShellProcessRegistry.ContextToken<>(null, false));
        LoggingUtils.setSessionId();
    }

    @Test
    void sessionIdTokenRestoresPriorBinding() {
        ShellProcessRegistry.ContextToken<String> outer = ShellProcessRegistry.setShellSessionId("outer");
        ShellProcessRegistry.ContextToken<String> inner = ShellProcessRegistry.setShellSessionId("inner");

        assertThat(ShellProcessRegistry.getShellSessionId()).isEqualTo("inner");

        ShellProcessRegistry.resetShellSessionId(inner);
        assertThat(ShellProcessRegistry.getShellSessionId()).isEqualTo("outer");

        ShellProcessRegistry.resetShellSessionId(outer);
        assertThat(ShellProcessRegistry.getShellSessionId()).isNull();
    }

    @Test
    void resolveShellSessionIdFallsBackToLoggingTraceId() {
        LoggingUtils.setSessionId("trace-123");
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isEqualTo("trace-123");

        ShellProcessRegistry.ContextToken<String> token = ShellProcessRegistry.setShellSessionId("shell-456");
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isEqualTo("shell-456");

        ShellProcessRegistry.resetShellSessionId(token);
        LoggingUtils.setSessionId();
        assertThat(ShellProcessRegistry.resolveShellSessionId()).isNull();
    }

    @Test
    void killTrackedProcessForSessionMatchesPythonBehavior() throws Exception {
        Process process = startLongRunningProcess();
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_kill", process);

        int killed = ShellProcessRegistry.killShellProcessesForSession("sess_kill");

        assertThat(killed).isEqualTo(1);
        assertThat(process.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_kill")).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_kill")).isFalse();
    }

    @Test
    void killSessionTreeIncludesChildSessionKeys() throws Exception {
        Process parent = startLongRunningProcess();
        Process child = startLongRunningProcess();
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_tree", parent);
        ShellProcessRegistry.SHELL_PROCESS_REGISTRY.register("sess_tree_child", child);

        int killed = ShellProcessRegistry.killShellProcessesForSessionTree("sess_tree");

        assertThat(killed).isEqualTo(2);
        assertThat(parent.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(child.waitFor(3, TimeUnit.SECONDS)).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_tree")).isTrue();
        assertThat(ShellProcessRegistry.consumeShellSessionCancelled("sess_tree_child")).isTrue();
    }

    private Process startLongRunningProcess() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return new ProcessBuilder("powershell", "-NoProfile", "-Command", "Start-Sleep -Seconds 30")
                    .start();
        }
        return new ProcessBuilder("sleep", "30").start();
    }
}
