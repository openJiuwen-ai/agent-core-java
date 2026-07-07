/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.clients.ClientRegistry;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.UserConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * LLM Model Client abstract base class.
 *
 * <p>Mirrors Python's {@code BaseModelClient} in
 * {@code openjiuwen/core/foundation/llm/model_clients/base_model_client.py}.</p>
 */
public abstract class BaseModelClient implements Model.ModelClient {

    protected static final String __client_name__ = null;
    public static final String __client_type__ = "llm";
    public static final String CLIENT_TYPE = __client_type__;

    private static final Set<String> INTERNAL_REQUEST_PARAMS = Set.of("parser", "output_parser");
    private static final Set<String> DECIMAL_REQUEST_PARAMS = Set.of("temperature", "top_p");

    protected final ModelRequestConfig modelConfig;
    protected final ModelClientConfig modelClientConfig;

    /**
     * Initialize Model Client.
     *
     * @param modelConfig model parameter configuration
     * @param modelClientConfig client connection configuration
     */
    protected BaseModelClient(ModelRequestConfig modelConfig, ModelClientConfig modelClientConfig) {
        this.modelConfig = modelConfig;
        this.modelClientConfig = modelClientConfig;
        validateConfig();
    }

    /**
     * Java equivalent of Python subclass registration. Java has no
     * {@code __init_subclass__} hook, so subclasses call this from static
     * initializers when they need registry parity.
     *
     * @param clientClass model client class
     */
    protected static void registerClientClass(Class<? extends BaseModelClient> clientClass) {
        ClientRegistry.getClientRegistry().registerClass(clientClass);
    }

    /**
     * Extract cost information from response or chunk usage objects.
     *
     * @param obj response or usage object
     * @return input, output, and total costs
     */
    protected static CostInfo extractCostInfo(Object obj) {
        double inputCost = 0.0D;
        double outputCost = 0.0D;
        double totalCost = 0.0D;
        Object costInfo = firstTruthy(attribute(obj, "cost"), attribute(obj, "usage_cost", "usageCost"));
        Object costDetails = attribute(obj, "cost_details", "costDetails");

        if (isPythonTruthy(costInfo)) {
            if (costInfo instanceof Number number) {
                totalCost = number.doubleValue();
            } else {
                inputCost = doubleValue(firstTruthy(
                        attribute(costInfo, "input_cost", "inputCost"),
                        attribute(costInfo, "prompt_cost", "promptCost")));
                outputCost = doubleValue(firstTruthy(
                        attribute(costInfo, "output_cost", "outputCost"),
                        attribute(costInfo, "completion_cost", "completionCost")));
                totalCost = doubleValue(attribute(costInfo, "total_cost", "totalCost"));
                if (totalCost == 0.0D) {
                    totalCost = inputCost + outputCost;
                }
            }
        }

        if (isPythonTruthy(costDetails) && inputCost == 0.0D && outputCost == 0.0D) {
            inputCost = doubleValue(attribute(
                    costDetails,
                    "upstream_inference_prompt_cost",
                    "upstreamInferencePromptCost"));
            outputCost = doubleValue(attribute(
                    costDetails,
                    "upstream_inference_completions_cost",
                    "upstreamInferenceCompletionsCost"));
            double detailTotal = doubleValue(attribute(
                    costDetails,
                    "upstream_inference_cost",
                    "upstreamInferenceCost"));
            if (totalCost == 0.0D) {
                totalCost = detailTotal != 0.0D ? detailTotal : inputCost + outputCost;
            }
        }

        return new CostInfo(inputCost, outputCost, totalCost);
    }

    /**
     * Get client name for error messages. Subclasses can override.
     *
     * @return simple class name by default
     */
    protected String getClientName() {
        return getClass().getSimpleName();
    }

    /**
     * Validate configuration parameters. Subclasses can override.
     */
    protected void validateConfig() {
        String clientName = getClientName();
        if (modelClientConfig == null || isBlank(modelClientConfig.getApiKey())) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "error_msg",
                    "model client config api_key is required for " + clientName + ".");
        }
        if (isBlank(modelClientConfig.getApiBase())) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_SERVICE_CONFIG_ERROR,
                    "rror_msg",
                    "model client config api_base is required for " + clientName + ".");
        }
    }

    /**
     * Convert messages to OpenAI-compatible maps.
     *
     * @param messages string, list of messages, or list of maps
     * @return converted message maps
     */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> convertMessagesToDict(Object messages) {
        if (!isPythonTruthy(messages)) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_INVOKE_PARAM_ERROR,
                    "error_msg",
                    "The message sent to the llm cannot be empty.");
        }
        if (messages instanceof String text) {
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", text);
            return List.of(message);
        }
        if (!(messages instanceof List<?> list)) {
            throw new ClassCastException("Unsupported message type: " + messages.getClass().getName());
        }
        if (list.stream().allMatch(Map.class::isInstance)) {
            return normalizeMapList((List<Map<?, ?>>) messages);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            BaseMessage msg = (BaseMessage) item;
            Map<String, Object> msgDict = new LinkedHashMap<>();
            msgDict.put("role", msg.getRole());
            msgDict.put("content", msg.getContent());

            if (msg instanceof AssistantMessage assistantMessage
                    && assistantMessage.getToolCalls() != null
                    && !assistantMessage.getToolCalls().isEmpty()) {
                List<Map<String, Object>> toolCallsList = new ArrayList<>();
                for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", toolCall.getName());
                    function.put("arguments", toolCall.getArguments());

                    Map<String, Object> toolCallMap = new LinkedHashMap<>();
                    toolCallMap.put("id", toolCall.getId());
                    toolCallMap.put("type", toolCall.getType());
                    toolCallMap.put("function", function);
                    toolCallsList.add(toolCallMap);
                }
                msgDict.put("tool_calls", toolCallsList);
                if (isPythonTruthy(assistantMessage.getReasoningContent())) {
                    msgDict.put("reasoning_content", assistantMessage.getReasoningContent());
                }
            }

            if (msg instanceof ToolMessage toolMessage) {
                msgDict.put("tool_call_id", toolMessage.getToolCallId());
            }
            result.add(msgDict);
        }
        return result;
    }

    /**
     * Convert tool descriptors to OpenAI-compatible maps.
     *
     * @param tools list of ToolInfo or list of maps
     * @return converted tool maps, or null when tools is empty
     */
    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> convertToolsToDict(Object tools) {
        if (!isPythonTruthy(tools)) {
            return null;
        }
        if (!(tools instanceof List<?> list)) {
            return null;
        }
        if (list.stream().allMatch(Map.class::isInstance)) {
            return normalizeMapList((List<Map<?, ?>>) tools);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            ToolInfo tool = (ToolInfo) item;
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", modelDumpIfPresent(tool.getParameters()));

            Map<String, Object> toolDict = new LinkedHashMap<>();
            toolDict.put("type", tool.getType());
            toolDict.put("function", function);
            result.add(toolDict);
        }
        return result;
    }

    /**
     * Build OpenAI-compatible chat completion request parameters.
     *
     * @param messages input messages
     * @param tools available tools
     * @param temperature temperature override
     * @param topP top-p override
     * @param model model override
     * @param stop stop override
     * @param maxTokens max tokens override
     * @param stream whether streaming is requested
     * @param kwargs extra request keyword arguments
     * @return request parameter map
     */
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
        if (model == null && (modelConfig == null || modelConfig.getModelName() == null)) {
            throw ErrorHelper.buildError(
                    StatusCode.MODEL_CONFIG_ERROR,
                    "error_msg",
                    "The model cannot be None.");
        }

        List<Map<String, Object>> messagesDict = convertMessagesToDict(messages);
        String resolvedModel = isPythonTruthy(model)
                ? model
                : modelConfig == null ? null : modelConfig.getModelName();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("model", resolvedModel);
        params.put("messages", messagesDict);
        params.put("stream", stream);

        Number finalTemperature = normalizeDecimalNumber(temperature != null ? temperature : modelConfig.getTemperature());
        if (finalTemperature != null) {
            params.put("temperature", finalTemperature);
        }

        Number finalTopP = normalizeDecimalNumber(topP != null ? topP : modelConfig.getTopP());
        if (finalTopP != null) {
            params.put("top_p", finalTopP);
        }

        Integer finalMaxTokens = maxTokens != null ? maxTokens : modelConfig.getMaxTokens();
        if (finalMaxTokens != null) {
            params.put("max_tokens", finalMaxTokens);
        }

        if (modelConfig.getUser() != null) {
            params.put("user", modelConfig.getUser());
        }
        if (modelConfig.getSeed() != null) {
            params.put("seed", modelConfig.getSeed());
        }

        String finalStop = stop != null ? stop : modelConfig.getStop();
        if (finalStop != null) {
            params.put("stop", finalStop);
        }

        List<Map<String, Object>> toolsDict = convertToolsToDict(tools);
        if (toolsDict != null && !toolsDict.isEmpty()) {
            params.put("tools", toolsDict);
            params.put("tool_choice", "auto");
        }

        if (modelConfig.getExtraFields() != null) {
            for (Map.Entry<String, Object> entry : modelConfig.getExtraFields().entrySet()) {
                if (entry.getValue() != null) {
                    params.put(entry.getKey(), normalizeDecimalRequestParam(entry.getKey(), entry.getValue()));
                }
            }
        }

        if (kwargs != null) {
            for (Map.Entry<String, Object> entry : kwargs.entrySet()) {
                if (!INTERNAL_REQUEST_PARAMS.contains(entry.getKey())) {
                    params.put(entry.getKey(), normalizeDecimalRequestParam(entry.getKey(), entry.getValue()));
                }
            }
        }

        logRequestParams(resolvedModel, messagesDict, toolsDict, finalTemperature, finalTopP, finalMaxTokens,
                finalStop, stream, modelConfig.getExtraFields());
        return params;
    }

    private void logRequestParams(
            String model,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools,
            Number temperature,
            Number topP,
            Integer maxTokens,
            String stop,
            boolean stream,
            Map<String, Object> extraParams) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", getClientName());
        metadata.put("model_name", model);
        metadata.put("model_provider", modelClientConfig.getClientProvider());
        metadata.put("temperature", temperature);
        metadata.put("top_p", topP);
        metadata.put("max_tokens", maxTokens);
        metadata.put("is_stream", stream);
        metadata.put("extra_params", extraParams);
        if (UserConfig.isSensitive()) {
            metadata.put("stop", stop);
        } else {
            metadata.put("messages", messages);
            metadata.put("tools", tools);
        }
        Loggers.LLM.info("Before request chat model, LLM request params ready. {}", metadata);
    }

    public abstract AssistantMessage invoke(Object messages,
                                            Object tools,
                                            Float temperature,
                                            Float topP,
                                            String model,
                                            Integer maxTokens,
                                            String stop,
                                            BaseOutputParser outputParser,
                                            Float timeout,
                                            Map<String, Object> kwargs) throws Exception;

    @Override
    public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
        ModelInvokeOptions resolvedOptions = options == null ? ModelInvokeOptions.builder().build() : options;
        try {
            return CompletableFuture.completedFuture(invoke(
                    messages,
                    resolvedOptions.getTools(),
                    resolvedOptions.getTemperature(),
                    resolvedOptions.getTopP(),
                    resolvedOptions.getModel(),
                    resolvedOptions.getMaxTokens(),
                    resolvedOptions.getStop(),
                    resolvedOptions.getOutputParser(),
                    resolvedOptions.getTimeout(),
                    resolvedOptions.getExtraFields()
            ));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public abstract Iterator<AssistantMessageChunk> stream(Object messages,
                                                           Object tools,
                                                           Float temperature,
                                                           Float topP,
                                                           String model,
                                                           Integer maxTokens,
                                                           String stop,
                                                           BaseOutputParser outputParser,
                                                           Float timeout,
                                                           Map<String, Object> kwargs) throws Exception;

    @Override
    public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
        ModelInvokeOptions resolvedOptions = options == null ? ModelInvokeOptions.builder().build() : options;
        try {
            return stream(
                    messages,
                    resolvedOptions.getTools(),
                    resolvedOptions.getTemperature(),
                    resolvedOptions.getTopP(),
                    resolvedOptions.getModel(),
                    resolvedOptions.getMaxTokens(),
                    resolvedOptions.getStop(),
                    resolvedOptions.getOutputParser(),
                    resolvedOptions.getTimeout(),
                    resolvedOptions.getExtraFields()
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public abstract ImageGenerationResponse generateImage(List<UserMessage> messages,
                                                          String model,
                                                          String size,
                                                          String negativePrompt,
                                                          int n,
                                                          boolean promptExtend,
                                                          boolean watermark,
                                                          int seed,
                                                          Map<String, Object> kwargs) throws Exception;

    @Override
    public CompletionStage<ImageGenerationResponse> generateImage(List<UserMessage> messages,
                                                                  Model.ImageGenerationOptions options) {
        try {
            return CompletableFuture.completedFuture(generateImage(
                    messages,
                    options.model(),
                    options.size(),
                    options.negativePrompt(),
                    options.n(),
                    options.promptExtend(),
                    options.watermark(),
                    options.seed(),
                    options.extraFields()
            ));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public abstract AudioGenerationResponse generateSpeech(List<UserMessage> messages,
                                                           String model,
                                                           String voice,
                                                           String languageType,
                                                           Map<String, Object> kwargs) throws Exception;

    @Override
    public CompletionStage<AudioGenerationResponse> generateSpeech(List<UserMessage> messages,
                                                                   Model.SpeechGenerationOptions options) {
        try {
            return CompletableFuture.completedFuture(generateSpeech(
                    messages,
                    options.model(),
                    options.voice(),
                    options.languageType(),
                    options.extraFields()
            ));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public abstract VideoGenerationResponse generateVideo(List<UserMessage> messages,
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
                                                          Map<String, Object> kwargs) throws Exception;

    @Override
    public CompletionStage<VideoGenerationResponse> generateVideo(List<UserMessage> messages,
                                                                  Model.VideoGenerationOptions options) {
        try {
            return CompletableFuture.completedFuture(generateVideo(
                    messages,
                    options.imgUrl(),
                    options.audioUrl(),
                    options.model(),
                    options.size(),
                    options.resolution(),
                    options.duration(),
                    options.promptExtend(),
                    options.watermark(),
                    options.negativePrompt(),
                    options.seed(),
                    options.extraFields()
            ));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public Boolean release(String sessionId,
                           Object messages,
                           int messagesReleasedIndex,
                           Object tools,
                           Integer toolsReleasedIndex,
                           String model) throws Exception {
        return false;
    }

    @Override
    public CompletionStage<Boolean> release(String sessionId,
                                            List<BaseMessage> messages,
                                            Integer messagesReleasedIndex,
                                            List<ToolInfo> tools,
                                            Integer toolsReleasedIndex) {
        try {
            return CompletableFuture.completedFuture(Boolean.TRUE.equals(release(
                    sessionId,
                    messages,
                    messagesReleasedIndex == null ? 0 : messagesReleasedIndex,
                    tools,
                    toolsReleasedIndex,
                    null
            )));
        } catch (Exception exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public boolean supportsKvCacheRelease() {
        return false;
    }

    private static Object modelDumpIfPresent(Object value) {
        if (value == null) {
            return null;
        }
        for (String methodName : List.of("modelDump", "model_dump")) {
            Method method = findMethod(value.getClass(), methodName);
            if (method != null && method.getParameterCount() == 0) {
                try {
                    method.setAccessible(true);
                    return method.invoke(value);
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        return value;
    }

    private static List<Map<String, Object>> normalizeMapList(List<Map<?, ?>> rawMaps) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> rawMap : rawMaps) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            result.add(normalized);
        }
        return result;
    }

    private static Object firstTruthy(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (isPythonTruthy(value)) {
                return value;
            }
        }
        return null;
    }

    private static Object attribute(Object target, String... names) {
        if (target == null || names == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            for (String name : names) {
                if (map.containsKey(name)) {
                    return map.get(name);
                }
            }
            return null;
        }

        for (String name : names) {
            Method method = findMethod(target.getClass(), accessorName("get", name));
            if (method == null) {
                method = findMethod(target.getClass(), accessorName("is", name));
            }
            if (method != null && method.getParameterCount() == 0) {
                try {
                    method.setAccessible(true);
                    return method.invoke(target);
                } catch (ReflectiveOperationException ignored) {
                }
            }

            Field field = findField(target.getClass(), name);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
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

    private static Field findField(Class<?> type, String name) {
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

    private static String accessorName(String prefix, String name) {
        String camel = snakeToCamel(name);
        return prefix + Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    private static String snakeToCamel(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            builder.append(upperNext ? Character.toUpperCase(current) : current);
            upperNext = false;
        }
        return builder.toString();
    }

    private static boolean isPythonTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0D;
        }
        if (value instanceof CharSequence sequence) {
            return !sequence.isEmpty();
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isEmpty();
    }

    private static double doubleValue(Object value) {
        Object resolved = firstTruthy(value);
        if (resolved == null) {
            return 0.0D;
        }
        if (resolved instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(resolved));
    }

    private static Object normalizeDecimalRequestParam(String key, Object value) {
        if (!DECIMAL_REQUEST_PARAMS.contains(key) || !(value instanceof Number number)) {
            return value;
        }
        return normalizeDecimalNumber(number);
    }

    private static Number normalizeDecimalNumber(Number value) {
        if (value instanceof Float floatValue) {
            return Double.valueOf(Float.toString(floatValue));
        }
        if (value instanceof Double doubleValue) {
            float narrowed = doubleValue.floatValue();
            if (Double.compare((double) narrowed, doubleValue) == 0) {
                return Double.valueOf(Float.toString(narrowed));
            }
        }
        return value;
    }

    /**
     * Mirrors Python's tuple return from {@code BaseModelClient._extract_cost_info} in
     * {@code openjiuwen/core/foundation/llm/model_clients/base_model_client.py}.
     *
     * @param inputCost prompt/input cost
     * @param outputCost completion/output cost
     * @param totalCost total cost
     */
    public record CostInfo(double inputCost, double outputCost, double totalCost) {
    }
}
