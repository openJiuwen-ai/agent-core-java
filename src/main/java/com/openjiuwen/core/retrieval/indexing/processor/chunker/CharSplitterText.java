/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple text splitter based on character length, no dependency on tokenizer.
 * Corresponds to Python {@code text_splitter.py::CharSplitter}.
 * 
 * @since 0.1.7
 */
public class CharSplitterText extends TextSplitter {
    private static final int DEFAULT_CHUNK_SIZE = 200;
    private static final int DEFAULT_CHUNK_OVERLAP = 40;

    private final int chunkSize;
    private final int chunkOverlap;

    /**
     * CharSplitterText.
     * 
     * @since 0.1.7
     */
    public CharSplitterText() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    /**
     * CharSplitterText.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    public CharSplitterText(Integer chunkSize, Integer chunkOverlap) {
        int size = chunkSize != null ? chunkSize : DEFAULT_CHUNK_SIZE;
        int overlap = chunkOverlap != null ? chunkOverlap : DEFAULT_CHUNK_OVERLAP;
        overlap = Math.max(0, Math.min(overlap, size - 1));
        this.chunkSize = Math.max(1, size);
        this.chunkOverlap = overlap;
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
        String text = doc.getText() != null ? doc.getText() : "";
        String docId = doc.getId();
        Map<String, Object> meta = doc.getMetadata();

        List<TextChunk> result = new ArrayList<>();
        int step = chunkSize > chunkOverlap ? chunkSize - chunkOverlap : chunkSize;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            result.add(new TextChunk(UUID.randomUUID().toString(), text.substring(start, end), docId, meta, null));
            start += step;
        }
        return result;
    }
}
