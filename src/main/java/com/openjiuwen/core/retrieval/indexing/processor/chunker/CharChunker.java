/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.List;

/**
 * Character window chunker.
 *
 * <p>Mirrors Python's {@code CharChunker} in
 * {@code openjiuwen.core.retrieval.indexing.processor.chunker.char_chunker}.
 */
public class CharChunker extends Chunker {

    public CharChunker() {
        this(512, 50);
    }

    public CharChunker(int chunkSize, int chunkOverlap) {
        super(chunkSize, chunkOverlap);
    }

    @Override
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        CharSplitterText splitter = new CharSplitterText(chunkSize, chunkOverlap);
        return splitter.split(new Document(text)).stream()
                .map(TextChunk::getText)
                .toList();
    }
}
