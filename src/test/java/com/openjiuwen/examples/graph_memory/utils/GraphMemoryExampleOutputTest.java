/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.graph_memory.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.assertj.core.api.Assertions.assertThat;

class GraphMemoryExampleOutputTest {

    @Test
    void writeOutputLogsWarningOnPrintLogger() {
        CapturingHandler handler = new CapturingHandler();
        GraphMemoryExampleOutput.getPrintLogger().addHandler(handler);
        GraphMemoryExampleOutput.getPrintLogger().setUseParentHandlers(false);
        try {
            GraphMemoryExampleOutput.writeOutput("hello");
        } finally {
            GraphMemoryExampleOutput.getPrintLogger().removeHandler(handler);
            GraphMemoryExampleOutput.getPrintLogger().setUseParentHandlers(true);
        }

        assertThat(handler.records).hasSize(1);
        LogRecord record = handler.records.getFirst();
        assertThat(record.getLoggerName()).isEqualTo("print");
        assertThat(record.getLevel()).isEqualTo(Level.WARNING);
        assertThat(record.getMessage()).isEqualTo("hello");
    }

    @Test
    void writeOutputFormatsArgumentsLikeLoggingWarning() {
        CapturingHandler handler = new CapturingHandler();
        GraphMemoryExampleOutput.getPrintLogger().addHandler(handler);
        GraphMemoryExampleOutput.getPrintLogger().setUseParentHandlers(false);
        try {
            GraphMemoryExampleOutput.writeOutput("count=%d, name=%s", 3, "demo");
        } finally {
            GraphMemoryExampleOutput.getPrintLogger().removeHandler(handler);
            GraphMemoryExampleOutput.getPrintLogger().setUseParentHandlers(true);
        }

        assertThat(handler.records.getFirst().getMessage()).isEqualTo("count=3, name=demo");
    }

    private static final class CapturingHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
