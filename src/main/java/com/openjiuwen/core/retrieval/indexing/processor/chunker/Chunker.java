/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Chunker abstraction for documents.
 *
 * <p>Mirrors Python's {@code Chunker} in
 * {@code openjiuwen.core.retrieval.indexing.processor.chunker.base}.</p>
 */
public abstract class Chunker implements Processor<List<Document>, List<TextChunk>> {

    protected final int chunkSize;
    protected final int chunkOverlap;
    protected final Function<String, Integer> lengthFunction;

    protected Chunker() {
        this(512, 50, null);
    }

    protected Chunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null);
    }

    protected Chunker(int chunkSize, int chunkOverlap, Function<String, Integer> lengthFunction) {
        if (chunkSize <= 0) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID,
                    "chunk_size must be greater than 0, current value: " + chunkSize);
        }
        if (chunkOverlap < 0) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "chunk_overlap must be greater than or equal to 0, current value: " + chunkOverlap);
        }
        if (chunkOverlap >= chunkSize) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "chunk_overlap must be less than chunk_size");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.lengthFunction = lengthFunction == null ? String::length : lengthFunction;
    }

    public abstract List<String> chunkText(String text);

    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<TextChunk> chunks = new ArrayList<>();
        if (documents == null) {
            return chunks;
        }
        for (Document document : documents) {
            List<String> parts = chunkText(document.getText());
            for (int i = 0; i < parts.size(); i++) {
                TextChunk chunk = TextChunk.fromDocument(document, parts.get(i));
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("total_chunks", parts.size());
                chunk.getMetadata().put("chunk_id", chunk.getId());
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    /**
     * Get chunk size.
     *
     * @return chunk size in characters
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Get chunk overlap.
     *
     * @return chunk overlap in characters
     */
    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /**
     * Get the length function used by this chunker.
     *
     * @return length function
     */
    public Function<String, Integer> getLengthFunction() {
        return lengthFunction;
    }

    @Override
    public List<TextChunk> process(List<Document> input, Map<String, Object> options) {
        return chunkDocuments(input);
    }
}
