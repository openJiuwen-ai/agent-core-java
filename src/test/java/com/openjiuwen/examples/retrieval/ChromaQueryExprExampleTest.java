/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.openjiuwen.spi.store.query.QueryExpressions.eq;
import static com.openjiuwen.spi.store.query.QueryExpressions.gt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChromaQueryExprExampleTest {

    @Test
    void createTestDataMatchesPythonDocumentShape() {
        List<ChromaQueryExprExample.ExampleDocument> docs = ChromaQueryExprExample.createTestData();

        assertEquals(10, docs.size());
        assertEquals("doc_1", docs.get(0).id());
        assertEquals("tech", docs.get(0).metadata().get("category"));
        assertEquals("Alice", docs.get(0).metadata().get("author"));
        assertEquals(50, docs.get(0).metadata().get("score"));
        assertEquals(2020, docs.get(0).metadata().get("year"));
        assertEquals(ChromaQueryExprExample.EMBEDDING_DIM, docs.get(0).embedding().size());
        assertEquals("science", docs.get(9).metadata().get("category"));
        assertEquals(95, docs.get(9).metadata().get("score"));
    }

    @Test
    void comparisonRangeLogicalAndTextCountsMatchPythonScenario() {
        List<ChromaQueryExprExample.ExampleDocument> docs = ChromaQueryExprExample.createTestData();

        assertEquals(Map.of(
                "eq_category_tech", 3,
                "ne_category_tech", 7,
                "gt_score_70", 5,
                "lt_score_70", 4,
                "gte_score_80", 4,
                "lte_score_70", 5
        ), ChromaQueryExprExample.testComparisonOperators(docs));

        assertEquals(Map.of(
                "category_in_tech_science", 6,
                "score_in_70_80_90", 3,
                "year_in_2020_2021_2022", 6
        ), ChromaQueryExprExample.testRangeOperators(docs));

        assertEquals(Map.of(
                "tech_and_score_gt_70", 1,
                "tech_or_science", 6,
                "tech_and_score_gte_70_and_year_gte_2022", 2,
                "author_alice_or_bob", 6,
                "tech_or_science_and_score_gt_70", 3
        ), ChromaQueryExprExample.testLogicalOperators(docs));

        assertEquals(Map.of(
                "exact_contains_tech", 3,
                "prefix_document_1", 2,
                "infix_alice", 3,
                "suffix_score_70", 1
        ), ChromaQueryExprExample.testTextMatching(docs));
    }

    @Test
    void sparseHybridDeleteAndUnsupportedOperationsAreCovered() {
        List<ChromaQueryExprExample.ExampleDocument> docs = ChromaQueryExprExample.createTestData();

        assertEquals(Map.of(
                "sparse_tech_in_tech_category", 3,
                "sparse_document_score_gt_70", 5
        ), ChromaQueryExprExample.testSparseSearchWithFilters(docs));
        assertEquals(Map.of(
                "hybrid_tech_document_category_tech", 3
        ), ChromaQueryExprExample.testHybridSearchWithFilters(docs));

        ChromaQueryExprExample.DeleteSummary deleteSummary = ChromaQueryExprExample.testDeleteWithFilters(docs);
        assertEquals(10, deleteSummary.beforeCount());
        assertEquals(8, deleteSummary.afterCount());
        assertEquals(2, deleteSummary.deletedCount());
        assertEquals(0, deleteSummary.healthRemaining());
        assertTrue(deleteSummary.success());

        Map<String, String> unsupported = ChromaQueryExprExample.demonstrateUnsupportedOperations();
        assertEquals(4, unsupported.size());
        assertTrue(unsupported.get("ArithmeticExpr").contains("Chroma does not support arithmetic"));
        assertTrue(unsupported.get("NullExpr").contains("Chroma does not support null checks"));
        assertTrue(unsupported.get("JSONExpr").contains("Chroma does not support nested JSON"));
        assertTrue(unsupported.get("ArrayExpr").contains("Chroma does not support array indexing"));
    }

    @Test
    void queryExpressionsConvertToChromaDialectMaps() {
        Object eqExpr = ChromaQueryExprExample.toChromaExpr(eq("category", "tech"));
        Object gtExpr = ChromaQueryExprExample.toChromaExpr(gt("score", 70));
        Object logicalExpr = ChromaQueryExprExample.toChromaExpr(eq("category", "tech").and(gt("score", 70)));

        assertInstanceOf(Map.class, eqExpr);
        assertEquals("tech", ((Map<?, ?>) ((Map<?, ?>) eqExpr).get("where")).get("category"));
        assertEquals(Map.of("$gt", 70), ((Map<?, ?>) ((Map<?, ?>) gtExpr).get("where")).get("score"));
        assertTrue(((Map<?, ?>) ((Map<?, ?>) logicalExpr).get("where")).containsKey("$and"));
    }

    @Test
    void runExampleReturnsCompleteReport() {
        ChromaQueryExprExample.DemoReport report = ChromaQueryExprExample.runExample();

        assertEquals(ChromaQueryExprExample.COLLECTION_NAME, report.collectionName());
        assertEquals(ChromaQueryExprExample.DATABASE_NAME, report.databaseName());
        assertEquals(10, report.documents().size());
        assertEquals(3, report.comparisonCounts().get("eq_category_tech"));
        assertEquals(2, report.deleteSummary().deletedCount());
        assertTrue(report.unsupportedErrors().containsKey("ArithmeticExpr"));
    }

}
