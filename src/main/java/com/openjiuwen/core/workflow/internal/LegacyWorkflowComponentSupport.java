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
 * 
 * @since 0.1.7
 */
public final class LegacyWorkflowComponentSupport {
    /**
     * LegacyWorkflowComponentSupport.
     * 
     * @since 0.1.7
     */
    private LegacyWorkflowComponentSupport() {
    }

    /**
     * adapt.
     * 
     * @param component component
     * @return the result
     * @since 0.1.7
     */
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

        /**
         * LegacyComponentAdapter.
         * 
         * @param delegate delegate
         * @since 0.1.7
         */
        private LegacyComponentAdapter(Object delegate) {
            this.delegate = delegate;
        }

        /**
         * invoke.
         * 
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Object invoke(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callRequired("invoke", inputs, session, context);
            return unwrap(result);
        }

        /**
         * stream.
         * 
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Iterator<Object> stream(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("stream", inputs, session, context);
            if (result == null) {
                return toIterator(invoke(inputs, session, context));
            }
            return toIterator(unwrap(result));
        }

        /**
         * collect.
         * 
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Object collect(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("collect", inputs, session, context);
            return result != null ? unwrap(result) : invoke(inputs, session, context);
        }

        /**
         * transform.
         * 
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        @Override
        public Iterator<Object> transform(Object inputs, NodeSessionApi session, ModelContext context) {
            Object result = callOptional("transform", inputs, session, context);
            if (result == null) {
                return stream(inputs, session, context);
            }
            return toIterator(unwrap(result));
        }

        /**
         * callRequired.
         * 
         * @param methodName methodName
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        private Object callRequired(String methodName, Object inputs, NodeSessionApi session, ModelContext context) {
            Method method = findMethod(methodName);
            if (method == null) {
                throw new UnsupportedOperationException("Legacy component '" + delegate.getClass().getSimpleName()
                        + "' is missing required method: " + methodName);
            }
            return invokeMethod(method, inputs, session, context);
        }

        /**
         * callOptional.
         * 
         * @param methodName methodName
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
        private Object callOptional(String methodName, Object inputs, NodeSessionApi session, ModelContext context) {
            Method method = findMethod(methodName);
            return method != null ? invokeMethod(method, inputs, session, context) : null;
        }

        /**
         * findMethod.
         * 
         * @param methodName methodName
         * @return the result
         * @since 0.1.7
         */
        private Method findMethod(String methodName) {
            for (Method method : delegate.getClass().getMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 3) {
                    return method;
                }
            }
            return null;
        }

        /**
         * invokeMethod.
         * 
         * @param method method
         * @param inputs inputs
         * @param session session
         * @param context context
         * @return the result
         * @since 0.1.7
         */
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
                throw new IllegalStateException("Failed to invoke legacy component method '" + method.getName()
                        + "' on " + delegate.getClass().getSimpleName(), e);
            }
        }

        /**
         * unwrap.
         * 
         * @param result result
         * @return the result
         * @since 0.1.7
         */
        private static Object unwrap(Object result) {
            if (result instanceof CompletableFuture<?> future) {
                return future.join();
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        /**
         * toIterator.
         * 
         * @param value value
         * @return the result
         * @since 0.1.7
         */
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
