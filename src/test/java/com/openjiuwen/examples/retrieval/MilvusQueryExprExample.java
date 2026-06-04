/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.store.query.QueryDialectRegistration;
import com.openjiuwen.spi.store.query.ArithmeticExpr;
import com.openjiuwen.spi.store.query.ComparisonExpr;
import com.openjiuwen.spi.store.query.JSONExpr;
import com.openjiuwen.spi.store.query.LogicalExpr;
import com.openjiuwen.spi.store.query.MatchExpr;
import com.openjiuwen.spi.store.query.MatchMode;
import com.openjiuwen.spi.store.query.NullExpr;
import com.openjiuwen.spi.store.query.QueryExpr;
import com.openjiuwen.spi.store.query.RangeExpr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.openjiuwen.spi.store.query.QueryExpressions.eq;
import static com.openjiuwen.spi.store.query.QueryExpressions.gt;
import static com.openjiuwen.spi.store.query.QueryExpressions.gte;
import static com.openjiuwen.spi.store.query.QueryExpressions.inList;
import static com.openjiuwen.spi.store.query.QueryExpressions.isNotNull;
import static com.openjiuwen.spi.store.query.QueryExpressions.isNull;
import static com.openjiuwen.spi.store.query.QueryExpressions.jsonKey;
import static com.openjiuwen.spi.store.query.QueryExpressions.lt;
import static com.openjiuwen.spi.store.query.QueryExpressions.lte;
import static com.openjiuwen.spi.store.query.QueryExpressions.ne;

/**
 * Milvus Query Expression Example.
 *
 * Mirrors Python's {@code milvus_query_expr} in
 * {@code examples.retrieval.milvus_query_expr}.
 */
public final class MilvusQueryExprExample {

    public static final String MILVUS_URI = "http://localhost:19530";
    public static final String DATABASE_NAME = "test_query_expr";
    public static final String COLLECTION_NAME = "test_query_expr_collection";
    public static final int EMBEDDING_DIM = 384;
    public static final int DEFAULT_DOC_COUNT = 10;
    public static final int DEFAULT_BATCH_SIZE = 5;

    private static final List<String> CATEGORIES = List.of("tech", "science", "business", "health");
    private static final List<String> AUTHORS = List.of("Alice", "Bob", "Charlie", "Diana");

    private MilvusQueryExprExample() {
    }

    /**
     * Build the collection schema and index plan used by the Python Milvus example.
     */
    public static CollectionDefinition collectionDefinition() {
        return new CollectionDefinition(
                COLLECTION_NAME,
                DATABASE_NAME,
                List.of(
                        new FieldDefinition("id", "VARCHAR", true, false, 256, null),
                        new FieldDefinition("content", "VARCHAR", false, false, 65535, null),
                        new FieldDefinition("embedding", "FLOAT_VECTOR", false, false, null, EMBEDDING_DIM),
                        new FieldDefinition("metadata", "JSON", false, false, null, null),
                        new FieldDefinition("category", "VARCHAR", false, false, 256, null),
                        new FieldDefinition("author", "VARCHAR", false, false, 256, null),
                        new FieldDefinition("score", "INT64", false, false, null, null),
                        new FieldDefinition("year", "INT64", false, false, null, null),
                        new FieldDefinition("optional_field", "JSON", false, true, null, null)
                ),
                List.of(
                        new IndexDefinition("embedding", "AUTOINDEX", "COSINE"),
                        new IndexDefinition("category", "INVERTED", null),
                        new IndexDefinition("author", "INVERTED", null),
                        new IndexDefinition("score", "STL_SORT", null),
                        new IndexDefinition("year", "STL_SORT", null)
                )
        );
    }

    /**
     * Create test data with the same metadata and top-level filter fields as Python.
     */
    public static List<ExampleDocument> createTestData() {
        return createTestData(DEFAULT_DOC_COUNT);
    }

    public static List<ExampleDocument> createTestData(int numDocs) {
        List<ExampleDocument> docs = new ArrayList<>();
        for (int i = 0; i < numDocs; i++) {
            String docId = "doc_" + (i + 1);
            String category = CATEGORIES.get(i % CATEGORIES.size());
            String author = AUTHORS.get(i % AUTHORS.size());
            int score = 50 + i * 5;
            int year = 2020 + (i % 5);
            List<Double> embedding = new ArrayList<>();
            for (int j = 0; j < EMBEDDING_DIM; j++) {
                embedding.add(0.1d * (i + j));
            }
            String text = "This is document " + (i + 1) + " about " + category
                    + " written by " + author + " in " + year + " with score " + score + ".";
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("category", category);
            metadata.put("author", author);
            metadata.put("score", score);
            metadata.put("year", year);
            metadata.put("document_id", docId);
            metadata.put("chunk_id", "chunk_" + (i + 1));
            docs.add(new ExampleDocument(
                    docId,
                    text,
                    embedding,
                    metadata,
                    category,
                    author,
                    score,
                    year,
                    optionalFieldForInsertIndex(i)
            ));
        }
        return docs;
    }

    /**
     * Test comparison operators: eq, ne, gt, lt, gte, lte.
     */
    public static Map<String, Integer> testComparisonOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("eq_category_tech", searchVectors(docs, queryVector(), 10, eq("category", "tech")).size());
        counts.put("ne_category_tech", searchVectors(docs, queryVector(), 10, ne("category", "tech")).size());
        counts.put("gt_score_70", searchVectors(docs, queryVector(), 10, gt("score", 70)).size());
        counts.put("lt_score_70", searchVectors(docs, queryVector(), 10, lt("score", 70)).size());
        counts.put("gte_score_80", searchVectors(docs, queryVector(), 10, gte("score", 80)).size());
        counts.put("lte_score_70", searchVectors(docs, queryVector(), 10, lte("score", 70)).size());
        return counts;
    }

    /**
     * Test range operators: in_list.
     */
    public static Map<String, Integer> testRangeOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("category_in_tech_science",
                searchVectors(docs, queryVector(), 10, inList("category", List.of("tech", "science"))).size());
        counts.put("score_in_70_80_90",
                searchVectors(docs, queryVector(), 10, inList("score", List.of(70, 80, 90))).size());
        counts.put("year_in_2020_2021_2022",
                searchVectors(docs, queryVector(), 10, inList("year", List.of(2020, 2021, 2022))).size());
        return counts;
    }

    /**
     * Test logical operators: and, or.
     */
    public static Map<String, Integer> testLogicalOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("tech_and_score_gt_70",
                searchVectors(docs, queryVector(), 10, eq("category", "tech").and(gt("score", 70))).size());
        counts.put("tech_or_science",
                searchVectors(docs, queryVector(), 10, eq("category", "tech").or(eq("category", "science"))).size());
        counts.put("tech_and_score_gte_70_and_year_gte_2022",
                searchVectors(docs, queryVector(), 10,
                        eq("category", "tech").and(gte("score", 70)).and(gte("year", 2022))).size());
        counts.put("author_alice_or_bob",
                searchVectors(docs, queryVector(), 10, eq("author", "Alice").or(eq("author", "Bob"))).size());
        counts.put("tech_or_science_and_score_gt_70",
                searchVectors(docs, queryVector(), 10,
                        eq("category", "tech").or(eq("category", "science")).and(gt("score", 70))).size());
        return counts;
    }

    /**
     * Test text matching with MatchExpr.
     */
    public static Map<String, Integer> testTextMatching(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("exact_contains_tech",
                searchVectors(docs, queryVector(), 10,
                        new MatchExpr("content", "tech", MatchMode.EXACT)).size());
        counts.put("prefix_document_1",
                searchVectors(docs, queryVector(), 10,
                        new MatchExpr("content", "This is document 1", MatchMode.PREFIX)).size());
        counts.put("infix_alice",
                searchVectors(docs, queryVector(), 10,
                        new MatchExpr("content", "Alice", MatchMode.INFIX)).size());
        counts.put("suffix_score_70",
                searchVectors(docs, queryVector(), 10,
                        new MatchExpr("content", "score 70.", MatchMode.SUFFIX)).size());
        return counts;
    }

    /**
     * Test arithmetic operators supported by Milvus.
     */
    public static Map<String, Integer> testArithmeticOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("score_plus_10_gt_80",
                searchVectors(docs, queryVector(), 10,
                        new ArithmeticExpr("score", "+", 10, ">", 80)).size());
        counts.put("score_times_2_gte_150",
                searchVectors(docs, queryVector(), 10,
                        new ArithmeticExpr("score", "*", 2, ">=", 150)).size());
        return counts;
    }

    /**
     * Test null value checks supported by Milvus.
     */
    public static Map<String, Integer> testNullOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("category_is_not_null",
                searchVectors(docs, queryVector(), 10, isNotNull("category")).size());
        counts.put("optional_field_is_null",
                searchVectors(docs, queryVector(), 10, isNull("optional_field")).size());
        return counts;
    }

    /**
     * Test JSON field operations supported by Milvus.
     */
    public static Map<String, Integer> testJsonOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("metadata_category_tech",
                searchVectors(docs, queryVector(), 10,
                        jsonKey("metadata", "category", "==", "tech")).size());
        counts.put("metadata_score_gt_70",
                searchVectors(docs, queryVector(), 10,
                        jsonKey("metadata", "score", ">", 70)).size());
        return counts;
    }

    /**
     * Test delete operation with QueryExpr filters.
     */
    public static DeleteSummary testDeleteWithFilters(List<ExampleDocument> docs) {
        List<ExampleDocument> remaining = new ArrayList<>(docs);
        int before = searchVectors(remaining, queryVector(), 100, null).size();
        boolean success = remaining.removeIf(doc -> matches(doc, eq("category", "health")));
        int after = searchVectors(remaining, queryVector(), 100, null).size();
        int healthAfter = searchVectors(remaining, queryVector(), 10, eq("category", "health")).size();
        return new DeleteSummary(before, after, before - after, healthAfter, success);
    }

    /**
     * Convert a query expression to Milvus' filter-string dialect.
     */
    public static String toMilvusExpr(QueryExpr expression) {
        QueryDialectRegistration.ensureRegistered();
        return String.valueOf(expression.toExpr("milvus"));
    }

    /**
     * Run the complete deterministic example without requiring a live Milvus service.
     */
    public static DemoReport runExample() {
        List<ExampleDocument> docs = createTestData();
        return new DemoReport(
                MILVUS_URI,
                COLLECTION_NAME,
                DATABASE_NAME,
                collectionDefinition(),
                docs,
                testComparisonOperators(docs),
                testRangeOperators(docs),
                testLogicalOperators(docs),
                testTextMatching(docs),
                testArithmeticOperators(docs),
                testNullOperators(docs),
                testJsonOperators(docs),
                testDeleteWithFilters(docs)
        );
    }

    public static void main(String[] args) {
        DemoReport report = runExample();
        System.out.println("Milvus QueryExpr Test Script");
        System.out.println("URI: " + report.milvusUri());
        System.out.println("Collection: " + report.collectionName());
        System.out.println("Database: " + report.databaseName());
        System.out.println("Documents: " + report.documents().size());
        System.out.println("Comparison: " + report.comparisonCounts());
        System.out.println("Range: " + report.rangeCounts());
        System.out.println("Logical: " + report.logicalCounts());
        System.out.println("Text: " + report.textCounts());
        System.out.println("Arithmetic: " + report.arithmeticCounts());
        System.out.println("Null: " + report.nullCounts());
        System.out.println("JSON: " + report.jsonCounts());
        System.out.println("Delete: " + report.deleteSummary());
    }

    public static List<SearchHit> searchVectors(
            List<ExampleDocument> docs,
            List<Double> queryVector,
            int topK,
            QueryExpr filters
    ) {
        if (queryVector == null || queryVector.size() != EMBEDDING_DIM) {
            throw new IllegalArgumentException("queryVector must have " + EMBEDDING_DIM + " dimensions");
        }
        return docs.stream()
                .filter(doc -> filters == null || matches(doc, filters))
                .limit(topK)
                .map(doc -> new SearchHit(doc.id(), doc.content(), scoreFor(doc), doc.metadata()))
                .toList();
    }

    public static List<Double> queryVector() {
        return java.util.Collections.nCopies(EMBEDDING_DIM, 0.1d);
    }

    private static Object optionalFieldForInsertIndex(int zeroBasedIndex) {
        int batchStart = (zeroBasedIndex / DEFAULT_BATCH_SIZE) * DEFAULT_BATCH_SIZE;
        return batchStart % 2 == 0 ? null : 1;
    }

    private static boolean matches(ExampleDocument doc, QueryExpr expression) {
        if (expression instanceof ComparisonExpr comparison) {
            return compare(fieldValue(doc, comparison.getField()), comparison.getOperator(), comparison.getValue());
        }
        if (expression instanceof RangeExpr range) {
            return "in".equalsIgnoreCase(range.getOperator())
                    && range.getValue() instanceof Collection<?> values
                    && values.contains(fieldValue(doc, range.getField()));
        }
        if (expression instanceof LogicalExpr logical) {
            String op = logical.getOperator().toLowerCase();
            return switch (op) {
                case "and" -> matches(doc, logical.getLeft()) && matches(doc, logical.getRight());
                case "or" -> matches(doc, logical.getLeft()) || matches(doc, logical.getRight());
                case "not" -> !matches(doc, logical.getLeft());
                default -> throw new IllegalArgumentException("Unsupported logical operator: " + logical.getOperator());
            };
        }
        if (expression instanceof MatchExpr match) {
            return matchText(doc, match);
        }
        if (expression instanceof ArithmeticExpr arithmetic) {
            return matchArithmetic(doc, arithmetic);
        }
        if (expression instanceof NullExpr nullExpr) {
            Object value = fieldValue(doc, nullExpr.getField());
            return nullExpr.isNull() ? value == null : value != null;
        }
        if (expression instanceof JSONExpr json) {
            return matchJson(doc, json);
        }
        throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compare(Object actual, String operator, Object expected) {
        return switch (operator) {
            case "==" -> java.util.Objects.equals(actual, expected);
            case "!=" -> !java.util.Objects.equals(actual, expected);
            case ">" -> actual != null && ((Comparable) actual).compareTo(expected) > 0;
            case "<" -> actual != null && ((Comparable) actual).compareTo(expected) < 0;
            case ">=" -> actual != null && ((Comparable) actual).compareTo(expected) >= 0;
            case "<=" -> actual != null && ((Comparable) actual).compareTo(expected) <= 0;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private static boolean matchText(ExampleDocument doc, MatchExpr match) {
        String text = String.valueOf(fieldValue(doc, match.getField()));
        return switch (match.getMatchMode()) {
            case EXACT, INFIX -> text.contains(match.getValue());
            case PREFIX -> text.startsWith(match.getValue());
            case SUFFIX -> text.endsWith(match.getValue());
        };
    }

    private static boolean matchArithmetic(ExampleDocument doc, ArithmeticExpr arithmetic) {
        Object value = fieldValue(doc, arithmetic.getField());
        if (!(value instanceof Number number)) {
            return false;
        }
        double arithmeticValue = arithmetic.getArithmeticValue().doubleValue();
        double computed = switch (arithmetic.getArithmeticOperator()) {
            case "+" -> number.doubleValue() + arithmeticValue;
            case "-" -> number.doubleValue() - arithmeticValue;
            case "*" -> number.doubleValue() * arithmeticValue;
            case "/" -> number.doubleValue() / arithmeticValue;
            default -> throw new IllegalArgumentException(
                    "Unsupported arithmetic operator: " + arithmetic.getArithmeticOperator());
        };
        return compareDouble(computed, arithmetic.getComparisonOperator(), arithmetic.getComparisonValue().doubleValue());
    }

    private static boolean matchJson(ExampleDocument doc, JSONExpr json) {
        Object root = fieldValue(doc, json.getField());
        if (!(root instanceof Map<?, ?> map)) {
            return false;
        }
        return compare(map.get(json.getKey()), json.getOperator(), json.getValue());
    }

    private static boolean compareDouble(double actual, String operator, double expected) {
        return switch (operator) {
            case "==" -> Double.compare(actual, expected) == 0;
            case "!=" -> Double.compare(actual, expected) != 0;
            case ">" -> actual > expected;
            case "<" -> actual < expected;
            case ">=" -> actual >= expected;
            case "<=" -> actual <= expected;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private static Object fieldValue(ExampleDocument doc, String field) {
        return switch (field) {
            case "id" -> doc.id();
            case "content" -> doc.content();
            case "embedding" -> doc.embedding();
            case "metadata" -> doc.metadata();
            case "category" -> doc.category();
            case "author" -> doc.author();
            case "score" -> doc.score();
            case "year" -> doc.year();
            case "optional_field" -> doc.optionalField();
            default -> doc.metadata().get(field);
        };
    }

    private static double scoreFor(ExampleDocument doc) {
        return doc.score() / 100.0d;
    }

    public record CollectionDefinition(
            String collectionName,
            String databaseName,
            List<FieldDefinition> fields,
            List<IndexDefinition> indexes
    ) {
    }

    public record FieldDefinition(
            String name,
            String dataType,
            boolean primary,
            boolean nullable,
            Integer maxLength,
            Integer dimension
    ) {
    }

    public record IndexDefinition(String fieldName, String indexType, String metricType) {
    }

    public record ExampleDocument(
            String id,
            String content,
            List<Double> embedding,
            Map<String, Object> metadata,
            String category,
            String author,
            int score,
            int year,
            Object optionalField
    ) {
    }

    public record SearchHit(String id, String text, double score, Map<String, Object> metadata) {
    }

    public record DeleteSummary(int beforeCount, int afterCount, int deletedCount, int healthRemaining, boolean success) {
    }

    public record DemoReport(
            String milvusUri,
            String collectionName,
            String databaseName,
            CollectionDefinition collectionDefinition,
            List<ExampleDocument> documents,
            Map<String, Integer> comparisonCounts,
            Map<String, Integer> rangeCounts,
            Map<String, Integer> logicalCounts,
            Map<String, Integer> textCounts,
            Map<String, Integer> arithmeticCounts,
            Map<String, Integer> nullCounts,
            Map<String, Integer> jsonCounts,
            DeleteSummary deleteSummary
    ) {
    }
}
