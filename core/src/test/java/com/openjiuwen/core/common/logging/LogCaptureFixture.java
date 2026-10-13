/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Log capture fixture for the {@link Loggers} facade log types. Registers a
 * recording {@link LoggerProtocol} through {@link LogManager#registerLogger}
 * so tests can assert aggregated ERROR output of the destroy chain and
 * callback batch cleanup without depending on a concrete logging backend.
 *
 * <p>The recording protocol delegates every event to the previously
 * registered protocol, so surrounding log output stays intact while the
 * fixture is attached. Closing the fixture restores the previous protocol;
 * tests must not attach two fixtures to the same log type at the same
 * time.</p>
 *
 * @since 0.1.16
 */
public final class LogCaptureFixture implements AutoCloseable {
    private static final String DEBUG = "DEBUG";
    private static final String INFO = "INFO";
    private static final String WARNING = "WARNING";
    private static final String ERROR = "ERROR";

    private final String logType;
    private final LoggerProtocol previous;
    private final List<CapturedEvent> events = new CopyOnWriteArrayList<>();

    private LogCaptureFixture(String logType, LoggerProtocol previous) {
        this.logType = logType;
        this.previous = previous;
    }

    /**
     * Attaches a capturing protocol to the given log type (use
     * {@code "agent"} or {@code "tool"} for the {@link Loggers} facade).
     *
     * @param logType log type name registered in {@link LogManager}
     * @return attached fixture; close it to detach
     */
    public static LogCaptureFixture attach(String logType) {
        LoggerProtocol previous = LogManager.getLogger(logType);
        LogCaptureFixture fixture = new LogCaptureFixture(logType, previous);
        LogManager.registerLogger(logType, new RecordingProtocol(fixture, previous));
        return fixture;
    }

    /**
     * Returns the formatted messages of all captured events at a level.
     *
     * @param level level name such as {@code "ERROR"} or {@code "WARNING"}
     * @return formatted messages in order
     */
    public List<String> messagesAt(String level) {
        return events.stream()
                .filter(event -> event.level().equals(level))
                .map(CapturedEvent::message)
                .toList();
    }

    /**
     * Counts events at a level whose formatted message contains the text.
     *
     * @param level level name such as {@code "ERROR"} or {@code "WARNING"}
     * @param contains substring to look for
     * @return number of matching events
     */
    public long count(String level, String contains) {
        return events.stream()
                .filter(event -> event.level().equals(level))
                .filter(event -> event.message().contains(contains))
                .count();
    }

    @Override
    public void close() {
        LogManager.registerLogger(logType, previous);
    }

    private void record(String level, String message) {
        events.add(new CapturedEvent(level, message));
    }

    private static String format(String msg, List<Object> args) {
        String result = String.valueOf(msg);
        if (args == null || args.isEmpty()) {
            return result;
        }
        for (Object arg : args) {
            int placeholder = result.indexOf("{}");
            if (placeholder < 0) {
                break;
            }
            result = result.substring(0, placeholder) + String.valueOf(arg)
                    + result.substring(placeholder + 2);
        }
        return result;
    }

    private record CapturedEvent(String level, String message) {
    }

    /**
     * Recording protocol: every event is captured first and then delegated
     * to the previous protocol so surrounding log output stays intact.
     */
    private static final class RecordingProtocol implements LoggerProtocol {
        private final LogCaptureFixture fixture;
        private final LoggerProtocol delegate;

        private RecordingProtocol(LogCaptureFixture fixture, LoggerProtocol delegate) {
            this.fixture = fixture;
            this.delegate = delegate;
        }

        @Override
        public void debug(String msg, Object... args) {
            fixture.record(DEBUG, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.debug(msg, args);
        }

        @Override
        public void info(String msg, Object... args) {
            fixture.record(INFO, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.info(msg, args);
        }

        @Override
        public void warning(String msg, Object... args) {
            fixture.record(WARNING, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.warning(msg, args);
        }

        @Override
        public void error(String msg, Object... args) {
            fixture.record(ERROR, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.error(msg, args);
        }

        @Override
        public void critical(String msg, Object... args) {
            fixture.record(ERROR, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.critical(msg, args);
        }

        @Override
        public void exception(String msg, Throwable throwable, Object... args) {
            fixture.record(ERROR, format(msg, args == null ? List.of() : Arrays.asList(args)) + " - " + throwable);
            delegate.exception(msg, throwable, args);
        }

        @Override
        public void log(int level, String msg, Object... args) {
            fixture.record("LEVEL_" + level, format(msg, args == null ? List.of() : Arrays.asList(args)));
            delegate.log(level, msg, args);
        }

        @Override
        public void setLevel(int level) {
            delegate.setLevel(level);
        }

        @Override
        public Map<String, Object> getConfig() {
            return delegate.getConfig();
        }

        @Override
        public void reconfigure(Map<String, Object> config) {
            delegate.reconfigure(config);
        }
    }
}
