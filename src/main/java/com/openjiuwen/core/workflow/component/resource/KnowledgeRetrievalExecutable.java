/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.workflow.component.resource;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.context_engine.ModelContext;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.output_parsers.BaseOutputParser;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.AudioGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ImageGenerationResponse;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.llm.schema.VideoGenerationResponse;
import com.openjiuwen.core.foundation.store.EmbeddingConfig;
import com.openjiuwen.core.retrieval.GraphKnowledgeBase;
import com.openjiuwen.core.retrieval.KnowledgeBase;
import com.openjiuwen.core.retrieval.SimpleKnowledgeBase;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.MultiKBRetrievalResult;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.embedding.OpenAIEmbedding;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import com.openjiuwen.core.retrieval.vector_store.VectorStoreFactory;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.workflow.ComponentExecutable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Executable for the Knowledge Retrieval workflow component.
 *
 * <p>Mirrors Python's {@code KnowledgeRetrievalExecutable} in
 * {@code openjiuwen/core/workflow/components/resource/knowledge_retrieval_comp.py}.</p>
 */
public class KnowledgeRetrievalExecutable extends ComponentExecutable<Object, Object> {

    private final KnowledgeRetrievalCompConfig config;
    private List<KnowledgeBase> knowledgeBases = new ArrayList<>();
    private BaseModelClient llmClient;
    private boolean initialized;
    private BaseSession session;

    public KnowledgeRetrievalExecutable(KnowledgeRetrievalCompConfig config) {
        this.config = config;
    }

    @Override
    public Object invoke(Object inputs, BaseSession session, ModelContext context) {
        this.session = session;
        initializeIfNeeded();

        KnowledgeRetrievalInput knowledgeRetrievalInput = validateInputs(inputs);
        String query = knowledgeRetrievalInput.getQuery();
        if (query == null || query.strip().isEmpty()) {
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR,
                    "error_msg",
                    "Query must be a non-empty string"
            );
        }

        RetrievalConfig retrievalConfig = config.getRetrievalConfig();
        List<MultiKBRetrievalResult> retrievalResults;
        try {
            retrievalResults = SimpleKnowledgeBase.retrieveMultiKbWithSourceAsync(
                            knowledgeBases,
                            query,
                            retrievalConfig,
                            null
                    )
                    .toCompletableFuture()
                    .join();
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED,
                    null,
                    null,
                    cause,
                    Map.of("error_msg", "Knowledge retrieval retrieve call failed")
            );
        }

        return formatOutput(retrievalResults);
    }

    @SuppressWarnings("unchecked")
    private KnowledgeRetrievalInput validateInputs(Object inputs) {
        if (inputs instanceof Map<?, ?> rawMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return KnowledgeRetrievalInput.fromMap(normalized);
        }
        throw ErrorHelper.buildError(
                StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INPUT_PARAM_ERROR,
                "error_msg",
                "inputs must be a map containing 'query'"
        );
    }

    private synchronized void initializeIfNeeded() {
        if (initialized) {
            return;
        }
        try {
            RetrievalConfig retrievalConfig = config.getRetrievalConfig();
            llmClient = retrievalConfig != null && retrievalConfig.isAgentic() ? createLlmClient() : null;
            knowledgeBases = createKnowledgeBases();
            initialized = true;
        } catch (RuntimeException exception) {
            Throwable cause = unwrap(exception);
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_INVOKE_CALL_FAILED,
                    null,
                    null,
                    cause,
                    Map.of("error_msg", "Failed to initialise knowledge retrieval component")
            );
        }
    }

    private List<KnowledgeBase> createKnowledgeBases() {
        boolean useGraph = Boolean.TRUE.equals(config.getRetrievalConfig().getUseGraph());
        List<KnowledgeBase> kbInstances = new ArrayList<>();
        for (ComponentKBConfig componentKbConfig : config.getComponentKbConfigs()) {
            VectorStore vectorStore = VectorStoreFactory.createVectorStore(
                    componentKbConfig.getVectorStoreConfig(),
                    config.getVectorStoreConnectionConfig()
            );
            Embedding embedModel = createEmbeddingModel(componentKbConfig);
            KnowledgeBaseConfig kbConfig = componentKbConfig.getKbConfig();
            if (useGraph) {
                kbInstances.add(new GraphKnowledgeBase(
                        kbConfig,
                        vectorStore,
                        embedModel,
                        null,
                        null,
                        null,
                        null,
                        llmClient,
                        null,
                        null
                ));
            } else {
                kbInstances.add(new SimpleKnowledgeBase(
                        kbConfig,
                        vectorStore,
                        embedModel,
                        null,
                        null,
                        null,
                        null,
                        llmClient,
                        null
                ));
            }
        }
        return kbInstances;
    }

    private Embedding createEmbeddingModel(ComponentKBConfig componentKbConfig) {
        EmbeddingConfig embedConfig = componentKbConfig.getEmbedConfig();
        KnowledgeBaseConfig kbConfig = componentKbConfig.getKbConfig();
        String indexType = kbConfig == null ? null : kbConfig.getIndexType();
        if (embedConfig == null) {
            if ("vector".equals(indexType) || "hybrid".equals(indexType)) {
                throw ErrorHelper.buildError(
                        StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_EMBED_MODEL_INIT_ERROR,
                        "error_msg",
                        "Embedding config is required for vector or hybrid index type"
                );
            }
            return null;
        }
        try {
            Map<String, Object> additional = componentKbConfig.getEmbedAdditionalConfig();
            if (additional == null || additional.isEmpty()) {
                return new OpenAIEmbedding(embedConfig);
            }
            return new OpenAIEmbedding(
                    embedConfig,
                    intOption(additional, "timeout", 60),
                    intOption(additional, "max_retries", 3),
                    stringMapOption(additional, "extra_headers"),
                    intOption(additional, "max_batch_size", 8),
                    intOption(additional, "max_concurrent", 50),
                    integerOption(additional, "dimension"),
                    null
            );
        } catch (RuntimeException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_EMBED_MODEL_INIT_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "Failed to initialise embedding model")
            );
        }
    }

    private BaseModelClient createLlmClient() {
        if (config.getModelId() != null) {
            Object resolved = resolveRunnerModel();
            if (resolved instanceof BaseModelClient modelClient) {
                return modelClient;
            }
            if (resolved instanceof Model model) {
                return new ModelBackedModelClient(model);
            }
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR,
                    "error_msg",
                    "Runner.resourceMgr.getModel did not return a supported model instance"
            );
        }
        if (config.getModelClientConfig() == null || config.getModelConfig() == null) {
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR,
                    "error_msg",
                    "LLM model config is required for agentic retrieval"
            );
        }
        return new ModelBackedModelClient(new Model(config.getModelClientConfig(), config.getModelConfig()));
    }

    private Object resolveRunnerModel() {
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceManager = runnerType.getMethod("resourceMgr").invoke(null);
            Object resolved = invokeGetModel(resourceManager);
            if (resolved instanceof CompletionStage<?> stage) {
                return stage.toCompletableFuture().join();
            }
            return resolved;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR,
                    null,
                    null,
                    exception,
                    Map.of("error_msg", "Runner.resourceMgr.getModel is not available")
            );
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw ErrorHelper.buildError(
                    StatusCode.COMPONENT_KNOWLEDGE_RETRIEVAL_LLM_MODEL_INIT_ERROR,
                    null,
                    null,
                    cause,
                    Map.of("error_msg", "Runner.resourceMgr.getModel failed")
            );
        }
    }

    private Object invokeGetModel(Object resourceManager)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getModel;
        try {
            getModel = resourceManager.getClass().getMethod("getModel", String.class, Object.class);
            return getModel.invoke(resourceManager, config.getModelId(), session);
        } catch (NoSuchMethodException ignored) {
            getModel = resourceManager.getClass().getMethod("getModel", String.class);
            return getModel.invoke(resourceManager, config.getModelId());
        }
    }

    private Map<String, Object> formatOutput(List<MultiKBRetrievalResult> results) {
        List<String> texts = new ArrayList<>();
        if (results != null) {
            for (MultiKBRetrievalResult result : results) {
                texts.add(result.getText());
            }
        }
        return new KnowledgeRetrievalOutput(texts, String.join("\n\n", texts)).toMap();
    }

    private static int intOption(Map<String, Object> options, String key, int defaultValue) {
        Integer value = integerOption(options, key);
        return value == null ? defaultValue : value;
    }

    private static Integer integerOption(Map<String, Object> options, String key) {
        Object value = option(options, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    private static Map<String, String> stringMapOption(Map<String, Object> options, String key) {
        Object value = option(options, key);
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((mapKey, mapValue) -> result.put(String.valueOf(mapKey), mapValue == null ? null : String.valueOf(mapValue)));
        return result;
    }

    private static Object option(Map<String, Object> options, String snakeKey) {
        if (options == null) {
            return null;
        }
        if (options.containsKey(snakeKey)) {
            return options.get(snakeKey);
        }
        return options.get(snakeToCamel(snakeKey));
    }

    private static String snakeToCamel(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean upperNext = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '_') {
                upperNext = true;
            } else {
                builder.append(upperNext ? Character.toUpperCase(current) : current);
                upperNext = false;
            }
        }
        return builder.toString();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * Adapter that exposes the workflow-level {@link Model} facade through the retrieval-layer
     * {@link BaseModelClient} contract used by {@code AgenticRetriever}.
     */
    private static final class ModelBackedModelClient extends BaseModelClient {

        private final Model model;

        private ModelBackedModelClient(Model model) {
            super(model == null ? null : model.getModelConfig(), model == null ? null : model.getModelClientConfig());
            this.model = model;
        }

        @Override
        protected void validateConfig() {
            // The wrapped Model already owns provider validation.
        }

        @Override
        public AssistantMessage invoke(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String modelName,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return model.invoke(normalizeMessages(messages)).toCompletableFuture().join();
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(
                Object messages,
                Object tools,
                Float temperature,
                Float topP,
                String modelName,
                Integer maxTokens,
                String stop,
                BaseOutputParser outputParser,
                Float timeout,
                Map<String, Object> kwargs
        ) {
            return model.stream(normalizeMessages(messages));
        }

        @Override
        public ImageGenerationResponse generateImage(
                List<UserMessage> messages,
                String modelName,
                String size,
                String negativePrompt,
                int n,
                boolean promptExtend,
                boolean watermark,
                int seed,
                Map<String, Object> kwargs
        ) {
            return model.generateImage(messages, modelName, size, negativePrompt, n, promptExtend, watermark, seed,
                    kwargs).toCompletableFuture().join();
        }

        @Override
        public AudioGenerationResponse generateSpeech(
                List<UserMessage> messages,
                String modelName,
                String voice,
                String languageType,
                Map<String, Object> kwargs
        ) {
            return model.generateSpeech(messages, modelName, voice, languageType, kwargs)
                    .toCompletableFuture().join();
        }

        @Override
        public VideoGenerationResponse generateVideo(
                List<UserMessage> messages,
                String imgUrl,
                String audioUrl,
                String modelName,
                String size,
                String resolution,
                int duration,
                boolean promptExtend,
                boolean watermark,
                String negativePrompt,
                Integer seed,
                Map<String, Object> kwargs
        ) {
            return model.generateVideo(messages, imgUrl, audioUrl, modelName, size, resolution, duration, promptExtend,
                    watermark, negativePrompt, seed, kwargs).toCompletableFuture().join();
        }

        private static List<BaseMessage> normalizeMessages(Object messages) {
            if (messages instanceof String text) {
                return List.of(new UserMessage(text));
            }
            if (!(messages instanceof List<?> list)) {
                return List.of(new UserMessage(messages == null ? "" : String.valueOf(messages)));
            }
            List<BaseMessage> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof BaseMessage message) {
                    result.add(message);
                } else if (item instanceof Map<?, ?> rawMap) {
                    result.add(messageFromMap(rawMap));
                } else {
                    result.add(new UserMessage(item == null ? "" : String.valueOf(item)));
                }
            }
            return result;
        }

        private static BaseMessage messageFromMap(Map<?, ?> rawMap) {
            String role = rawMap.get("role") == null ? "user" : String.valueOf(rawMap.get("role"));
            Object content = rawMap.containsKey("content") ? rawMap.get("content") : "";
            String text = content == null ? "" : String.valueOf(content);
            if ("assistant".equals(role)) {
                return new AssistantMessage(text);
            }
            if ("user".equals(role)) {
                return new UserMessage(text);
            }
            return new BaseMessage(role, text);
        }
    }
}
