/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChromaQueryLanguageTest {

    @BeforeEach
    void setUp() {
        QueryExpr.registerLanguage("chroma", ChromaQueryLanguage.CHROMA_DEF, true);
    }

    @AfterEach
    void tearDown() {
        QueryExpr.resetRegisteredLanguagesForTest();
    }

    @SuppressWarnings("unchecked")
    @Test
    void comparisonOperatorsFollowPythonChromaConventions() {
        Map<String, Object> equalResult = (Map<String, Object>) QueryExpressions.eq("status", "ready").toExpr("chroma");
        Map<String, Object> notEqualResult = (Map<String, Object>) QueryExpressions.ne("status", "ready").toExpr("chroma");
        Map<String, Object> greaterThanResult = (Map<String, Object>) QueryExpressions.gt("score", 7).toExpr("chroma");

        assertThat((Map<String, Object>) equalResult.get("where")).containsEntry("status", "ready");
        assertThat((Map<String, Object>) notEqualResult.get("where"))
                .containsKey("status")
                .extractingByKey("status")
                .isEqualTo(Map.of("$nin", java.util.Collections.singletonList("ready")));
        assertThat((Map<String, Object>) greaterThanResult.get("where"))
                .containsEntry("score", Map.of("$gt", 7));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rangeAndLogicalFiltersMatchPythonStructure() {
        Map<String, Object> rangeResult = (Map<String, Object>) QueryExpressions.inList("tags", List.of("a", "b")).toExpr("chroma");
        Map<String, Object> logicalResult = (Map<String, Object>) QueryExpressions.eq("status", "ready")
                .and(new MatchExpr("content", "hello", MatchMode.INFIX))
                .toExpr("chroma");

        assertThat((Map<String, Object>) rangeResult.get("where"))
                .containsEntry("tags", Map.of("$in", List.of("a", "b")));
        assertThat((Map<String, Object>) logicalResult.get("where"))
                .containsEntry("status", "ready");
        assertThat((Map<String, Object>) logicalResult.get("where_document"))
                .containsEntry("$contains", "hello");
    }

    @SuppressWarnings("unchecked")
    @Test
    void textMatchModesUseContainsOrRegexAsInPython() {
        Map<String, Object> prefixResult = (Map<String, Object>) new MatchExpr("content", "pre", MatchMode.PREFIX).toExpr("chroma");
        Map<String, Object> suffixResult = (Map<String, Object>) new MatchExpr("content", "suf", MatchMode.SUFFIX).toExpr("chroma");
        Map<String, Object> exactResult = (Map<String, Object>) new MatchExpr("content", "same").toExpr("chroma");

        assertThat((Map<String, Object>) prefixResult.get("where_document")).containsEntry("$regex", "^pre");
        assertThat((Map<String, Object>) suffixResult.get("where_document")).containsEntry("$regex", "suf$");
        assertThat((Map<String, Object>) exactResult.get("where_document")).containsEntry("$contains", "same");
    }

    @Test
    void unsupportedChromaOperationsRaisePythonMessages() {
        assertThatThrownBy(() -> new ArithmeticExpr("score", "+", 1, ">", 2).toExpr("chroma"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Chroma does not support arithmetic operations in metadata filters");
        assertThatThrownBy(() -> QueryExpressions.wildcardMatch("status", "x", "like").toExpr("chroma"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Unsupported range operator: like");
        assertThatThrownBy(() -> QueryExpressions.eq("status", "ready").not().toExpr("chroma"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Unsupported logical operator: not");
    }
}
