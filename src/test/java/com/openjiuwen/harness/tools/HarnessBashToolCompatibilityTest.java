package com.openjiuwen.harness.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessBashToolCompatibilityTest {

    @TempDir
    Path tempDir;

    @Test
    void bashToolShouldExecuteCommandAndInterpretGrepExit() {
        BashTool tool = new BashTool();

        ToolOutput echo = tool.invoke("echo hello", null, false, 8000);
        ToolOutput grep = tool.invoke("echo hello | grep missing_pattern_xyz", null, false, 8000);

        assertThat(echo.isSuccess()).isTrue();
        assertThat(String.valueOf(((java.util.Map<?, ?>) echo.getData()).get("stdout"))).contains("hello");
        assertThat(grep.isSuccess()).isTrue();
        assertThat(((java.util.Map<?, ?>) grep.getData()).get("return_code_interpretation")).isEqualTo("No matches found");
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
    void bashToolShouldSupportWorkdirBackgroundAndWarnings() {
        BashTool tool = new BashTool();

        ToolOutput missingDir = tool.invoke("echo hi", tempDir.resolve("missing").toString(), false, 8000);
        ToolOutput background = tool.invoke("sleep 1", tempDir.toString(), true, 8000);
        ToolOutput warning = tool.invoke("git commit --amend -m test", tempDir.toString(), false, 8000);

        assertThat(missingDir.isSuccess()).isFalse();
        assertThat(background.isSuccess()).isTrue();
        assertThat(((java.util.Map<?, ?>) background.getData()).get("status")).isEqualTo("started");
        assertThat(((java.util.Map<?, ?>) warning.getData()).get("destructive_warning")).isNotNull();
    }
}
