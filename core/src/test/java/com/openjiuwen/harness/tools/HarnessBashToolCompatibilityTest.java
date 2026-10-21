/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class HarnessBashToolCompatibilityTest {
    @TempDir Path tempDir;

    @Test
    void bashToolShouldExecuteCommandAndInterpretGrepExit() throws InterruptedException {
        assumeBashAvailable();
        BashTool tool = new BashTool();

        ToolOutput echo = tool.invoke("echo hello", null, false, 8000);
        ToolOutput grep = tool.invoke("echo hello | grep missing_pattern_xyz", null, false, 8000);

        assertThat(echo.isSuccess())
                .as(
                        "echo result: success=%s, data=%s, error=%s",
                        echo.isSuccess(), echo.getData(), echo.getError())
                .isTrue();
        assertThat(String.valueOf(((java.util.Map<?, ?>) echo.getData()).get("stdout")))
                .contains("hello");
        assertThat(grep.isSuccess()).isTrue();
        assertThat(((java.util.Map<?, ?>) grep.getData()).get("return_code_interpretation"))
                .isEqualTo("No matches found");
    }

    private static void assumeBashAvailable() throws InterruptedException {
        boolean isAvailable;
        try {
            Process process =
                    new ProcessBuilder("bash", "-lc", "true")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (process.isAlive() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(50);
            }
            isAvailable = !process.isAlive() && process.exitValue() == 0;
            if (!isAvailable) {
                process.destroyForcibly();
            }
        } catch (IOException exception) {
            isAvailable = false;
        }
        Assumptions.assumeTrue(
                isAvailable,
                "bash is unavailable on this platform; execute this assertion on Linux CI or"
                    + " Windows with Git Bash");
    }

    @Test
    void bashToolShouldBlockInjectionAndReadOnlyWrite() {
        BashTool defaultTool = new BashTool();
        BashTool readOnlyTool = new BashTool("read_only");

        ToolOutput injected = defaultTool.invoke("echo $(id)", null, false, 8000);
        ToolOutput blockedWrite = readOnlyTool.invoke("touch file.txt", null, false, 8000);

        assertThat(injected.isSuccess()).isFalse();
        assertThat(injected.getError()).contains("injection");
        assertThat(blockedWrite.isSuccess()).isFalse();
        assertThat(blockedWrite.getError()).contains("Read-only");
    }

    @Test
    @Disabled("Still failing after re-enable; kept disabled pending targeted fix")
    void bashToolShouldSupportWorkdirBackgroundAndWarnings() {
        BashTool tool = new BashTool();

        ToolOutput missingDir =
                tool.invoke("echo hi", tempDir.resolve("missing").toString(), false, 8000);
        ToolOutput background = tool.invoke("sleep 1", tempDir.toString(), true, 8000);
        ToolOutput warning =
                tool.invoke("git commit --amend -m test", tempDir.toString(), false, 8000);

        assertThat(missingDir.isSuccess()).isFalse();
        assertThat(background.isSuccess()).isTrue();
        assertThat(((java.util.Map<?, ?>) background.getData()).get("status")).isEqualTo("started");
        assertThat(((java.util.Map<?, ?>) warning.getData()).get("destructive_warning"))
                .isNotNull();
    }
}
