
package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.testsupport.OsTestSupport;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class HarnessBashToolCompatibilityTest {
    @TempDir
    Path tempDir;

    @Test
    void bashToolShouldExecuteCommandAndInterpretGrepExit() {
        Assumptions.assumeTrue(OsTestSupport.isBashAvailable(), "bash not found, skipping");
        BashTool tool = new BashTool();

        ToolOutput echo = tool.invoke("echo hello", null, false, 8000);
        ToolOutput grep = tool.invoke("echo hello | grep missing_pattern_xyz", null, false, 8000);

        assertThat(echo.isSuccess()).isTrue();
        assertThat(String.valueOf(((Map<?, ?>) echo.getData()).get("stdout"))).contains("hello");
        assertThat(grep.isSuccess()).isTrue();
        assertThat(((Map<?, ?>) grep.getData()).get("return_code_interpretation")).isEqualTo("No matches found");
    }

    @Test
    void bashToolShouldBlockInjectionAndReadOnlyWrite() {
        Assumptions.assumeTrue(OsTestSupport.isBashAvailable(), "bash not found, skipping");
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
    void bashToolShouldSupportWorkdirBackgroundAndWarnings() throws Exception {
        Assumptions.assumeTrue(OsTestSupport.isBashAvailable(), "bash not found, skipping");
        BashTool tool = new BashTool();
        // Keep background cwd outside @TempDir so Windows cleanup is not blocked by the child process.
        Path backgroundDir = Files.createTempDirectory("bash-bg");
        try {
            ToolOutput missingDir = tool.invoke("echo hi", tempDir.resolve("missing").toString(), false, 8000);
            ToolOutput background =
                tool.invoke(OsTestSupport.shortBackgroundWaitCommand(), backgroundDir.toString(), true, 8000);
            ToolOutput warning = tool.invoke("git commit --amend -m test", tempDir.toString(), false, 8000);

            assertThat(missingDir.isSuccess()).isFalse();
            assertThat(background.isSuccess()).isTrue();
            assertThat(((Map<?, ?>) background.getData()).get("status")).isEqualTo("started");
            Object pid = ((Map<?, ?>) background.getData()).get("pid");
            if (pid instanceof Number number) {
                OsTestSupport.destroyProcessTree(number.longValue());
            }
            assertThat(((Map<?, ?>) warning.getData()).get("destructive_warning")).isNotNull();
        } finally {
            Thread.sleep(300);
            try {
                Files.walk(backgroundDir).sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }
}
