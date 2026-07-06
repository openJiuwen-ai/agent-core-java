/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Top-level document parser router for URLs and local files.
 *
 * <p>Mirrors Python's {@code AutoParser} in
 * {@code openjiuwen/core/retrieval/indexing/processor/parser/auto_parser.py}.</p>
 */
public class AutoParser extends Parser {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoParser.class);

    private final Parser linkParser;
    private final Parser fileParser;

    public AutoParser() {
        this(null, null);
    }

    public AutoParser(Parser linkParser, Parser fileParser) {
        this.linkParser = linkParser == null ? new AutoLinkParser() : linkParser;
        this.fileParser = fileParser == null ? new AutoFileParser() : fileParser;
    }

    @Override
    public boolean supports(String doc) {
        if (isLikelyUrl(doc)) {
            return linkParser.supports(doc);
        }
        return fileParser.supports(doc);
    }

    @Override
    public CompletableFuture<List<Document>> parseAsync(
            String doc,
            String docId,
            BaseModelClient llmClient,
            Map<String, Object> options
    ) {
        Map<String, Object> safeOptions = options == null ? Map.of() : options;
        if (isLikelyUrl(doc) && linkParser.supports(doc)) {
            LOGGER.debug("AutoParser delegating to link parser");
            return linkParser.parseAsync(doc, docId, llmClient, safeOptions);
        }
        if (fileParser.supports(doc)) {
            LOGGER.debug("AutoParser delegating to file parser");
            return fileParser.parseAsync(doc, docId, llmClient, safeOptions);
        }
        return CompletableFuture.completedFuture(List.of());
    }

    static boolean isLikelyUrl(String doc) {
        return AutoLinkParser.matchDoc(AutoLinkParser.HTTP_URL_PATTERN, doc);
    }
}
