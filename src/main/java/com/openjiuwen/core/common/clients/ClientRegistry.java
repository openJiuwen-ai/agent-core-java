/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for managing client classes and factories.
 *
 * <p>Mirrors Python's {@code ClientRegistry} in
 * {@code openjiuwen/core/common/clients/client_registry.py}.</p>
 */
public class ClientRegistry {

    @FunctionalInterface
    public interface ClientFactory {
        Object create(Map<String, Object> kwargs) throws Exception;
    }

    private static final ClientRegistry GLOBAL_REGISTRY = new ClientRegistry();

    private final Map<String, ClientFactory> factories = new LinkedHashMap<>();
    private final Map<String, Class<?>> clientClasses = new LinkedHashMap<>();

    public static ClientRegistry getClientRegistry() {
        return GLOBAL_REGISTRY;
    }

    public void registerClient(String name, String clientType, ClientFactory factoryFunc) {
        String fullName = fullName(name, clientType);
        if (factories.containsKey(fullName)) {
            throw new IllegalArgumentException("Client type '" + fullName + "' already registered");
        }
        factories.put(fullName, factoryFunc);
    }

    public void registerClient(String name, ClientFactory factoryFunc) {
        registerClient(name, "common", factoryFunc);
    }

    public void registerClass(Class<?> clientClass) {
        List<String> names = getClientNames(clientClass);
        if (names.isEmpty()) {
            throw new IllegalArgumentException(
                    "Client class " + clientClass.getSimpleName() + " must define __client_name__");
        }

        String clientType = getClientType(clientClass);
        if (clientType == null) {
            throw new IllegalArgumentException(
                    "Client class " + clientClass.getSimpleName() + " must define __client_type__");
        }
        if (clientType.isEmpty()) {
            throw new IllegalArgumentException(
                    "Client class " + clientClass.getSimpleName()
                            + " __client_type__ cannot be empty, register failed");
        }

        for (String name : names) {
            String fullName = clientType + "_" + name;
            if (factories.containsKey(fullName) || clientClasses.containsKey(fullName)) {
                return;
            }
            clientClasses.put(fullName, clientClass);
            factories.put(fullName, kwargs -> instantiateClient(clientClass, kwargs));
        }
    }

    public Object getClient(String name) {
        return getClient(name, "common", Map.of());
    }

    public Object getClient(String name, Map<String, Object> kwargs) {
        return getClient(name, "common", kwargs);
    }

    public Object getClient(String name, String clientType) {
        return getClient(name, clientType, Map.of());
    }

    public Object getClient(String name, String clientType, Map<String, Object> kwargs) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Client name cannot be empty");
        }

        String lookupName = name;
        if (clientType != null) {
            String fullName = fullName(name, clientType);
            if (factories.containsKey(fullName)) {
                lookupName = fullName;
            }
        }

        ClientFactory factory = factories.get(lookupName);
        if (factory == null) {
            List<String> available = new ArrayList<>(factories.keySet());
            String searchKey = clientType != null ? fullName(name, clientType) : name;
            throw new IllegalArgumentException(
                    "Unknown client type: '" + searchKey + "'. Available: " + available);
        }

        try {
            return factory.create(kwargs != null ? kwargs : Map.of());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create client '" + lookupName + "': " + ex.getMessage(), ex);
        }
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

    public boolean isRegistered(String name, String clientType) {
        String fullName = fullName(name, clientType);
        return factories.containsKey(fullName) || clientClasses.containsKey(fullName);
    }

    public boolean isRegistered(String name) {
        return isRegistered(name, "common");
    }

    public List<String> listClients() {
        return new ArrayList<>(factories.keySet());
    }

    private String fullName(String name, String clientType) {
        return clientType != null ? clientType + "_" + name : name;
    }

    private Object instantiateClient(Class<?> clientClass, Map<String, Object> kwargs) throws Exception {
        Constructor<?> mapConstructor = findConstructor(clientClass, Map.class);
        if (mapConstructor != null) {
            return mapConstructor.newInstance(kwargs);
        }

        Constructor<?> emptyConstructor = findConstructor(clientClass);
        if (emptyConstructor != null) {
            Object instance = emptyConstructor.newInstance();
            if (instance instanceof BaseClient baseClient) {
                baseClient.initialize(kwargs);
            }
            return instance;
        }

        throw new IllegalStateException("No usable constructor for " + clientClass.getName());
    }

    private Constructor<?> findConstructor(Class<?> type, Class<?>... parameterTypes) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    private List<String> getClientNames(Class<?> clientClass) {
        Object value = getClassMetadata(
                clientClass,
                new String[]{"getClientName"},
                "__client_name__",
                "CLIENT_NAME",
                "clientName");
        List<String> result = new ArrayList<>();
        if (value instanceof String text) {
            result.add(text);
        } else if (value instanceof String[] array) {
            for (String item : array) {
                result.add(item);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    private String getClientType(Class<?> clientClass) {
        Object value = getClassMetadata(
                clientClass,
                new String[]{"getClientType"},
                "__client_type__",
                "CLIENT_TYPE",
                "clientType");
        return value instanceof String text ? text : null;
    }

    private Object getClassMetadata(Class<?> clientClass, String[] methodNames, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Field field = findField(clientClass, fieldName);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value != null) {
                        return value;
                    }
                } catch (IllegalAccessException ignored) {
                }
            }
        }

        for (String methodName : methodNames) {
            Method method = findMethod(clientClass, methodName);
            if (method != null) {
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(null);
                    if (value != null) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private Method findMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
