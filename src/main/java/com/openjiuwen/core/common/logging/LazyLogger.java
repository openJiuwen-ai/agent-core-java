/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.logging;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Lazy initialization logger wrapper.
 * <p>
 * Logger is only initialized when actually used, improving startup performance.
 * Suitable for static/module-level logger definitions.
 */
public class LazyLogger implements LoggerProtocol {

    private static final java.util.Set<LazyLogger> INSTANCES =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    private final java.util.function.Supplier<LoggerProtocol> getter;
    private volatile LoggerProtocol delegate;

    public LazyLogger(java.util.function.Supplier<LoggerProtocol> getter) {
        this.getter = getter;
        INSTANCES.add(this);
    }

    /**
     * Reset every lazy logger delegate after {@link LogManager#reset()}.
     */
    public static void resetAll() {
        INSTANCES.forEach(logger -> logger.delegate = null);
    }

    private LoggerProtocol getDelegate() {
        if (delegate == null) {
            synchronized (this) {
                if (delegate == null) {
                    delegate = getter.get();
                }
            }
        }
        return delegate;
    }

    @Override
    public void debug(String msg, Object... args) {
        getDelegate().debug(msg, args);
    }

    @Override
    public void info(String msg, Object... args) {
        getDelegate().info(msg, args);
    }

    @Override
    public void warning(String msg, Object... args) {
        getDelegate().warning(msg, args);
    }

    @Override
    public void error(String msg, Object... args) {
        getDelegate().error(msg, args);
    }

    @Override
    public void critical(String msg, Object... args) {
        getDelegate().critical(msg, args);
    }

    @Override
    public void exception(String msg, Throwable t, Object... args) {
        getDelegate().exception(msg, t, args);
    }

    @Override
    public void log(int level, String msg, Object... args) {
        getDelegate().log(level, msg, args);
    }

    @Override
    public void setLevel(int level) {
        getDelegate().setLevel(level);
    }

    @Override
    public void addHandler(Handler handler) {
        getDelegate().addHandler(handler);
    }

    @Override
    public void removeHandler(Handler handler) {
        getDelegate().removeHandler(handler);
    }

    @Override
    public void addFilter(Filter filter) {
        getDelegate().addFilter(filter);
    }

    @Override
    public void removeFilter(Filter filter) {
        getDelegate().removeFilter(filter);
    }

    @Override
    public Logger logger() {
        return getDelegate().logger();
    }

    @Override
    public java.util.Map<String, Object> getConfig() {
        return getDelegate().getConfig();
    }

    @Override
    public void reconfigure(java.util.Map<String, Object> config) {
        getDelegate().reconfigure(config);
    }
}
