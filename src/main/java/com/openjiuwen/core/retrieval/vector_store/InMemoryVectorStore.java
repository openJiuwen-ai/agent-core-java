/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.common.async.CompletableList;
import com.openjiuwen.core.common.async.CompletableMap;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.query.ComparisonExpr;
import com.openjiuwen.core.memory.migration.operation.BaseOperation;
import com.openjiuwen.core.retrieval.common.RetrievalResult;
import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 0.1.12-compatible in-memory retrieval vector store.
 *
 * <p>Mirrors Python's in-memory fallback behavior used by
 * {@code openjiuwen/core/retrieval/vector_store/chroma_store.py}.</p>
 */
public class InMemoryVectorStore extends BaseVectorStore implements VectorStore {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}_]+");
    private static final double BM25_K1 = 1.5d;
    private static final double BM25_B = 0.75d;
    private static final float EPSILON = 1e-6f;
    private static final Map<String, Backend> DATABASES = new ConcurrentHashMap<>();

    private final Backend backend;
    private final String databaseName;
    private String collectionName;
    private final String distanceMetric;
    private final String indexType;
    private final String textField;
    private final String vectorField;
    private final String sparseVectorField;
    private final String metadataField;
    private final String docIdField;

    public InMemoryVectorStore(String collectionName) {
        this(new VectorStoreConfig("chroma", collectionName), "hybrid");
    }

    public InMemoryVectorStore(VectorStoreConfig config, String indexType) {
        config.validate();
        this.databaseName = config.getDatabaseName();
        this.collectionName = config.getCollectionName();
        this.distanceMetric = config.getDistanceMetric();
        this.indexType = validateIndexType(indexType);
        this.textField = "text";
        this.vectorField = "vector";
        this.sparseVectorField = "sparse_vector";
        this.metadataField = "metadata";
        this.docIdField = "doc_id";
        this.backend = DATABASES.computeIfAbsent(databaseName, key -> new Backend());
        backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        backend.collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    private InMemoryVectorStore(InMemoryVectorStore source, String collectionName) {
        this.backend = source.backend;
        this.databaseName = source.databaseName;
        this.collectionName = collectionName;
        this.distanceMetric = source.distanceMetric;
        this.indexType = source.indexType;
        this.textField = source.textField;
        this.vectorField = source.vectorField;
        this.sparseVectorField = source.sparseVectorField;
        this.metadataField = source.metadataField;
        this.docIdField = source.docIdField;
        backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        backend.collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
        backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        backend.collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    public InMemoryVectorStore withCollection(String collectionName) {
        return new InMemoryVectorStore(this, collectionName);
    }

    public void checkVectorField() {
        // The compatibility in-memory backend does not persist external vector-field configuration.
    }

    public void ensureCollection(String collectionName, String indexType, Integer dimension) {
        ensureCollection(collectionName, indexType, dimension, Map.of());
    }

    public void ensureCollection(String collectionName,
                                 String indexType,
                                 Integer dimension,
                                 Map<String, Object> options) {
        backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        backend.collectionMetadata.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    @Override
    public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
        ensureCollection(collectionName, indexType, null, kwargs);
        if (schema instanceof CollectionSchema collectionSchema) {
            updateCollectionMetadata(collectionName, Map.of("schema", collectionSchema.toDict()));
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
        return deleteTable(collectionName);
    }

    @Override
    public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
        return tableExists(collectionName);
    }

    @Override
    public CompletableFuture<Void> add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        addSync(data, batchSize, options);
        return CompletableFuture.completedFuture(null);
    }

    private void addSync(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, StoredRecord> collection = currentCollection();
        for (Map<String, Object> item : data) {
            Map<String, Object> safeItem = item == null ? Map.of() : item;
            Map<String, Object> metadata = castMap(safeItem.get(metadataField));
            String id = firstNonBlank(
                    stringValue(safeItem.get("id")),
                    stringValue(safeItem.get("chunk_id")),
                    stringValue(metadata.get("chunk_id")),
                    UUID.randomUUID().toString());
            String text = firstNonBlank(stringValue(safeItem.get(textField)), stringValue(safeItem.get("content")), "");
            List<Float> vector = castFloatList(firstNonNull(safeItem.get(vectorField), safeItem.get("embedding")));
            collection.put(id, new StoredRecord(id, text, vector, metadata, new LinkedHashMap<>(safeItem)));
        }
    }

    public List<SearchResult> search(List<Float> queryVector, int topK, Map<String, Object> filters, Map<String, Object> options) {
        if (queryVector == null || queryVector.isEmpty()) {
            return List.of();
        }
        List<ScoredRecord> scored = new ArrayList<>();
        for (StoredRecord record : filteredRecords(filters)) {
            if (record.vector == null || record.vector.isEmpty()) {
                continue;
            }
            scored.add(new ScoredRecord(record, vectorScore(queryVector, record.vector)));
        }
        return toSearchResults(scored, topK);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> search(List<Double> queryVector,
                                                           int topK,
                                                           VectorStoreFilter filters,
                                                           Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(toRetrievalResults(search(
                toFloatList(queryVector),
                topK,
                filterMap(filters),
                kwargs)));
    }

    public List<SearchResult> sparseSearch(String queryText,
                                           int topK,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        List<StoredRecord> corpus = filteredRecords(filters);
        List<ScoredRecord> scored = new ArrayList<>();
        for (StoredRecord record : corpus) {
            scored.add(new ScoredRecord(record, sparseScore(queryText, record.text, corpus)));
        }
        return toSearchResults(scored, topK);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> sparseSearch(String queryText,
                                                                 int topK,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(toRetrievalResults(sparseSearch(
                queryText,
                topK,
                filterMap(filters),
                kwargs)));
    }

    public List<SearchResult> hybridSearch(String queryText,
                                           List<Float> queryVector,
                                           int topK,
                                           double alpha,
                                           Map<String, Object> filters,
                                           Map<String, Object> options) {
        List<StoredRecord> corpus = filteredRecords(filters);
        List<ScoredRecord> scored = new ArrayList<>();
        for (StoredRecord record : corpus) {
            double sparse = sparseScore(queryText, record.text, corpus);
            double vector = queryVector == null || queryVector.isEmpty() || record.vector == null
                    ? 0.0d
                    : normalizedVectorScore(queryVector, record.vector);
            scored.add(new ScoredRecord(record, alpha * vector + (1.0d - alpha) * sparse));
        }
        return toSearchResults(scored, topK);
    }

    @Override
    public CompletableFuture<List<RetrievalResult>> hybridSearch(String queryText,
                                                                 List<Double> queryVector,
                                                                 int topK,
                                                                 double alpha,
                                                                 VectorStoreFilter filters,
                                                                 Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(toRetrievalResults(hybridSearch(
                queryText,
                toFloatList(queryVector),
                topK,
                alpha,
                filterMap(filters),
                kwargs)));
    }

    public boolean delete(List<String> ids, Map<String, Object> filterExpr, Map<String, Object> options) {
        Map<String, StoredRecord> collection = currentCollection();
        boolean changed = false;
        if (ids != null && !ids.isEmpty()) {
            for (String id : ids) {
                changed |= collection.remove(id) != null;
            }
        }
        if (filterExpr != null && !filterExpr.isEmpty()) {
            List<String> matched = new ArrayList<>();
            for (StoredRecord record : collection.values()) {
                if (matches(record, filterExpr)) {
                    matched.add(record.id);
                }
            }
            for (String id : matched) {
                changed |= collection.remove(id) != null;
            }
        }
        return changed;
    }

    @Override
    public CompletableFuture<Boolean> delete(List<String> ids, DeleteFilter filterExpr, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(delete(ids, deleteFilterMap(filterExpr), kwargs));
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return CompletableFuture.completedFuture(backend.collections.containsKey(tableName));
    }

    @Override
    public CompletableFuture<Void> deleteTable(String tableName) {
        backend.collections.remove(tableName);
        backend.collectionMetadata.remove(tableName);
        return CompletableFuture.completedFuture(null);
    }

    public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
        List<SearchResult> results = new ArrayList<>();
        for (StoredRecord record : filteredRecords(filters)) {
            results.add(new SearchResult(record.id, record.text, 0.0d, record.metadata));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    public long count(String tableName) {
        return backend.collections.getOrDefault(tableName, Map.of()).size();
    }

    @Override
    public CompletableList<String> listCollectionNames() {
        return CompletableList.completed(new ArrayList<>(backend.collections.keySet()));
    }

    @Override
    public CompletableMap<String, Object> getCollectionMetadata(String collectionName) {
        return CompletableMap.completed(new LinkedHashMap<>(backend.collectionMetadata.getOrDefault(collectionName, Map.of())));
    }

    @Override
    public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        backend.collectionMetadata
                .computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>())
                .putAll(metadata);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> updateSchema(String collectionName, List<BaseOperation> operations) {
        if (operations == null || operations.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<String, StoredRecord> collection = backend.collections
                .computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        for (Map.Entry<String, StoredRecord> entry : new ArrayList<>(collection.entrySet())) {
            StoredRecord updated = entry.getValue();
            for (Object operation : operations) {
                updated = applyOperation(updated, operation);
            }
            collection.put(entry.getKey(), updated);
        }
        return CompletableFuture.completedFuture(null);
    }

    public CollectionSchema getSchema(String collectionName) {
        return new CollectionSchema();
    }

    @Override
    public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
        return CompletableFuture.completedFuture(getSchema(collectionName));
    }

    @Override
    public CompletableFuture<Void> addDocs(String collectionName,
                                           List<Map<String, Object>> docs,
                                           Map<String, Object> kwargs) {
        InMemoryVectorStore target = withCollection(collectionName);
        target.addSync(docs, batchSize(kwargs), kwargs);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<VectorSearchResult>> search(String collectionName,
                                                              List<Double> queryVector,
                                                              String vectorField,
                                                              int topK,
                                                              Map<String, Object> filters,
                                                              Map<String, Object> kwargs) {
        InMemoryVectorStore target = withCollection(collectionName);
        List<SearchResult> searchResults = target.search(toFloatList(queryVector), topK, filters, kwargs);
        List<VectorSearchResult> results = new ArrayList<>(searchResults.size());
        for (SearchResult result : searchResults) {
            Map<String, Object> fields = new LinkedHashMap<>(result.getMetadata());
            fields.putIfAbsent("id", result.getId());
            fields.putIfAbsent("text", result.getText());
            results.add(new VectorSearchResult(result.getScore(), fields));
        }
        return CompletableFuture.completedFuture(results);
    }

    @Override
    public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
        InMemoryVectorStore target = withCollection(collectionName);
        target.delete(ids, Map.of(), kwargs);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteDocsByFilters(String collectionName,
                                                       Map<String, Object> filters,
                                                       Map<String, Object> kwargs) {
        InMemoryVectorStore target = withCollection(collectionName);
        target.delete(List.of(), filters, kwargs);
        return CompletableFuture.completedFuture(null);
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDistanceMetric() {
        return distanceMetric;
    }

    public String getIndexType() {
        return indexType;
    }

    public String getTextField() {
        return textField;
    }

    public String getVectorField() {
        return vectorField;
    }

    public String getSparseVectorField() {
        return sparseVectorField;
    }

    public String getMetadataField() {
        return metadataField;
    }

    public String getDocIdField() {
        return docIdField;
    }

    @Override
    public void close() {
    }

    private Map<String, StoredRecord> currentCollection() {
        return backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    private List<StoredRecord> filteredRecords(Map<String, Object> filters) {
        List<StoredRecord> records = new ArrayList<>();
        for (StoredRecord record : currentCollection().values()) {
            if (filters == null || filters.isEmpty() || matches(record, filters)) {
                records.add(record);
            }
        }
        return records;
    }

    private boolean matches(StoredRecord record, Map<String, Object> filters) {
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            Object expected = entry.getValue();
            Object actual = record.field(entry.getKey());
            if (expected instanceof Collection<?> collection) {
                if (!collection.contains(actual)) {
                    return false;
                }
            } else if (actual == null || !actual.equals(expected)) {
                return false;
            }
        }
        return true;
    }

    private static List<SearchResult> toSearchResults(List<ScoredRecord> scored, int topK) {
        scored.sort(Comparator.comparingDouble(ScoredRecord::score).reversed());
        List<SearchResult> results = new ArrayList<>();
        int limit = Math.min(Math.max(topK, 0), scored.size());
        for (int index = 0; index < limit; index++) {
            ScoredRecord item = scored.get(index);
            results.add(new SearchResult(item.record.id, item.record.text, item.score, item.record.metadata));
        }
        return results;
    }

    private static List<RetrievalResult> toRetrievalResults(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<RetrievalResult> output = new ArrayList<>(results.size());
        for (SearchResult result : results) {
            output.add(new RetrievalResult(
                    result.getText(),
                    result.getScore(),
                    result.getMetadata(),
                    null,
                    result.getId()));
        }
        return output;
    }

    private static Map<String, Object> filterMap(VectorStoreFilter filters) {
        if (filters == null || filters.mapping() == null) {
            return Map.of();
        }
        return filters.mapping();
    }

    private static Map<String, Object> deleteFilterMap(DeleteFilter filters) {
        if (filters == null) {
            return Map.of();
        }
        if (filters.queryExpr() instanceof ComparisonExpr comparison
                && "==".equals(comparison.getOperator())
                && comparison.getField() != null
                && comparison.getValue() != null) {
            return Map.of(comparison.getField(), comparison.getValue());
        }
        return Map.of();
    }

    private static List<Float> toFloatList(List<Double> values) {
        if (values == null) {
            return List.of();
        }
        List<Float> result = new ArrayList<>(values.size());
        for (Double value : values) {
            result.add(value == null ? 0.0f : value.floatValue());
        }
        return result;
    }

    private double vectorScore(List<Float> queryVector, List<Float> vector) {
        return switch (distanceMetric) {
            case "dot" -> dot(queryVector, vector);
            case "euclidean" -> -euclidean(queryVector, vector);
            default -> cosine(queryVector, vector);
        };
    }

    private double normalizedVectorScore(List<Float> queryVector, List<Float> vector) {
        double score = vectorScore(queryVector, vector);
        return switch (distanceMetric) {
            case "euclidean" -> 1.0d / (1.0d + Math.max(0.0d, -score));
            default -> (score + 1.0d) / 2.0d;
        };
    }

    private static double sparseScore(String queryText, String text, List<StoredRecord> corpus) {
        if (queryText == null || text == null) {
            return 0.0d;
        }
        List<String> queryTokens = tokenList(queryText);
        List<String> docTokens = tokenList(text);
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return 0.0d;
        }
        Map<String, Integer> termFrequency = termFrequency(docTokens);
        Map<String, Integer> documentFrequency = documentFrequency(corpus == null ? List.of() : corpus);
        double averageDocLength = averageDocLength(corpus == null || corpus.isEmpty() ? List.of() : corpus);
        double score = 0.0d;
        for (String token : new LinkedHashSet<>(queryTokens)) {
            int tf = termFrequency.getOrDefault(token, 0);
            if (tf == 0) {
                continue;
            }
            int df = documentFrequency.getOrDefault(token, 0);
            double idf = Math.log(1.0d + ((Math.max(corpus == null ? 0 : corpus.size(), 1) - df) + 0.5d) / (df + 0.5d));
            double denominator = tf + BM25_K1 * (1.0d - BM25_B + BM25_B * (docTokens.size() / Math.max(averageDocLength, 1.0d)));
            score += idf * (tf * (BM25_K1 + 1.0d)) / Math.max(denominator, 1e-9d);
        }
        return score;
    }

    private static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static List<String> tokenList(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String part : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (!part.isBlank()) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private static Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String token : tokens) {
            frequency.merge(token, 1, Integer::sum);
        }
        return frequency;
    }

    private static Map<String, Integer> documentFrequency(List<StoredRecord> corpus) {
        Map<String, Integer> frequency = new HashMap<>();
        for (StoredRecord record : corpus) {
            for (String token : tokens(record.text)) {
                frequency.merge(token, 1, Integer::sum);
            }
        }
        return frequency;
    }

    private static double averageDocLength(List<StoredRecord> corpus) {
        if (corpus == null || corpus.isEmpty()) {
            return 1.0d;
        }
        int sum = 0;
        for (StoredRecord record : corpus) {
            sum += tokenList(record.text).size();
        }
        return (double) sum / corpus.size();
    }

    private static double dot(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double sum = 0.0d;
        for (int index = 0; index < size; index++) {
            sum += left.get(index) * right.get(index);
        }
        return sum;
    }

    private static double euclidean(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double sum = 0.0d;
        for (int index = 0; index < size; index++) {
            double diff = left.get(index) - right.get(index);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(List<Float> left, List<Float> right) {
        double dot = dot(left, right);
        double leftNorm = Math.sqrt(dot(left, left));
        double rightNorm = Math.sqrt(dot(right, right));
        if (Math.abs(leftNorm - 0.0d) < EPSILON || Math.abs(rightNorm - 0.0d) < EPSILON) {
            return 0.0d;
        }
        return dot / (leftNorm * rightNorm);
    }

    private StoredRecord applyOperation(StoredRecord record, Object operation) {
        String operationName = operation.getClass().getSimpleName();
        Map<String, Object> metadata = new LinkedHashMap<>(record.metadata == null ? Map.of() : record.metadata);
        Map<String, Object> fields = new LinkedHashMap<>(record.fields == null ? Map.of() : record.fields);
        List<Float> vector = record.vector == null ? null : new ArrayList<>(record.vector);

        switch (operationName) {
            case "AddScalarFieldOperation" -> {
                String fieldName = readString(operation, "getFieldName");
                Object defaultValue = readValue(operation, "getDefaultValue");
                fields.putIfAbsent(fieldName, defaultValue);
                metadata.putIfAbsent(fieldName, defaultValue);
            }
            case "RenameScalarFieldOperation" -> {
                String oldFieldName = readString(operation, "getOldFieldName");
                String newFieldName = readString(operation, "getNewFieldName");
                renameField(fields, oldFieldName, newFieldName);
                renameField(metadata, oldFieldName, newFieldName);
            }
            case "UpdateScalarFieldTypeOperation" -> {
                String fieldName = readString(operation, "getFieldName");
                String newFieldType = readString(operation, "getNewFieldType");
                if (fields.containsKey(fieldName)) {
                    fields.put(fieldName, coerceScalar(fields.get(fieldName), newFieldType));
                }
                if (metadata.containsKey(fieldName)) {
                    metadata.put(fieldName, coerceScalar(metadata.get(fieldName), newFieldType));
                }
            }
            case "UpdateEmbeddingDimensionOperation" -> {
                String fieldName = readString(operation, "getFieldName");
                int newDimension = readInt(operation, "getNewDimension");
                if ((fieldName == null || fieldName.equals(vectorField) || fieldName.equals("embedding") || fieldName.equals("vector"))
                        && vector != null) {
                    vector = resizeVector(vector, newDimension);
                    fields.put(vectorField, vector);
                }
            }
            default -> {
                return record;
            }
        }
        return new StoredRecord(record.id, record.text, vector, metadata, fields);
    }

    private static void renameField(Map<String, Object> values, String oldFieldName, String newFieldName) {
        if (values.containsKey(oldFieldName)) {
            Object value = values.remove(oldFieldName);
            values.put(newFieldName, value);
        }
    }

    private static Object coerceScalar(Object value, String type) {
        if (value == null || type == null) {
            return value;
        }
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "int", "int32", "int64", "integer", "long" -> value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
            case "float", "double", "number" -> value instanceof Number number
                    ? number.doubleValue()
                    : Double.parseDouble(String.valueOf(value));
            case "bool", "boolean" -> value instanceof Boolean bool
                    ? bool
                    : Boolean.parseBoolean(String.valueOf(value));
            case "string", "varchar", "text" -> String.valueOf(value);
            default -> value;
        };
    }

    private static List<Float> resizeVector(List<Float> vector, int dimension) {
        if (dimension <= 0) {
            return vector;
        }
        List<Float> resized = new ArrayList<>(dimension);
        for (int index = 0; index < dimension; index++) {
            resized.add(index < vector.size() ? vector.get(index) : 0.0f);
        }
        return resized;
    }

    private static String readString(Object target, String methodName) {
        Object value = readValue(target, methodName);
        return value == null ? null : String.valueOf(value);
    }

    private static int readInt(Object target, String methodName) {
        Object value = readValue(target, methodName);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static int batchSize(Map<String, Object> kwargs) {
        Object value = kwargs == null ? null : kwargs.get("batch_size");
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : 128;
    }

    private static Object readValue(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static List<Float> castFloatList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Float> result = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.floatValue());
            }
        }
        return result;
    }

    private static Object firstNonNull(Object left, Object right) {
        return left == null ? right : left;
    }

    private static String validateIndexType(String indexType) {
        String value = indexType == null || indexType.isBlank() ? "hybrid" : indexType;
        if (!Set.of("vector", "sparse", "hybrid").contains(value)) {
            throw new IllegalArgumentException("indexType must be one of vector, sparse, hybrid");
        }
        return value;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class Backend {
        private final Map<String, Map<String, StoredRecord>> collections = new ConcurrentHashMap<>();
        private final Map<String, Map<String, Object>> collectionMetadata = new ConcurrentHashMap<>();
    }

    private record StoredRecord(String id,
                                String text,
                                List<Float> vector,
                                Map<String, Object> metadata,
                                Map<String, Object> fields) {
        Object field(String key) {
            if ("id".equals(key)) {
                return id;
            }
            if ("text".equals(key)) {
                return text;
            }
            if (fields.containsKey(key)) {
                return fields.get(key);
            }
            return metadata.get(key);
        }
    }

    private record ScoredRecord(StoredRecord record, double score) {
    }
}
