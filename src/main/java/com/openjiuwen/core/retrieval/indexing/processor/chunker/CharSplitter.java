/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Simple text splitter based on character length.
 * <p>
 * Mirrors Python's {@code CharSplitter} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_splitter.py}.
 * </p>
 */
public class CharSplitter extends TextSplitter {

    public static final int DEFAULT_CHAR_CHUNK_SIZE = 200;
    public static final int DEFAULT_CHAR_CHUNK_OVERLAP = 40;

    private final int chunkSize;
    private final int chunkOverlap;

    public CharSplitter() {
        this(null, null);
    }

    public CharSplitter(Integer chunkSize, Integer chunkOverlap) {
        int size = chunkSize == null || chunkSize == 0 ? DEFAULT_CHAR_CHUNK_SIZE : chunkSize;
        int overlap = chunkOverlap == null ? DEFAULT_CHAR_CHUNK_OVERLAP : chunkOverlap;
        overlap = Math.max(0, Math.min(overlap, size - 1));
        this.chunkSize = Math.max(1, size);
        this.chunkOverlap = overlap;
    }

    @Override
    public List<TextChunk> split(Document doc) {
        String text = doc.getText() == null ? "" : doc.getText();
        String docId = doc.getId_();
        Map<String, Object> metadata = doc.getMetadata();
        List<TextChunk> chunks = new ArrayList<>();
        int step = chunkSize > chunkOverlap ? chunkSize - chunkOverlap : chunkSize;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            chunks.add(new TextChunk(UUID.randomUUID().toString(), text.substring(start, end), docId, metadata));
            start += step;
        }
        return chunks;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }
}
