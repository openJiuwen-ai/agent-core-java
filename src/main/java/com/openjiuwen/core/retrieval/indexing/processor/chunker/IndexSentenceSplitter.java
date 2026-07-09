/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.splitter.SentenceSplitter;

import java.util.List;
import java.util.function.Function;

/**
 * SentenceSplitter wrapper with sentence splitting capabilities. Corresponds to Python {@code
 * text_splitter.py::IndexSentenceSplitter}.
 * 
 * @since 0.1.7
 */
public class IndexSentenceSplitter extends TextSplitter {
    private static final int DEFAULT_CHUNK_SIZE = 200;

    private final SentenceSplitter splitter;

    /**
     * IndexSentenceSplitter.
     * 
     * @since 0.1.7
     */
    public IndexSentenceSplitter() {
        this(null, null, null, null, "auto");
    }

    /**
     * IndexSentenceSplitter.
     * 
     * @param tokenizer tokenizer function, can be null (falls back to whitespace split)
     * @param chunkSize chunk size, null for default
     * @param chunkOverlap chunk overlap, null for chunkSize/5
     * @param splitterConfig extra configuration (reserved, currently unused)
     * @param language language code, defaults to "auto"
     * @since 0.1.7
     */
    public IndexSentenceSplitter(Function<String, List<String>> tokenizer, Integer chunkSize, Integer chunkOverlap,
            java.util.Map<String, Object> splitterConfig, String language) {
        int resolvedChunkSize = resolveChunkSize(chunkSize);
        int resolvedOverlap = chunkOverlap != null ? chunkOverlap : resolvedChunkSize / 5;
        String lang = language != null ? language : "auto";
        @SuppressWarnings("unchecked")
        Function<List<String>, String> decoder =
            splitterConfig != null && splitterConfig.get("tokenizer_dec") instanceof Function<?, ?> function
                    ? (Function<List<String>, String>) function
                    : null;
        this.splitter = new SentenceSplitter(resolvedChunkSize, resolvedOverlap, tokenizer, lang, decoder);
    }

    /**
     * split.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<TextChunk> split(Document doc) {
        return splitter.getNodesFromDocuments(List.of(doc));
    }

    /**
     * resolveChunkSize.
     * 
     * @param chunkSize chunkSize
     * @return the result
     * @since 0.1.7
     */
    private static int resolveChunkSize(Integer chunkSize) {
        return chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
    }
}
