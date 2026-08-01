/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.query;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mirrors Python's registry behavior in
 * {@code openjiuwen/core/foundation/store/query/registry.py}.
 */
class QueryLanguageRegistryTest {

    @AfterEach
    void resetRegistry() {
        QueryExpr.resetRegisteredLanguagesForTest();
    }

    @Test
    @Disabled
    void registerDatabaseQueryLanguageRegistersDefinition() {
        QueryLanguageDefinition definition = definition("first");

        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", definition);

        assertNotNull(QueryExpr.getLanguageDefinition("milvus"));
    }

    @Test
    void registerDatabaseQueryLanguageRejectsDuplicateWhenForceIsFalse() {
        QueryLanguageDefinition first = definition("first");
        QueryLanguageDefinition second = definition("second");
        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", first);

        BaseError error = assertThrows(
                BaseError.class,
                () -> QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", second)
        );

        assertEquals(StatusCode.RETRIEVAL_VECTOR_STORE_QUERY_INVALID, error.getStatus());
    }

    @Test
    void registerDatabaseQueryLanguageOverridesWhenForceIsTrue() {
        QueryLanguageDefinition first = definition("first");
        QueryLanguageDefinition second = definition("second");
        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", first);

        QueryLanguageRegistry.registerDatabaseQueryLanguage("milvus", second, true);

        assertEquals("second", QueryExpr.getLanguageDefinition("milvus").applyComparison(null));
    }

    private static QueryLanguageDefinition definition(String name) {
        return new QueryLanguageDefinition(
                expr -> name,
                expr -> name,
                expr -> name,
                expr -> name,
                expr -> name,
                expr -> name,
                expr -> name,
                expr -> name
        );
    }
}
