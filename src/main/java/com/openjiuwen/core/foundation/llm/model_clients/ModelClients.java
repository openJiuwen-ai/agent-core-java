/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.clients.Clients;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Package facade for model client creation.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.foundation.llm.model_clients} module in
 * {@code openjiuwen/core/foundation/llm/model_clients/__init__.py}.</p>
 */
public final class ModelClients {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/llm/model_clients/__init__.py";

    public static final List<String> MODULE_SYMBOLS = List.of(
            "BaseModelClient",
            "_builtin_model_client",
            "create_model_client"
    );

    public static final List<String> PUBLIC_EXPORTS = List.of(
            "BaseModelClient",
            "create_model_client"
    );

    public static final List<String> BUILTIN_PROVIDER_NAMES = List.of(
            ProviderType.OPEN_AI.getValue(),
            ProviderType.OPEN_ROUTER.getValue(),
            ProviderType.SILICON_FLOW.getValue(),
            ProviderType.DASH_SCOPE.getValue(),
            ProviderType.INFERENCE_AFFINITY.getValue(),
            ProviderType.DEEP_SEEK.getValue(),
            ProviderType.INTELLI_ROUTER.getValue()
    );

    private static final String PACKAGE_NAME = ModelClients.class.getPackageName();
    private static final Map<String, BuiltinClientFactory<?>> REGISTERED_BUILTINS = new LinkedHashMap<>();
    private static final Map<String, String> BUILTIN_CLASS_NAMES = new LinkedHashMap<>();

    static {
        BUILTIN_CLASS_NAMES.put(ProviderType.OPEN_AI.getValue(), PACKAGE_NAME + ".OpenAIModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.OPEN_ROUTER.getValue(), PACKAGE_NAME + ".OpenAIModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.SILICON_FLOW.getValue(), PACKAGE_NAME + ".SiliconFlowModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.DASH_SCOPE.getValue(), PACKAGE_NAME + ".DashScopeModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.INFERENCE_AFFINITY.getValue(), PACKAGE_NAME + ".InferenceAffinityModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.DEEP_SEEK.getValue(), PACKAGE_NAME + ".DeepSeekModelClient");
        BUILTIN_CLASS_NAMES.put(ProviderType.INTELLI_ROUTER.getValue(), PACKAGE_NAME + ".IntelliRouterModelClient");
    }

    private ModelClients() {
    }

    /**
     * Mirrors Python's lazy built-in model client constructor calls in
     * {@code openjiuwen/core/foundation/llm/model_clients/__init__.py}.
     *
     * @param <T> Java client type returned by the provider implementation
     */
    @FunctionalInterface
    public interface BuiltinClientFactory<T> {
        T create(ModelRequestConfig modelConfig, ModelClientConfig clientConfig);
    }

    public static List<String> moduleSymbols() {
        return MODULE_SYMBOLS;
    }

    public static List<String> publicExports() {
        return PUBLIC_EXPORTS;
    }

    public static List<String> builtinProviderNames() {
        return BUILTIN_PROVIDER_NAMES;
    }

    public static boolean isBuiltinProvider(String provider) {
        return BUILTIN_PROVIDER_NAMES.contains(provider);
    }

    public static synchronized void registerBuiltinProvider(
            ProviderType provider,
            BuiltinClientFactory<?> factory) {
        registerBuiltinProvider(provider == null ? null : provider.getValue(), factory);
    }

    public static synchronized void registerBuiltinProvider(
            String provider,
            BuiltinClientFactory<?> factory) {
        if (!isBuiltinProvider(provider)) {
            throw new IllegalArgumentException("Unsupported built-in model provider: " + provider);
        }
        REGISTERED_BUILTINS.put(provider, Objects.requireNonNull(factory, "factory must not be null"));
    }

    public static synchronized void unregisterBuiltinProvider(String provider) {
        REGISTERED_BUILTINS.remove(provider);
    }

    public static Object builtinModelClient(
            String provider,
            ModelClientConfig clientConfig,
            ModelRequestConfig modelConfig) {
        if (clientConfig == null) {
            return null;
        }
        if (!isBuiltinProvider(provider)) {
            return null;
        }

        BuiltinClientFactory<?> factory;
        synchronized (ModelClients.class) {
            factory = REGISTERED_BUILTINS.get(provider);
        }
        if (factory != null) {
            return factory.create(modelConfig, clientConfig);
        }
        return instantiateBuiltin(provider, modelConfig, clientConfig);
    }

    public static <T> T builtinModelClient(
            String provider,
            ModelClientConfig clientConfig,
            ModelRequestConfig modelConfig,
            Class<T> expectedType) {
        Object client = builtinModelClient(provider, clientConfig, modelConfig);
        return client == null ? null : expectedType.cast(client);
    }

    public static Object createModelClient(
            ModelClientConfig clientConfig,
            ModelRequestConfig modelConfig) {
        if (clientConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config is none"
            );
        }
        if (clientConfig.getClientProvider() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config client_provider is none"
            );
        }
        if (clientConfig.getClientId() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config client_id is none"
            );
        }

        String provider = clientConfig.getClientProvider();
        Object client = builtinModelClient(provider, clientConfig, modelConfig);
        if (client != null) {
            return client;
        }

        try {
            return Clients.getClientRegistry().getClient(provider, "llm", modelClientKwargs(modelConfig, clientConfig));
        } catch (IllegalArgumentException ex) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_PROVIDER_INVALID,
                    "error_msg",
                    "Unsupported client_provider: '" + clientConfig.getClientProvider()
                            + "', Supported types: " + supportedLlmTypes()
            );
        }
    }

    public static <T> T createModelClient(
            ModelClientConfig clientConfig,
            ModelRequestConfig modelConfig,
            Class<T> expectedType) {
        return expectedType.cast(createModelClient(clientConfig, modelConfig));
    }

    static Map<String, Object> modelClientKwargs(
            ModelRequestConfig modelConfig,
            ModelClientConfig clientConfig) {
        Map<String, Object> kwargs = new LinkedHashMap<>();
        kwargs.put("model_config", modelConfig);
        kwargs.put("model_client_config", clientConfig);
        return kwargs;
    }

    private static Object instantiateBuiltin(
            String provider,
            ModelRequestConfig modelConfig,
            ModelClientConfig clientConfig) {
        String className = BUILTIN_CLASS_NAMES.get(provider);
        if (className == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> constructor = type.getConstructor(ModelRequestConfig.class, ModelClientConfig.class);
            return constructor.newInstance(modelConfig, clientConfig);
        } catch (ClassNotFoundException ex) {
            return null;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to instantiate built-in model client for " + provider, ex);
        }
    }

    private static List<String> supportedLlmTypes() {
        return ClientRegistry.getClientRegistry().listClients().stream()
                .filter(name -> name.startsWith("llm_"))
                .map(name -> name.substring("llm_".length()))
                .toList();
    }
}
