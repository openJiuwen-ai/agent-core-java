/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Logback capture fixture for the {@link Loggers} facade names. Attaches a
 * {@link ListAppender} to the named logger so tests can assert aggregated
 * ERROR output of the destroy chain and callback batch cleanup.
 *
 * @since 0.1.16
 */
public final class LogCaptureFixture implements AutoCloseable {
    private final Logger logger;

    private final ListAppender<ILoggingEvent> appender;

    private LogCaptureFixture(Logger logger, ListAppender<ILoggingEvent> appender) {
        this.logger = logger;
        this.appender = appender;
    }

    /**
     * Attaches a capturing appender to the given logger name (use
     * {@code "agent"} or {@code "tool"} for the {@link Loggers} facade).
     *
     * @param loggerName logback logger name
     * @return attached fixture; close it to detach
     */
    public static LogCaptureFixture attach(String loggerName) {
        org.slf4j.Logger boundLogger = LoggerFactory.getLogger(loggerName);
        if (!(boundLogger instanceof Logger logger)) {
            throw new IllegalStateException("logback-backed logger expected for " + loggerName);
        }
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new LogCaptureFixture(logger, appender);
    }

    /**
     * Returns all captured events (unmodifiable view of the snapshot).
     *
     * @return captured events in order
     */
    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    /**
     * Returns the formatted messages of all captured events at a level.
     *
     * @param level level to filter by
     * @return formatted messages in order
     */
    public List<String> messagesAt(Level level) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());
    }

    /**
     * Counts events at a level whose formatted message contains the text.
     *
     * @param level level to filter by
     * @param contains substring to look for
     * @return number of matching events
     */
    public long count(Level level, String contains) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == level)
                .filter(event -> event.getFormattedMessage().contains(contains))
                .count();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }
}
