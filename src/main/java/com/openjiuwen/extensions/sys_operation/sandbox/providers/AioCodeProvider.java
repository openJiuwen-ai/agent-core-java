/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.sys_operation.sandbox.providers;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.config.SandboxGatewayConfig;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult;
import com.openjiuwen.core.sys_operation.sandbox.gateway.SandboxEndpoint;
import com.openjiuwen.core.sys_operation.sandbox.providers.BaseCodeProvider;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * Mirrors Python's {@code AIOCodeProvider} in
 * {@code openjiuwen/extensions/sys_operation/sandbox/providers/aio.py}.
 */
public class AioCodeProvider extends BaseCodeProvider {

    private final SandboxEndpoint endpoint;
    private final SandboxGatewayConfig config;

    public AioCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
        super(endpoint, config);
        this.endpoint = endpoint;
        this.config = config;
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
                return AioProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        "code can not be empty",
                        ExecuteCodeResult.class,
                        seedData);
            }
            if (!"python".equals(language) && !"javascript".equals(language)) {
                return AioProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        language + " is not supported",
                        ExecuteCodeResult.class,
                        seedData);
            }

            boolean forceFile = options != null && Boolean.TRUE.equals(options.get("force_file"));
            String command = buildCodeCommand(code, language, forceFile);
            if (command == null) {
                return AioProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        "subprocess cmd can not be none",
                        ExecuteCodeResult.class,
                        seedData);
            }

            AioShellProvider shellProvider = new AioShellProvider(endpoint, config);
            ExecuteCmdResult shellResult = shellProvider.executeCmd(command, ".", timeoutSeconds, environment, null).join();
            ExecuteCodeData resultData = ExecuteCodeData.builder()
                    .codeContent(code)
                    .language(language)
                    .stdout(shellResult.getData() == null ? "" : Optional.ofNullable(shellResult.getData().getStdout()).orElse(""))
                    .stderr(shellResult.getData() == null ? "" : Optional.ofNullable(shellResult.getData().getStderr()).orElse(""))
                    .exitCode(shellResult.getData() == null ? -1 : shellResult.getData().getExitCode())
                    .build();
            if (shellResult.getCode() != StatusCode.SUCCESS.getCode()) {
                String message = shellResult.getMessage() == null ? "" : shellResult.getMessage();
                if (message.toLowerCase().contains("timeout")) {
                    return AioProviderSupport.buildCodeErrorResult(
                            "execute_code",
                            "execution timeout after " + timeoutSeconds + " seconds",
                            ExecuteCodeResult.class,
                            resultData);
                }
                return AioProviderSupport.buildCodeErrorResult(
                        "execute_code",
                        stripReasonPrefix(message),
                        ExecuteCodeResult.class,
                        resultData);
            }

            ExecuteCodeResult result = new ExecuteCodeResult();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage("Code executed successfully");
            result.setData(resultData);
            return result;
        });
    }

    @Override
    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
            String code,
            String language,
            int timeoutSeconds,
            Map<String, String> environment,
            String cwd,
            Map<String, Object> options) {
        SubmissionPublisher<ExecuteCodeStreamResult> publisher = new SubmissionPublisher<>();
        Thread.startVirtualThread(() -> {
            if (code == null || code.isBlank()) {
                publisher.submit(AioProviderSupport.buildCodeErrorResult(
                        "execute_code_stream",
                        "code can not be empty",
                        ExecuteCodeStreamResult.class,
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                publisher.close();
                return;
            }
            if (!"python".equals(language) && !"javascript".equals(language)) {
                publisher.submit(AioProviderSupport.buildCodeErrorResult(
                        "execute_code_stream",
                        language + " is not supported",
                        ExecuteCodeStreamResult.class,
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                publisher.close();
                return;
            }
            boolean forceFile = options != null && Boolean.TRUE.equals(options.get("force_file"));
            String command = buildCodeCommand(code, language, forceFile);
            if (command == null) {
                publisher.submit(AioProviderSupport.buildCodeErrorResult(
                        "execute_code_stream",
                        "subprocess cmd can not be none",
                        ExecuteCodeStreamResult.class,
                        ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                publisher.close();
                return;
            }

            AioShellProvider shellProvider = new AioShellProvider(endpoint, config);
            Flow.Publisher<ExecuteCmdStreamResult> shellPublisher = shellProvider.executeCmdStream(
                    command,
                    ".",
                    timeoutSeconds,
                    environment,
                    null);
            shellPublisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ExecuteCmdStreamResult item) {
                    if (item.getCode() != StatusCode.SUCCESS.getCode()) {
                        publisher.submit(AioProviderSupport.buildCodeErrorResult(
                                "execute_code_stream",
                                stripReasonPrefix(item.getMessage()),
                                ExecuteCodeStreamResult.class,
                                ExecuteCodeChunkData.builder()
                                        .chunkIndex(item.getData() == null ? 0 : item.getData().getChunkIndex())
                                        .exitCode(-1)
                                        .build()));
                        publisher.close();
                        return;
                    }
                    ExecuteCmdChunkData shellChunk = item.getData();
                    ExecuteCodeStreamResult result = new ExecuteCodeStreamResult();
                    result.setCode(StatusCode.SUCCESS.getCode());
                    if (shellChunk != null && shellChunk.getExitCode() != null) {
                        result.setMessage("Code executed successfully");
                        result.setData(ExecuteCodeChunkData.builder()
                                .chunkIndex(shellChunk.getChunkIndex())
                                .exitCode(shellChunk.getExitCode())
                                .build());
                    } else if (shellChunk != null) {
                        result.setMessage("Get " + shellChunk.getType() + " stream successfully");
                        result.setData(ExecuteCodeChunkData.builder()
                                .text(shellChunk.getText())
                                .type(shellChunk.getType())
                                .chunkIndex(shellChunk.getChunkIndex())
                                .build());
                    }
                    publisher.submit(result);
                }

                @Override
                public void onError(Throwable throwable) {
                    publisher.submit(AioProviderSupport.buildCodeErrorResult(
                            "execute_code_stream",
                            throwable.getMessage(),
                            ExecuteCodeStreamResult.class,
                            ExecuteCodeChunkData.builder().chunkIndex(0).exitCode(-1).build()));
                    publisher.close();
                }

                @Override
                public void onComplete() {
                    publisher.close();
                }
            });
        });
        return publisher;
    }

    static String buildCodeCommand(String code, String language, boolean forceFile) {
        String encoded = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        if ("python".equals(language)) {
            if (forceFile) {
                return "tmp=$(mktemp /tmp/ojw_code_XXXXXX.py) && "
                        + "printf %s " + AioProviderSupport.quoteShellValue(encoded)
                        + " | base64 -d > \"$tmp\" && "
                        + "python \"$tmp\"; status=$?; rm -f \"$tmp\"; exit $status";
            }
            return "python -c " + AioProviderSupport.quoteShellValue(
                    "import base64; exec(base64.b64decode('" + encoded + "').decode('utf-8'))");
        }
        if ("javascript".equals(language)) {
            if (forceFile) {
                return "tmp=$(mktemp /tmp/ojw_code_XXXXXX.js) && "
                        + "printf %s " + AioProviderSupport.quoteShellValue(encoded)
                        + " | base64 -d > \"$tmp\" && "
                        + "node \"$tmp\"; status=$?; rm -f \"$tmp\"; exit $status";
            }
            return "node -e " + AioProviderSupport.quoteShellValue(
                    "eval(Buffer.from('" + encoded + "','base64').toString('utf8'))");
        }
        return null;
    }

    private static String stripReasonPrefix(String message) {
        if (message == null) {
            return "";
        }
        int index = message.indexOf("reason: ");
        return index >= 0 ? message.substring(index + "reason: ".length()) : message;
    }
}
