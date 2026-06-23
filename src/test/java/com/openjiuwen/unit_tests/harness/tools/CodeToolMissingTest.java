/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.harness.tools;

import com.openjiuwen.core.sys_operation.Cwd;
import com.openjiuwen.harness.tools.CodeTool;
import com.openjiuwen.harness.tools.ToolOutput;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/harness/tools/test_code_tools.py}.</p>
 */
class CodeToolMissingTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearCwd() {
        Cwd.clear();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCodeTool() throws Exception {
        CodeTool codeTool = new CodeTool((code, language, timeoutSeconds, kwargs) ->
                new CodeTool.CodeExecutionResult("你好\n", "", 0));

        ToolOutput codeResult = (ToolOutput) codeTool.invoke(Map.of(
                "code", "print('你好')",
                "language", "python"
        ));

        Map<String, Object> data = (Map<String, Object>) codeResult.getData();
        assertThat(codeResult.isSuccess()).isTrue();
        assertThat(data.get("exit_code")).isEqualTo(0);
        assertThat(data.get("stderr")).isEqualTo("");
        assertThat(String.valueOf(data.get("stdout"))).contains("你好");
        assertThat(codeResult.getError()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCodeToolError() throws Exception {
        CodeTool codeTool = new CodeTool((code, language, timeoutSeconds, kwargs) ->
                new CodeTool.CodeExecutionResult("", "SyntaxError: invalid syntax", 1));

        ToolOutput errorResult = (ToolOutput) codeTool.invoke(Map.of(
                "code", "def f(:\n    pass",
                "language", "python"
        ));

        Map<String, Object> data = (Map<String, Object>) errorResult.getData();
        assertThat(errorResult.isSuccess()).isFalse();
        assertThat((Integer) data.get("exit_code")).isNotZero();
        assertThat(String.valueOf(data.get("stderr"))).isNotEmpty();
    }

    @Test
    void testCodeToolUnsupportedLanguage() throws Exception {
        CodeTool codeTool = new CodeTool((code, language, timeoutSeconds, kwargs) ->
                new CodeTool.CodeExecutionResult("", language + " is not supported", 1));

        ToolOutput languageResult = (ToolOutput) codeTool.invoke(Map.of(
                "code", "print(1)",
                "language", "ruby"
        ));

        assertThat(languageResult.isSuccess()).isFalse();
        assertThat(languageResult.getError()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCodeToolUsesContextCwd() throws Exception {
        Cwd.initCwd(tempDir.resolve("member-workspace").toString(),
                tempDir.resolve("member-workspace").toString(),
                tempDir.resolve("member-workspace").toString(),
                null);
        AtomicReference<Map<String, Object>> capturedKwargs = new AtomicReference<>();
        CodeTool codeTool = new CodeTool((code, language, timeoutSeconds, kwargs) -> {
            capturedKwargs.set(new LinkedHashMap<>(kwargs));
            return new CodeTool.CodeExecutionResult(String.valueOf(kwargs.get("cwd")), "", 0);
        });

        ToolOutput codeResult = (ToolOutput) codeTool.invoke(Map.of(
                "code", "import os; print(os.getcwd())",
                "language", "python"
        ));

        Map<String, Object> data = (Map<String, Object>) codeResult.getData();
        assertThat(codeResult.isSuccess()).isTrue();
        assertThat(data.get("exit_code")).isEqualTo(0);
        assertThat(String.valueOf(data.get("stdout"))).isEqualTo(tempDir.resolve("member-workspace").toAbsolutePath()
                .normalize().toString());
        assertThat(capturedKwargs.get()).containsEntry("cwd", tempDir.resolve("member-workspace").toAbsolutePath()
                .normalize().toString());
    }

    @Test
    void testCodeToolDoesNotPassCwdForNonLocalOperation() throws Exception {
        AtomicReference<Map<String, Object>> capturedKwargs = new AtomicReference<>();
        CodeTool codeTool = new CodeTool((code, language, timeoutSeconds, kwargs) -> {
            capturedKwargs.set(new LinkedHashMap<>(kwargs));
            return new CodeTool.CodeExecutionResult("ok", "", 0);
        }, false);

        ToolOutput codeResult = (ToolOutput) codeTool.invoke(Map.of(
                "code", "print('ok')",
                "language", "python"
        ));

        assertThat(codeResult.isSuccess()).isTrue();
        assertThat(capturedKwargs.get()).doesNotContainKey("cwd");
    }
}
