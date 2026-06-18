/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code execution tool facade.
 *
 * <p>Mirrors Python's {@code CodeTool} in
 * {@code openjiuwen/harness/tools/code.py}.</p>
 */
public class CodeTool extends AbstractHarnessTool {

    private static final int DEFAULT_TIMEOUT_SECONDS = 300;
    private static final int FALLBACK_MAX_TIMEOUT_SECONDS = 3600;

    private final CodeExecutor executor;

    public CodeTool(CodeExecutor executor) {
        super(toolCard("code", "CodeTool", "Execute source code snippets in the configured runtime."));
        this.executor = executor;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
        String code = stringValue(inputs == null ? null : inputs.get("code"));
        String language = stringValue(inputs == null ? "python" : inputs.getOrDefault("language", "python"));
        int timeout = resolveTimeout(inputs == null ? null : inputs.get("timeout"));
        if (executor == null) {
            return ToolOutput.failure("code executor is not configured");
        }
        CodeExecutionResult result = executor.execute(code, language, timeout, kwargs == null ? Map.of() : kwargs);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stdout", result.stdout());
        data.put("stderr", result.stderr());
        data.put("exit_code", result.exitCode());
        return ToolOutput.of(result.exitCode() == 0, data, result.exitCode() == 0 ? null : result.stderr());
    }

    public static int resolveTimeout(Object rawValue) {
        int timeout = intValue(rawValue, DEFAULT_TIMEOUT_SECONDS);
        int maxTimeout = intValue(System.getenv("CODE_TOOL_MAX_TIMEOUT_SECONDS"), FALLBACK_MAX_TIMEOUT_SECONDS);
        maxTimeout = Math.max(1, maxTimeout);
        return Math.max(1, Math.min(timeout, maxTimeout));
    }

    /**
     * Java boundary for Python's {@code SysOperation.code().execute_code(...)}.
     */
    @FunctionalInterface
    public interface CodeExecutor {
        CodeExecutionResult execute(String code, String language, int timeoutSeconds, Map<String, Object> kwargs);
    }

    /**
     * Mirrors Python's code execution payload consumed by {@code CodeTool}.
     */
    public record CodeExecutionResult(String stdout, String stderr, int exitCode) {
    }
}
