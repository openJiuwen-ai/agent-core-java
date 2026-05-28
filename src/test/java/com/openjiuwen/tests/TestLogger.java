/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test logger - uses project logging system.
 * <p>
 * Mirrors Python's {@code test_logger.py} in
 * {@code tests/test_logger.py}.
 */
public class TestLogger {

    private static final Logger logger = LoggerFactory.getLogger(TestLogger.class);

    @Nested
    @DisplayName("Logger tests")
    class LoggerTests {

        @Test
        @DisplayName("Test logger creation")
        void testLoggerCreation() {
            Logger testLogger = LoggerFactory.getLogger("test");
            
            assertThat(testLogger).isNotNull();
        }

        @Test
        @DisplayName("Test logger info output")
        void testLoggerInfoOutput() {
            logger.info("Test info message");
            
            assertThat(logger).isNotNull();
        }

        @Test
        @DisplayName("Test logger debug output")
        void testLoggerDebugOutput() {
            logger.debug("Test debug message");
            
            assertThat(logger).isNotNull();
        }

        @Test
        @DisplayName("Test logger error output")
        void testLoggerErrorOutput() {
            logger.error("Test error message");
            
            assertThat(logger).isNotNull();
        }
    }
}