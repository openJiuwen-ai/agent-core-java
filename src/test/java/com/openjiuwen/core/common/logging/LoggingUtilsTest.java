/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoggingUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void sessionIdDefaultsAndOverridesMatchPython() {
        LoggingUtils.setSessionId();
        assertThat(LoggingUtils.getSessionId()).isEqualTo("default_trace_id");

        LoggingUtils.setSessionId("trace-123");
        assertThat(LoggingUtils.getSessionId()).isEqualTo("trace-123");

        LoggingUtils.setSessionId(null);
        assertThat(LoggingUtils.getSessionId()).isEqualTo("default_trace_id");
    }

    @Test
    void memberIdDefaultsAndOverridesMatchPython() {
        LoggingUtils.setMemberId("worker-1");
        assertThat(LoggingUtils.getMemberId()).isEqualTo("worker-1");

        LoggingUtils.setMemberId(null);
        assertThat(LoggingUtils.getMemberId()).isEmpty();
    }

    @Test
    void logMaxBytesUsesPythonRangeRules() {
        assertThat(LoggingUtils.getLogMaxBytes("1024")).isEqualTo(1024);
        assertThat(LoggingUtils.getLogMaxBytes(0)).isEqualTo(100 * 1024 * 1024);
        assertThat(LoggingUtils.getLogMaxBytes(200L * 1024L * 1024L)).isEqualTo(100 * 1024 * 1024);
    }

    @Test
    void invalidMaxBytesRaisesMappedError() {
        assertThatThrownBy(() -> LoggingUtils.getLogMaxBytes("bad"))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.COMMON_LOG_CONFIG_INVALID);
    }

    @Test
    void normalizeAndValidateLogPathAcceptsPathAndStringInputs() {
        Path file = tempDir.resolve("logs").resolve("app.log");
        assertThat(LoggingUtils.normalizeAndValidateLogPath(file))
                .isEqualTo(file.toAbsolutePath().normalize().toString());
        assertThat(LoggingUtils.normalizeAndValidateLogPath(file.toString()))
                .isEqualTo(file.toAbsolutePath().normalize().toString());
    }

    @Test
    void normalizeAndValidateLogPathRejectsInvalidInputs() {
        assertThatThrownBy(() -> LoggingUtils.normalizeAndValidateLogPath(""))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.COMMON_LOG_PATH_INVALID);

        assertThatThrownBy(() -> LoggingUtils.normalizeAndValidateLogPath(new Object()))
                .isInstanceOf(BaseError.class)
                .extracting(error -> ((BaseError) error).getStatus())
                .isEqualTo(StatusCode.COMMON_LOG_PATH_INVALID);
    }
}
