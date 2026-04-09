/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval.vector_store;

import com.openjiuwen.core.retrieval.common.SearchResult;
import com.openjiuwen.core.retrieval.common.VectorStoreConfig;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Local in-memory vector store used for translated retrieval regression tests.
 */
public class InMemoryVectorStore implements VectorStore, SchemaMutableVectorStore {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}_]+");
    private static final double BM25_K1 = 1.5;
    private static final double BM25_B = 0.75;
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
        this.indexType = RetrievalValidation.validateIndexType(indexType, "indexType");
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

    @Override
    public String getCollectionName() {
        return collectionName;
    }

    @Override
    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
        backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
    }

    @Override
    public VectorStore withCollection(String collectionName) {
        return new InMemoryVectorStore(this, collectionName);
    }

    @Override
    public void add(List<Map<String, Object>> data, Integer batchSize, Map<String, Object> options) {
        if (data == null || data.isEmpty()) {
            return;
        }
        Map<String, StoredRecord> collection = currentCollection();
        for (Map<String, Object> item : data) {
            Map<String, Object> metadata = castMap(item.get(metadataField));
            String id = firstNonBlank(
                    stringValue(item.get("id")),
                    stringValue(item.get("chunk_id")),
                    stringValue(metadata.get("chunk_id")),
                    UUID.randomUUID().toString());
            String text = stringValue(item.get(textField));
            List<Float> vector = castFloatList(item.get(vectorField));
            collection.put(id, new StoredRecord(id, text, vector, metadata, new LinkedHashMap<>(item)));
        }
    }

    @Override
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
    public List<SearchResult> sparseSearch(String queryText, int topK, Map<String, Object> filters, Map<String, Object> options) {
        List<StoredRecord> corpus = filteredRecords(filters);
        List<ScoredRecord> scored = new ArrayList<>();
        for (StoredRecord record : corpus) {
            scored.add(new ScoredRecord(record, sparseScore(queryText, record.text, corpus)));
        }
        return toSearchResults(scored, topK);
    }

    @Override
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
                    ? 0.0
                    : normalizedVectorScore(queryVector, record.vector);
            scored.add(new ScoredRecord(record, alpha * vector + (1.0 - alpha) * sparse));
        }
        return toSearchResults(scored, topK);
    }

    @Override
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
    public boolean tableExists(String tableName) {
        return backend.collections.containsKey(tableName);
    }

    @Override
    public void deleteTable(String tableName) {
        backend.collections.remove(tableName);
        backend.collectionMetadata.remove(tableName);
    }

    @Override
    public List<SearchResult> queryByFilters(Map<String, Object> filters, int limit) {
        List<SearchResult> results = new ArrayList<>();
        for (StoredRecord record : filteredRecords(filters)) {
            results.add(new SearchResult(record.id, record.text, 0.0, record.metadata));
            if (results.size() >= limit) {
                break;
            }
        }
        return results;
    }

    @Override
    public long count(String tableName) {
        return backend.collections.getOrDefault(tableName, Map.of()).size();
    }

    @Override
    public List<String> listCollectionNames() {
        return new ArrayList<>(backend.collections.keySet());
    }

    @Override
    public Map<String, Object> getCollectionMetadata(String collectionName) {
        return new LinkedHashMap<>(backend.collectionMetadata.getOrDefault(collectionName, Map.of()));
    }

    @Override
    public void updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        backend.collectionMetadata
                .computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>())
                .putAll(metadata);
    }

    @Override
    public void updateSchema(String collectionName, List<?> operations) {
        if (operations == null || operations.isEmpty()) {
            return;
        }
        Map<String, StoredRecord> collection = backend.collections.computeIfAbsent(collectionName, key -> new ConcurrentHashMap<>());
        for (Map.Entry<String, StoredRecord> entry : new ArrayList<>(collection.entrySet())) {
            StoredRecord current = entry.getValue();
            StoredRecord updated = current;
            for (Object operation : operations) {
                updated = applyOperation(updated, operation);
            }
            collection.put(entry.getKey(), updated);
        }
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getDistanceMetric() {
        return distanceMetric;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    @Override
    public String getTextField() {
        return textField;
    }

    @Override
    public String getVectorField() {
        return vectorField;
    }

    @Override
    public String getSparseVectorField() {
        return sparseVectorField;
    }

    @Override
    public String getMetadataField() {
        return metadataField;
    }

    @Override
    public String getDocIdField() {
        return docIdField;
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
        int limit = Math.min(topK, scored.size());
        for (int i = 0; i < limit; i++) {
            ScoredRecord item = scored.get(i);
            results.add(new SearchResult(item.record.id, item.record.text, item.score, item.record.metadata));
        }
        return results;
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
            case "euclidean" -> 1.0 / (1.0 + Math.max(0.0, -score));
            default -> (score + 1.0) / 2.0;
        };
    }

    private static double sparseScore(String queryText, String text, List<StoredRecord> corpus) {
        if (queryText == null || text == null) {
            return 0.0;
        }
        List<String> queryTokens = tokenList(queryText);
        List<String> docTokens = tokenList(text);
        if (queryTokens.isEmpty() || docTokens.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> termFrequency = termFrequency(docTokens);
        Map<String, Integer> documentFrequency = documentFrequency(corpus == null ? List.of() : corpus);
        double averageDocLength = averageDocLength(corpus == null || corpus.isEmpty() ? List.of() : corpus);
        double docLength = docTokens.size();
        double score = 0.0;
        for (String token : new LinkedHashSet<>(queryTokens)) {
            int tf = termFrequency.getOrDefault(token, 0);
            if (tf == 0) {
                continue;
            }
            int df = documentFrequency.getOrDefault(token, 0);
            double idf = Math.log(1.0 + ((Math.max(corpus == null ? 0 : corpus.size(), 1) - df) + 0.5) / (df + 0.5));
            double denominator = tf + BM25_K1 * (1.0 - BM25_B + BM25_B * (docLength / Math.max(averageDocLength, 1.0)));
            score += idf * (tf * (BM25_K1 + 1.0)) / Math.max(denominator, 1e-9);
        }
        return score;
    }

    private static Set<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : TOKEN_SPLIT.split(text.toLowerCase())) {
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
        for (String part : TOKEN_SPLIT.split(text.toLowerCase())) {
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
            return 1.0;
        }
        int sum = 0;
        for (StoredRecord record : corpus) {
            sum += tokenList(record.text).size();
        }
        return (double) sum / corpus.size();
    }

    private static double dot(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            sum += left.get(i) * right.get(i);
        }
        return sum;
    }

    private static double euclidean(List<Float> left, List<Float> right) {
        int size = Math.min(left.size(), right.size());
        double sum = 0.0;
        for (int i = 0; i < size; i++) {
            double diff = left.get(i) - right.get(i);
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private static double cosine(List<Float> left, List<Float> right) {
        double dot = dot(left, right);
        double leftNorm = Math.sqrt(dot(left, left));
        double rightNorm = Math.sqrt(dot(right, right));
        if (Math.abs(leftNorm - 0.0) < EPSILON || Math.abs(rightNorm - 0.0) < EPSILON) {
            return 0.0;
        }
        return dot / (leftNorm * rightNorm);
    }

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
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

    private record StoredRecord(String id, String text, List<Float> vector, Map<String, Object> metadata, Map<String, Object> fields) {
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
        return switch (type.toLowerCase()) {
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
        for (int i = 0; i < dimension; i++) {
            resized.add(i < vector.size() ? vector.get(i) : 0.0f);
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

    private static Object readValue(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception ignored) {
            return null;
        }
    }
}
