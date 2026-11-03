/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import com.openjiuwen.core.common.logging.defaults.DefaultLogger;
import com.openjiuwen.core.common.logging.events.BaseLogEvent;
import com.openjiuwen.core.common.logging.events.LogEventType;

import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Loguru-style logger implementation.
 * <p>
 * Mirrors Python's {@code LoguruLogger} in
 * {@code openjiuwen/core/common/logging/loguru/loguru_impl.py}.
 *
 * <p>Provides a simplified, fluent logging interface on top of the repository's
 * Java logging stack.</p>
 */
public class LoguruLogger implements LoggerProtocol {

    private final DefaultLogger delegate;
    private final String name;

    public LoguruLogger(String name, Map<String, Object> config) {
        this.name = name;
        this.delegate = new DefaultLogger(name, config);
    }

    public LoguruLogger(String name) {
        this(name, Map.of());
    }

    @Override
    public void debug(String msg, Object... args) {
        delegate.debug(msg, args);
    }

    @Override
    public void info(String msg, Object... args) {
        delegate.info(msg, args);
    }

    @Override
    public void warning(String msg, Object... args) {
        delegate.warning(msg, args);
    }

    @Override
    public void error(String msg, Object... args) {
        delegate.error(msg, args);
    }

    @Override
    public void critical(String msg, Object... args) {
        delegate.critical(msg, args);
    }

    public void logEvent(String msg, LogEventType eventType, BaseLogEvent event) {
        delegate.logEvent(msg, eventType, event);
    }

    @Override
    public void exception(String msg, Throwable t, Object... args) {
        delegate.exception(msg, t, args);
    }

    @Override
    public void log(int level, String msg, Object... args) {
        delegate.log(level, msg, args);
    }

    @Override
    public void setLevel(int level) {
        delegate.setLevel(level);
    }

    @Override
    public void addHandler(Handler handler) {
        delegate.addHandler(handler);
    }

    @Override
    public void removeHandler(Handler handler) {
        delegate.removeHandler(handler);
    }

    @Override
    public void addFilter(Filter filter) {
        delegate.addFilter(filter);
    }

    @Override
    public void removeFilter(Filter filter) {
        delegate.removeFilter(filter);
    }

    @Override
    public Logger logger() {
        return delegate.logger();
    }

    @Override
    public Map<String, Object> getConfig() {
        return delegate.getConfig();
    }

    @Override
    public void reconfigure(Map<String, Object> config) {
        delegate.reconfigure(config);
    }

    public String getName() {
        return name;
    }
}

