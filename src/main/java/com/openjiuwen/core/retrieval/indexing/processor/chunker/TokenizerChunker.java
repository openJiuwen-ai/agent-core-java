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
 */
public class TokenizerChunker extends Chunker {

    private final SentenceSplitter splitter;
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
        this.splitter = new SentenceSplitter(chunkSize, chunkOverlap, tokenizer, this.language);
    }

    @Override
    public List<String> chunkText(String text) {
        return splitter.splitText(text);
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
