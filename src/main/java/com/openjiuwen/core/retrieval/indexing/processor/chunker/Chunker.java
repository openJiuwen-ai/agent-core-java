/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chunker abstraction for documents.
 * 
 * @since 0.1.7
 */
public abstract class Chunker implements Processor<List<Document>, List<TextChunk>> {
    /**
     * chunkSize.
     * 
     * @since 0.1.7
     */
    protected final int chunkSize;

    /**
     * chunkOverlap.
     * 
     * @since 0.1.7
     */
    protected final int chunkOverlap;

    /**
     * Chunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @since 0.1.7
     */
    protected Chunker(int chunkSize, int chunkOverlap) {
        RetrievalValidation.requirePositive(chunkSize, "chunk_size", StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
        RetrievalValidation.requireNonNegative(chunkOverlap, "chunk_overlap",
                StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
        if (chunkOverlap >= chunkSize) {
            throw RetrievalExceptions.error(StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "chunk_overlap must be smaller than chunk_size");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    /**
     * chunkText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    public abstract List<String> chunkText(String text);

    /**
     * chunkDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
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
     * process.
     * 
     * @param input input
     * @param options options
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<TextChunk> process(List<Document> input, Map<String, Object> options) {
        return chunkDocuments(input);
    }
}
