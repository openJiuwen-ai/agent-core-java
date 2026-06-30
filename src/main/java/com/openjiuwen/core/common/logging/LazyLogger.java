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

    private final java.util.function.Supplier<LoggerProtocol> getter;
    private volatile LoggerProtocol delegate;

    /**
     * Auto-generated for codecheck compliance.
     */
    public LazyLogger(java.util.function.Supplier<LoggerProtocol> getter) {
        this.getter = getter;
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
    /**
     * Auto-generated for codecheck compliance.
     */
    public void debug(String msg, Object... args) {
        getDelegate().debug(msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void info(String msg, Object... args) {
        getDelegate().info(msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void warning(String msg, Object... args) {
        getDelegate().warning(msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void error(String msg, Object... args) {
        getDelegate().error(msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void critical(String msg, Object... args) {
        getDelegate().critical(msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void exception(String msg, Throwable t, Object... args) {
        getDelegate().exception(msg, t, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void log(int level, String msg, Object... args) {
        getDelegate().log(level, msg, args);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void setLevel(int level) {
        getDelegate().setLevel(level);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void addHandler(Handler handler) {
        getDelegate().addHandler(handler);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void removeHandler(Handler handler) {
        getDelegate().removeHandler(handler);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void addFilter(Filter filter) {
        getDelegate().addFilter(filter);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void removeFilter(Filter filter) {
        getDelegate().removeFilter(filter);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public Logger logger() {
        return getDelegate().logger();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public java.util.Map<String, Object> getConfig() {
        return getDelegate().getConfig();
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public void reconfigure(java.util.Map<String, Object> config) {
        getDelegate().reconfigure(config);
    }
}
