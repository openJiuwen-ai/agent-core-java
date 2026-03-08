/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Text splitter abstraction.
 */
public abstract class Splitter implements Processor<List<Document>, List<TextChunk>> {

    protected final int chunkSize;
    protected final int chunkOverlap;

    protected Splitter(int chunkSize, int chunkOverlap) {
        RetrievalValidation.requirePositive(chunkSize, "chunk_size", StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
        RetrievalValidation.requireNonNegative(
                chunkOverlap,
                "chunk_overlap",
                StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
        if (chunkOverlap >= chunkSize) {
            throw com.openjiuwen.core.retrieval.common.RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "chunk_overlap must be smaller than chunk_size");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public abstract List<String> splitText(String text);

    public List<TextChunk> getNodesFromDocuments(List<Document> documents) {
        List<TextChunk> result = new ArrayList<>();
        if (documents == null) {
            return result;
        }
        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            List<String> parts = splitText(document.getText());
            for (int i = 0; i < parts.size(); i++) {
                TextChunk chunk = TextChunk.fromDocument(document, parts.get(i));
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("total_chunks", parts.size());
                chunk.getMetadata().put("chunk_id", chunk.getId());
                result.add(chunk);
            }
        }
        return result;
    }

    @Override
    public List<TextChunk> process(List<Document> input, Map<String, Object> options) {
        return getNodesFromDocuments(input);
    }
}
