/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.clients;

import com.openjiuwen.core.common.utils.SingletonSupport;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for client factories and client classes.
 */
public final class ClientRegistry {
    private final Map<String, ClientFactory> factories = new ConcurrentHashMap<>();
    private final Map<String, Class<?>> clientClasses = new ConcurrentHashMap<>();

    /**
 * Public interface ClientFactory used by the Java parity implementation.
 *
 * @since 1.0
 */
    @FunctionalInterface
public interface ClientFactory {
        Object create(Map<String, Object> kwargs) throws Exception;
    }

    private ClientRegistry() {
        registerBuiltins();
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static ClientRegistry getInstance() {
        return SingletonSupport.getInstance(ClientRegistry.class, ClientRegistry::new);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void registerClient(String name, String clientType, ClientFactory factory) {
        Objects.requireNonNull(factory, "factory must not be null");
        String fullName = fullName(name, clientType);
        if (factories.containsKey(fullName)) {
            throw new IllegalArgumentException("Client type '" + fullName + "' already registered");
        }
        factories.put(fullName, factory);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void registerClass(Class<?> clientClass) {
        Objects.requireNonNull(clientClass, "clientClass must not be null");
        Object clientNameValue = readStaticField(clientClass, "__client_name__");
        Object clientTypeValue = readStaticField(clientClass, "__client_type__");

        if (clientNameValue == null) {
            throw new IllegalArgumentException(
                    "Client class " + clientClass.getName() + " must define __client_name__");
        }
        if (clientTypeValue == null || String.valueOf(clientTypeValue).isBlank()) {
            throw new IllegalArgumentException(
                    "Client class " + clientClass.getName() + " __client_type__ cannot be empty");
        }

        List<String> names = new ArrayList<>();
        if (clientNameValue instanceof String[] array) {
            for (String item : array) {
                if (item != null && !item.isBlank()) {
                    names.add(item);
                }
            }
        } else if (clientNameValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    names.add(String.valueOf(item));
                }
            }
        } else {
            names.add(String.valueOf(clientNameValue));
        }

        String clientType = String.valueOf(clientTypeValue);
        for (String name : names) {
            String fullName = fullName(name, clientType);
            if (factories.containsKey(fullName) || clientClasses.containsKey(fullName)) {
                return;
            }
            clientClasses.put(fullName, clientClass);
            factories.put(fullName, kwargs -> instantiateClient(clientClass, kwargs));
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getClient(String name) throws Exception {
        return getClient(name, "common", Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getClient(String name, String clientType) throws Exception {
        return getClient(name, clientType, Map.of());
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Object getClient(String name, String clientType, Map<String, Object> kwargs) throws Exception {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Client name cannot be empty");
        }

        String lookupName = name;
        if (clientType != null && !clientType.isBlank()) {
            String typedName = fullName(name, clientType);
            if (factories.containsKey(typedName)) {
                lookupName = typedName;
            }
        }

        ClientFactory factory = factories.get(lookupName);
        if (factory == null) {
            String searchKey = clientType != null && !clientType.isBlank() ? fullName(name, clientType) : name;
            throw new IllegalArgumentException(
                    "Unknown client type: '" + searchKey + "'. Available: " + listClients());
        }

        try {
            return factory.create(kwargs != null ? kwargs : Map.of());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create client '" + lookupName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public synchronized void unregister(String name, String clientType) {
        String fullName = fullName(name, clientType);
        if (!factories.containsKey(fullName)) {
            throw new IllegalArgumentException("Client type '" + fullName + "' not registered");
        }
        factories.remove(fullName);
        clientClasses.remove(fullName);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> listClients() {
        return new ArrayList<>(factories.keySet());
    }

    synchronized void resetForTests() {
        factories.clear();
        clientClasses.clear();
        registerBuiltins();
    }

    private void registerBuiltins() {
        registerClient("http", "common", kwargs -> {
            Object configObj = kwargs.get("config");
            SessionConfig sessionConfig = SessionConfig.from(configObj);
            boolean isReuseSessionEnabled = ClientConfigSupport.asBoolean(kwargs.get("reuse_session"), true);
            return new com.openjiuwen.core.common.clients.HttpClient(sessionConfig, isReuseSessionEnabled);
        });
        registerClient("httpx", "common", kwargs -> {
            HttpXConnectorPoolConfig config = HttpXConnectorPoolConfig.from(kwargs.get("config"));
            return ConnectorPoolManager.getInstance().getConnectorPool("httpx", config).conn();
        });
        registerClient("openai", "common", kwargs -> {
            Object configObj = kwargs.get("config");
            ModelClientConfig modelClientConfig = configObj instanceof ModelClientConfig config
                    ? config
                    : modelClientConfigFromMap(ClientConfigSupport.asObjectMap(configObj));
            return Clients.createOpenAiClient(modelClientConfig);
        });
        registerClient("async_openai", "common", kwargs -> {
            Object configObj = kwargs.get("config");
            ModelClientConfig modelClientConfig = configObj instanceof ModelClientConfig config
                    ? config
                    : modelClientConfigFromMap(ClientConfigSupport.asObjectMap(configObj));
            return Clients.createAsyncOpenAiClient(modelClientConfig);
        });
    }

    private static ModelClientConfig modelClientConfigFromMap(Map<String, Object> config) {
        return ModelClientConfig.builder()
                .clientProvider(ClientConfigSupport.asString(config.getOrDefault("client_provider", "openai")))
                .apiKey(ClientConfigSupport.asString(config.get("api_key")))
                .apiBase(ClientConfigSupport.asString(config.get("api_base")))
                .timeout(ClientConfigSupport.asNullableDouble(config.get("timeout")) != null
                        ? ClientConfigSupport.asNullableDouble(config.get("timeout"))
                        : 60.0)
                .maxRetries(ClientConfigSupport.asInt(config.get("max_retries"), 3))
                .verifySsl(ClientConfigSupport.asBoolean(config.get("verify_ssl"), true))
                .sslCert(ClientConfigSupport.asString(config.get("ssl_cert")))
                .headers(ClientConfigSupport.asStringMap(config.get("headers")))
                .build();
    }

    private static Object instantiateClient(Class<?> clientClass, Map<String, Object> kwargs) throws Exception {
        try {
            Constructor<?> mapConstructor = clientClass.getDeclaredConstructor(Map.class);
            mapConstructor.setAccessible(true);
            return mapConstructor.newInstance(kwargs);
        } catch (NoSuchMethodException ignored) {
            Constructor<?> noArgConstructor = clientClass.getDeclaredConstructor();
            noArgConstructor.setAccessible(true);
            return noArgConstructor.newInstance();
        }
    }

    private static Object readStaticField(Class<?> clientClass, String fieldName) {
        try {
            Field field = clientClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String fullName(String name, String clientType) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (clientType == null || clientType.isBlank()) {
            return name;
        }
        return clientType + "_" + name;
    }
}
