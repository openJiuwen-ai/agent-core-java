/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.KnowledgeBaseConfig;
import com.openjiuwen.core.retrieval.common.RetrievalConfig;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.embedding.Embedding;
import com.openjiuwen.core.retrieval.indexing.indexer.IndexBackendConfig;
import com.openjiuwen.core.retrieval.indexing.indexer.Indexer;
import com.openjiuwen.core.retrieval.indexing.processor.chunker.Chunker;
import com.openjiuwen.core.retrieval.indexing.processor.extractor.Extractor;
import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.retriever.Retriever;
import com.openjiuwen.core.retrieval.vector_store.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Mirrors Python's {@code KnowledgeBase} in
 * {@code openjiuwen/core/retrieval/knowledge_base.py}.
 */
public abstract class KnowledgeBase implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeBase.class);
    private static final List<String> INDEX_ATTRIBUTES = List.of(
            "database_name",
            "distance_metric",
            "text_field",
            "vector_field",
            "sparse_vector_field",
            "metadata_field",
            "doc_id_field"
    );

    protected boolean strictValidation;
    protected final KnowledgeBaseConfig config;
    protected VectorStore vectorStore;
    protected Embedding embedModel;
    protected Parser parser;
    protected Chunker chunker;
    protected Extractor extractor;
    protected Indexer indexManager;
    protected BaseModelClient llmClient;
    protected Retriever retriever;

    protected KnowledgeBase(KnowledgeBaseConfig config) {
        this(config, null, null, null, null, null, null, null, null, true);
    }

    protected KnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient
    ) {
        this(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, null, true);
    }

    protected KnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient,
            Retriever retriever
    ) {
        this(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, retriever, true);
    }

    protected KnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient,
            boolean strictValidation
    ) {
        this(config, vectorStore, embedModel, parser, chunker, extractor, indexManager, llmClient, null, strictValidation);
    }

    protected KnowledgeBase(
            KnowledgeBaseConfig config,
            VectorStore vectorStore,
            Embedding embedModel,
            Parser parser,
            Chunker chunker,
            Extractor extractor,
            Indexer indexManager,
            BaseModelClient llmClient,
            Retriever retriever,
            boolean strictValidation
    ) {
        this.strictValidation = strictValidation;
        this.config = config;
        this.vectorStore = vectorStore;
        this.embedModel = embedModel;
        this.parser = parser;
        this.chunker = chunker;
        this.extractor = extractor;
        this.indexManager = indexManager;
        this.llmClient = llmClient;
        this.retriever = retriever;
        validateIndexAfterSpecialAttributeSet();
    }

    public KnowledgeBaseConfig getConfig() {
        return config;
    }

    public VectorStore getVectorStore() {
        return vectorStore;
    }

    public void setVectorStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        validateIndexAfterSpecialAttributeSet();
    }

    public Embedding getEmbedModel() {
        return embedModel;
    }

    public void setEmbedModel(Embedding embedModel) {
        this.embedModel = embedModel;
    }

    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public Chunker getChunker() {
        return chunker;
    }

    public void setChunker(Chunker chunker) {
        this.chunker = chunker;
    }

    public Extractor getExtractor() {
        return extractor;
    }

    public void setExtractor(Extractor extractor) {
        this.extractor = extractor;
    }

    public Indexer getIndexManager() {
        return indexManager;
    }

    public void setIndexManager(Indexer indexManager) {
        this.indexManager = indexManager;
        validateIndexAfterSpecialAttributeSet();
    }

    public BaseModelClient getLlmClient() {
        return llmClient;
    }

    public void setLlmClient(BaseModelClient llmClient) {
        this.llmClient = llmClient;
    }

    public Retriever getRetriever() {
        return retriever;
    }

    public void setRetriever(Retriever retriever) {
        this.retriever = retriever;
    }

    public boolean isStrictValidation() {
        return strictValidation;
    }

    public void setStrictValidation(boolean strictValidation) {
        this.strictValidation = strictValidation;
    }

    public void validateIndex() {
        for (String attr : INDEX_ATTRIBUTES) {
            Object vectorStoreValue = readPythonStyleAttribute(vectorStore, attr);
            Object indexManagerValue = readPythonStyleAttribute(indexManager, attr);
            if (!equalsPythonNoneAware(vectorStoreValue, indexManagerValue)) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "error_msg",
                        "Vector store and index manager have incompatible " + attr + " configs:\n"
                                + "- Vector Store (" + simpleName(vectorStore) + ") is using \"" + vectorStoreValue + "\"\n"
                                + "- Index manager (" + simpleName(indexManager) + ") is using \"" + indexManagerValue + "\""
                );
            }
        }
        if (strictValidation && vectorStore != null) {
            vectorStore.checkVectorField();
        }
    }

    public void deleteCollection(String collection) {
        join(deleteCollectionAsync(collection));
    }

    public CompletableFuture<Void> deleteCollectionAsync(String collection) {
        if (vectorStore == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_VECTOR_STORE_NOT_FOUND,
                    "error_msg",
                    "vector_store is required for delete_collection"
            );
        }
        return vectorStore.deleteTable(collection);
    }

    public List<Document> parseFiles(List<String> filePaths) {
        return join(parseFilesAsync(filePaths, Map.of()));
    }

    public List<Document> parseFiles(List<String> filePaths, Map<String, Object> options) {
        return join(parseFilesAsync(filePaths, options));
    }

    public CompletableFuture<List<Document>> parseFilesAsync(List<String> filePaths) {
        return parseFilesAsync(filePaths, Map.of());
    }

    public CompletableFuture<List<Document>> parseFilesAsync(List<String> filePaths, Map<String, Object> options) {
        if (parser == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_PARSER_NOT_FOUND,
                    "error_msg",
                    "parser is required for parse_files"
            );
        }
        List<Document> documents = new ArrayList<>();
        if (filePaths == null || filePaths.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String filePath : filePaths) {
            chain = chain.thenCompose(ignored -> parseOneFile(filePath, options)
                    .thenAccept(documents::addAll));
        }
        return chain.thenApply(ignored -> List.copyOf(documents));
    }

    public List<Document> parseUrls(List<String> urls) {
        return join(parseUrlsAsync(urls, Map.of()));
    }

    public List<Document> parseUrls(List<String> urls, Map<String, Object> options) {
        return join(parseUrlsAsync(urls, options));
    }

    public CompletableFuture<List<Document>> parseUrlsAsync(List<String> urls) {
        return parseUrlsAsync(urls, Map.of());
    }

    public CompletableFuture<List<Document>> parseUrlsAsync(List<String> urls, Map<String, Object> options) {
        if (parser == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_PARSER_NOT_FOUND,
                    "error_msg",
                    "parser is required for parse_urls"
            );
        }
        List<Document> documents = new ArrayList<>();
        if (urls == null || urls.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String url : urls) {
            chain = chain.thenCompose(ignored -> parseOneUrl(url, options)
                    .thenAccept(documents::addAll));
        }
        return chain.thenApply(ignored -> List.copyOf(documents));
    }

    public CompletableFuture<List<String>> addDocuments(List<Document> documents, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(addDocuments(documents));
    }

    public List<String> addDocuments(List<Document> documents) {
        return join(addDocuments(documents, Map.of()));
    }

    public CompletableFuture<List<String>> addDocumentsAsync(List<Document> documents) {
        return addDocuments(documents, Map.of());
    }

    public CompletableFuture<List<RetrievalResult>> retrieve(
            String query,
            RetrievalConfig config,
            Map<String, Object> kwargs
    ) {
        return CompletableFuture.completedFuture(retrieve(query, config));
    }

    public List<RetrievalResult> retrieve(String query, RetrievalConfig config) {
        return join(retrieve(query, config, Map.of()));
    }

    public CompletableFuture<List<RetrievalResult>> retrieveAsync(String query, RetrievalConfig config) {
        return retrieve(query, config, Map.of());
    }

    public CompletableFuture<Boolean> deleteDocuments(List<String> docIds, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(deleteDocuments(docIds));
    }

    public boolean deleteDocuments(List<String> docIds) {
        return Boolean.TRUE.equals(join(deleteDocuments(docIds, Map.of())));
    }

    public CompletableFuture<Boolean> deleteDocumentsAsync(List<String> docIds) {
        return deleteDocuments(docIds, Map.of());
    }

    public CompletableFuture<List<String>> updateDocuments(List<Document> documents, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(updateDocuments(documents));
    }

    public List<String> updateDocuments(List<Document> documents) {
        return join(updateDocuments(documents, Map.of()));
    }

    public CompletableFuture<List<String>> updateDocumentsAsync(List<Document> documents) {
        return updateDocuments(documents, Map.of());
    }

    protected CompletableFuture<Map<String, Object>> getStatisticsAsync() {
        return CompletableFuture.completedFuture(getStatistics());
    }

    public Map<String, Object> getStatistics() {
        return join(getStatisticsAsync());
    }

    public CompletableFuture<Map<String, Object>> getStatisticsFuture() {
        return getStatisticsAsync();
    }

    @Override
    public void close() {
        join(closeAsync());
    }

    public CompletableFuture<Void> closeAsync() {
        return closeMaybe(retriever)
                .thenCompose(ignored -> closeMaybe(vectorStore))
                .thenCompose(ignored -> closeMaybe(indexManager));
    }

    private CompletableFuture<List<Document>> parseOneFile(String filePath, Map<String, Object> options) {
        try {
            String fileName = pythonSlashBasename(filePath);
            java.util.LinkedHashMap<String, Object> parseOptions = new java.util.LinkedHashMap<>();
            if (options != null) {
                parseOptions.putAll(options);
            }
            parseOptions.putIfAbsent("file_name", fileName);
            String fileId = UUID.randomUUID().toString();
            CompletableFuture<List<Document>> parsed = parser.parseAsync(filePath, fileId, llmClient, parseOptions);
            if (parsed == null) {
                throw new IllegalStateException("parser returned null");
            }
            return parsed.exceptionally(exception -> {
                LOGGER.error("Failed to parse file {}: {}", filePath, unwrap(exception).getMessage());
                return List.of();
            });
        } catch (Exception exception) {
            LOGGER.error("Failed to parse file {}: {}", filePath, exception.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private CompletableFuture<List<Document>> parseOneUrl(String url, Map<String, Object> options) {
        try {
            if (!parser.supports(url)) {
                LOGGER.warn("Parser does not support URL {}, skipping", url);
                return CompletableFuture.completedFuture(List.of());
            }
            String docId = UUID.randomUUID().toString();
            CompletableFuture<List<Document>> parsed = parser.parseAsync(url, docId, llmClient, options == null ? Map.of() : options);
            if (parsed == null) {
                throw new IllegalStateException("parser returned null");
            }
            return parsed.exceptionally(exception -> {
                LOGGER.error("Failed to parse URL {}: {}", url, unwrap(exception).getMessage());
                return List.of();
            });
        } catch (Exception exception) {
            LOGGER.error("Failed to parse URL {}: {}", url, exception.getMessage());
            return CompletableFuture.completedFuture(List.of());
        }
    }

    private void validateIndexAfterSpecialAttributeSet() {
        if (vectorStore == null || indexManager == null) {
            return;
        }
        validateIndex();
        if (isChromaLike(vectorStore) || isChromaLike(indexManager)) {
            if (strictValidation && !"vector".equals(config.getIndexType())) {
                throw ErrorHelper.buildError(
                        StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                        "error_msg",
                        "Chroma database does not support sparse embedding & hybrid search in local mode yet"
                );
            }
        }
        invokeNoArgIfPresent(vectorStore, "_ensure_loaded");
        invokeNoArgIfPresent(vectorStore, "ensureLoaded");
    }

    protected static void compareConfig(
            String field,
            Object left,
            Object right,
            IndexBackendConfig leftOwner,
            IndexBackendConfig rightOwner
    ) {
        if (!equalsPythonNoneAware(left, right)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_DATABASE_CONFIG_INVALID,
                    "error_msg",
                    "incompatible " + field + " configs between "
                            + simpleName(leftOwner) + "=" + left
                            + " and " + simpleName(rightOwner) + "=" + right
            );
        }
    }

    protected Indexer resolveIndexManager() {
        return indexManager;
    }

    protected Indexer requireIndexManager() {
        Indexer activeIndexManager = resolveIndexManager();
        if (activeIndexManager == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_KB_INDEX_MANAGER_NOT_FOUND,
                    "error_msg",
                    "index_manager is required"
            );
        }
        return activeIndexManager;
    }

    protected static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Mirrors Python best-effort close cleanup.
        }
    }

    private static boolean isChromaLike(Object value) {
        return value != null && value.getClass().getSimpleName().toLowerCase(Locale.ROOT).startsWith("chroma");
    }

    private static boolean equalsPythonNoneAware(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        return left != null && left.equals(right);
    }

    private static Object readPythonStyleAttribute(Object target, String attr) {
        if (target == null) {
            return null;
        }
        String camel = snakeToCamel(attr);
        for (String methodName : List.of("get" + capitalize(camel), camel, attr)) {
            try {
                Method method = target.getClass().getMethod(methodName);
                if (method.getParameterCount() == 0) {
                    return method.invoke(target);
                }
            } catch (NoSuchMethodException ignored) {
                // Mirrors Python getattr(..., None).
            } catch (IllegalAccessException | InvocationTargetException exception) {
                throw new IllegalStateException("Failed to read attribute " + attr + " from " + simpleName(target), exception);
            }
        }
        return null;
    }

    private static void invokeNoArgIfPresent(Object target, String methodName) {
        if (target == null) {
            return;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            if (method.getParameterCount() == 0) {
                method.invoke(target);
            }
        } catch (NoSuchMethodException ignored) {
            // Mirrors Python getattr(..., None).
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to invoke " + methodName + " on " + simpleName(target), exception);
        }
    }

    private static CompletableFuture<Void> closeMaybe(Object target) {
        if (target == null) {
            return CompletableFuture.completedFuture(null);
        }
        Method method;
        try {
            method = target.getClass().getMethod("close");
        } catch (NoSuchMethodException ignored) {
            return CompletableFuture.completedFuture(null);
        }
        if (method.getParameterCount() != 0) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            Object result = method.invoke(target);
            if (result instanceof CompletableFuture<?> future) {
                return future.thenApply(ignored -> null);
            }
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            if (CompletableFuture.class.isAssignableFrom(method.getReturnType())) {
                return CompletableFuture.failedFuture(cause);
            }
            LOGGER.warn("Failed to close object", cause);
            return CompletableFuture.completedFuture(null);
        } catch (IllegalAccessException exception) {
            return CompletableFuture.failedFuture(exception);
        }
        return CompletableFuture.completedFuture(null);
    }

    private static String pythonSlashBasename(String value) {
        int slashIndex = value.lastIndexOf('/');
        return slashIndex < 0 ? value : value.substring(slashIndex + 1);
    }

    private static String snakeToCamel(String value) {
        StringBuilder builder = new StringBuilder();
        boolean uppercaseNext = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') {
                uppercaseNext = true;
            } else if (uppercaseNext) {
                builder.append(Character.toUpperCase(ch));
                uppercaseNext = false;
            } else {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String simpleName(Object value) {
        return value == null ? "None" : value.getClass().getSimpleName();
    }

    private static <T> T join(CompletableFuture<T> future) {
        if (future == null) {
            return null;
        }
        return future.toCompletableFuture().join();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
