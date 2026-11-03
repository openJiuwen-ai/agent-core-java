/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MilvusQueryLanguageTest {

    @BeforeEach
    void setUp() {
        QueryExpr.registerLanguage("milvus", MilvusQueryLanguage.MILVUS_DEF, true);
    }

    @AfterEach
    void tearDown() {
        QueryExpr.resetRegisteredLanguagesForTest();
    }

    @Test
    void comparisonAndCollectionRangeExpressionsMatchPythonStrings() {
        assertThat(QueryExpressions.eq("status", "ready").toExpr("milvus"))
                .isEqualTo("status == \"ready\"");
        assertThat(QueryExpressions.inList("tags", List.of("a", "b")).toExpr("milvus"))
                .isEqualTo("tags in [\"a\",\"b\"]");
        assertThat(QueryExpressions.inList("scores", List.of(1, 2)).toExpr("milvus"))
                .isEqualTo("scores in [1,2]");
    }

    @Test
    void likeAndTextMatchPreserveMilvusFormatting() {
        assertThat(QueryExpressions.wildcardMatch("title", "%abc%", "like").toExpr("milvus"))
                .isEqualTo("title like \"%abc%\"");
        assertThat(new MatchExpr("content", "abc", MatchMode.EXACT).toExpr("milvus"))
                .isEqualTo("TEXT_MATCH(content, \"abc\")");
        assertThat(new MatchExpr("content", "abc", MatchMode.PREFIX).toExpr("milvus"))
                .isEqualTo("content like \"abc%\"");
    }

    @Test
    void arithmeticNullJsonArrayAndLogicalExpressionsFollowPythonOutput() {
        assertThat(new ArithmeticExpr("score", "+", 1, ">", 2).toExpr("milvus"))
                .isEqualTo("score + 1> 2");
        assertThat(QueryExpressions.isNull("deleted_at").toExpr("milvus"))
                .isEqualTo("deleted_at is null");
        assertThat(QueryExpressions.jsonKey("payload", "user", "==", "alice").toExpr("milvus"))
                .isEqualTo("payload[\"user\"] == \"alice\"");
        assertThat(QueryExpressions.arrayIndex("items", 0, "==", "a").toExpr("milvus"))
                .isEqualTo("items[0] == \"a\"");
        assertThat(QueryExpressions.eq("status", "ready").not().toExpr("milvus"))
                .isEqualTo("not (status == \"ready\")");
        assertThat(QueryExpressions.eq("status", "ready").and(QueryExpressions.gt("score", 3)).toExpr("milvus"))
                .isEqualTo("(status == \"ready\") and (score > 3)");
    }

    @Test
    void invalidRangeAndLogicalFormsRaisePythonMessages() {
        assertThatThrownBy(() -> QueryExpressions.wildcardMatch("title", "abc", "like").toExpr("milvus"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Milvus's like operator uses % for wildcard matching");
        assertThatThrownBy(() -> QueryExpressions.wildcardMatch("title", 123 + "", "between").toExpr("milvus"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Unsupported range operator: between");
        assertThatThrownBy(() -> new LogicalExpr("and", QueryExpressions.eq("status", "ready"), null).toExpr("milvus"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("and operator requires both left and right operands");
        assertThatThrownBy(() -> new LogicalExpr("not", QueryExpressions.eq("status", "ready"), QueryExpressions.gt("score", 1)).toExpr("milvus"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("not operator should not have a right operand");
    }
}
