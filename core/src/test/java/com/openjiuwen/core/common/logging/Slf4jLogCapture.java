/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.List;

/**
 * Test-only log capture utility for raw SLF4J loggers.
 *
 * <p>Encapsulates backend-specific appender logic so test classes don't
 * need to reference a concrete logging backend directly. This class is
 * excluded from {@link LoggingBackendDecouplingTest}'s guard because it
 * must reference the backend to attach the appender.</p>
 *
 * @since 0.1.17
 */
public final class Slf4jLogCapture implements AutoCloseable {
    private final ch.qos.logback.classic.Logger logger;
    private final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;
    private final ch.qos.logback.classic.Level originalLevel;

    /**
     * Attaches a capturing appender to the SLF4J logger for the given class.
     *
     * @param clazz the class whose logger to capture
     */
    public Slf4jLogCapture(Class<?> clazz) {
        logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(clazz);
        appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        originalLevel = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    /**
     * Returns the formatted messages of all captured log events.
     *
     * @return formatted messages in order
     */
    public List<String> formattedMessages() {
        return appender.list.stream()
                .map(event -> event.getFormattedMessage())
                .toList();
    }

    /**
     * Detaches the appender and restores the original log level.
     */
    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    /**
     * Convenience method: attach capture, run action, return formatted messages.
     *
     * @param clazz  the class whose logger to capture
     * @param action the action to run while capturing
     * @return formatted log lines produced during the action
     */
    public static List<String> capture(Class<?> clazz, Runnable action) {
        try (Slf4jLogCapture capture = new Slf4jLogCapture(clazz)) {
            action.run();
            return capture.formattedMessages();
        }
    }
}
