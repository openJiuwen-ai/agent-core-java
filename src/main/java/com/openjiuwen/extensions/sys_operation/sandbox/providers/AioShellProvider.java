/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.fasterxml.jackson.databind.JsonNode;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.providers.BaseShellProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Mirrors Python's {@code AIOShellProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/aio.py}.
 */
public class AioShellProvider extends BaseShellProvider {

    private final AioProviderSupport.AioHttpClient client;
    private final int timeoutSeconds;

    public AioShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.timeoutSeconds = AioProviderSupport.resolveProviderTimeoutSeconds(config);
        this.client = new AioProviderSupport.AioHttpClient(endpoint, config, timeoutSeconds);
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
                return AioProviderSupport.buildShellErrorResult(
                        "execute_cmd",
                        "command can not be empty",
                        ExecuteCmdResult.class);
            }
            String wrappedCommand = buildWrappedCommand(
                    command,
                    cwd == null ? "." : cwd,
                    timeoutSeconds,
                    environment);
            try {
                JsonNode response = AioProviderSupport.withRetry(this.timeoutSeconds, () -> client.postJson(
                        "v1/shell/exec",
                        Map.of("command", wrappedCommand)));
                int exitCode = response.path("exit_code").asInt(0);
                String output = response.path("output").asText("");
                AioProviderSupport.SplitOutput splitOutput = AioProviderSupport.splitMarkedShellOutput(output);
                ExecuteCmdData data = ExecuteCmdData.builder()
                        .command(command)
                        .cwd(cwd == null ? "." : cwd)
                        .stdout(splitOutput.stdout())
                        .stderr(splitOutput.stderr())
                        .exitCode(exitCode)
                        .build();
                if (exitCode == 124) {
                    return AioProviderSupport.buildShellErrorResult(
                            "execute_cmd",
                            "execution timeout after " + timeoutSeconds + " seconds",
                            ExecuteCmdResult.class,
                            data);
                }
                ExecuteCmdResult result = new ExecuteCmdResult();
                result.setCode(StatusCode.SUCCESS.getCode());
                result.setMessage(StatusCode.SUCCESS.getErrmsg());
                result.setData(data);
                return result;
            } catch (Exception exception) {
                return AioProviderSupport.buildShellErrorResult(
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
            if (command == null || command.isBlank()) {
                publisher.submit(AioProviderSupport.buildShellErrorResult(
                        "execute_cmd_stream",
                        "command can not be empty",
                        ExecuteCmdStreamResult.class,
                        ExecuteCmdChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return;
            }
            ExecuteCmdResult result = executeCmd(command, cwd, timeoutSeconds, environment, options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                publisher.submit(AioProviderSupport.buildShellErrorResult(
                        "execute_cmd_stream",
                        stripReasonPrefix(result.getMessage()),
                        ExecuteCmdStreamResult.class,
                        ExecuteCmdChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return;
            }

            List<Chunk> chunks = new ArrayList<>();
            String stdout = result.getData() == null ? "" : Optional.ofNullable(result.getData().getStdout()).orElse("");
            String stderr = result.getData() == null ? "" : Optional.ofNullable(result.getData().getStderr()).orElse("");
            addSplitLines(chunks, stdout, "stdout");
            addSplitLines(chunks, stderr, "stderr");
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

    static String buildWrappedCommand(
            String command,
            String cwd,
            Integer timeoutSeconds,
            Map<String, String> environment) {
        List<String> innerParts = new ArrayList<>();
        if (cwd != null && !cwd.isBlank()) {
            innerParts.add("cd " + AioProviderSupport.quoteShellValue(cwd));
        }
        if (environment != null && !environment.isEmpty()) {
            StringBuilder envBuilder = new StringBuilder("export ");
            boolean first = true;
            for (Map.Entry<String, String> entry : environment.entrySet()) {
                if (!first) {
                    envBuilder.append(' ');
                }
                first = false;
                envBuilder.append(entry.getKey())
                        .append('=')
                        .append(AioProviderSupport.quoteShellValue(entry.getValue()));
            }
            innerParts.add(envBuilder.toString());
        }
        innerParts.add("{ " + command + "; } 2> >(sed 's/^/" + AioProviderSupport.STDERR_PREFIX + "/')");
        String shellCommand = "/bin/bash -lc " + AioProviderSupport.quoteShellValue(String.join(" && ", innerParts));
        if (timeoutSeconds != null && timeoutSeconds > 0) {
            shellCommand = "timeout " + timeoutSeconds + "s " + shellCommand;
        }
        return shellCommand;
    }

    private static void addSplitLines(List<Chunk> chunks, String content, String type) {
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

    private static String stripReasonPrefix(String message) {
        if (message == null) {
            return "";
        }
        int index = message.indexOf("reason: ");
        return index >= 0 ? message.substring(index + "reason: ".length()) : message;
    }

    private record Chunk(String text, String type) {
    }
}
