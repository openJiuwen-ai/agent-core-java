/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.examples.retrieval;

import com.openjiuwen.spi.store.query.ArithmeticExpr;
import com.openjiuwen.spi.store.query.MatchExpr;
import com.openjiuwen.spi.store.query.MatchMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.openjiuwen.spi.store.query.QueryExpressions.eq;
import static com.openjiuwen.spi.store.query.QueryExpressions.gt;
import static com.openjiuwen.spi.store.query.QueryExpressions.inList;
import static com.openjiuwen.spi.store.query.QueryExpressions.isNull;
import static com.openjiuwen.spi.store.query.QueryExpressions.jsonKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MilvusQueryExprExampleTest {

    @Test
    void collectionDefinitionMatchesPythonMilvusSchema() {
        MilvusQueryExprExample.CollectionDefinition definition = MilvusQueryExprExample.collectionDefinition();

        assertEquals(MilvusQueryExprExample.COLLECTION_NAME, definition.collectionName());
        assertEquals(MilvusQueryExprExample.DATABASE_NAME, definition.databaseName());
        assertEquals(9, definition.fields().size());
        assertEquals("id", definition.fields().get(0).name());
        assertTrue(definition.fields().get(0).primary());
        assertEquals(MilvusQueryExprExample.EMBEDDING_DIM, definition.fields().get(2).dimension());
        assertTrue(definition.fields().get(8).nullable());
        assertEquals(5, definition.indexes().size());
        assertEquals("AUTOINDEX", definition.indexes().get(0).indexType());
        assertEquals("COSINE", definition.indexes().get(0).metricType());
    }

    @Test
    void createTestDataMatchesPythonInsertedDocumentShape() {
        List<MilvusQueryExprExample.ExampleDocument> docs = MilvusQueryExprExample.createTestData();

        assertEquals(10, docs.size());
        assertEquals("doc_1", docs.get(0).id());
        assertEquals("tech", docs.get(0).metadata().get("category"));
        assertEquals("Alice", docs.get(0).metadata().get("author"));
        assertEquals(50, docs.get(0).metadata().get("score"));
        assertEquals(2020, docs.get(0).metadata().get("year"));
        assertEquals(MilvusQueryExprExample.EMBEDDING_DIM, docs.get(0).embedding().size());
        assertNull(docs.get(0).optionalField());
        assertEquals(1, docs.get(5).optionalField());
        assertEquals("science", docs.get(9).metadata().get("category"));
        assertEquals(95, docs.get(9).metadata().get("score"));
    }

    @Test
    void comparisonRangeLogicalAndTextCountsMatchPythonScenario() {
        List<MilvusQueryExprExample.ExampleDocument> docs = MilvusQueryExprExample.createTestData();

        assertEquals(Map.of(
                "eq_category_tech", 3,
                "ne_category_tech", 7,
                "gt_score_70", 5,
                "lt_score_70", 4,
                "gte_score_80", 4,
                "lte_score_70", 5
        ), MilvusQueryExprExample.testComparisonOperators(docs));

        assertEquals(Map.of(
                "category_in_tech_science", 6,
                "score_in_70_80_90", 3,
                "year_in_2020_2021_2022", 6
        ), MilvusQueryExprExample.testRangeOperators(docs));

        assertEquals(Map.of(
                "tech_and_score_gt_70", 1,
                "tech_or_science", 6,
                "tech_and_score_gte_70_and_year_gte_2022", 2,
                "author_alice_or_bob", 6,
                "tech_or_science_and_score_gt_70", 3
        ), MilvusQueryExprExample.testLogicalOperators(docs));

        assertEquals(Map.of(
                "exact_contains_tech", 3,
                "prefix_document_1", 2,
                "infix_alice", 3,
                "suffix_score_70", 1
        ), MilvusQueryExprExample.testTextMatching(docs));
    }

    @Test
    void milvusSpecificArithmeticNullJsonAndDeleteScenariosAreCovered() {
        List<MilvusQueryExprExample.ExampleDocument> docs = MilvusQueryExprExample.createTestData();

        assertEquals(Map.of(
                "score_plus_10_gt_80", 5,
                "score_times_2_gte_150", 5
        ), MilvusQueryExprExample.testArithmeticOperators(docs));
        assertEquals(Map.of(
                "category_is_not_null", 10,
                "optional_field_is_null", 5
        ), MilvusQueryExprExample.testNullOperators(docs));
        assertEquals(Map.of(
                "metadata_category_tech", 3,
                "metadata_score_gt_70", 5
        ), MilvusQueryExprExample.testJsonOperators(docs));

        MilvusQueryExprExample.DeleteSummary deleteSummary = MilvusQueryExprExample.testDeleteWithFilters(docs);
        assertEquals(10, deleteSummary.beforeCount());
        assertEquals(8, deleteSummary.afterCount());
        assertEquals(2, deleteSummary.deletedCount());
        assertEquals(0, deleteSummary.healthRemaining());
        assertTrue(deleteSummary.success());
    }

    @Test
    void queryExpressionsConvertToMilvusDialectStrings() {
        assertEquals("category == \"tech\"", MilvusQueryExprExample.toMilvusExpr(eq("category", "tech")));
        assertEquals("score > 70", MilvusQueryExprExample.toMilvusExpr(gt("score", 70)));
        assertEquals("category in [\"tech\",\"science\"]",
                MilvusQueryExprExample.toMilvusExpr(inList("category", List.of("tech", "science"))));
        assertEquals("(category == \"tech\") and (score > 70)",
                MilvusQueryExprExample.toMilvusExpr(eq("category", "tech").and(gt("score", 70))));
        assertEquals("TEXT_MATCH(content, \"tech\")",
                MilvusQueryExprExample.toMilvusExpr(new MatchExpr("content", "tech", MatchMode.EXACT)));
        assertEquals("content like \"Alice%\"",
                MilvusQueryExprExample.toMilvusExpr(new MatchExpr("content", "Alice", MatchMode.PREFIX)));
        assertEquals("score + 10> 80",
                MilvusQueryExprExample.toMilvusExpr(new ArithmeticExpr("score", "+", 10, ">", 80)));
        assertEquals("optional_field is null", MilvusQueryExprExample.toMilvusExpr(isNull("optional_field")));
        assertEquals("metadata[\"category\"] == \"tech\"",
                MilvusQueryExprExample.toMilvusExpr(jsonKey("metadata", "category", "==", "tech")));
    }

    @Test
    void runExampleReturnsCompleteReport() {
        MilvusQueryExprExample.DemoReport report = MilvusQueryExprExample.runExample();

        assertEquals(MilvusQueryExprExample.MILVUS_URI, report.milvusUri());
        assertEquals(MilvusQueryExprExample.COLLECTION_NAME, report.collectionName());
        assertEquals(MilvusQueryExprExample.DATABASE_NAME, report.databaseName());
        assertEquals(10, report.documents().size());
        assertEquals(3, report.comparisonCounts().get("eq_category_tech"));
        assertEquals(5, report.arithmeticCounts().get("score_plus_10_gt_80"));
        assertEquals(5, report.nullCounts().get("optional_field_is_null"));
        assertEquals(2, report.deleteSummary().deletedCount());
    }
}
