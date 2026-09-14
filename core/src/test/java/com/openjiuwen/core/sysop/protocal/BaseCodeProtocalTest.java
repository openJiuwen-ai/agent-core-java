/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.protocal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import org.junit.jupiter.api.Test;

/**
 * Mirrors Python's {@code BaseCodeProtocal} in
 * {@code openjiuwen/core/sys_operation/protocal/code_protocal.py}.
 */
class BaseCodeProtocalTest {

    @Test
    void executeCodeUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();

        CompletableFuture<ExecuteCodeResult> expected = CompletableFuture.completedFuture(new ExecuteCodeResult());
        protocal.executeResult = expected;

        CompletableFuture<ExecuteCodeResult> actual = protocal.executeCode("print('ok')");

        assertSame(expected, actual);
        assertEquals("print('ok')", protocal.lastCode);
        assertEquals(BaseCodeProtocal.DEFAULT_LANGUAGE, protocal.lastLanguage);
        assertEquals(BaseCodeProtocal.DEFAULT_TIMEOUT_SECONDS, protocal.lastTimeoutSeconds);
        assertNull(protocal.lastEnvironment);
        assertNull(protocal.lastCwd);
        assertNull(protocal.lastOptions);
    }

    @Test
    void executeCodeStreamUsesPythonDefaults() {
        RecordingProtocal protocal = new RecordingProtocal();
        Flow.Publisher<ExecuteCodeStreamResult> expected = new SubmissionPublisher<>();
        protocal.streamResult = expected;

        Flow.Publisher<ExecuteCodeStreamResult> actual = protocal.executeCodeStream("print('stream')");

        assertSame(expected, actual);
        assertEquals("print('stream')", protocal.lastCode);
        assertEquals(BaseCodeProtocal.DEFAULT_LANGUAGE, protocal.lastLanguage);
        assertEquals(BaseCodeProtocal.DEFAULT_TIMEOUT_SECONDS, protocal.lastTimeoutSeconds);
        assertNull(protocal.lastEnvironment);
        assertNull(protocal.lastCwd);
        assertNull(protocal.lastOptions);
    }

    @Test
    void explicitArgumentsAreForwarded() {
        RecordingProtocal protocal = new RecordingProtocal();
        Map<String, String> environment = Map.of("LANG", "C");
        Map<String, Object> options = Map.of("sandbox", "python");

        protocal.executeCode("print('x')", "javascript", 90, environment, "/tmp", options);

        assertEquals("print('x')", protocal.lastCode);
        assertEquals("javascript", protocal.lastLanguage);
        assertEquals(90, protocal.lastTimeoutSeconds);
        assertSame(environment, protocal.lastEnvironment);
        assertEquals("/tmp", protocal.lastCwd);
        assertSame(options, protocal.lastOptions);
    }

    private static final class RecordingProtocal extends BaseCodeProtocal {
        private String lastCode;
        private String lastLanguage;
        private int lastTimeoutSeconds;
        private Map<String, String> lastEnvironment;
        private String lastCwd;
        private Map<String, Object> lastOptions;
        private CompletableFuture<ExecuteCodeResult> executeResult =
                CompletableFuture.completedFuture(new ExecuteCodeResult());
        private Flow.Publisher<ExecuteCodeStreamResult> streamResult = new SubmissionPublisher<>();

        @Override
        public CompletableFuture<ExecuteCodeResult> executeCode(
                String code,
                String language,
                int timeoutSeconds,
                Map<String, String> environment,
                String cwd,
                Map<String, Object> options) {
            record(code, language, timeoutSeconds, environment, cwd, options);
            return executeResult;
        }

        @Override
        public Flow.Publisher<ExecuteCodeStreamResult> executeCodeStream(
                String code,
                String language,
                int timeoutSeconds,
                Map<String, String> environment,
                String cwd,
                Map<String, Object> options) {
            record(code, language, timeoutSeconds, environment, cwd, options);
            return streamResult;
        }

        private void record(
                String code,
                String language,
                int timeoutSeconds,
                Map<String, String> environment,
                String cwd,
                Map<String, Object> options) {
            this.lastCode = code;
            this.lastLanguage = language;
            this.lastTimeoutSeconds = timeoutSeconds;
            this.lastEnvironment = environment;
            this.lastCwd = cwd;
            this.lastOptions = options;
        }
    }
}
