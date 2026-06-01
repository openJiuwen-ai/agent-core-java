/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.core.common.logging;

/**
 * Test utility that initializes logging config and exposes a logger.
 * <p>
 * Mirrors Python's {@code test_logger.py} in
 * {@code tests.unit_tests.core.common.log.test_logger}.
 * <p>
 * Usage:
 * <pre>
 *     LoggerProtocol logger = TestLoggerInitializer.getLogger();
 *     logger.info("something happened");
 * </pre>
 */
public final class TestLoggerInitializer {

    private static final LazyLogger LOGGER = new LazyLogger(() -> LogManager.getLogger("test"));

    static {
        LogManager.initialize();
    }

    private TestLoggerInitializer() {
        throw new AssertionError("No TestLoggerInitializer instances");
    }

    public static LoggerProtocol getLogger() {
        return LOGGER;
    }
}
