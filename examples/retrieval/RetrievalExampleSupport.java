import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.foundation.llm.model_clients.DashScopeModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.DefaultModelClientFactories;
import com.openjiuwen.core.foundation.llm.model_clients.InferenceAffinityModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.OpenAiModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.OpenRouterModelClientFactory;
import com.openjiuwen.core.foundation.llm.model_clients.SiliconFlowModelClientFactory;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.retrieval.common.EmbeddingConfig;
import com.openjiuwen.core.retrieval.common.RerankerConfig;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Shared configuration and utility methods for retrieval examples.
 */
public final class RetrievalExampleSupport {

    public static final int DEFAULT_EMBEDDING_DIMENSION = 128;
    public static final int DEFAULT_FILTER_EMBEDDING_DIMENSION = 32;
    public static final int DEFAULT_QUERY_REWRITER_CONTEXT_SIZE = 50;

    private RetrievalExampleSupport() {
    }

    public static EmbeddingConfig embeddingConfig() {
        EmbeddingConfig fallback = new EmbeddingConfig(
                SharedExampleApiConfigLoader.getModelName(),
                SharedExampleApiConfigLoader.getApiBase(),
                SharedExampleApiConfigLoader.getApiKey());
        return loadEmbeddingConfig("EMBEDDING", fallback);
    }

    public static EmbeddingConfig multimodalEmbeddingConfig() {
        return loadEmbeddingConfig("MULTIMODAL_EMBEDDING", embeddingConfig());
    }

    public static RerankerConfig rerankerConfig() {
        return loadRerankerConfig("RERANKER", SharedExampleApiConfigLoader.getModelName());
    }

    public static RerankerConfig chatRerankerConfig() {
        RerankerConfig config = loadRerankerConfig("CHAT_RERANKER", rerankerConfig().getModelName());
        List<Integer> yesNoIds = parseIntegerList(resolveStringConfig("CHAT_RERANKER_YES_NO_IDS", ""));
        if (yesNoIds.size() != 2) {
            throw new IllegalStateException(
                    "CHAT_RERANKER_YES_NO_IDS must provide exactly two integer token ids, for example: 9454,2753");
        }
        config.setYesNoIds(yesNoIds);
        return config;
    }

    public static BaseModelClient queryRewriterClient() {
        String provider = resolveStringConfig("QUERY_REWRITER_PROVIDER", SharedExampleApiConfigLoader.getModelProvider());
        String apiBase = resolveStringConfig("QUERY_REWRITER_API_BASE", SharedExampleApiConfigLoader.getApiBase());
        String apiKey = resolveStringConfig("QUERY_REWRITER_API_KEY", SharedExampleApiConfigLoader.getApiKey());
        String model = resolveStringConfig("QUERY_REWRITER_MODEL", SharedExampleApiConfigLoader.getModelName());
        double timeout = resolveDoubleConfig("QUERY_REWRITER_TIMEOUT", 60.0);
        int maxRetries = resolveIntConfig("QUERY_REWRITER_MAX_RETRIES", 2);
        boolean verifySsl = resolveBooleanConfig("QUERY_REWRITER_SSL_VERIFY", SharedExampleApiConfigLoader.getSslVerify());
        String sslCert = resolveStringConfig("QUERY_REWRITER_SSL_CERT", "");

        ModelClientConfig.Builder clientBuilder = ModelClientConfig.builder()
                .clientProvider(provider)
                .apiBase(apiBase)
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .verifySsl(verifySsl);
        if (!sslCert.isBlank()) {
            clientBuilder.sslCert(sslCert);
        }

        ModelRequestConfig requestConfig = ModelRequestConfig.builder()
                .modelName(model)
                .temperature(0.0)
                .topP(0.1)
                .maxTokens(resolveIntConfig("QUERY_REWRITER_MAX_TOKENS", 512))
                .build();
        return createModelClient(requestConfig, clientBuilder.build());
    }

    public static ModelContext createContext(String contextId) {
        ContextEngineConfig config = ContextEngineConfig.builder()
                .maxContextMessageNum(DEFAULT_QUERY_REWRITER_CONTEXT_SIZE)
                .defaultWindowMessageNum(DEFAULT_QUERY_REWRITER_CONTEXT_SIZE)
                .build();
        return new ContextEngine(config).createContext(contextId, null);
    }

    public static VectorStoreConfig chromaVectorStoreConfig(String collectionName) {
        String databaseName = resolveStringConfig("CHROMA_DATABASE_NAME", "retrieval_examples");
        return new VectorStoreConfig("chroma", databaseName, collectionName, "cosine");
    }

    public static VectorStoreConfig milvusVectorStoreConfig(String collectionName) {
        String databaseName = resolveStringConfig("MILVUS_DATABASE_NAME", "retrieval_examples");
        return new VectorStoreConfig("milvus", databaseName, collectionName, "cosine");
    }

    public static String milvusUri() {
        return resolveStringConfig("MILVUS_URI", "http://localhost:19530");
    }

    public static String milvusToken() {
        return resolveStringConfig("MILVUS_TOKEN", "");
    }

    public static Path sampleImagePath() {
        List<Path> candidates = List.of(
                Paths.get("images", "sample.png"),
                Paths.get("examples", "skill_use", "data", "sample.png"));
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }
        throw new IllegalStateException("Unable to locate sample image. Tried: " + candidates);
    }

    public static Path resolvePathConfig(String key, Path defaultPath) {
        String configured = resolveStringConfig(key, "");
        Path path = configured.isBlank() ? defaultPath : Paths.get(configured);
        return path.toAbsolutePath().normalize();
    }

    public static String resolveStringConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return defaultValue;
    }

    public static int resolveIntConfig(String key, int defaultValue) {
        String value = resolveStringConfig(key, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid integer for " + key + ": " + value, ex);
        }
    }

    public static double resolveDoubleConfig(String key, double defaultValue) {
        String value = resolveStringConfig(key, Double.toString(defaultValue));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid number for " + key + ": " + value, ex);
        }
    }

    public static boolean resolveBooleanConfig(String key, boolean defaultValue) {
        String value = resolveStringConfig(key, Boolean.toString(defaultValue));
        return Boolean.parseBoolean(value);
    }

    private static EmbeddingConfig loadEmbeddingConfig(String prefix, EmbeddingConfig fallback) {
        return new EmbeddingConfig(
                resolveStringConfig(prefix + "_MODEL", fallback.getModelName()),
                resolveStringConfig(prefix + "_API_BASE", fallback.getBaseUrl()),
                resolveStringConfig(prefix + "_API_KEY", fallback.getApiKey()));
    }

    private static RerankerConfig loadRerankerConfig(String prefix, String defaultModel) {
        RerankerConfig config = new RerankerConfig();
        config.setApiBase(resolveStringConfig(prefix + "_API_BASE", SharedExampleApiConfigLoader.getApiBase()));
        config.setApiKey(resolveStringConfig(prefix + "_API_KEY", SharedExampleApiConfigLoader.getApiKey()));
        config.setModelName(resolveStringConfig(prefix + "_MODEL", defaultModel));
        config.setTimeout(resolveDoubleConfig(prefix + "_TIMEOUT", 10.0));
        config.setTemperature(resolveDoubleConfig(prefix + "_TEMPERATURE", 0.95));
        config.setTopP(resolveDoubleConfig(prefix + "_TOP_P", 0.1));
        return config;
    }

    private static BaseModelClient createModelClient(ModelRequestConfig requestConfig, ModelClientConfig clientConfig) {
        DefaultModelClientFactories.ensureRegistered();
        String provider = clientConfig.getClientProvider();
        for (Model.ModelClientFactory factory : loadFactories()) {
            if (factory.providerName().equalsIgnoreCase(provider)) {
                return factory.create(requestConfig, clientConfig);
            }
        }
        throw new IllegalStateException("Unsupported model provider for retrieval example: " + provider);
    }

    private static List<Model.ModelClientFactory> loadFactories() {
        Map<String, Model.ModelClientFactory> factories = new LinkedHashMap<>();
        for (Model.ModelClientFactory factory : ServiceLoader.load(Model.ModelClientFactory.class)) {
            factories.putIfAbsent(factory.providerName().toLowerCase(Locale.ROOT), factory);
        }
        registerFallbackFactory(factories, new OpenAiModelClientFactory());
        registerFallbackFactory(factories, new OpenRouterModelClientFactory());
        registerFallbackFactory(factories, new SiliconFlowModelClientFactory());
        registerFallbackFactory(factories, new DashScopeModelClientFactory());
        registerFallbackFactory(factories, new InferenceAffinityModelClientFactory("InferenceAffinity"));
        registerFallbackFactory(factories, new InferenceAffinityModelClientFactory("inference_affinity"));
        return new ArrayList<>(factories.values());
    }

    private static void registerFallbackFactory(Map<String, Model.ModelClientFactory> factories,
                                                Model.ModelClientFactory factory) {
        factories.putIfAbsent(factory.providerName().toLowerCase(Locale.ROOT), factory);
    }

    private static List<Integer> parseIntegerList(String rawValue) {
        List<Integer> values = new ArrayList<>();
        if (rawValue == null || rawValue.isBlank()) {
            return values;
        }
        for (String token : rawValue.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                values.add(Integer.parseInt(trimmed));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid integer token id in list: " + rawValue, ex);
            }
        }
        return values;
    }
}