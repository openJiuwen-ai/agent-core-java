/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Fixed-size chunker based on character length.
 * <p>
 * Mirrors Python's {@code CharChunker} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/char_chunker.py}.
 * </p>
 */
public class CharChunker extends Chunker {

    public CharChunker() {
        this(512, 50);
    }

    public CharChunker(int chunkSize, int chunkOverlap) {
        super(chunkSize, chunkOverlap, null);
    }

    @Override
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        CharSplitter splitter = new CharSplitter(getChunkSize(), getChunkOverlap());
        Document document = new Document(null, text, new LinkedHashMap<>());
        return splitter.split(document).stream()
                .map(TextChunk::getText)
                .toList();
    }
}
