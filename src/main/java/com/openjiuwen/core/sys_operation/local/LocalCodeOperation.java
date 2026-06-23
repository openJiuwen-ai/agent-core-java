/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseCodeOperation;
import com.openjiuwen.core.sys_operation.OperationDef;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.OperationRegistry;
import com.openjiuwen.core.sys_operation.result.BaseResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeChunkData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeData;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Local code operation implementation.
 *
 * <p>Mirrors Python's {@code CodeOperation} in
 * {@code openjiuwen/core/sys_operation/local/code_operation.py}.</p>
 */
public class LocalCodeOperation extends BaseCodeOperation {

    public static final OperationDef OP_DEF = new OperationDef(
            LocalCodeOperation.class,
            "local code operation",
            "code",
            OperationMode.LOCAL
    );

    static {
        OperationRegistry.register(LocalCodeOperation.class);
    }

    private static final int WINDOWS_CMD_LIMIT = 8000;
    private static final int UNIX_CMD_LIMIT = 100000;
    private static final int DEFAULT_STREAM_CHUNK_SIZE = 1024;
    private static final String DEFAULT_ENCODING = "utf-8";
    private static final String PYTHON_EXECUTABLE_ENV = "PYTHON";

    public LocalCodeOperation(String name, OperationMode mode, String description, Object runConfig) {
        super(name, mode, description, runConfig);
    }

    @Override
    public CompletableFuture<ExecuteCodeResult> executeCode(String code, CodeLanguage language, int timeout,
                                                            Map<String, String> environment, String cwd,
                                                            Map<String, Object> options) {
        return executeCode(code, languageValue(language), timeout, environment, cwd, options);
    }

    public CompletableFuture<ExecuteCodeResult> executeCode(String code, String language, int timeout,
                                                            Map<String, String> environment, String cwd,
                                                            Map<String, Object> options) {
        return CompletableFuture.supplyAsync(() -> {
            String effectiveLanguage = normalizeLanguage(language);
            if (code == null || code.isBlank()) {
                return codeError("execute_code", "code can not be empty", ExecuteCodeResult.class, null);
            }
            if (languageConfig(effectiveLanguage) == null) {
                return codeError("execute_code", effectiveLanguage + " is not supported", ExecuteCodeResult.class,
                        ExecuteCodeData.builder()
                                .codeContent(code)
                                .language(effectiveLanguage)
                                .build());
            }

            CommandSpec commandSpec = null;
            try {
                commandSpec = buildSubprocessCommand(code, effectiveLanguage, options);
                if (commandSpec == null || commandSpec.command() == null) {
                    return codeError("execute_code", "subprocess cmd can not be none", ExecuteCodeResult.class,
                            ExecuteCodeData.builder()
                                    .codeContent(code)
                                    .language(effectiveLanguage)
                                    .build());
                }

                Process process = createProcess(commandSpec.command(), effectiveLanguage, environment, cwd);
                String encoding = stringOption(options, "encoding", DEFAULT_ENCODING);
                InvokeData invokeData = OperationUtils.createHandler(process, encoding, timeout).invoke().join();
                Exception invokeException = invokeData.getException();
                if (invokeException instanceof TimeoutException) {
                    return codeError("execute_code", "execution timeout after " + timeout + " seconds",
                            ExecuteCodeResult.class, ExecuteCodeData.builder()
                                    .codeContent(code)
                                    .language(effectiveLanguage)
                                    .exitCode(invokeData.getExitCode())
                                    .stdout(invokeData.getStdout())
                                    .stderr(invokeData.getStderr())
                                    .build());
                }

                return successResult(ExecuteCodeResult.class, "Code executed successfully", ExecuteCodeData.builder()
                        .codeContent(code)
                        .language(effectiveLanguage)
                        .exitCode(invokeData.getExitCode())
                        .stdout(invokeData.getStdout())
                        .stderr(invokeData.getStderr())
                        .build());
            } catch (IOException exception) {
                return codeError("execute_code", effectiveLanguage + " file not found error, please install "
                                + "and add it to your system environment variable PATH.",
                        ExecuteCodeResult.class, ExecuteCodeData.builder()
                                .codeContent(code)
                                .language(effectiveLanguage)
                                .build());
            } catch (Exception exception) {
                return codeError("execute_code", "unexpected error: " + rootMessage(exception),
                        ExecuteCodeResult.class, ExecuteCodeData.builder()
                                .codeContent(code)
                                .language(effectiveLanguage)
                                .build());
            } finally {
                deleteTempFile(commandSpec);
            }
        });
    }

    @Override
    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(String code, CodeLanguage language, int timeout,
                                                                     Map<String, String> environment, String cwd,
                                                                     Map<String, Object> options) {
        return executeCodeStream(code, languageValue(language), timeout, environment, cwd, options);
    }

    public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
                                                                     Map<String, String> environment, String cwd,
                                                                     Map<String, Object> options) {
        return asyncPublisher(publisher -> emitCodeStream(code, normalizeLanguage(language), timeout, environment, cwd,
                options, publisher));
    }

    private void emitCodeStream(String code, String effectiveLanguage, int timeout, Map<String, String> environment,
                                String cwd, Map<String, Object> options,
                                SubmissionPublisher<ExecuteCodeStreamResult> publisher) {
        int chunkIndex = 0;
        if (code == null || code.isBlank()) {
            publisher.submit(codeStreamError("code can not be empty", ExecuteCodeChunkData.builder()
                    .chunkIndex(chunkIndex)
                    .exitCode(-1)
                    .build()));
            return;
        }
        if (languageConfig(effectiveLanguage) == null) {
            publisher.submit(codeStreamError(effectiveLanguage + " is not supported", ExecuteCodeChunkData.builder()
                    .chunkIndex(chunkIndex)
                    .exitCode(-1)
                    .build()));
            return;
        }

        CommandSpec commandSpec = null;
        try {
            commandSpec = buildSubprocessCommand(code, effectiveLanguage, options);
            if (commandSpec == null || commandSpec.command() == null) {
                publisher.submit(codeStreamError("subprocess cmd can not be none", ExecuteCodeChunkData.builder()
                        .chunkIndex(chunkIndex)
                        .exitCode(-1)
                        .build()));
                return;
            }

            int chunkSize = intOption(options, "chunk_size", DEFAULT_STREAM_CHUNK_SIZE);
            String encoding = stringOption(options, "encoding", DEFAULT_ENCODING);
            Process process = createProcess(commandSpec.command(), effectiveLanguage, environment, cwd);
            BlockingQueue<StreamEvent> queue = OperationUtils.createHandler(process, chunkSize, encoding, timeout)
                    .stream();
            while (true) {
                StreamEvent event = queue.poll(Math.max(timeout, 1), TimeUnit.SECONDS);
                if (event == null) {
                    publisher.submit(codeStreamError("execution receive error: stream timeout", ExecuteCodeChunkData
                            .builder()
                            .chunkIndex(chunkIndex)
                            .exitCode(-1)
                            .build()));
                    return;
                }
                publisher.submit(toStreamResult(event, chunkIndex));
                chunkIndex += 1;
                if (event.getType() == StreamEventType.ERROR || event.getType() == StreamEventType.EXIT) {
                    return;
                }
            }
        } catch (IOException exception) {
            publisher.submit(codeStreamError(effectiveLanguage + " file not found error, please install "
                    + "and add it to your system environment variable PATH.", ExecuteCodeChunkData.builder()
                    .chunkIndex(chunkIndex)
                    .exitCode(-1)
                    .build()));
        } catch (Exception exception) {
            publisher.submit(codeStreamError("unexpected error: " + rootMessage(exception), ExecuteCodeChunkData
                    .builder()
                    .chunkIndex(chunkIndex)
                    .exitCode(-1)
                    .build()));
        } finally {
            deleteTempFile(commandSpec);
        }
    }

    private CommandSpec buildSubprocessCommand(String code, String effectiveLanguage, Map<String, Object> options) {
        boolean forceFile = booleanOption(options, "force_file", false);
        LanguageConfig config = languageConfig(effectiveLanguage);
        if (config == null) {
            return null;
        }
        if (!forceFile && code.length() <= getDefaultCommandLimit() && !requiresFileTransport(code)) {
            return new CommandSpec(config.cliCommand(code), null);
        }
        String tempPath = OperationUtils.createTmpFile(code, config.fileSuffix()).join();
        if (tempPath == null) {
            return null;
        }
        return new CommandSpec(config.fileCommand(tempPath), tempPath);
    }

    private Process createProcess(List<String> command, String language, Map<String, String> environment, String cwd)
            throws IOException {
        Map<String, String> mergedEnvironment = OperationUtils.prepareEnvironment(environment);
        if (CodeLanguage.JAVASCRIPT.value().equals(language)) {
            mergedEnvironment.put("NODE_DISABLE_COLORS", "1");
        } else if (CodeLanguage.PYTHON.value().equals(language)) {
            mergedEnvironment.put("PYTHONIOENCODING", "utf-8");
            mergedEnvironment.put("PYTHONUTF8", "1");
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().clear();
        builder.environment().putAll(mergedEnvironment);
        if (cwd != null) {
            builder.directory(new File(cwd));
        }
        return builder.start();
    }

    private ExecuteCodeStreamResult toStreamResult(StreamEvent event, int chunkIndex) {
        if (event.getType() == StreamEventType.ERROR) {
            return codeStreamError("execution receive error: " + event.getData(), ExecuteCodeChunkData.builder()
                    .chunkIndex(chunkIndex)
                    .exitCode(-1)
                    .build());
        }
        if (event.getType() == StreamEventType.EXIT) {
            Integer exitCode = event.getData() instanceof Number number ? number.intValue() : -1;
            return successResult(ExecuteCodeStreamResult.class, "Code executed successfully",
                    ExecuteCodeChunkData.builder()
                            .chunkIndex(chunkIndex)
                            .exitCode(exitCode)
                            .build());
        }
        String type = event.getType().getValue();
        return successResult(ExecuteCodeStreamResult.class, "Get " + type + " stream successfully",
                ExecuteCodeChunkData.builder()
                        .text(String.valueOf(event.getData()))
                        .type(type)
                        .chunkIndex(chunkIndex)
                        .build());
    }

    private ExecuteCodeStreamResult codeStreamError(String message, ExecuteCodeChunkData data) {
        return codeError("execute_code_stream", message, ExecuteCodeStreamResult.class, data);
    }

    private LanguageConfig languageConfig(String language) {
        if (CodeLanguage.PYTHON.value().equals(language)) {
            String executable = System.getenv().getOrDefault(PYTHON_EXECUTABLE_ENV, "python");
            return new LanguageConfig(
                    ".py",
                    code -> List.of(executable, "-u", "-c", code),
                    path -> List.of(executable, "-u", path));
        }
        if (CodeLanguage.JAVASCRIPT.value().equals(language)) {
            return new LanguageConfig(
                    ".js",
                    code -> List.of("node", "-e", code),
                    path -> List.of("node", path));
        }
        return null;
    }

    private int getDefaultCommandLimit() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                ? WINDOWS_CMD_LIMIT
                : UNIX_CMD_LIMIT;
    }

    private boolean requiresFileTransport(String code) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return false;
        }
        return code.indexOf('"') >= 0 || code.chars().anyMatch(character -> character > 127);
    }

    private boolean booleanOption(Map<String, Object> options, String key, boolean defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intOption(Map<String, Object> options, String key, int defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(options.get(key)));
    }

    private String stringOption(Map<String, Object> options, String key, String defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        return String.valueOf(options.get(key));
    }

    private String languageValue(CodeLanguage language) {
        return (language == null ? CodeLanguage.PYTHON : language).value();
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return CodeLanguage.PYTHON.value();
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private void deleteTempFile(CommandSpec commandSpec) {
        if (commandSpec != null && commandSpec.tempPath() != null) {
            OperationUtils.deleteTmpFile(commandSpec.tempPath()).join();
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static <T, R extends BaseResult<T>> R successResult(Class<R> resultClass, String message, T data) {
        try {
            R result = resultClass.getDeclaredConstructor().newInstance();
            result.setCode(StatusCode.SUCCESS.getCode());
            result.setMessage(message);
            result.setData(data);
            return result;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot create result " + resultClass.getName(), exception);
        }
    }

    private static <T, R> R codeError(String execution, String message, Class<R> resultClass, T data) {
        return BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR,
                Map.of("execution", execution, "error_msg", message == null ? "" : message),
                resultClass,
                data);
    }

    private static <T> Flow.Publisher<T> asyncPublisher(Consumer<SubmissionPublisher<T>> emitter) {
        return subscriber -> {
            SubmissionPublisher<T> publisher = new SubmissionPublisher<>();
            publisher.subscribe(subscriber);
            CompletableFuture.runAsync(() -> {
                try {
                    emitter.accept(publisher);
                    publisher.close();
                } catch (RuntimeException exception) {
                    publisher.closeExceptionally(exception);
                }
            });
        };
    }

    private record CommandSpec(List<String> command, String tempPath) {
    }

    private record LanguageConfig(
            String fileSuffix,
            java.util.function.Function<String, List<String>> cliCommandFactory,
            java.util.function.Function<String, List<String>> fileCommandFactory) {

        private List<String> cliCommand(String code) {
            return new ArrayList<>(cliCommandFactory.apply(code));
        }

        private List<String> fileCommand(String path) {
            return new ArrayList<>(fileCommandFactory.apply(path));
        }
    }
}
