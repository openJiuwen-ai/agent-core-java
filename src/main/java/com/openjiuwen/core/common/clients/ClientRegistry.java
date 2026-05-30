/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final ClientRegistry GLOBAL_REGISTRY = new ClientRegistry();

    private final Map<String, Function<Map<String, Object>, ?>> factories = new LinkedHashMap<>();
    private final Map<String, Class<?>> clientClasses = new HashMap<>();

    public ClientRegistry() {
    }

    public static ClientRegistry getClientRegistry() {
        return GLOBAL_REGISTRY;
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
            Function<Map<String, Object>, ?> factoryFunc) {
        String fullName = fullName(name, clientType);

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
    public void registerClass(Class<?> clientClass) {
        List<String> clientNames = getClientNamesFromClass(clientClass);
        String clientType = getClientTypeFromClass(clientClass);

        if (clientNames.isEmpty()) {
            throw new IllegalArgumentException(
                "Client class " + clientClass.getSimpleName() + 
                " must define __client_name__");
        }

        if (clientType == null) {
            throw new IllegalArgumentException(
                "Client class " + clientClass.getSimpleName() + 
                " must define __client_type__");
        }

        if (clientType.isEmpty()) {
            throw new IllegalArgumentException(
                "Client class " + clientClass.getSimpleName()
                    + " __client_type__ cannot be empty, register failed");
        }

        for (String clientName : clientNames) {
            String fullName = clientType + "_" + clientName;
            if (factories.containsKey(fullName) || clientClasses.containsKey(fullName)) {
                return;
            }

            clientClasses.put(fullName, clientClass);
            factories.put(fullName, kwargs -> instantiateClient(clientClass, kwargs));
        }
    }

    private String fullName(String name, String clientType) {
        return clientType != null && !clientType.isEmpty() ? clientType + "_" + name : name;
    }

    /**
     * Get the client names from a class (via reflection or static metadata).
     */
    private List<String> getClientNamesFromClass(Class<?> clazz) {
        Object value = getClassMetadata(clazz, "getClientName", "__client_name__", "CLIENT_NAME", "clientName");
        return normalizeNames(value);
    }

    private List<String> normalizeNames(Object value) {
        List<String> names = new ArrayList<>();
        if (value instanceof String text && !text.isEmpty()) {
            names.add(text);
        } else if (value instanceof String[] array) {
            for (String name : array) {
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof String name && !name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Get the client type from a class (via reflection or static metadata).
     */
    private String getClientTypeFromClass(Class<?> clazz) {
        Object value = getClassMetadata(clazz, "getClientType", "__client_type__", "CLIENT_TYPE", "clientType");
        return value instanceof String text ? text : null;
    }

    private Object getClassMetadata(Class<?> clazz, String methodName, String... fieldNames) {
        try {
            java.lang.reflect.Method method = clazz.getDeclaredMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(null);
            if (value != null) {
                return value;
            }
        } catch (Exception e) {
            // Fall through to field lookup.
        }
        for (String fieldName : fieldNames) {
            try {
                java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(null);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                // Try the next Java metadata convention.
            }
        }
        return null;
    }

    private Object instantiateClient(Class<?> clazz, Map<String, Object> config) {
        try {
            java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor(Map.class);
            constructor.setAccessible(true);
            return constructor.newInstance(config);
        } catch (NoSuchMethodException e) {
            try {
                java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance();
                if (instance instanceof BaseClient baseClient) {
                    baseClient.initialize(config);
                }
                return instance;
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to instantiate client: " + clazz.getSimpleName(), ex);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to instantiate client: " + clazz.getSimpleName(), e);
        }
    }

    public Object getClient(String name) {
        return getClient(name, "common", Map.of());
    }

    public Object getClient(String name, String clientType) {
        return getClient(name, clientType, Map.of());
    }

    public Object getClient(String name, Map<String, Object> config) {
        return getClient(name, "common", config);
    }

    public Object getClient(String name, String clientType, Map<String, Object> config) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Client name cannot be empty");
        }

        String lookupName = name;
        if (clientType != null && !clientType.isEmpty()) {
            String typedName = clientType + "_" + name;
            if (factories.containsKey(typedName)) {
                lookupName = typedName;
            }
        }

        Function<Map<String, Object>, ?> factory = factories.get(lookupName);
        if (factory == null) {
            String searchKey = fullName(name, clientType);
            throw new IllegalArgumentException(
                "Unknown client type: '" + searchKey + "'. Available: " + new ArrayList<>(factories.keySet()));
        }

        try {
            Map<String, Object> safeConfig = config == null ? Map.of() : config;
            return factory.apply(safeConfig);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to create client '" + lookupName + "': " + e.getMessage(), e);
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
        Object client = getClient(name, clientType, config);
        if (client instanceof BaseClient baseClient) {
            return baseClient;
        }
        throw new IllegalStateException("Registered client '" + fullName(name, clientType)
            + "' is not a BaseClient");
    }

    /**
     * Check if a client is registered.
     *
     * @param name the client name
     * @param clientType the client type
     * @return true if the client is registered
     */
    public boolean isRegistered(String name, String clientType) {
        String fullName = fullName(name, clientType);
        return factories.containsKey(fullName) || clientClasses.containsKey(fullName);
    }

    public void unregister(String name) {
        unregister(name, null);
    }

    public void unregister(String name, String clientType) {
        String fullName = fullName(name, clientType);
        if (!factories.containsKey(fullName)) {
            throw new IllegalArgumentException("Client type '" + fullName + "' not registered");
        }
        factories.remove(fullName);
        clientClasses.remove(fullName);
    }

    public List<String> listClients() {
        return new ArrayList<>(factories.keySet());
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
