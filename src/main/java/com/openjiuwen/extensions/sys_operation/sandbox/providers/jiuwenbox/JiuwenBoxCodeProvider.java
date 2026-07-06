/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCodeData;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.SandboxOperationSupport;
import com.openjiuwen.core.sysop.sandbox.providers.BaseCodeProvider;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JiuwenBox sandbox code provider that extends BaseCodeProvider
 * and uses JiuwenBoxProviderMixin for sandbox management.
 *
 * @since 2026-01-01
 * @version 1.0
 */
public class JiuwenBoxCodeProvider extends BaseCodeProvider {
    private final JiuwenBoxProviderMixin mixin;

    public JiuwenBoxCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.mixin = new JiuwenBoxProviderMixin(endpoint, config);
    }

    @Override
    public ExecuteCodeResult executeCode(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        if (isLanguageExcluded(language)) {
            return executeLocalCode(code, language, timeout, environment, options);
        }
        String command = buildCodeCommand(code, language);
        List<String> commandList = List.of("bash", "-lc", command);
        String normalizedCwd = resolveCwd().orElse(null);
        Map<String, String> mergedEnv = prepareCodeEnvironment(language, environment);
        ExecuteCodeResult result;
        try {
            result = mixin.executeWithSandboxRetry(sandboxId -> {
                JiuwenBoxClient.ExecResponse resp = mixin.getClient().exec(
                        sandboxId, commandList, normalizedCwd, timeout, mergedEnv, null);
                ExecuteCodeData data = ExecuteCodeData.builder()
                        .codeContent(code)
                        .language(language)
                        .exitCode(resp.getExitCode())
                        .stdout(resp.getStdout() != null ? resp.getStdout() : "")
                        .stderr(resp.getStderr() != null ? resp.getStderr() : "")
                        .build();
                return new ExecuteCodeResult(0, "success", data);
            });
        } catch (SandboxOperationException | SandboxRecreateExhaustedException e) {
            if (isFallbackOnFailure()) {
                return executeLocalCode(code, language, timeout, environment, options);
            }
            throw e;
        }
        if (isFallbackOnFailure() && result.getData() != null
                && result.getData().getExitCode() != null && result.getData().getExitCode() != 0) {
            return executeLocalCode(code, language, timeout, environment, options);
        }
        return result;
    }

    @Override
    public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language,
            int timeout, Map<String, String> environment, Map<String, Object> options) {
        ExecuteCodeResult fullResult = executeCode(code, language, timeout, environment, options);
        String stdout = fullResult.getData() != null ? fullResult.getData().getStdout() : "";
        String stderr = fullResult.getData() != null ? fullResult.getData().getStderr() : "";
        Integer exitCode = fullResult.getData() != null ? fullResult.getData().getExitCode() : null;
        List<ExecuteCodeStreamResult> chunks = new ArrayList<>();
        String[] lines = stdout.split("\n", -1);
        int totalChunks = Math.max(1, lines.length);
        if (lines.length == 0) {
            totalChunks = 1;
        }
        for (int i = 0; i < totalChunks; i++) {
            String chunkText;
            if (lines.length == 0) {
                chunkText = "";
            } else {
                chunkText = lines[i];
            }
            boolean isLast = i == totalChunks - 1;
            ExecuteCodeChunkData chunkData = ExecuteCodeChunkData.builder()
                    .text(chunkText)
                    .type("stdout")
                    .chunkIndex(i)
                    .exitCode(isLast ? exitCode : null)
                    .metadata(isLast ? Map.of("language", language, "stderr", stderr) : null)
                    .build();
            chunks.add(new ExecuteCodeStreamResult(isLast ? fullResult.getCode() : 0, "success", chunkData));
        }
        return chunks.iterator();
    }

    private String buildCodeCommand(String code, String language) {
        String base64Encoded = Base64.getEncoder().encodeToString(
                code.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        switch (language) {
            case "python":
                return "python3 -c \"import base64,sys;exec(base64.b64decode('" + base64Encoded + "').decode())\"";
            case "javascript":
                return "node -e \"eval(Buffer.from('" + base64Encoded + "','base64').toString())\"";
            default:
                return "bash -lc \"echo '" + base64Encoded + "' | base64 -d > /tmp/_jiuwenbox_code_tmp && "
                        + getInterpreter(language) + " /tmp/_jiuwenbox_code_tmp\"";
        }
    }

    private String getInterpreter(String language) {
        switch (language) {
            case "python":
                return "python3";
            case "javascript":
                return "node";
            case "ruby":
                return "ruby";
            case "perl":
                return "perl";
            case "sh":
            case "bash":
                return "bash";
            default:
                return language;
        }
    }

    private Map<String, String> prepareCodeEnvironment(String language, Map<String, String> userEnv) {
        Map<String, String> defaults = new LinkedHashMap<>();
        switch (language) {
            case "python":
                defaults.put("PYTHONIOENCODING", "utf-8");
                defaults.put("PYTHONUNBUFFERED", "1");
                break;
            case "javascript":
                defaults.put("NODE_OPTIONS", "--no-warnings");
                break;
            default:
                break;
        }
        if (userEnv != null) {
            defaults.putAll(userEnv);
        }
        return defaults;
    }

    private boolean isLanguageExcluded(String language) {
        Map<String, Object> extraParams = mixin.launcherExtraParams(false);
        Object excludedCommands = extraParams.get("excluded_commands");
        if (excludedCommands instanceof List<?> patterns) {
            for (Object patternObj : patterns) {
                String pattern = String.valueOf(patternObj);
                if (matchesGlobPattern(language, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesGlobPattern(String text, String pattern) {
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(java.nio.file.Path.of(text));
        } catch (IllegalArgumentException e) {
            String regex = globToRegex(pattern);
            return text.matches(regex);
        }
    }

    private String globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append(".");
            } else if ("[](){}^$|+\\.".indexOf(c) >= 0) {
                regex.append("\\").append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append("$");
        return regex.toString();
    }

    private boolean isFallbackOnFailure() {
        Map<String, Object> extraParams = mixin.launcherExtraParams(false);
        Object fallback = extraParams.get("fallback_on_failure");
        if (fallback instanceof Boolean isFallback) {
            return isFallback;
        }
        return false;
    }

    private Optional<String> resolveCwd() {
        if (config != null && config.getLauncherConfig() != null) {
            Map<String, Object> params = config.getParams();
            if (params != null && params.containsKey("work_dir")) {
                return Optional.of(String.valueOf(params.get("work_dir")));
            }
        }
        return Optional.empty();
    }

    private ExecuteCodeResult executeLocalCode(String code, String language, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        LocalCodeOperation localOp = new LocalCodeOperation(
                SandboxOperationSupport.toLocalWorkConfig(config));
        return localOp.executeCode(code, language, timeout, environment, options);
    }
}
