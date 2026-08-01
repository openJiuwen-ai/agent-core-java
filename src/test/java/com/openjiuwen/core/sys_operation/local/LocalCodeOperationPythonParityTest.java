/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.sys_operation.BaseCodeOperation;
import com.openjiuwen.core.sys_operation.OperationMode;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeResult;
import com.openjiuwen.core.sys_operation.result.ExecuteCodeStreamResult;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.sys_operation.local.test_code_operation} in
 * {@code tests/unit_tests/core/sys_operation/local/test_code_operation.py}.</p>
 */
class LocalCodeOperationPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_execute_python_code_success",
            "test_execute_python_code_with_cwd",
            "test_execute_javascript_code_success",
            "test_execute_code_with_environment_vars",
            "test_execute_code_with_custom_timeout",
            "test_execute_empty_code",
            "test_execute_unsupported_language",
            "test_execute_python_code_with_syntax_error",
            "test_execute_code_timeout",
            "test_execute_long_running_valid_code",
            "test_execute_code_with_large_output",
            "test_execute_code_with_special_characters",
            "test_sys_op_fixture_reusability",
            "test_execute_code_force_file_true_via_options",
            "test_execute_code_force_file_true_javascript",
            "test_execute_code_force_file_true_with_error",
            "test_execute_code_force_file_true_timeout",
            "test_execute_code_stream_empty_code",
            "test_execute_code_stream_unsupported_language",
            "test_execute_code_stream_python_normal",
            "test_execute_code_stream_python_stderr",
            "test_execute_code_stream_javascript_normal",
            "test_execute_code_stream_custom_options",
            "test_execute_code_stream_custom_environment",
            "test_execute_code_stream_timeout",
            "test_execute_code_stream_default_params"
    );

    @TempDir
    private Path tempDir;

    @Disabled("remote env do not support node")
    @TestFactory
    Collection<DynamicTest> pythonLocalCodeOperationCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) throws Exception {
        switch (name) {
            case "test_execute_python_code_success" -> executePythonCodeSuccess();
            case "test_execute_python_code_with_cwd" -> executePythonCodeWithCwd();
            case "test_execute_javascript_code_success" -> executeJavascriptCodeSuccess();
            case "test_execute_code_with_environment_vars" -> executeCodeWithEnvironmentVars();
            case "test_execute_code_with_custom_timeout" -> executeCodeWithCustomTimeout();
            case "test_execute_empty_code" -> executeEmptyCode();
            case "test_execute_unsupported_language" -> executeUnsupportedLanguage();
            case "test_execute_python_code_with_syntax_error" -> executePythonCodeWithSyntaxError();
            case "test_execute_code_timeout" -> executeCodeTimeout();
            case "test_execute_long_running_valid_code" -> executeLongRunningValidCode();
            case "test_execute_code_with_large_output" -> executeCodeWithLargeOutput();
            case "test_execute_code_with_special_characters" -> executeCodeWithSpecialCharacters();
            case "test_sys_op_fixture_reusability" -> sysOpFixtureReusability();
            case "test_execute_code_force_file_true_via_options" -> executeCodeForceFileTrueViaOptions();
            case "test_execute_code_force_file_true_javascript" -> executeCodeForceFileTrueJavascript();
            case "test_execute_code_force_file_true_with_error" -> executeCodeForceFileTrueWithError();
            case "test_execute_code_force_file_true_timeout" -> executeCodeForceFileTrueTimeout();
            case "test_execute_code_stream_empty_code" -> executeCodeStreamEmptyCode();
            case "test_execute_code_stream_unsupported_language" -> executeCodeStreamUnsupportedLanguage();
            case "test_execute_code_stream_python_normal" -> executeCodeStreamPythonNormal();
            case "test_execute_code_stream_python_stderr" -> executeCodeStreamPythonStderr();
            case "test_execute_code_stream_javascript_normal" -> executeCodeStreamJavascriptNormal();
            case "test_execute_code_stream_custom_options" -> executeCodeStreamCustomOptions();
            case "test_execute_code_stream_custom_environment" -> executeCodeStreamCustomEnvironment();
            case "test_execute_code_stream_timeout" -> executeCodeStreamTimeout();
            case "test_execute_code_stream_default_params" -> executeCodeStreamDefaultParams();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void executePythonCodeSuccess() throws Exception {
        String code = "print('Hello, Python!'); x = 1 + 2; print(x)";

        ExecuteCodeResult result = execute(code);

        assertSuccess(result);
        assertThat(result.getData().getCodeContent()).isEqualTo(code);
        assertThat(result.getData().getLanguage()).isEqualTo("python");
        assertThat(result.getData().getExitCode()).isZero();
        assertThat(result.getData().getStdout().lines()).containsExactly("Hello, Python!", "3");
        assertThat(result.getData().getStderr()).isEmpty();
    }

    private void executePythonCodeWithCwd() throws Exception {
        Path cwd = Files.createDirectory(tempDir.resolve("code-cwd"));
        String code = "import os; print(os.getcwd())";

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, cwd.toString(), null);

        assertSuccess(result);
        assertThat(Path.of(result.getData().getStdout().strip()).toRealPath()).isEqualTo(cwd.toRealPath());
    }

    private void executeJavascriptCodeSuccess() throws Exception {
        assertNodeAvailable();
        String code = "console.log('Hello, JavaScript!'); const x = 3 * 4; console.log(x)";

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.JAVASCRIPT, 10, null, null, null);

        assertSuccess(result);
        assertThat(result.getData().getLanguage()).isEqualTo("javascript");
        assertThat(result.getData().getExitCode()).isZero();
        assertThat(result.getData().getStdout().lines()).containsExactly("Hello, JavaScript!", "12");
        assertThat(result.getData().getStderr()).isEmpty();
    }

    private void executeCodeWithEnvironmentVars() throws Exception {
        String code = """
                import os
                print(os.getenv('TEST_ENV'))
                print(os.getenv('COUNT'))
                """;

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 10,
                Map.of("TEST_ENV", "pytest_test", "COUNT", "5"), null, null);

        assertSuccess(result);
        assertThat(result.getData().getStdout().lines()).containsExactly("pytest_test", "5");
    }

    private void executeCodeWithCustomTimeout() throws Exception {
        ExecuteCodeResult result = execute("import time; time.sleep(1); print('Timeout test pass')",
                BaseCodeOperation.CodeLanguage.PYTHON, 2, null, null, null);

        assertSuccess(result);
        assertThat(result.getData().getStdout()).contains("pass");
    }

    private void executeEmptyCode() throws Exception {
        for (String code : List.of("", "   ", "\n", "\t")) {
            ExecuteCodeResult result = execute(code);

            assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
            assertThat(result.getMessage()).contains("code can not be empty");
            assertThat(result.getData() == null
                    || code.equals(result.getData().getCodeContent())
                    && "python".equals(result.getData().getLanguage())).isTrue();
        }
    }

    private void executeUnsupportedLanguage() throws Exception {
        String code = "print('test')";

        for (String language : List.of("java", "c++", "ruby", "go")) {
            ExecuteCodeResult result = operation().executeCode(code, language, 10, null, null, null)
                    .get(20, TimeUnit.SECONDS);

            assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
            assertThat(result.getMessage()).contains(language + " is not supported");
            assertThat(result.getData().getCodeContent()).isEqualTo(code);
            assertThat(result.getData().getLanguage()).isEqualTo(language);
        }
    }

    private void executePythonCodeWithSyntaxError() throws Exception {
        String code = "print('missing quote";

        ExecuteCodeResult result = execute(code);

        assertSuccess(result);
        assertThat(result.getData().getLanguage()).isEqualTo("python");
        assertThat(result.getData().getExitCode()).isNotZero();
        assertThat(result.getData().getStderr()).contains("SyntaxError");
    }

    private void executeCodeTimeout() throws Exception {
        ExecuteCodeResult result = execute("import time; time.sleep(3)",
                BaseCodeOperation.CodeLanguage.PYTHON, 1, null, null, null);

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("execution timeout after 1 seconds");
        assertThat(result.getData().getExitCode()).isNotZero();
    }

    private void executeLongRunningValidCode() throws Exception {
        ExecuteCodeResult result = execute("import time; time.sleep(2); print('Long run success')",
                BaseCodeOperation.CodeLanguage.PYTHON, 3, null, null, null);

        assertSuccess(result);
        assertThat(result.getData().getStdout().toLowerCase()).contains("success");
    }

    private void executeCodeWithLargeOutput() throws Exception {
        ExecuteCodeResult result = execute("print('\\n'.join([f'Line {i}' for i in range(1000)]))");

        assertSuccess(result);
        assertThat(result.getData().getStdout().lines()).hasSize(1000);
        assertThat(result.getData().getStderr()).isEmpty();
    }

    private void executeCodeWithSpecialCharacters() throws Exception {
        String code = """
                print("Chinese test: 中文测试")
                print("Special symbols: !@#$%^&*()_+-=[]{}|;:,.<>?")
                """;

        ExecuteCodeResult result = execute(code);

        assertSuccess(result);
        assertThat(result.getData().getStdout()).contains("中文测试", "!@#$%^&*()");
    }

    private void sysOpFixtureReusability() throws Exception {
        ExecuteCodeResult result1 = execute("print(1)");
        ExecuteCodeResult result2 = execute("print(2)");

        assertSuccess(result1);
        assertSuccess(result2);
        assertThat(result2.getData().getStdout()).contains("2");
    }

    private void executeCodeForceFileTrueViaOptions() throws Exception {
        String code = """
                print(f"Python Exec Mode: Temp File")
                a, b = 50, 60
                print(f"50 + 60 = {a + b}")
                """;

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null,
                Map.of("force_file", true, "encoding", "utf-8"));

        assertSuccess(result);
        assertThat(result.getData().getExitCode()).isZero();
        assertThat(result.getData().getStdout()).contains("50 + 60 = 110");
        assertThat(result.getData().getStderr().strip()).isEmpty();
    }

    private void executeCodeForceFileTrueJavascript() throws Exception {
        assertNodeAvailable();
        String code = """
                console.log("JS Exec Mode: Temp File");
                const num1 = 15, num2 = 25;
                console.log(`15 * 25 = ${num1 * num2}`);
                """;

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.JAVASCRIPT, 10, null, null,
                Map.of("force_file", true, "encoding", "utf-8"));

        assertSuccess(result);
        assertThat(result.getData().getExitCode()).isZero();
        assertThat(result.getData().getStdout()).contains("JS Exec Mode: Temp File", "15 * 25 = 375");
        assertThat(result.getData().getStderr().strip()).isEmpty();
    }

    private void executeCodeForceFileTrueWithError() throws Exception {
        String code = "print(undefined_variable_999)  # undefined variable";

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null,
                Map.of("force_file", true));

        assertSuccess(result);
        assertThat(result.getData().getExitCode()).isNotZero();
        assertThat(result.getData().getStderr()).contains("undefined_variable_999");
    }

    private void executeCodeForceFileTrueTimeout() throws Exception {
        String code = """
                import time
                time.sleep(3)
                print("This line should not be printed")
                """;

        ExecuteCodeResult result = execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 1, null, null,
                Map.of("force_file", true));

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("timeout after 1 seconds");
        assertThat(result.getData().getExitCode()).isNotZero();
    }

    private void executeCodeStreamEmptyCode() throws Exception {
        List<ExecuteCodeStreamResult> empty = collect(operation().executeCodeStream(
                "", BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null));
        assertThat(empty).hasSize(1);
        assertThat(empty.get(0).getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(empty.get(0).getMessage()).contains("code can not be empty");
        assertThat(empty.get(0).getData().getExitCode()).isNotZero();

        List<ExecuteCodeStreamResult> blank = collect(operation().executeCodeStream(
                "   \n\t", BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null));
        assertThat(blank).hasSize(1);
        assertThat(blank.get(0).getMessage()).contains("code can not be empty");
    }

    private void executeCodeStreamUnsupportedLanguage() throws Exception {
        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                "print(1)", "java", 10, null, null, null));

        assertThat(results).hasSize(1);
        ExecuteCodeStreamResult result = results.get(0);
        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_CODE_EXECUTION_ERROR.getCode());
        assertThat(result.getMessage()).contains("java is not supported");
        assertThat(result.getData().getExitCode()).isNotZero();
    }

    private void executeCodeStreamPythonNormal() throws Exception {
        String code = """
                print("hello python")
                print("stream test for python")
                """;

        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null));

        assertThat(results.size()).isGreaterThanOrEqualTo(2);
        assertThat(combinedText(results, StreamEventType.STDOUT)).contains("hello python", "stream test for python");
        assertThat(results.get(results.size() - 1).getMessage()).isEqualTo("Code executed successfully");
        assertThat(results.get(results.size() - 1).getData().getExitCode()).isZero();
    }

    private void executeCodeStreamPythonStderr() throws Exception {
        String code = "print(undefined_variable)  # Undefined variable will throw NameError";

        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null));

        assertThat(results).isNotEmpty();
        assertThat(combinedText(results, StreamEventType.STDERR)).contains("NameError");
        assertThat(results.get(results.size() - 1).getMessage()).isEqualTo("Code executed successfully");
        assertThat(results.get(results.size() - 1).getData().getExitCode()).isNotZero();
    }

    private void executeCodeStreamJavascriptNormal() throws Exception {
        assertNodeAvailable();
        String code = """
                console.log("hello javascript");
                console.log("stream test for js");
                """;

        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                code, BaseCodeOperation.CodeLanguage.JAVASCRIPT, 10, null, null, null));

        assertThat(results.size()).isGreaterThanOrEqualTo(2);
        assertThat(combinedText(results, StreamEventType.STDOUT)).contains("hello javascript", "stream test for js");
        assertThat(results.get(results.size() - 1).getMessage()).isEqualTo("Code executed successfully");
        assertThat(results.get(results.size() - 1).getData().getExitCode()).isZero();
    }

    private void executeCodeStreamCustomOptions() throws Exception {
        String code = "print('a'*2048)  # Output 2048 characters for chunk test";

        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null,
                Map.of("chunk_size", 512, "encoding", "utf-8")));

        assertThat(results.size()).isGreaterThanOrEqualTo(5);
        long totalACount = results.stream()
                .filter(result -> result.getData() != null && result.getData().getText() != null)
                .map(ExecuteCodeStreamResult::getData)
                .mapToLong(data -> data.getText().chars().filter(ch -> ch == 'a').count())
                .sum();
        assertThat(totalACount).isEqualTo(2048L);
    }

    private void executeCodeStreamCustomEnvironment() throws Exception {
        String code = """
                import os
                print(os.getenv("TEST_ENV_KEY"))
                print(os.getenv("TEST_ENV_VALUE"))
                """;

        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                code, BaseCodeOperation.CodeLanguage.PYTHON, 10,
                Map.of("TEST_ENV_KEY", "python_test", "TEST_ENV_VALUE", "123456"), null, null));

        assertThat(stdoutText(results)).contains("python_test", "123456");
    }

    private void executeCodeStreamTimeout() throws Exception {
        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                "while True: pass  # Infinite loop for timeout test",
                BaseCodeOperation.CodeLanguage.PYTHON, 2, null, null, null));

        assertThat(results).isNotEmpty();
        assertThat(results).anySatisfy(result -> assertThat(result.getMessage().toLowerCase())
                .containsAnyOf("timeout", "execution receive error"));
    }

    private void executeCodeStreamDefaultParams() throws Exception {
        List<ExecuteCodeStreamResult> results = collect(operation().executeCodeStream(
                "print('default parameter test success')",
                BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null));

        assertThat(results.size()).isGreaterThanOrEqualTo(2);
        assertThat(stdoutText(results)).contains("default parameter test success");
        assertThat(results.get(results.size() - 1).getMessage()).isEqualTo("Code executed successfully");
        assertThat(results.get(results.size() - 1).getData().getExitCode()).isZero();
    }

    private ExecuteCodeResult execute(String code) throws Exception {
        return execute(code, BaseCodeOperation.CodeLanguage.PYTHON, 10, null, null, null);
    }

    private ExecuteCodeResult execute(String code,
                                      BaseCodeOperation.CodeLanguage language,
                                      int timeout,
                                      Map<String, String> environment,
                                      String cwd,
                                      Map<String, Object> options) throws Exception {
        return operation().executeCode(code, language, timeout, environment, cwd, options).get(40, TimeUnit.SECONDS);
    }

    private LocalCodeOperation operation() {
        return new LocalCodeOperation("code", OperationMode.LOCAL, "local code operation", null);
    }

    private static void assertSuccess(ExecuteCodeResult result) {
        assertThat(result.getCode()).isEqualTo(StatusCode.SUCCESS.getCode());
        assertThat(result.getMessage()).isEqualTo("Code executed successfully");
        assertThat(result.getData()).isNotNull();
    }

    private static String combinedText(List<ExecuteCodeStreamResult> results, StreamEventType type) {
        StringBuilder builder = new StringBuilder();
        for (ExecuteCodeStreamResult result : results) {
            if (result.getData() != null && type.getValue().equals(result.getData().getType())) {
                builder.append(result.getData().getText());
            }
        }
        return builder.toString();
    }

    private static String stdoutText(List<ExecuteCodeStreamResult> results) {
        StringBuilder builder = new StringBuilder();
        for (ExecuteCodeStreamResult result : results) {
            if (result.getData() != null && result.getData().getText() != null) {
                builder.append(result.getData().getText());
            }
        }
        return builder.toString();
    }

    private static <T> List<T> collect(Flow.Publisher<T> publisher) throws Exception {
        CapturingSubscriber<T> subscriber = new CapturingSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.await();
    }

    private static void assertNodeAvailable() throws Exception {
        Process process = new ProcessBuilder("node", "--version").start();
        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        assertThat(exited && process.exitValue() == 0)
                .as("Node.js must be available because the Python baseline passed JavaScript cases")
                .isTrue();
    }

    private static final class CapturingSubscriber<T> implements Flow.Subscriber<T> {

        private final List<T> items = new ArrayList<>();

        private final CompletableFuture<List<T>> done = new CompletableFuture<>();

        private Flow.Subscription subscription;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(T item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            done.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            done.complete(List.copyOf(items));
        }

        private List<T> await() throws Exception {
            try {
                return done.get(60, TimeUnit.SECONDS);
            } finally {
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
