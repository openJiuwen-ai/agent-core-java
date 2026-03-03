// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.

package com.openjiuwen.core.common.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton metaclass for implementing the singleton pattern.
 *
 * <p>This class provides a thread-safe implementation of the singleton pattern.
 * Classes that extend this class will have only one instance, regardless of how
 * many times {@link #getInstance(Object...)} is called.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * public class MySingleton extends SingletonMeta<String> {
 *     private final String name;
 *
 *     public MySingleton(String name) {
 *         super();
 *         this.name = name;
 *     }
 *
 *     public String getName() {
 *         return name;
 *     }
 *
 *     public static MySingleton getInstance(String name) {
 *         return SingletonMeta.getInstance(MySingleton.class, name);
 *     }
 * }
 *
 * // Usage:
 * MySingleton instance1 = MySingleton.getInstance("first");
 * MySingleton instance2 = MySingleton.getInstance("second"); // returns same instance
 * assert instance1 == instance2;
 * }</pre>
 *
 * @param <T> The type of the initialization parameter
 */
public abstract class SingletonMeta<T> {

    /**
     * Storage for singleton instances.
     * Uses a ConcurrentHashMap to ensure thread safety.
     */
    private static final Map<Class<?>, Object> INSTANCES = new ConcurrentHashMap<>();

    /**
     * Protected constructor for subclasses.
     */
    protected SingletonMeta() {
    }

    /**
     * Get the singleton instance of the specified class.
     *
     * <p>This method is thread-safe and will create the instance if it doesn't exist.
     * The instance is created only once, subsequent calls return the same instance.
     *
     * @param clazz    The class of the singleton to get or create
     * @param args     Arguments to pass to the constructor (only used on first call)
     * @param <S>      The type of the singleton
     * @param <T>      The type of the initialization parameter
     * @return The singleton instance
     * @throws RuntimeException if the instance cannot be created
     */
    @SuppressWarnings("unchecked")
    public static <S extends SingletonMeta<T>, T> S getInstance(Class<S> clazz, Object... args) {
        return (S) INSTANCES.computeIfAbsent(clazz, key -> {
            try {
                // Find a constructor that matches the provided arguments
                java.lang.reflect.Constructor<S> constructor = findConstructor(clazz, args);
                constructor.setAccessible(true);
                return constructor.newInstance(args);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create singleton instance of " + clazz.getName(), e);
            }
        });
    }

    /**
     * Find a constructor that matches the provided arguments.
     *
     * @param clazz The class to find the constructor for
     * @param args  The arguments to match against
     * @param <S>   The type of the class
     * @return The matching constructor
     * @throws NoSuchMethodException if no matching constructor is found
     */
    @SuppressWarnings("unchecked")
    private static <S extends SingletonMeta<T>, T> java.lang.reflect.Constructor<S> findConstructor(
            Class<S> clazz, Object... args) throws NoSuchMethodException {

        // Get all declared constructors
        java.lang.reflect.Constructor<?>[] constructors = clazz.getDeclaredConstructors();

        // If no arguments, try to find the no-arg constructor
        if (args == null || args.length == 0) {
            for (java.lang.reflect.Constructor<?> constructor : constructors) {
                if (constructor.getParameterCount() == 0) {
                    return (java.lang.reflect.Constructor<S>) constructor;
                }
            }
            throw new NoSuchMethodException("No no-arg constructor found for " + clazz.getName());
        }

        // Try to find a constructor that matches the argument types
        outer:
        for (java.lang.reflect.Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            if (paramTypes.length != args.length) {
                continue;
            }

            // Check if each parameter type is compatible with the argument
            for (int i = 0; i < paramTypes.length; i++) {
                if (args[i] == null) {
                    // null can be passed to any non-primitive parameter
                    if (paramTypes[i].isPrimitive()) {
                        continue outer;
                    }
                } else if (!paramTypes[i].isAssignableFrom(args[i].getClass())) {
                    // Check for primitive/wrapper compatibility
                    if (!isPrimitiveCompatible(paramTypes[i], args[i].getClass())) {
                        continue outer;
                    }
                }
            }
            return (java.lang.reflect.Constructor<S>) constructor;
        }

        throw new NoSuchMethodException("No matching constructor found for " + clazz.getName() +
                " with arguments: " + java.util.Arrays.toString(args));
    }

    /**
     * Check if a primitive type is compatible with its wrapper class.
     *
     * @param primitiveType The primitive type
     * @param wrapperType   The wrapper class
     * @return true if compatible
     */
    private static boolean isPrimitiveCompatible(Class<?> primitiveType, Class<?> wrapperType) {
        if (primitiveType == int.class && wrapperType == Integer.class) return true;
        if (primitiveType == long.class && wrapperType == Long.class) return true;
        if (primitiveType == double.class && wrapperType == Double.class) return true;
        if (primitiveType == float.class && wrapperType == Float.class) return true;
        if (primitiveType == boolean.class && wrapperType == Boolean.class) return true;
        if (primitiveType == byte.class && wrapperType == Byte.class) return true;
        if (primitiveType == short.class && wrapperType == Short.class) return true;
        if (primitiveType == char.class && wrapperType == Character.class) return true;
        return false;
    }

    /**
     * Clear the singleton instance for a specific class.
     * <p>
     * <b>Warning:</b> This method is primarily for testing purposes.
     * Using it in production code may break the singleton pattern.
     *
     * @param clazz The class to clear the singleton for
     */
    protected static void clearInstance(Class<?> clazz) {
        INSTANCES.remove(clazz);
    }
}