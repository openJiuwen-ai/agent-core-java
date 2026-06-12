/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.providers.BaseShellProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Mirrors Python's {@code JiuwenBoxShellProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
 */
public class JiuwenBoxShellProvider extends BaseShellProvider {

    private final JiuwenBoxProviderSupport.ProviderState state;

    public JiuwenBoxShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.state = new JiuwenBoxProviderSupport.ProviderState(endpoint, config);
    }

    @Override
    public CompletableFuture<ExecuteCmdResult> executeCmd(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            if (command == null || command.isBlank()) {
                return JiuwenBoxProviderSupport.buildShellErrorResult(
                        "execute_cmd",
                        "command can not be empty",
                        ExecuteCmdResult.class);
            }
            String workdir = cwd == null || ".".equals(cwd) ? null : cwd;
            Integer execTimeout = timeoutSeconds != null && timeoutSeconds > 0 ? timeoutSeconds : null;
            Map<String, Object> extraParams = state.launcherExtraParams(false);
            List<String> excludePatterns = JiuwenBoxProviderSupport.readExcludedCommands(extraParams);
            boolean fallbackOnFailure = extraParams.get("fallback_on_failure") instanceof Boolean bool && bool;

            if (JiuwenBoxProviderSupport.commandMatchesExclude(command, excludePatterns)) {
                JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                        JiuwenBoxProviderSupport.shellCommand(command),
                        workdir,
                        environment,
                        execTimeout,
                        null);
                return wrapLocalResult(command, cwd, timeoutSeconds, localResult);
            }

            try {
                Map<String, Object> result = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().exec(
                                sandboxId,
                                JiuwenBoxProviderSupport.shellCommand(command),
                                workdir,
                                execTimeout,
                                environment,
                                null));
                ExecuteCmdData data = ExecuteCmdData.builder()
                        .command(command)
                        .cwd(cwd == null ? "." : cwd)
                        .stdout(Optional.ofNullable((String) result.get("stdout")).orElse(""))
                        .stderr(Optional.ofNullable((String) result.get("stderr")).orElse(""))
                        .exitCode(JiuwenBoxProviderSupport.asInt(result.get("exit_code"), 0))
                        .build();
                if (data.getExitCode() == 124) {
                    return JiuwenBoxProviderSupport.buildShellErrorResult(
                            "execute_cmd",
                            "execution timeout after " + timeoutSeconds + " seconds",
                            ExecuteCmdResult.class,
                            data);
                }
                if (data.getExitCode() != 0 && fallbackOnFailure) {
                    JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                            JiuwenBoxProviderSupport.shellCommand(command),
                            workdir,
                            environment,
                            execTimeout,
                            null);
                    return wrapLocalResult(command, cwd, timeoutSeconds, localResult);
                }
                ExecuteCmdResult shellResult = new ExecuteCmdResult();
                shellResult.setCode(StatusCode.SUCCESS.getCode());
                shellResult.setMessage(StatusCode.SUCCESS.getErrmsg());
                shellResult.setData(data);
                return shellResult;
            } catch (Exception exception) {
                if (fallbackOnFailure) {
                    JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                            JiuwenBoxProviderSupport.shellCommand(command),
                            workdir,
                            environment,
                            execTimeout,
                            null);
                    return wrapLocalResult(command, cwd, timeoutSeconds, localResult);
                }
                return JiuwenBoxProviderSupport.buildShellErrorResult(
                        "execute_cmd",
                        exception.getMessage(),
                        ExecuteCmdResult.class);
            }
        });
    }

    @Override
    public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment,
            Map<String, Object> options) {
        return AioProviderSupport.asyncPublisher(publisher -> {
            ExecuteCmdResult result = executeCmd(command, cwd, timeoutSeconds, environment, options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                publisher.submit(JiuwenBoxProviderSupport.buildShellErrorResult(
                        "execute_cmd_stream",
                        result.getMessage(),
                        ExecuteCmdStreamResult.class,
                        ExecuteCmdChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return;
            }

            List<Chunk> chunks = new ArrayList<>();
            addSplitLines(chunks, result.getData() == null ? "" : result.getData().getStdout(), "stdout");
            addSplitLines(chunks, result.getData() == null ? "" : result.getData().getStderr(), "stderr");
            for (int index = 0; index < chunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                ExecuteCmdStreamResult streamResult = new ExecuteCmdStreamResult();
                streamResult.setCode(StatusCode.SUCCESS.getCode());
                streamResult.setMessage("Get " + chunk.type() + " stream successfully");
                streamResult.setData(ExecuteCmdChunkData.builder()
                        .text(chunk.text())
                        .type(chunk.type())
                        .chunkIndex(index)
                        .build());
                publisher.submit(streamResult);
            }
            ExecuteCmdStreamResult finalResult = new ExecuteCmdStreamResult();
            finalResult.setCode(StatusCode.SUCCESS.getCode());
            finalResult.setMessage("Command executed successfully");
            finalResult.setData(ExecuteCmdChunkData.builder()
                    .chunkIndex(chunks.size())
                    .exitCode(result.getData() == null ? 0 : result.getData().getExitCode())
                    .build());
            publisher.submit(finalResult);
        });
    }

    private ExecuteCmdResult wrapLocalResult(
            String command,
            String cwd,
            Integer timeoutSeconds,
            JiuwenBoxProviderSupport.LocalProcessResult localResult) {
        ExecuteCmdData data = ExecuteCmdData.builder()
                .command(command)
                .cwd(cwd == null ? "." : cwd)
                .stdout(localResult.stdout())
                .stderr(localResult.stderr())
                .exitCode(localResult.exitCode())
                .build();
        if (localResult.exitCode() == 124) {
            return JiuwenBoxProviderSupport.buildShellErrorResult(
                    "execute_cmd",
                    "execution timeout after " + timeoutSeconds + " seconds (local fallback)",
                    ExecuteCmdResult.class,
                    data);
        }
        ExecuteCmdResult result = new ExecuteCmdResult();
        result.setCode(StatusCode.SUCCESS.getCode());
        result.setMessage(StatusCode.SUCCESS.getErrmsg());
        result.setData(data);
        return result;
    }

    private void addSplitLines(List<Chunk> chunks, String content, String type) {
        if (content == null || content.isEmpty()) {
            return;
        }
        int start = 0;
        while (start < content.length()) {
            int lineEnd = content.indexOf('\n', start);
            String line;
            if (lineEnd >= 0) {
                line = content.substring(start, lineEnd + 1);
                start = lineEnd + 1;
            } else {
                line = content.substring(start);
                start = content.length();
            }
            chunks.add(new Chunk(line, type));
        }
    }

    private record Chunk(String text, String type) {
    }
}
