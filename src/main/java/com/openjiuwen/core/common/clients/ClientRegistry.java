/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for managing client classes and factories.
 * <p>
 * Mirrors Python's {@code ClientRegistry} class from
 * <code>common/clients/client_registry.py</code>.
 *
 * <p>Provides a central registry for client classes and their factories.
 * Supports both decorator-based and class-based registration.
 */
public class ClientRegistry {

    private final Map<String, Function<Map<String, Object>, BaseClient>> factories = new HashMap<>();
    private final Map<String, Class<? extends BaseClient>> clientClasses = new HashMap<>();

    public ClientRegistry() {
    }

    /**
     * Register a client factory function.
     *
     * @param name the primary name of the client
     * @param clientType the type category (e.g., 'database', 'cache')
     * @param factoryFunc the factory function that creates the client
     * @throws IllegalArgumentException if the client name is already registered
     */
    public void registerClient(String name, String clientType, 
            Function<Map<String, Object>, BaseClient> factoryFunc) {
        String fullName = clientType != null && !clientType.isEmpty() 
            ? clientType + "_" + name : name;

        if (factories.containsKey(fullName)) {
            throw new IllegalArgumentException(
                "Client type '" + fullName + "' already registered");
        }

        factories.put(fullName, factoryFunc);
    }

    /**
     * Register a client class.
     *
     * @param clientClass the client class to register
     * @throws IllegalArgumentException if the class doesn't define required attributes
     */
    public void registerClass(Class<? extends BaseClient> clientClass) {
        // In Java, we use annotations or static methods for metadata
        // For now, we use reflection to check for getClientName() static method
        String clientName = getClientNameFromClass(clientClass);
        String clientType = getClientTypeFromClass(clientClass);

        if (clientName == null) {
            throw new IllegalArgumentException(
                "Client class " + clientClass.getSimpleName() + 
                " must define getClientName()");
        }

        if (clientType == null) {
            throw new IllegalArgumentException(
                "Client class " + clientClass.getSimpleName() + 
                " must define getClientType()");
        }

        String fullName = clientType + "_" + clientName;
        if (clientClasses.containsKey(fullName)) {
            throw new IllegalArgumentException(
                "Client '" + fullName + "' already registered");
        }

        clientClasses.put(fullName, clientClass);
    }

    /**
     * Get the client name from a class (via reflection or static method).
     */
    private String getClientNameFromClass(Class<?> clazz) {
        try {
            java.lang.reflect.Method method = clazz.getMethod("getClientName");
            return (String) method.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the client type from a class (via reflection or static method).
     */
    private String getClientTypeFromClass(Class<?> clazz) {
        try {
            java.lang.reflect.Method method = clazz.getMethod("getClientType");
            return (String) method.invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Create a client instance using a registered factory.
     *
     * @param name the client name
     * @param clientType the client type
     * @param config the configuration parameters
     * @return the created client instance
     * @throws IllegalArgumentException if the client is not registered
     */
    public BaseClient createClient(String name, String clientType, 
            Map<String, Object> config) {
        String fullName = clientType != null && !clientType.isEmpty() 
            ? clientType + "_" + name : name;

        Function<Map<String, Object>, BaseClient> factory = factories.get(fullName);
        if (factory != null) {
            return factory.apply(config);
        }

        Class<? extends BaseClient> clazz = clientClasses.get(fullName);
        if (clazz != null) {
            try {
                // Try constructor with config parameter
                java.lang.reflect.Constructor<? extends BaseClient> constructor = 
                    clazz.getConstructor(Map.class);
                return constructor.newInstance(config);
            } catch (Exception e) {
                try {
                    // Try default constructor
                    return clazz.getDeclaredConstructor().newInstance();
                } catch (Exception ex) {
                    throw new RuntimeException(
                        "Failed to instantiate client: " + fullName, ex);
                }
            }
        }

        throw new IllegalArgumentException("Client '" + fullName + "' not registered");
    }

    /**
     * Check if a client is registered.
     *
     * @param name the client name
     * @param clientType the client type
     * @return true if the client is registered
     */
    public boolean isRegistered(String name, String clientType) {
        String fullName = clientType != null && !clientType.isEmpty() 
            ? clientType + "_" + name : name;
        return factories.containsKey(fullName) || clientClasses.containsKey(fullName);
    }

    /**
     * Get all registered client names.
     *
     * @return a map of registered client names to their types
     */
    public Map<String, String> getRegisteredClients() {
        Map<String, String> result = new HashMap<>();
        for (String key : factories.keySet()) {
            result.put(key, "factory");
        }
        for (String key : clientClasses.keySet()) {
            result.put(key, "class");
        }
        return result;
    }
}