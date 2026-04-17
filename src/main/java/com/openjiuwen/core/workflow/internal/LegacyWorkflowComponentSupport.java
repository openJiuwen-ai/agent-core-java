/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.internal;

import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.session.NodeSessionApi;
import com.openjiuwen.core.workflow.ComponentComposable;
import com.openjiuwen.core.workflow.WorkflowComponent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;

/**
 * Internal compatibility bridge for translated tests that still register plain
 * POJO nodes instead of WorkflowComponent implementations.
 */
public final class LegacyWorkflowComponentSupport {

    private LegacyWorkflowComponentSupport() {
    }

    public static ComponentComposable adapt(Object component) {
        if (component instanceof ComponentComposable composable) {
            return composable;
        }
        if (component == null) {
            throw new IllegalArgumentException("workflow component cannot be null");
        }
        return new LegacyComponentAdapter(component);
    }

    private static final class LegacyComponentAdapter extends WorkflowComponent {

        private final Object delegate;

        private LegacyComponentAdapter(Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callRequired("invoke", inputs, session, context);
            return unwrap(result);
        }

        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("stream", inputs, session, context);
            if (result == null) {
                return toIterator(invoke(inputs, session, context));
            }
            return toIterator(unwrap(result));
        }

        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("collect", inputs, session, context);
            return result != null ? unwrap(result) : invoke(inputs, session, context);
        }

        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("transform", inputs, session, context);
            if (result == null) {
                return stream(inputs, session, context);
            }
            return toIterator(unwrap(result));
        }

        private Object callRequired(String methodName, Object inputs, NodeSessionApi session, ModelContext context) {
            Method method = findMethod(methodName);
            if (method == null) {
                throw new UnsupportedOperationException(
                        "Legacy component '" + delegate.getClass().getSimpleName()
                                + "' is missing required method: " + methodName);
            }
            return invokeMethod(method, inputs, session, context);
        }

        private Object callOptional(String methodName, Object inputs, NodeSessionApi session, ModelContext context) {
            Method method = findMethod(methodName);
            return method != null ? invokeMethod(method, inputs, session, context) : null;
        }

        private Method findMethod(String methodName) {
            for (Method method : delegate.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 3) {
                    return method;
                }
            }
            return null;
        }

        private Object invokeMethod(Method method, Object inputs, NodeSessionApi session, ModelContext context) {
            try {
                return method.invoke(delegate, inputs, session, context);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (target instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(target);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to invoke legacy component method '" + method.getName() + "' on "
                                + delegate.getClass().getSimpleName(),
                        e);
            }
        }

        private static Object unwrap(Object result) {
            if (result instanceof CompletableFuture<?> future) {
                return future.join();
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private static Iterator<Object> toIterator(Object value) {
            if (value instanceof Iterator<?> iterator) {
                return (Iterator<Object>) iterator;
            }
            if (value instanceof Iterable<?> iterable) {
                return (Iterator<Object>) iterable.iterator();
            }
            return Collections.singletonList(value).iterator();
        }
    }
}
