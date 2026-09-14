/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code BaseShellProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/shell_protocal.py}.
 */
class BaseShellProtocalTest {

    @Test
    void executeCmdUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();
        CompletableFuture<ExecuteCmdResult> expected = CompletableFuture.completedFuture(new ExecuteCmdResult());
        protocal.executeResult = expected;

        CompletableFuture<ExecuteCmdResult> actual = protocal.executeCmd("pwd");

        assertSame(expected, actual);
        assertEquals("pwd", protocal.lastCommand);
        assertNull(protocal.lastCwd);
        assertEquals(BaseShellProtocal.DEFAULT_TIMEOUT_SECONDS, protocal.lastTimeoutSeconds);
        assertNull(protocal.lastEnvironment);
        assertNull(protocal.lastOptions);
    }

    @Test
    void executeCmdStreamUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();
        Flow.Publisher<ExecuteCmdStreamResult> expected = new SubmissionPublisher<>();
        protocal.streamResult = expected;

        Flow.Publisher<ExecuteCmdStreamResult> actual = protocal.executeCmdStream("dir");

        assertSame(expected, actual);
        assertEquals("dir", protocal.lastCommand);
        assertNull(protocal.lastCwd);
        assertEquals(BaseShellProtocal.DEFAULT_TIMEOUT_SECONDS, protocal.lastTimeoutSeconds);
        assertNull(protocal.lastEnvironment);
        assertNull(protocal.lastOptions);
    }

    @Test
    void explicitArgumentsAreForwarded() {
        RecordingProtocal protocal = new RecordingProtocal();
        Map<String, String> environment = Map.of("LANG", "C");
        Map<String, Object> options = Map.of("shell", "powershell");

        protocal.executeCmd("echo ok", "/tmp", 45, environment, options);

        assertEquals("echo ok", protocal.lastCommand);
        assertEquals("/tmp", protocal.lastCwd);
        assertEquals(45, protocal.lastTimeoutSeconds);
        assertSame(environment, protocal.lastEnvironment);
        assertSame(options, protocal.lastOptions);
    }

    private static final class RecordingProtocal extends BaseShellProtocal {
        private String lastCommand;
        private String lastCwd;
        private Integer lastTimeoutSeconds;
        private Map<String, String> lastEnvironment;
        private Map<String, Object> lastOptions;
        private CompletableFuture<ExecuteCmdResult> executeResult =
                CompletableFuture.completedFuture(new ExecuteCmdResult());
        private Flow.Publisher<ExecuteCmdStreamResult> streamResult = new SubmissionPublisher<>();

        @Override
        public CompletableFuture<ExecuteCmdResult> executeCmd(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options) {
            record(command, cwd, timeoutSeconds, environment, options);
            return executeResult;
        }

        @Override
        public Flow.Publisher<ExecuteCmdStreamResult> executeCmdStream(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options) {
            record(command, cwd, timeoutSeconds, environment, options);
            return streamResult;
        }

        private void record(
                String command,
                String cwd,
                Integer timeoutSeconds,
                Map<String, String> environment,
                Map<String, Object> options) {
            this.lastCommand = command;
            this.lastCwd = cwd;
            this.lastTimeoutSeconds = timeoutSeconds;
            this.lastEnvironment = environment;
            this.lastOptions = options;
        }
    }
}
