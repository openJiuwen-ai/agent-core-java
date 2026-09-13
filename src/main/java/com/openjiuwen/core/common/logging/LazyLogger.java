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
 * 
 * @since 0.1.7
 */
public class LazyLogger implements LoggerProtocol {
    private final java.util.function.Supplier<LoggerProtocol> getter;
    private volatile LoggerProtocol delegate;

    /**
     * LazyLogger.
     * 
     * @param getter getter
     * @since 0.1.7
     */
    public LazyLogger(java.util.function.Supplier<LoggerProtocol> getter) {
        this.getter = getter;
    }

    /**
     * getDelegate.
     * 
     * @return the result
     * @since 0.1.7
     */
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

    /**
     * debug.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void debug(String msg, Object... args) {
        getDelegate().debug(msg, args);
    }

    /**
     * info.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void info(String msg, Object... args) {
        getDelegate().info(msg, args);
    }

    /**
     * warning.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void warning(String msg, Object... args) {
        getDelegate().warning(msg, args);
    }

    /**
     * error.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void error(String msg, Object... args) {
        getDelegate().error(msg, args);
    }

    /**
     * critical.
     * 
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void critical(String msg, Object... args) {
        getDelegate().critical(msg, args);
    }

    /**
     * exception.
     * 
     * @param msg msg
     * @param t t
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void exception(String msg, Throwable t, Object... args) {
        getDelegate().exception(msg, t, args);
    }

    /**
     * log.
     * 
     * @param level level
     * @param msg msg
     * @param args args
     * @since 0.1.7
     */
    @Override
    public void log(int level, String msg, Object... args) {
        getDelegate().log(level, msg, args);
    }

    /**
     * setLevel.
     * 
     * @param level level
     * @since 0.1.7
     */
    @Override
    public void setLevel(int level) {
        getDelegate().setLevel(level);
    }

    /**
     * addHandler.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    @Override
    public void addHandler(Handler handler) {
        getDelegate().addHandler(handler);
    }

    /**
     * removeHandler.
     * 
     * @param handler handler
     * @since 0.1.7
     */
    @Override
    public void removeHandler(Handler handler) {
        getDelegate().removeHandler(handler);
    }

    /**
     * addFilter.
     * 
     * @param filter filter
     * @since 0.1.7
     */
    @Override
    public void addFilter(Filter filter) {
        getDelegate().addFilter(filter);
    }

    /**
     * removeFilter.
     * 
     * @param filter filter
     * @since 0.1.7
     */
    @Override
    public void removeFilter(Filter filter) {
        getDelegate().removeFilter(filter);
    }

    /**
     * logger.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Logger logger() {
        return getDelegate().logger();
    }

    /**
     * getConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public java.util.Map<String, Object> getConfig() {
        return getDelegate().getConfig();
    }

    /**
     * reconfigure.
     * 
     * @param config config
     * @since 0.1.7
     */
    @Override
    public void reconfigure(java.util.Map<String, Object> config) {
        getDelegate().reconfigure(config);
    }
}
