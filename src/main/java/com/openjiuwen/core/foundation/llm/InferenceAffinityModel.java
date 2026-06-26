/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.context.KVCacheManager;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * InferenceAffinity (vLLM) model unified invocation entry point.
 *
 * <p>Mirrors Python's {@code InferenceAffinityModel} in
 * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
 */
public class InferenceAffinityModel implements KVCacheManager.ReleaseCapableModel {

    private static final String CLIENT_CLASS_NAME =
            "com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClient";

    private final ModelRequestConfig modelConfig;
    private final ModelClientConfig modelClientConfig;
    private final InferenceAffinityClient client;

    public InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig) {
        this(modelClientConfig, modelConfig, createDefaultClient(modelConfig, modelClientConfig));
    }

    InferenceAffinityModel(ModelClientConfig modelClientConfig, ModelRequestConfig modelConfig,
                           InferenceAffinityClient client) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config is none"
            );
        }
        if (client == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client is none"
            );
        }
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        this.client = client;
    }

    public ModelRequestConfig getModelConfig() {
        return modelConfig;
    }

    public ModelClientConfig getModelClientConfig() {
        return modelClientConfig;
    }

    public CompletionStage<AssistantMessage> invoke(Object messages) {
        return invoke(messages, null, null, null, null, null, null, null, null, false, null);
    }

    public CompletionStage<AssistantMessage> invoke(Object messages,
                                                    List<?> tools,
                                                    Float temperature,
                                                    Float topP,
                                                    Integer maxTokens,
                                                    String stop,
                                                    String model,
                                                    BaseOutputParser outputParser,
                                                    String sessionId,
                                                    boolean enableCacheSharing,
                                                    Map<String, Object> kwargs) {
        return client.invoke(
                messages,
                tools,
                temperature,
                topP,
                maxTokens,
                stop,
                model,
                outputParser,
                sessionId,
                enableCacheSharing,
                normalizeKwargs(sessionId, enableCacheSharing, kwargs)
        );
    }

    public Iterator<AssistantMessageChunk> stream(Object messages) {
        return stream(messages, null, null, null, null, null, null, null, null, false, null);
    }

    public Iterator<AssistantMessageChunk> stream(Object messages,
                                                  List<?> tools,
                                                  Float temperature,
                                                  Float topP,
                                                  Integer maxTokens,
                                                  String stop,
                                                  String model,
                                                  BaseOutputParser outputParser,
                                                  String sessionId,
                                                  boolean enableCacheSharing,
                                                  Map<String, Object> kwargs) {
        return client.stream(
                messages,
                tools,
                temperature,
                topP,
                maxTokens,
                stop,
                model,
                outputParser,
                sessionId,
                enableCacheSharing,
                normalizeKwargs(sessionId, enableCacheSharing, kwargs)
        );
    }

    @Override
    public CompletionStage<Boolean> release(String sessionId,
                                            List<BaseMessage> messages,
                                            Integer messagesReleasedIndex,
                                            List<ToolInfo> tools,
                                            Integer toolsReleasedIndex) {
        return release(sessionId, messages, messagesReleasedIndex, tools, toolsReleasedIndex, null);
    }

    public CompletionStage<Boolean> release(String sessionId,
                                            List<?> messages,
                                            Integer messagesReleasedIndex,
                                            List<?> tools,
                                            Integer toolsReleasedIndex,
                                            String model) {
        int releasedIndex = messagesReleasedIndex == null ? 0 : messagesReleasedIndex;
        return client.release(sessionId, messages, releasedIndex, tools, toolsReleasedIndex, model);
    }

    public static boolean supportsKvCacheRelease() {
        return true;
    }

    public static Map<String, Object> buildKvCacheInvokeKwargs(Object session, boolean enableKvCacheRelease) {
        Map<String, Object> extra = new LinkedHashMap<>();
        Object sessionId = resolveSessionId(session);
        if (sessionId != null) {
            extra.put("session_id", sessionId);
        }
        if (enableKvCacheRelease) {
            extra.put("enable_cache_sharing", true);
        }
        return extra;
    }

    private static Map<String, Object> normalizeKwargs(String sessionId, boolean enableCacheSharing,
                                                       Map<String, Object> kwargs) {
        Map<String, Object> options = kwargs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(kwargs);
        if (sessionId != null) {
            options.put("session_id", sessionId);
        }
        options.put("enable_cache_sharing", enableCacheSharing);
        return options;
    }

    private static Object resolveSessionId(Object session) {
        if (session == null) {
            return null;
        }
        for (String methodName : List.of("getSessionId", "get_session_id")) {
            try {
                Method method = session.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(session);
            } catch (NoSuchMethodException ignored) {
                // Try the Python-style or Java-style alternative.
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Failed to read session id", e);
            }
        }
        return null;
    }

    private static InferenceAffinityClient createDefaultClient(ModelRequestConfig modelConfig,
                                                              ModelClientConfig modelClientConfig) {
        if (modelClientConfig == null) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config is none"
            );
        }
        try {
            Class<?> clientClass = Class.forName(CLIENT_CLASS_NAME);
            Constructor<?> constructor = clientClass.getConstructor(ModelRequestConfig.class, ModelClientConfig.class);
            Object target = constructor.newInstance(modelConfig, modelClientConfig);
            return new ReflectiveInferenceAffinityClient(target);
        } catch (ClassNotFoundException e) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "InferenceAffinityModelClient translation is not available"
            );
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "failed to create InferenceAffinityModelClient: " + e.getMessage()
            );
        }
    }

    /**
     * Narrow adapter for the InferenceAffinity client dependency.
     *
     * <p>Mirrors Python's {@code InferenceAffinityModel._client} collaborator in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    public interface InferenceAffinityClient {
        CompletionStage<AssistantMessage> invoke(Object messages,
                                                 List<?> tools,
                                                 Float temperature,
                                                 Float topP,
                                                 Integer maxTokens,
                                                 String stop,
                                                 String model,
                                                 BaseOutputParser outputParser,
                                                 String sessionId,
                                                 boolean enableCacheSharing,
                                                 Map<String, Object> kwargs);

        Iterator<AssistantMessageChunk> stream(Object messages,
                                               List<?> tools,
                                               Float temperature,
                                               Float topP,
                                               Integer maxTokens,
                                               String stop,
                                               String model,
                                               BaseOutputParser outputParser,
                                               String sessionId,
                                               boolean enableCacheSharing,
                                               Map<String, Object> kwargs);

        CompletionStage<Boolean> release(String sessionId,
                                         Object messages,
                                         int messagesReleasedIndex,
                                         Object tools,
                                         Integer toolsReleasedIndex,
                                         String model);
    }

    /**
     * Reflection-backed adapter for the separately translated model client.
     *
     * <p>Mirrors Python's {@code InferenceAffinityModelClient} dependency used by
     * {@code InferenceAffinityModel} in
     * {@code openjiuwen/core/foundation/llm/inference_affinity_model.py}.</p>
     */
    private static final class ReflectiveInferenceAffinityClient implements InferenceAffinityClient {

        private final Object target;
        private final Method invokeMethod;
        private final Method streamMethod;
        private final Method releaseMethod;

        private ReflectiveInferenceAffinityClient(Object target) {
            this.target = target;
            this.invokeMethod = findMethod(target.getClass(), "invoke", 10);
            this.streamMethod = findMethod(target.getClass(), "stream", 10);
            this.releaseMethod = findMethod(target.getClass(), "release", 6);
        }

        @Override
        public CompletionStage<AssistantMessage> invoke(Object messages,
                                                        List<?> tools,
                                                        Float temperature,
                                                        Float topP,
                                                        Integer maxTokens,
                                                        String stop,
                                                        String model,
                                                        BaseOutputParser outputParser,
                                                        String sessionId,
                                                        boolean enableCacheSharing,
                                                        Map<String, Object> kwargs) {
            try {
                Object result = invokeMethod.invoke(target, messages, tools, temperature, topP, model, maxTokens, stop,
                        outputParser, null, kwargs);
                return toStage(result, AssistantMessage.class);
            } catch (IllegalAccessException e) {
                return failedStage(e);
            } catch (InvocationTargetException e) {
                return failedStage(e.getTargetException());
            }
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(Object messages,
                                                      List<?> tools,
                                                      Float temperature,
                                                      Float topP,
                                                      Integer maxTokens,
                                                      String stop,
                                                      String model,
                                                      BaseOutputParser outputParser,
                                                      String sessionId,
                                                      boolean enableCacheSharing,
                                                      Map<String, Object> kwargs) {
            try {
                Object result = streamMethod.invoke(target, messages, tools, temperature, topP, model, maxTokens, stop,
                        outputParser, null, kwargs);
                if (result instanceof CompletionStage<?> stage) {
                    result = stage.toCompletableFuture().join();
                }
                @SuppressWarnings("unchecked")
                Iterator<AssistantMessageChunk> chunks = (Iterator<AssistantMessageChunk>) result;
                return chunks;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            } catch (InvocationTargetException e) {
                throw new IllegalStateException(e.getTargetException());
            }
        }

        @Override
        public CompletionStage<Boolean> release(String sessionId,
                                                Object messages,
                                                int messagesReleasedIndex,
                                                Object tools,
                                                Integer toolsReleasedIndex,
                                                String model) {
            try {
                Object result = releaseMethod.invoke(target, sessionId, messages, messagesReleasedIndex, tools,
                        toolsReleasedIndex, model);
                return toStage(result, Boolean.class);
            } catch (IllegalAccessException e) {
                return failedStage(e);
            } catch (InvocationTargetException e) {
                return failedStage(e.getTargetException());
            }
        }

        private static Method findMethod(Class<?> type, String name, int parameterCount) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
                    return method;
                }
            }
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "InferenceAffinityModelClient method not found: " + name
            );
        }

        private static <T> CompletionStage<T> toStage(Object result, Class<T> expectedType) {
            if (result instanceof CompletionStage<?> stage) {
                return stage.thenApply(expectedType::cast);
            }
            return CompletableFuture.completedFuture(expectedType.cast(result));
        }

        private static <T> CompletionStage<T> failedStage(Throwable throwable) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(throwable);
            return future;
        }
    }
}
