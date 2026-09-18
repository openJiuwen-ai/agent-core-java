/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Mirrors Python's {@code Branch} dataclass in
 * {@code openjiuwen/core/graph/graph.py}.
 */
public class Branch {

    private final Object condition;

    public Branch(Object condition) {
        this.condition = condition;
    }

    public Object getCondition() {
        return condition;
    }

    public Supplier<?> asSupplier() {
        return this::route;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object route() {
        if (condition instanceof Router router) {
            return router.route();
        }
        if (condition instanceof Supplier<?> supplier) {
            return supplier.get();
        }
        Object reflected = invokeNoArg("route");
        if (reflected != InvocationMissing.INSTANCE) {
            return reflected;
        }
        reflected = invokeNoArg("call");
        if (reflected != InvocationMissing.INSTANCE) {
            return reflected;
        }
        if (condition instanceof Function function) {
            return function.apply(null);
        }
        throw new IllegalArgumentException("branch condition is not callable");
    }

    private Object invokeNoArg(String methodName) {
        if (condition == null) {
            return InvocationMissing.INSTANCE;
        }
        try {
            Method method = condition.getClass().getMethod(methodName);
            if (method.getParameterCount() != 0) {
                return InvocationMissing.INSTANCE;
            }
            method.setAccessible(true);
            return method.invoke(condition);
        } catch (NoSuchMethodException ignored) {
            return InvocationMissing.INSTANCE;
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException("failed to invoke branch condition", ex);
        }
    }

    private enum InvocationMissing {
        INSTANCE
    }
}
