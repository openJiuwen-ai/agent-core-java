/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider;

import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;

import java.util.Map;
import java.util.Set;

/**
 * Provider for optional document parsers.
 *
 * @since 0.1.15
 */
public interface ParserProvider {
    /**
     * Returns supported file extensions including the leading dot.
     *
     * @return supported extensions
     * @since 0.1.15
     */
    Set<String> extensions();

    /**
     * Creates a parser instance.
     *
     * @return parser instance
     * @since 0.1.15
     */
    Parser create();

    /**
     * Creates a parser using provider-specific options.
     *
     * @param options parser options
     * @return parser instance
     * @since 0.1.15
     */
    default Parser create(Map<String, Object> options) {
        return create();
    }
}
