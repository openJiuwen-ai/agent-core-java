/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;

import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek Model Client.
 *
 * <p>Mirrors Python's {@code DeepSeekModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/deepseek_model_client.py}.</p>
 */
public class DeepSeekModelClient extends BaseModelClient {

    public static final String __client_name__ = ProviderType.DEEP_SEEK.getValue();
    private static final String OPEN_AI_MODEL_CLIENT_CLASS =
            "com.openjiuwen.core.foundation.llm.model_clients.OpenAIModelClient";

    static {
        registerClientClass(DeepSeekModelClient.class);
    }

    /**
     * Initialize DeepSeek model client.
     *
     * @param modelConfig model request configuration
     * @param modelClientConfig model client connection configuration
     */
    public DeepSeekModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        super(modelConfig, modelClientConfig);
    }

    /**
     * Initialize from registry keyword arguments.
     *
     * @param kwargs Python-style registry kwargs
     */
    public DeepSeekModelClient(Map<String, Object> kwargs) {
        this((ModelRequestConfig) kwargs.get("model_config"),
                (ModelClientConfig) kwargs.get("model_client_config"));
    }

    /**
     * Get client name.
     *
     * @return DeepSeek client name
     */
    @Override
    protected String getClientName() {
        return "DeepSeek client";
    }

    /**
     * Convert messages and add the DeepSeek-required reasoning field for assistant messages.
     *
     * @param messages string, list of messages, or list of maps
     * @return converted message maps
     */
    protected static List<Map<String, Object>> convertMessagesToDict(Object messages) {
        List<Map<String, Object>> newMessages = BaseModelClient.convertMessagesToDict(messages);
        for (Map<String, Object> message : newMessages) {
            if ("assistant".equals(message.get("role")) && !message.containsKey("reasoning_content")) {
                message.put("reasoning_content", "");
            }
        }
        return newMessages;
    }

    @Override
    protected Map<String, Object> buildRequestParams(
            Object messages,
            Object tools,
            Number temperature,
            Number topP,
            String model,
            String stop,
            Integer maxTokens,
            boolean stream,
            Map<String, Object> kwargs) {
        return super.buildRequestParams(
                convertMessagesToDict(messages),
                tools,
                temperature,
                topP,
                model,
                stop,
                maxTokens,
                stream,
                kwargs);
    }

    @Override
    public AssistantMessage invoke(Object messages,
                                   Object tools,
                                   Float temperature,
                                   Float topP,
                                   String model,
                                   Integer maxTokens,
                                   String stop,
                                   BaseOutputParser outputParser,
                                   Float timeout,
                                   Map<String, Object> kwargs) {
        return openAiDelegate().invoke(
                convertMessagesToDict(messages),
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                outputParser,
                timeout,
                kwargs);
    }

    @Override
    public Iterator<AssistantMessageChunk> stream(Object messages,
                                                  Object tools,
                                                  Float temperature,
                                                  Float topP,
                                                  String model,
                                                  Integer maxTokens,
                                                  String stop,
                                                  BaseOutputParser outputParser,
                                                  Float timeout,
                                                  Map<String, Object> kwargs) {
        return openAiDelegate().stream(
                convertMessagesToDict(messages),
                tools,
                temperature,
                topP,
                model,
                maxTokens,
                stop,
                outputParser,
                timeout,
                kwargs);
    }

    @Override
    public ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                 String model,
                                                 String size,
                                                 String negativePrompt,
                                                 int n,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 int seed,
                                                 Map<String, Object> kwargs) {
        return null;
    }

    @Override
    public AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                  String model,
                                                  String voice,
                                                  String languageType,
                                                  Map<String, Object> kwargs) {
        return null;
    }

    @Override
    public VideoGenerationResponse generateVideo(List<UserMessage> messages,
                                                 String imgUrl,
                                                 String audioUrl,
                                                 String model,
                                                 String size,
                                                 String resolution,
                                                 int duration,
                                                 boolean promptExtend,
                                                 boolean watermark,
                                                 String negativePrompt,
                                                 Integer seed,
                                                 Map<String, Object> kwargs) {
        return null;
    }

    private BaseModelClient openAiDelegate() {
        try {
            Class<?> type = Class.forName(OPEN_AI_MODEL_CLIENT_CLASS);
            if (!BaseModelClient.class.isAssignableFrom(type)) {
                throw new IllegalStateException(OPEN_AI_MODEL_CLIENT_CLASS + " is not a BaseModelClient");
            }
            Constructor<?> constructor = type.getConstructor(ModelRequestConfig.class, ModelClientConfig.class);
            return (BaseModelClient) constructor.newInstance(modelConfig, modelClientConfig);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("OpenAIModelClient translation is required by DeepSeekModelClient",
                    exception);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to create OpenAIModelClient delegate for DeepSeekModelClient",
                    exception);
        }
    }
}
