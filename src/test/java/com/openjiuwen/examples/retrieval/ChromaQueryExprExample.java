/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.core.foundation.store.query.QueryDialectRegistration;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.spi.store.query.ArithmeticExpr;
import com.openjiuwen.spi.store.query.ArrayExpr;
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
import static com.openjiuwen.spi.store.query.QueryExpressions.lt;
import static com.openjiuwen.spi.store.query.QueryExpressions.lte;
import static com.openjiuwen.spi.store.query.QueryExpressions.ne;

/**
 * Chroma Query Expression Example.
 *
 * <p>Mirrors Python's {@code chroma_query_expr} in
 * {@code examples.retrieval.chroma_query_expr}.
 */
public final class ChromaQueryExprExample {

    public static final int EMBEDDING_DIM = 384;
    public static final int DEFAULT_DOC_COUNT = 10;
    public static final String COLLECTION_NAME = "test_query_expr_collection";
    public static final String DATABASE_NAME = "test_db";

    private static final List<String> CATEGORIES = List.of("tech", "science", "business", "health");
    private static final List<String> AUTHORS = List.of("Alice", "Bob", "Charlie", "Diana");

    private ChromaQueryExprExample() {
    }

    /**
     * Create test data with the same metadata shape as the Python example.
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
            docs.add(new ExampleDocument(docId, text, embedding, metadata));
        }
        return docs;
    }

    /**
     * Test comparison operators: eq, ne, gt, lt, gte, lte.
     */
    public static Map<String, Integer> testComparisonOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("eq_category_tech", search(docs, eq("category", "tech"), 10).size());
        counts.put("ne_category_tech", search(docs, ne("category", "tech"), 10).size());
        counts.put("gt_score_70", search(docs, gt("score", 70), 10).size());
        counts.put("lt_score_70", search(docs, lt("score", 70), 10).size());
        counts.put("gte_score_80", search(docs, gte("score", 80), 10).size());
        counts.put("lte_score_70", search(docs, lte("score", 70), 10).size());
        return counts;
    }

    /**
     * Test range operators: in_list.
     */
    public static Map<String, Integer> testRangeOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("category_in_tech_science",
                search(docs, inList("category", List.of("tech", "science")), 10).size());
        counts.put("score_in_70_80_90",
                search(docs, inList("score", List.of(70, 80, 90)), 10).size());
        counts.put("year_in_2020_2021_2022",
                search(docs, inList("year", List.of(2020, 2021, 2022)), 10).size());
        return counts;
    }

    /**
     * Test logical operators: and, or.
     */
    public static Map<String, Integer> testLogicalOperators(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("tech_and_score_gt_70",
                search(docs, eq("category", "tech").and(gt("score", 70)), 10).size());
        counts.put("tech_or_science",
                search(docs, eq("category", "tech").or(eq("category", "science")), 10).size());
        counts.put("tech_and_score_gte_70_and_year_gte_2022",
                search(docs, eq("category", "tech").and(gte("score", 70)).and(gte("year", 2022)), 10).size());
        counts.put("author_alice_or_bob",
                search(docs, eq("author", "Alice").or(eq("author", "Bob")), 10).size());
        counts.put("tech_or_science_and_score_gt_70",
                search(docs, eq("category", "tech").or(eq("category", "science")).and(gt("score", 70)), 10).size());
        return counts;
    }

    /**
     * Test text matching with MatchExpr.
     */
    public static Map<String, Integer> testTextMatching(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("exact_contains_tech",
                search(docs, new MatchExpr("content", "tech", MatchMode.EXACT), 10).size());
        counts.put("prefix_document_1",
                search(docs, new MatchExpr("content", "This is document 1", MatchMode.PREFIX), 10).size());
        counts.put("infix_alice",
                search(docs, new MatchExpr("content", "Alice", MatchMode.INFIX), 10).size());
        counts.put("suffix_score_70",
                search(docs, new MatchExpr("content", "score 70.", MatchMode.SUFFIX), 10).size());
        return counts;
    }

    /**
     * Test sparse search with QueryExpr filters.
     */
    public static Map<String, Integer> testSparseSearchWithFilters(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("sparse_tech_in_tech_category",
                sparseSearch(docs, "tech", 5, eq("category", "tech")).size());
        counts.put("sparse_document_score_gt_70",
                sparseSearch(docs, "document", 5, gt("score", 70)).size());
        return counts;
    }

    /**
     * Test hybrid search with QueryExpr filters.
     */
    public static Map<String, Integer> testHybridSearchWithFilters(List<ExampleDocument> docs) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("hybrid_tech_document_category_tech",
                hybridSearch(docs, "tech document", queryVector(), 5, eq("category", "tech")).size());
        return counts;
    }

    /**
     * Test delete operation with QueryExpr filters.
     */
    public static DeleteSummary testDeleteWithFilters(List<ExampleDocument> docs) {
        List<ExampleDocument> remaining = new ArrayList<>(docs);
        int before = remaining.size();
        boolean success = remaining.removeIf(doc -> matches(doc, eq("category", "health")));
        int after = remaining.size();
        int healthAfter = search(remaining, eq("category", "health"), 10).size();
        return new DeleteSummary(before, after, before - after, healthAfter, success);
    }

    /**
     * Demonstrate operations that ChromaDB does not support.
     */
    public static Map<String, String> demonstrateUnsupportedOperations() {
        Map<String, String> errors = new LinkedHashMap<>();
        captureUnsupported(errors, "ArithmeticExpr",
                new ArithmeticExpr("score", "+", 10, ">", 80));
        captureUnsupported(errors, "NullExpr", new NullExpr("category", false));
        captureUnsupported(errors, "JSONExpr", new JSONExpr("metadata", "category", "==", "tech"));
        captureUnsupported(errors, "ArrayExpr", new ArrayExpr("tags", 0, "==", "python"));
        return errors;
    }

    /**
     * Convert a query expression to Chroma's dialect map.
     */
    public static Object toChromaExpr(QueryExpr expression) {
        QueryDialectRegistration.ensureRegistered();
        return expression.toExpr("chroma");
    }

    /**
     * Run the complete deterministic example.
     */
    public static DemoReport runExample() {
        List<ExampleDocument> docs = createTestData();
        return new DemoReport(
                COLLECTION_NAME,
                DATABASE_NAME,
                docs,
                testComparisonOperators(docs),
                testRangeOperators(docs),
                testLogicalOperators(docs),
                testTextMatching(docs),
                testSparseSearchWithFilters(docs),
                testHybridSearchWithFilters(docs),
                testDeleteWithFilters(docs),
                demonstrateUnsupportedOperations()
        );
    }

    public static void main(String[] args) {
        DemoReport report = runExample();
        System.out.println("ChromaDB QueryExpr Test Script");
        System.out.println("Collection: " + report.collectionName());
        System.out.println("Database: " + report.databaseName());
        System.out.println("Documents: " + report.documents().size());
        System.out.println("Comparison: " + report.comparisonCounts());
        System.out.println("Range: " + report.rangeCounts());
        System.out.println("Logical: " + report.logicalCounts());
        System.out.println("Text: " + report.textCounts());
        System.out.println("Sparse: " + report.sparseCounts());
        System.out.println("Hybrid: " + report.hybridCounts());
        System.out.println("Delete: " + report.deleteSummary());
        System.out.println("Unsupported: " + report.unsupportedErrors());
    }

    public static List<SearchHit> search(List<ExampleDocument> docs, QueryExpr filter, int topK) {
        return docs.stream()
                .filter(doc -> filter == null || matches(doc, filter))
                .limit(topK)
                .map(doc -> new SearchHit(doc.id(), doc.content(), doc.metadata(), scoreFor(doc)))
                .toList();
    }

    public static List<SearchHit> sparseSearch(
            List<ExampleDocument> docs,
            String queryText,
            int topK,
            QueryExpr filter
    ) {
        String needle = queryText != null ? queryText.toLowerCase() : "";
        return docs.stream()
                .filter(doc -> doc.content().toLowerCase().contains(needle))
                .filter(doc -> filter == null || matches(doc, filter))
                .limit(topK)
                .map(doc -> new SearchHit(doc.id(), doc.content(), doc.metadata(), scoreFor(doc)))
                .toList();
    }

    public static List<SearchHit> hybridSearch(
            List<ExampleDocument> docs,
            String queryText,
            List<Double> queryVector,
            int topK,
            QueryExpr filter
    ) {
        if (queryVector == null || queryVector.size() != EMBEDDING_DIM) {
            throw new IllegalArgumentException("queryVector must have " + EMBEDDING_DIM + " dimensions");
        }
        return search(docs, filter, topK);
    }

    public static List<Double> queryVector() {
        return java.util.Collections.nCopies(EMBEDDING_DIM, 0.1d);
    }

    private static boolean matches(ExampleDocument doc, QueryExpr expression) {
        if (expression instanceof ComparisonExpr comparison) {
            return compare(doc.metadata().get(comparison.getField()), comparison.getOperator(), comparison.getValue());
        }
        if (expression instanceof RangeExpr range) {
            return "in".equalsIgnoreCase(range.getOperator())
                    && range.getValue() instanceof Collection<?> values
                    && values.contains(doc.metadata().get(range.getField()));
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
        throw new IllegalArgumentException("Unsupported expression type: " + expression.getClass().getName());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean compare(Object actual, String operator, Object expected) {
        return switch (operator) {
            case "==" -> java.util.Objects.equals(actual, expected);
            case "!=" -> !java.util.Objects.equals(actual, expected);
            case ">" -> ((Comparable) actual).compareTo(expected) > 0;
            case "<" -> ((Comparable) actual).compareTo(expected) < 0;
            case ">=" -> ((Comparable) actual).compareTo(expected) >= 0;
            case "<=" -> ((Comparable) actual).compareTo(expected) <= 0;
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
        };
    }

    private static boolean matchText(ExampleDocument doc, MatchExpr match) {
        String text = "content".equals(match.getField())
                ? doc.content()
                : String.valueOf(doc.metadata().getOrDefault(match.getField(), ""));
        return switch (match.getMatchMode()) {
            case EXACT, INFIX -> text.contains(match.getValue());
            case PREFIX -> text.startsWith(match.getValue());
            case SUFFIX -> text.endsWith(match.getValue());
        };
    }

    private static double scoreFor(ExampleDocument doc) {
        Object score = doc.metadata().get("score");
        return score instanceof Number number ? number.doubleValue() / 100.0d : 0.0d;
    }

    private static void captureUnsupported(Map<String, String> errors, String label, QueryExpr expression) {
        try {
            toChromaExpr(expression);
            errors.put(label, "Unexpected success");
        } catch (RuntimeException ex) {
            if (ex instanceof BaseError baseError && baseError.toMap().get("params") instanceof Map<?, ?> params
                    && params.get("reason") != null) {
                errors.put(label, ex.getClass().getSimpleName() + ": " + params.get("reason"));
            } else {
                errors.put(label, ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }
        }
    }

    public record ExampleDocument(
            String id,
            String content,
            List<Double> embedding,
            Map<String, Object> metadata
    ) {
    }

    public record SearchHit(String id, String text, Map<String, Object> metadata, double score) {
    }

    public record DeleteSummary(int beforeCount, int afterCount, int deletedCount, int healthRemaining, boolean success) {
    }

    public record DemoReport(
            String collectionName,
            String databaseName,
            List<ExampleDocument> documents,
            Map<String, Integer> comparisonCounts,
            Map<String, Integer> rangeCounts,
            Map<String, Integer> logicalCounts,
            Map<String, Integer> textCounts,
            Map<String, Integer> sparseCounts,
            Map<String, Integer> hybridCounts,
            DeleteSummary deleteSummary,
            Map<String, String> unsupportedErrors
    ) {
    }
}
