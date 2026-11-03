/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.spi.store.query;

/**
 * Compatibility facade for the 0.1.12 SPI query expression base class.
 *
 * <p>Mirrors Python's {@code QueryExpr} in
 * {@code openjiuwen/core/foundation/store/query/base.py}.</p>
 */
public abstract class QueryExpr extends com.openjiuwen.core.foundation.store.query.QueryExpr {

    static QueryExpr wrap(com.openjiuwen.core.foundation.store.query.QueryExpr expression) {
        return new DelegatingQueryExpr(expression);
    }

    private static final class DelegatingQueryExpr extends QueryExpr {

        private final com.openjiuwen.core.foundation.store.query.QueryExpr delegate;

        private DelegatingQueryExpr(com.openjiuwen.core.foundation.store.query.QueryExpr delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object toExpr(String database) {
            return delegate.toExpr(database);
        }
    }
}
