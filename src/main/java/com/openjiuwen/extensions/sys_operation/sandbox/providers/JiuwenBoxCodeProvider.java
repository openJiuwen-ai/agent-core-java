/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.common.VirtualThreadSupport;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.providers.BaseCodeProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/**
 * Mirrors Python's {@code JiuwenBoxCodeProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/jiuwenbox.py}.
 */
public class JiuwenBoxCodeProvider extends BaseCodeProvider {

    private static final java.util.concurrent.Executor IO_EXECUTOR =
            VirtualThreadSupport.newThreadPerTaskExecutor("jiuwenbox-code-provider-io");

    private final JiuwenBoxProviderSupport.ProviderState state;

    public JiuwenBoxCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.state = new JiuwenBoxProviderSupport.ProviderState(endpoint, config);
    }

    @Override
    public CompletableFuture<ExecuteCodeResult> executeCode(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            ExecuteCodeData seedData = ExecuteCodeData.builder()
                    .codeContent(code)
                    .language(language)
                    .build();
            if (code == null || code.isBlank()) {
                return JiuwenBoxProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        "code can not be empty",
                        ExecuteCodeResult.class,
                        seedData);
            }
            if (!"python".equals(language) && !"javascript".equals(language)) {
                return JiuwenBoxProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        language + " is not supported",
                        ExecuteCodeResult.class,
                        seedData);
            }

            boolean forceFile = options != null && Boolean.TRUE.equals(options.get("force_file"));
            List<String> command = buildCodeCommand(code, language, forceFile);
            Map<String, String> mergedEnvironment = prepareEnvironment(language, environment);
            Map<String, Object> extraParams = state.launcherExtraParams(false);
            List<String> excludePatterns = JiuwenBoxProviderSupport.readExcludedCommands(extraParams);
            boolean fallbackOnFailure = extraParams.get("fallback_on_failure") instanceof Boolean bool && bool;

            String firstLine = code.lines().findFirst().orElse(code);
            if (JiuwenBoxProviderSupport.commandMatchesExclude(firstLine, excludePatterns)) {
                JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                        command,
                        "/tmp",
                        mergedEnvironment,
                        timeoutSeconds,
                        null);
                return wrapLocalResult(code, language, timeoutSeconds, localResult);
            }

            try {
                Map<String, Object> result = state.executeWithSandboxRetry(
                        sandboxId -> state.getClient().exec(
                                sandboxId,
                                command,
                                "/tmp",
                                timeoutSeconds,
                                mergedEnvironment,
                                null));
                ExecuteCodeData data = ExecuteCodeData.builder()
                        .codeContent(code)
                        .language(language)
                        .stdout(Optional.ofNullable((String) result.get("stdout")).orElse(""))
                        .stderr(Optional.ofNullable((String) result.get("stderr")).orElse(""))
                        .exitCode(JiuwenBoxProviderSupport.asInt(result.get("exit_code"), 0))
                        .build();
                if (data.getExitCode() == 124) {
                    return JiuwenBoxProviderSupport.buildCodeErrorResult(
                            "execute_code",
                            "execution timeout after " + timeoutSeconds + " seconds",
                            ExecuteCodeResult.class,
                            data);
                }
                if (data.getExitCode() != 0 && fallbackOnFailure) {
                    JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                            command,
                            "/tmp",
                            mergedEnvironment,
                            timeoutSeconds,
                            null);
                    return wrapLocalResult(code, language, timeoutSeconds, localResult);
                }
                ExecuteCodeResult codeResult = new ExecuteCodeResult();
                codeResult.setCode(StatusCode.SUCCESS.getCode());
                codeResult.setMessage("Code executed successfully");
                codeResult.setData(data);
                return codeResult;
            } catch (Exception exception) {
                if (fallbackOnFailure) {
                    JiuwenBoxProviderSupport.LocalProcessResult localResult = JiuwenBoxProviderSupport.runLocalSubprocess(
                            command,
                            "/tmp",
                            mergedEnvironment,
                            timeoutSeconds,
                            null);
                    return wrapLocalResult(code, language, timeoutSeconds, localResult);
                }
                return JiuwenBoxProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        exception.getMessage(),
                        ExecuteCodeResult.class,
                        seedData);
            }
        }, IO_EXECUTOR);
    }

    @Override
    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options) {
        return AioProviderSupport.asyncPublisher(publisher -> {
            ExecuteCodeResult result = executeCode(code, language, timeoutSeconds, environment, cwd, options).join();
            if (result.getCode() != StatusCode.SUCCESS.getCode()) {
                publisher.submit(JiuwenBoxProviderSupport.buildCodeErrorResult(
                        "execute_code_stream",
                        result.getMessage(),
                        ExecuteCodeStreamResult.class,
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                return;
            }
            List<Chunk> chunks = new ArrayList<>();
            addSplitLines(chunks, result.getData() == null ? "" : result.getData().getStdout(), "stdout");
            addSplitLines(chunks, result.getData() == null ? "" : result.getData().getStderr(), "stderr");
            for (int index = 0; index < chunks.size(); index++) {
                Chunk chunk = chunks.get(index);
                ExecuteCodeStreamResult streamResult = new ExecuteCodeStreamResult();
                streamResult.setCode(StatusCode.SUCCESS.getCode());
                streamResult.setMessage("Get " + chunk.type() + " stream successfully");
                streamResult.setData(ExecuteCodeChunkData.builder()
                        .text(chunk.text())
                        .type(chunk.type())
                        .chunkIndex(index)
                        .build());
                publisher.submit(streamResult);
            }
            ExecuteCodeStreamResult finalResult = new ExecuteCodeStreamResult();
            finalResult.setCode(StatusCode.SUCCESS.getCode());
            finalResult.setMessage("Code executed successfully");
            finalResult.setData(ExecuteCodeChunkData.builder()
                    .chunkIndex(chunks.size())
                    .exitCode(result.getData() == null ? 0 : result.getData().getExitCode())
                    .build());
            publisher.submit(finalResult);
        });
    }

    private ExecuteCodeResult wrapLocalResult(
            String code,
            String language,
            int timeoutSeconds,
            JiuwenBoxProviderSupport.LocalProcessResult localResult) {
        ExecuteCodeData data = ExecuteCodeData.builder()
                .codeContent(code)
                .language(language)
                .stdout(localResult.stdout())
                .stderr(localResult.stderr())
                .exitCode(localResult.exitCode())
                .build();
        if (localResult.exitCode() == 124) {
            return JiuwenBoxProviderSupport.buildCodeErrorResult(
                    "execute_code",
                    "execution timeout after " + timeoutSeconds + " seconds (local fallback)",
                    ExecuteCodeResult.class,
                    data);
        }
        ExecuteCodeResult result = new ExecuteCodeResult();
        result.setCode(StatusCode.SUCCESS.getCode());
        result.setMessage("Code executed successfully");
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

    private List<String> buildCodeCommand(String code, String language, boolean forceFile) {
        String encoded = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        if ("python".equals(language)) {
            if (forceFile) {
                return List.of(
                        "bash",
                        "-lc",
                        "tmp=$(mktemp /tmp/ojw_code_XXXXXX.py) && printf %s "
                                + AioProviderSupport.quoteShellValue(encoded)
                                + " | base64 -d > \"$tmp\" && python3 \"$tmp\"; status=$?; rm -f \"$tmp\"; exit $status");
            }
            return List.of("python3", "-c", code);
        }
        if ("javascript".equals(language)) {
            if (forceFile) {
                return List.of(
                        "bash",
                        "-lc",
                        "tmp=$(mktemp /tmp/ojw_code_XXXXXX.js) && printf %s "
                                + AioProviderSupport.quoteShellValue(encoded)
                                + " | base64 -d > \"$tmp\" && node \"$tmp\"; status=$?; rm -f \"$tmp\"; exit $status");
            }
            return List.of("node", "-e", code);
        }
        throw new IllegalArgumentException(language + " is not supported");
    }

    private Map<String, String> prepareEnvironment(String language, Map<String, String> environment) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (environment != null) {
            merged.putAll(environment);
        }
        if ("javascript".equals(language)) {
            merged.putIfAbsent("NODE_DISABLE_COLORS", "1");
        } else if ("python".equals(language)) {
            merged.putIfAbsent("PYTHONIOENCODING", "utf-8");
            merged.putIfAbsent("PYTHONUTF8", "1");
        }
        return merged;
    }

    private record Chunk(String text, String type) {
    }
}
