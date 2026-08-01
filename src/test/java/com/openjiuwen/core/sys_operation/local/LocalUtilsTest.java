/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's local-utils coverage around subprocess helpers in
 * {@code openjiuwen/core/sys_operation/local/utils.py}.
 */
class LocalUtilsTest {

    @Test
    void createAndDeleteTmpFilePreserveUtf8Content() throws Exception {
        String pathText = OperationUtils.createTmpFile("hello", ".txt").get(5, TimeUnit.SECONDS);
        Path path = Path.of(pathText);

        assertThat(Files.exists(path)).isTrue();
        assertThat(Files.readString(path)).isEqualTo("hello");
        assertThat(OperationUtils.deleteTmpFile(pathText).get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(Files.exists(path)).isFalse();
    }

    @Test
    void prepareEnvironmentMergesCustomValues() {
        Map<String, String> environment = OperationUtils.prepareEnvironment(Map.of("OPENJIUWEN_TEST_ENV", "1"));

        assertThat(environment).containsEntry("OPENJIUWEN_TEST_ENV", "1");
        assertThat(environment).containsKey("PATH");
    }

    @Test
    void invokeCapturesStdoutStderrAndExitCode() throws Exception {
        Process process = startScript("Write-Output 'hello'; [Console]::Error.WriteLine('warn'); exit 7");
        InvokeData result = OperationUtils.createHandler(process, "utf-8", 5).invoke().get(10, TimeUnit.SECONDS);

        assertThat(result.getStdout()).contains("hello");
        assertThat(result.getStderr()).contains("warn");
        assertThat(result.getExitCode()).isEqualTo(7);
        assertThat(result.getException()).isNull();
    }

    @Disabled("remote env do not support node")
    @Test
    void invokeTimeoutReturnsPartialBuffersAndTimeoutException() throws Exception {
        Process process = startScript("Write-Output 'before'; Start-Sleep -Milliseconds 1500; Write-Output 'after'");
        InvokeData result = OperationUtils.createHandler(process, "utf-8", 1).invoke().get(10, TimeUnit.SECONDS);

        assertThat(result.getStdout()).contains("before");
        assertThat(result.getStdout()).doesNotContain("after");
        assertThat(result.getException()).isInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    @Test
    void streamEmitsStdoutStderrAndExitEvents() throws Exception {
        Process process = startScript("Write-Output 'hello'; [Console]::Error.WriteLine('warn'); exit 3");
        BlockingQueue<StreamEvent> queue = OperationUtils.createHandler(process, "utf-8", 5).stream();

        List<StreamEvent> events = collectUntilTerminal(queue);
        List<StreamEventType> eventTypes = events.stream().map(StreamEvent::getType).collect(Collectors.toList());
        String stdout = joinPayload(events, StreamEventType.STDOUT);
        String stderr = joinPayload(events, StreamEventType.STDERR);

        assertThat(eventTypes).contains(StreamEventType.STDOUT, StreamEventType.STDERR, StreamEventType.EXIT);
        assertThat(stdout).contains("hello");
        assertThat(stderr).contains("warn");
        assertThat(events.get(events.size() - 1).getType()).isEqualTo(StreamEventType.EXIT);
        assertThat(events.get(events.size() - 1).getData()).isEqualTo(3);
    }

    @Test
    void backgroundReportsEarlyFailure() throws Exception {
        Process process = startScript("[Console]::Error.WriteLine('boom'); exit 9");
        AsyncProcessHandler.BackgroundLaunchResult result =
                OperationUtils.createHandler(process).background(0.5d).get(5, TimeUnit.SECONDS);

        assertThat(result.pid()).isPositive();
        assertThat(result.error()).contains("code 9");
    }

    private static Process startScript(String powerShellScript) throws Exception {
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            return new ProcessBuilder("powershell", "-NoProfile", "-Command", powerShellScript).start();
        }
        return new ProcessBuilder("bash", "-lc", toBashScript(powerShellScript)).start();
    }

    private static String toBashScript(String powerShellScript) {
        if (powerShellScript.contains("Start-Sleep")) {
            return "printf 'before\\n'; sleep 1.5; printf 'after\\n'";
        }
        if (powerShellScript.contains("warn")) {
            return "printf 'hello\\n'; printf 'warn\\n' 1>&2; exit "
                    + (powerShellScript.contains("exit 7") ? "7" : "3");
        }
        return "printf 'boom\\n' 1>&2; exit 9";
    }

    private static List<StreamEvent> collectUntilTerminal(BlockingQueue<StreamEvent> queue) throws Exception {
        List<StreamEvent> events = new ArrayList<>();
        for (int index = 0; index < 32; index++) {
            StreamEvent event = queue.poll(5, TimeUnit.SECONDS);
            assertThat(event).as("stream event " + index).isNotNull();
            events.add(event);
            if (event.getType() == StreamEventType.EXIT || event.getType() == StreamEventType.ERROR) {
                break;
            }
        }
        return events;
    }

    private static String joinPayload(List<StreamEvent> events, StreamEventType type) {
        return events.stream()
                .filter(event -> event.getType() == type)
                .map(StreamEvent::getData)
                .map(String::valueOf)
                .collect(Collectors.joining());
    }
}
