/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Token-aware chunker backed by {@link SentenceSplitter}.
 * 
 * @since 0.1.7
 */
public class TokenizerChunker extends Chunker {
    private final SentenceSplitter splitter;
    private final Function<String, List<String>> tokenizer;
    private final String language;
    private final Map<String, Object> splitterConfig;

    /**
     * TokenizerChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    public TokenizerChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null, "auto", null);
    }

    /**
     * TokenizerChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param tokenizer tokenizer
     * @since 0.1.7
     */
    public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer) {
        this(chunkSize, chunkOverlap, tokenizer, "auto", null);
    }

    /**
     * TokenizerChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param tokenizer tokenizer
     * @param language language
     * @param splitterConfig splitterConfig
     * @since 0.1.7
     */
    public TokenizerChunker(int chunkSize, int chunkOverlap, Function<String, List<String>> tokenizer, String language,
            Map<String, Object> splitterConfig) {
        super(chunkSize, chunkOverlap);
        this.tokenizer = tokenizer;
        this.language = language == null ? "auto" : language;
        this.splitterConfig = splitterConfig == null ? Map.of() : Map.copyOf(splitterConfig);
        @SuppressWarnings("unchecked")
        Function<List<String>, String> decoder =
            this.splitterConfig.get("tokenizer_dec") instanceof Function<?, ?> function
                    ? (Function<List<String>, String>) function
                    : null;
        this.splitter = new SentenceSplitter(chunkSize, chunkOverlap, tokenizer, this.language, decoder);
    }

    /**
     * chunkText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> chunkText(String text) {
        return splitter.splitText(text);
    }

    /**
     * getTokenizer.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Function<String, List<String>> getTokenizer() {
        return tokenizer;
    }

    /**
     * getLanguage.
     * 
     * @return the result
     * @since 0.1.7
     */
    public String getLanguage() {
        return language;
    }

    /**
     * getSplitterConfig.
     * 
     * @return the result
     * @since 0.1.7
     */
    public Map<String, Object> getSplitterConfig() {
        return splitterConfig;
    }
}
