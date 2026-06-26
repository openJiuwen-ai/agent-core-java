/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import java.util.List;

/**
 * Package bridge for query expression exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/core/foundation/store/query/__init__.py}.
 * </p>
 */
public final class QueryPackage {

    public static final String PYTHON_MODULE = "openjiuwen/core/foundation/store/query/__init__.py";
    public static final Class<QueryLanguageDefinition> QUERY_LANGUAGE_DEFINITION = QueryLanguageDefinition.class;
    public static final Class<QueryExpr> QUERY_EXPR = QueryExpr.class;
    public static final Class<CustomExpr> CUSTOM_EXPR = CustomExpr.class;
    public static final Class<ComparisonExpr> COMPARISON_EXPR = ComparisonExpr.class;
    public static final Class<RangeExpr> RANGE_EXPR = RangeExpr.class;
    public static final Class<ArithmeticExpr> ARITHMETIC_EXPR = ArithmeticExpr.class;
    public static final Class<NullExpr> NULL_EXPR = NullExpr.class;
    public static final Class<JSONExpr> JSON_EXPR = JSONExpr.class;
    public static final Class<ArrayExpr> ARRAY_EXPR = ArrayExpr.class;
    public static final Class<LogicalExpr> LOGICAL_EXPR = LogicalExpr.class;
    public static final Class<MatchExpr> MATCH_EXPR = MatchExpr.class;
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "register_database_query_language",
            "QueryLanguageDefinition",
            "QueryExpr",
            "CustomExpr",
            "ComparisonExpr",
            "RangeExpr",
            "ArithmeticExpr",
            "NullExpr",
            "JSONExpr",
            "ArrayExpr",
            "LogicalExpr",
            "MatchExpr",
            "eq",
            "ne",
            "gt",
            "lt",
            "gte",
            "lte",
            "in_list",
            "wildcard_match",
            "is_null",
            "is_not_null",
            "json_key",
            "array_index",
            "filter_user",
            "chain_filters"
    );

    static {
        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", MilvusQueryLanguage.MILVUS_DEF, true);
        QueryLanguageRegistry.registerDatabaseQueryLanguage("chroma", ChromaQueryLanguage.CHROMA_DEF, true);
    }

    private QueryPackage() {
    }
}
