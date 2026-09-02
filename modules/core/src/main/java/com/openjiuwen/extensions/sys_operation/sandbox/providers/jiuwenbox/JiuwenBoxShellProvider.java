/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers.jiuwenbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.local.LocalShellOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sysop.result.ExecuteCmdData;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sysop.sandbox.SandboxEndpoint;
import com.openjiuwen.core.sysop.sandbox.SandboxOperationSupport;
import com.openjiuwen.core.sysop.sandbox.providers.BaseShellProvider;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JiuwenBox sandbox shell provider that extends BaseShellProvider
 * and uses JiuwenBoxProviderMixin for sandbox management.
 * 
 * @version 1.0
 * @since 0.1.7
 */
public class JiuwenBoxShellProvider extends BaseShellProvider {
    private final JiuwenBoxProviderMixin mixin;

    /**
     * JiuwenBoxShellProvider.
     * 
     * @param endpoint endpoint
     * @param config config
     * @since 0.1.7
     */
    public JiuwenBoxShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.mixin = new JiuwenBoxProviderMixin(endpoint, config);
    }

    /**
     * executeCmd.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdResult executeCmd(String command, String cwd, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        if (isCommandExcluded(command)) {
            return executeLocalCmd(command, cwd, timeout, environment, options);
        }
        ExecuteCmdResult result;
        try {
            result = mixin.executeWithSandboxRetry(sandboxId -> {
                List<String> cmdList = List.of("bash", "-lc", command);
                JiuwenBoxClient.ExecResponse resp =
                    mixin.getClient().exec(sandboxId, cmdList, cwd, timeout, environment, null);
                ExecuteCmdData data = ExecuteCmdData.builder().command(command).cwd(cwd != null ? cwd : ".")
                        .exitCode(resp.getExitCode()).stdout(resp.getStdout() != null ? resp.getStdout() : "")
                        .stderr(resp.getStderr() != null ? resp.getStderr() : "").shellType("bash").build();
                return new ExecuteCmdResult(0, "success", data);
            });
        } catch (SandboxOperationException | SandboxRecreateExhaustedException e) {
            if (isFallbackOnFailure()) {
                return executeLocalCmd(command, cwd, timeout, environment, options);
            }
            throw e;
        }
        if (isFallbackOnFailure() && result.getData() != null && result.getData().getExitCode() != null
                && result.getData().getExitCode() != 0) {
            return executeLocalCmd(command, cwd, timeout, environment, options);
        }
        return result;
    }

    /**
     * executeCmdStream.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Iterator<ExecuteCmdStreamResult> executeCmdStream(String command, String cwd, int timeout,
            Map<String, String> environment, Map<String, Object> options) {
        ExecuteCmdResult fullResult = executeCmd(command, cwd, timeout, environment, options);
        String stdout = fullResult.getData() != null ? fullResult.getData().getStdout() : "";
        String stderr = fullResult.getData() != null ? fullResult.getData().getStderr() : "";
        Integer exitCode = fullResult.getData() != null ? fullResult.getData().getExitCode() : null;
        List<ExecuteCmdStreamResult> chunks = new ArrayList<>();
        String[] lines = stdout.split("\n", -1);
        int chunkLineSize = 1;
        int totalChunks = Math.max(1, lines.length / chunkLineSize);
        if (lines.length == 0) {
            totalChunks = 1;
        } else {
            totalChunks = lines.length;
        }
        for (int i = 0; i < totalChunks; i++) {
            String chunkText;
            if (lines.length == 0) {
                chunkText = "";
            } else {
                chunkText = lines[i];
            }
            boolean isLast = i == totalChunks - 1;
            ExecuteCmdChunkData chunkData = ExecuteCmdChunkData.builder().text(chunkText).type("stdout").chunkIndex(i)
                    .exitCode(isLast ? exitCode : null)
                    .metadata(isLast
                            ? Map.of("command", command, "cwd", cwd != null ? cwd : ".", "stderr", stderr)
                            : null)
                    .build();
            chunks.add(new ExecuteCmdStreamResult(isLast ? fullResult.getCode() : 0, "success", chunkData));
        }
        return chunks.iterator();
    }

    /**
     * executeCmdBackground.
     * 
     * @param command command
     * @param cwd cwd
     * @param environment environment
     * @param grace grace
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd, Map<String, String> environment,
            double grace, Map<String, Object> options) {
        ExecuteCmdResult cmdResult = executeCmd(command, cwd, 30, environment, options);
        ExecuteCmdBackgroundData data = ExecuteCmdBackgroundData.builder().command(command).cwd(cwd != null ? cwd : ".")
                .pid(null).shellType("jiuwenbox-background").build();
        return new ExecuteCmdBackgroundResult(cmdResult.getCode(), cmdResult.getMessage(), data);
    }

    /**
     * isCommandExcluded.
     * 
     * @param command command
     * @return the result
     * @since 0.1.7
     */
    private boolean isCommandExcluded(String command) {
        Map<String, Object> extraParams = mixin.launcherExtraParams(false);
        Object excludedCommands = extraParams.get("excluded_commands");
        if (excludedCommands instanceof List<?> patterns) {
            for (Object patternObj : patterns) {
                String pattern = String.valueOf(patternObj);
                if (matchesGlobPattern(command, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * matchesGlobPattern.
     * 
     * @param text text
     * @param pattern pattern
     * @return the result
     * @since 0.1.7
     */
    private boolean matchesGlobPattern(String text, String pattern) {
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(java.nio.file.Path.of(text));
        } catch (IllegalArgumentException e) {
            String regex = globToRegex(pattern);
            return text.matches(regex);
        }
    }

    /**
     * globToRegex.
     * 
     * @param glob glob
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * isFallbackOnFailure.
     * 
     * @return the result
     * @since 0.1.7
     */
    private boolean isFallbackOnFailure() {
        Map<String, Object> extraParams = mixin.launcherExtraParams(false);
        Object fallback = extraParams.get("fallback_on_failure");
        if (fallback instanceof Boolean isFallback) {
            return isFallback;
        }
        return false;
    }

    /**
     * executeLocalCmd.
     * 
     * @param command command
     * @param cwd cwd
     * @param timeout timeout
     * @param environment environment
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    private ExecuteCmdResult executeLocalCmd(String command, String cwd, int timeout, Map<String, String> environment,
            Map<String, Object> options) {
        LocalShellOperation localOp = new LocalShellOperation(SandboxOperationSupport.toLocalWorkConfig(config));
        return localOp.executeCmd(command, cwd, timeout, environment, options);
    }
}
