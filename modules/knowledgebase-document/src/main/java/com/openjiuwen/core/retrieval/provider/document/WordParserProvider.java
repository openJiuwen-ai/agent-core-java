/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.provider.document;

import com.openjiuwen.core.retrieval.indexing.processor.parser.Parser;
import com.openjiuwen.core.retrieval.indexing.processor.parser.WordParser;
import com.openjiuwen.core.retrieval.provider.ParserProvider;

import java.util.Set;

/**
 * ServiceLoader provider for Word documents.
 *
 * @since 0.1.15
 */
public final class WordParserProvider implements ParserProvider {
    @Override
    public Set<String> extensions() {
        return Set.of(".docx");
    }

    @Override
    public Parser create() {
        return new WordParser();
    }
}
