/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.BaseError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QueryExpressionsTest {

    @AfterEach
    void tearDown() {
        QueryExpr.resetRegisteredLanguagesForTest();
    }

    @Test
    void sanitizeStrUsesPythonCompatibleStringRendering() {
        assertThat(QueryExpr.sanitizeStr("he\"llo")).isEqualTo("\"he\\\"llo\"");
        assertThat(QueryExpr.sanitizeStr(true)).isEqualTo("\"True\"");
        assertThat(QueryExpr.sanitizeStr(null)).isEqualTo("\"None\"");
    }

    @Test
    void missingLanguageRaisesPythonCompatibleQueryError() {
        assertThatThrownBy(() -> QueryExpressions.eq("category", "tech").toExpr("missing"))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("Database query language missing not registered");
    }

    @Test
    void registrationSupportsLookupAndForceOverride() {
        QueryLanguageDefinition original = demoLanguage("demo:");
        QueryLanguageDefinition replacement = demoLanguage("override:");

        QueryExpr.registerLanguage("demo", original);
        assertThat(QueryExpr.isLanguageRegistered("demo")).isTrue();
        assertThat(QueryExpressions.eq("score", 7).toExpr("demo")).isEqualTo("demo:score==7");

        assertThatThrownBy(() -> QueryExpr.registerLanguage("demo", replacement))
                .isInstanceOf(BaseError.class)
                .hasMessageContaining("already registered");

        QueryExpr.registerLanguage("demo", replacement, true);
        assertThat(QueryExpressions.eq("score", 7).toExpr("demo")).isEqualTo("override:score==7");
    }

    @Test
    void chainFiltersConsumesFirstItemAndBuildsAndExpression() {
        QueryExpr.registerLanguage("demo", demoLanguage(""));
        List<QueryExpr> filters = new ArrayList<>();
        filters.add(QueryExpressions.eq("category", "tech"));
        filters.add(QueryExpressions.gt("score", 70));
        filters.add(QueryExpressions.lt("age", 30));

        QueryExpr chained = QueryExpressions.chainFilters(filters);

        assertThat(filters).hasSize(2);
        assertThat(chained).isInstanceOf(LogicalExpr.class);
        assertThat(chained.toExpr("demo"))
                .isEqualTo("((category==tech) and (score>70)) and (age<30)");
    }

    @Test
    void helperFactoriesPreservePythonSingleValueSemantics() {
        assertThat(QueryExpressions.inList("user_id", List.of("solo")))
                .isInstanceOf(ComparisonExpr.class);
        assertThat(QueryExpressions.filterUser(List.of("u1", "u2")))
                .isInstanceOf(RangeExpr.class);
        assertThat(new MatchExpr("content", "abc").getMatchMode()).isEqualTo(MatchMode.EXACT);
        assertThat(MatchMode.fromValue("prefix")).isEqualTo(MatchMode.PREFIX);
        assertThat(MatchMode.PREFIX.toPythonValue()).isEqualTo("prefix");
    }

    private static QueryLanguageDefinition demoLanguage(String prefix) {
        return new QueryLanguageDefinition(
                expr -> prefix + expr.getField() + expr.getOperator() + expr.getValue(),
                expr -> prefix + expr.getField() + expr.getOperator() + expr.getValue(),
                expr -> prefix + expr.getField() + expr.getArithmeticOperator() + expr.getArithmeticValue()
                        + expr.getComparisonOperator() + expr.getComparisonValue(),
                expr -> prefix + expr.getField() + ":" + expr.isNull(),
                expr -> prefix + expr.getField() + "[" + expr.getKey() + "]" + expr.getOperator() + expr.getValue(),
                expr -> prefix + expr.getField() + "[" + expr.getIndex() + "]" + expr.getOperator() + expr.getValue(),
                expr -> {
                    if ("not".equals(expr.getOperator())) {
                        return "not (" + expr.getLeft().toExpr("demo") + ")";
                    }
                    return "(" + expr.getLeft().toExpr("demo") + ") "
                            + expr.getOperator()
                            + " (" + expr.getRight().toExpr("demo") + ")";
                },
                expr -> prefix + expr.getField() + ":" + expr.getMatchMode().toPythonValue() + ":" + expr.getValue()
        );
    }
}
