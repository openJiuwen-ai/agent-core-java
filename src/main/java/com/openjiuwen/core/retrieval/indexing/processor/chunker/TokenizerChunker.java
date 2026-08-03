/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed-size chunker based on tokenizer-aware sentence splitting.
 * <p>
 * Mirrors Python's {@code TokenizerChunker} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/tokenizer_chunker.py}.
 * </p>
 */
public class TokenizerChunker extends Chunker {

    private final IndexSentenceSplitter.TokenCodec tokenizer;
    private final IndexSentenceSplitter splitter;
    private final String language;
    private final Map<String, Object> splitterConfig;

    public TokenizerChunker(int chunkSize, int chunkOverlap, IndexSentenceSplitter.TokenCodec tokenizer) {
        this(chunkSize, chunkOverlap, tokenizer, "auto", null);
    }

    public TokenizerChunker(int chunkSize,
                            int chunkOverlap,
                            IndexSentenceSplitter.TokenCodec tokenizer,
                            String language,
                            Map<String, Object> splitterConfig) {
        super(chunkSize, chunkOverlap, null);
        this.tokenizer = tokenizer;
        this.language = language == null ? "auto" : language;
        this.splitterConfig = splitterConfig == null ? Map.of() : Map.copyOf(splitterConfig);
        this.splitter = new IndexSentenceSplitter(
                this.tokenizer,
                getChunkSize(),
                getChunkOverlap(),
                this.splitterConfig,
                this.language
        );
    }

    @Override
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        Document document = new Document(null, text, new LinkedHashMap<>());
        return splitter.split(document).stream()
                .map(TextChunk::getText)
                .toList();
    }

    public IndexSentenceSplitter.TokenCodec getTokenizer() {
        return tokenizer;
    }

    public IndexSentenceSplitter getSplitter() {
        return splitter;
    }

    public String getLanguage() {
        return language;
    }

    public Map<String, Object> getSplitterConfig() {
        return splitterConfig;
    }
}
