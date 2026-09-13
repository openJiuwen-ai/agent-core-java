/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.parser;

import com.openjiuwen.core.foundation.llm.model_clients.BaseModelClient;
import com.openjiuwen.core.retrieval.common.Document;

import java.util.List;
import java.util.Map;

/**
 * Top-level parser that routes between file and URL parsers.
 * 
 * @since 0.1.7
 */
public class AutoParser extends Parser {
    private final Parser linkParser;
    private final Parser fileParser;

    /**
     * AutoParser.
     * 
     * @since 0.1.7
     */
    public AutoParser() {
        this(new AutoLinkParser(), new AutoFileParser());
    }

    /**
     * AutoParser.
     * 
     * @param linkParser linkParser
     * @param fileParser fileParser
     * @since 0.1.7
     */
    public AutoParser(Parser linkParser, Parser fileParser) {
        this.linkParser = linkParser;
        this.fileParser = fileParser;
    }

    /**
     * parse.
     * 
     * @param doc doc
     * @param docId docId
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<Document> parse(String doc, String docId, BaseModelClient llmClient, Map<String, Object> options) {
        if (linkParser != null && linkParser.supports(doc)) {
            return linkParser.parse(doc, docId, llmClient, options);
        }
        if (fileParser != null && fileParser.supports(doc)) {
            return fileParser.parse(doc, docId, llmClient, options);
        }
        return List.of();
    }

    /**
     * parseContent.
     * 
     * @param doc doc
     * @param llmClient llmClient
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    protected String parseContent(String doc, BaseModelClient llmClient, Map<String, Object> options) {
        return null;
    }

    /**
     * supports.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    @Override
    public boolean supports(String doc) {
        return (linkParser != null && linkParser.supports(doc)) || (fileParser != null && fileParser.supports(doc));
    }
}
