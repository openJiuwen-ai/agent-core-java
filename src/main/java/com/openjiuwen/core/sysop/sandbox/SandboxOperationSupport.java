/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.BaseResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.sandbox.gateway.SandboxGateway;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class providing sandbox operation support methods for configuration,
 * isolation key resolution, shell/code error building, and parameter extraction.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public final class SandboxOperationSupport {
    private static final String SESSION_ID_PLACEHOLDER = "{session_id}";

    private SandboxOperationSupport() {}

    /**
     * Converts a SandboxGatewayConfig into a LocalWorkConfig for local sandbox operations.
     *
     * @param config the sandbox gateway configuration
     * @return a LocalWorkConfig instance derived from the gateway config
     */
    public static LocalWorkConfig toLocalWorkConfig(SandboxGatewayConfig config) {
        Map<String, Object> params =
                config != null && config.getParams() != null ? config.getParams() : Map.of();
        String rootPath =
                stringParam(
                        params,
                        "root_path",
                        stringParam(params, "work_dir",
                                Path.of(".").toAbsolutePath().normalize().toString()));
        List<String> allowlist = listParam(params, "shell_allowlist");
        LocalWorkConfig localConfig = new LocalWorkConfig(
                allowlist,
                List.of(Path.of(rootPath).toAbsolutePath().normalize().toString()),
                true,
                null,
                rootPath
        );
        return localConfig;
    }

    /**
     * Returns the sandbox root path derived from the given config.
     *
     * @param config the sandbox gateway configuration
     * @return the normalized absolute sandbox root path
     */
    public static Path sandboxRoot(SandboxGatewayConfig config) {
        return Path.of(toLocalWorkConfig(config).getWorkDir())
                .toAbsolutePath().normalize();
    }

    /**
     * Resolves the isolation key from config, substituting session placeholders if present.
     *
     * @param config the sandbox gateway configuration
     * @return the resolved isolation key
     */
    public static String resolveIsolationKey(SandboxGatewayConfig config) {
        String key = computeIsolationKey(config);
        return resolveIsolationKeyTemplate(key);
    }

    /**
     * Resolves session ID placeholders in an isolation key template.
     *
     * @param template the isolation key template possibly containing {session_id}
     * @return the resolved isolation key with session ID substituted
     */
    public static String resolveIsolationKeyTemplate(String template) {
        if (template.contains(SESSION_ID_PLACEHOLDER)) {
            Object session = com.openjiuwen.core.session.SessionContextHolder.getCurrentSession();
            String sessionId = com.openjiuwen.core.session.SessionContextHolder.resolveSessionId(session);
            String resolved = sessionId != null ? sessionId : "default_session";
            return template.replace(SESSION_ID_PLACEHOLDER, resolved);
        }
        return template;
    }

    /**
     * Computes the isolation key from the sandbox gateway configuration.
     *
     * @param config the sandbox gateway configuration
     * @return the computed isolation key
     */
    public static String computeIsolationKey(SandboxGatewayConfig config) {
        if (config != null && config.getIsolation() != null) {
            if (config.getIsolation().getCustomId() != null
                    && !config.getIsolation().getCustomId().isBlank()) {
                return config.getIsolation().getCustomId();
            }
            if (config.getIsolation().getPrefix() != null
                    && !config.getIsolation().getPrefix().isBlank()) {
                return config.getIsolation().getPrefix() + ":" + sandboxRoot(config);
            }
        }
        return "sandbox:" + sandboxRoot(config);
    }

    /**
     * Creates a parameter map from alternating key-value pairs provided as a list.
     *
     * @param kvPairs alternating key and value objects as a list
     * @return a map built from the key-value pairs
     */
    public static Map<String, Object> params(List<Object> kvPairs) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kvPairs.size(); i += 2) {
            params.put(String.valueOf(kvPairs.get(i)), kvPairs.get(i + 1));
        }
        return params;
    }

    /**
     * Convenience method to create a parameter map from alternating key-value pairs.
     *
     * @param kvPairs alternating key and value objects (varargs)
     * @return a map built from the key-value pairs
     */
    public static Map<String, Object> paramsOf(Object... kvPairs) {
        return params(new ArrayList<>(Arrays.asList(kvPairs)));
    }

    /**
     * Normalizes a shell working directory relative to the sandbox root, ensuring it does not traverse outside.
     *
     * @param config the sandbox gateway configuration
     * @param cwd the working directory path to normalize
     * @return the normalized relative path from sandbox root
     * @throws IllegalArgumentException if cwd traverses outside sandbox root
     */
    public static String normalizeShellCwd(SandboxGatewayConfig config, String cwd) {
        Path root = sandboxRoot(config);
        Path target;
        if (cwd == null || cwd.isBlank()) {
            target = root;
        } else {
            Path raw = Path.of(cwd);
            target =
                    raw.isAbsolute()
                            ? raw.toAbsolutePath().normalize()
                            : root.resolve(raw).toAbsolutePath().normalize();
        }
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException(
                    "Access denied: cwd " + target + " traverses outside " + root);
        }
        if (target.equals(root)) {
            return ".";
        }
        return root.relativize(target).toString();
    }

    /**
     * Wraps code with a sandbox working directory change directive for the given language.
     *
     * @param code the source code to wrap
     * @param language the programming language (python or javascript)
     * @param config the sandbox gateway configuration
     * @return the wrapped code with directory change prepended, or original code if language is unsupported
     */
    public static String wrapCodeWithSandboxCwd(
            String code, String language, SandboxGatewayConfig config) {
        Path root = sandboxRoot(config);
        String escaped = root.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        if ("python".equals(language)) {
            return "import os\nos.chdir(\"" + escaped + "\")\n" + code;
        }
        if ("javascript".equals(language)) {
            return "process.chdir(\"" + escaped + "\");\n" + code;
        }
        return code;
    }

    /**
     * Builds an error result for shell command execution failures.
     *
     * @param execution the execution identifier
     * @param errorMsg the error message
     * @param command the command that failed
     * @param cwd the working directory of the command
     * @return an ExecuteCmdResult representing the shell error
     */
    public static ExecuteCmdResult buildShellError(
            String execution, String errorMsg, String command, String cwd) {
        ExecuteCmdData cmdData = new ExecuteCmdData();
        cmdData.setCommand(command);
        cmdData.setCwd(cwd == null ? "." : cwd);
        return com.openjiuwen.core.sysop.result.BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", errorMsg),
                ExecuteCmdResult.class,
                cmdData);
    }

    /**
     * Builds an error result for code execution failures.
     *
     * @param execution the execution identifier
     * @param errorMsg the error message
     * @param code the code that failed
     * @param language the programming language of the code
     * @return an ExecuteCodeResult representing the code error
     */
    public static ExecuteCodeResult buildCodeError(
            String execution, String errorMsg, String code, String language) {
        ExecuteCodeData codeData = new ExecuteCodeData();
        codeData.setCodeContent(code);
        codeData.setLanguage(language);
        return com.openjiuwen.core.sysop.result.BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", errorMsg),
                ExecuteCodeResult.class,
                codeData);
    }

    /**
     * Builds a stream error result for shell command execution failures.
     *
     * @param execution the execution identifier
     * @param errorMsg the error message
     * @param command the command that failed
     * @param cwd the working directory of the command
     * @return an ExecuteCmdStreamResult representing the shell stream error
     */
    public static ExecuteCmdStreamResult buildShellStreamError(
            String execution, String errorMsg, String command, String cwd) {
        ExecuteCmdChunkData chunkData = new ExecuteCmdChunkData();
        chunkData.setChunkIndex(0);
        chunkData.setExitCode(-1);
        chunkData.setType("stderr");
        chunkData.setMetadata(Map.of(
                "command", command,
                "cwd", cwd == null ? "." : cwd));
        return com.openjiuwen.core.sysop.result.BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SHELL_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", errorMsg),
                ExecuteCmdStreamResult.class,
                chunkData);
    }

    /**
     * Builds a stream error result for code execution failures.
     *
     * @param execution the execution identifier
     * @param errorMsg the error message
     * @param code the code that failed
     * @param language the programming language of the code
     * @return an ExecuteCodeStreamResult representing the code stream error
     */
    public static ExecuteCodeStreamResult buildCodeStreamError(
            String execution, String errorMsg, String code, String language) {
        ExecuteCodeChunkData codeChunkData = new ExecuteCodeChunkData();
        codeChunkData.setChunkIndex(0);
        codeChunkData.setExitCode(-1);
        codeChunkData.setType("stderr");
        codeChunkData.setMetadata(Map.of(
                "code", code,
                "language", language));
        return com.openjiuwen.core.sysop.result.BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", errorMsg),
                ExecuteCodeStreamResult.class,
                codeChunkData);
    }

    /**
     * Extracts a string parameter from a map with a default value fallback.
     *
     * @param params the parameter map
     * @param key the parameter key
     * @param defaultValue the default value if the key is missing or blank
     * @return the string value or the default value
     */
    public static String stringParam(
            Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        return value == null || String.valueOf(value).isBlank()
                ? defaultValue : String.valueOf(value);
    }

    /**
     * Extracts a list of strings from a parameter map, supporting both List and comma-separated String values.
     *
     * @param params the parameter map
     * @param key the parameter key
     * @return a list of strings, or an empty list if the key is missing
     */
    @SuppressWarnings("unchecked")
    public static List<String> listParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (value instanceof String text) {
            return Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(part -> !part.isBlank())
                    .toList();
        }
        return List.of();
    }
}
