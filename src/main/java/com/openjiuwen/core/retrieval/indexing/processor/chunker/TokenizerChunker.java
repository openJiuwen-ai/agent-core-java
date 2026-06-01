/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Token-aware chunker backed by {@link IndexSentenceSplitter}.
 *
 * <p>Mirrors Python's {@code TokenizerChunker} in
 * {@code openjiuwen.core.retrieval.indexing.processor.chunker.tokenizer_chunker}.
 */
public class TokenizerChunker extends Chunker {

    private final IndexSentenceSplitter splitter;
    private final Function<String, List<String>> tokenizer;
    private final String language;
    private final Map<String, Object> splitterConfig;

    public TokenizerChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null, "auto", null);
    }

    public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer) {
        this(chunkSize, chunkOverlap, tokenizer, "auto", null);
    }

    public TokenizerChunker(int chunkSize,
                            int chunkOverlap,
                            Function<String, List<String>> tokenizer,
                            String language,
                            Map<String, Object> splitterConfig) {
        super(chunkSize, chunkOverlap);
        this.tokenizer = tokenizer;
        this.language = language == null ? "auto" : language;
        this.splitterConfig = splitterConfig == null ? Map.of() : Map.copyOf(splitterConfig);
        this.splitter = new IndexSentenceSplitter(tokenizer, chunkSize, chunkOverlap, splitterConfig, this.language);
    }

    @Override
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return splitter.split(new Document(text)).stream()
                .map(TextChunk::getText)
                .toList();
    }

    public Function<String, List<String>> getTokenizer() {
        return tokenizer;
    }

    public String getLanguage() {
        return language;
    }

    public Map<String, Object> getSplitterConfig() {
        return splitterConfig;
    }
}
