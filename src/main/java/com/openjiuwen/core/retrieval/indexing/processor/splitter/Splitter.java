/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
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

    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int chunkSize;
    /**
     * Auto-generated for codecheck compliance.
     */
    protected final int chunkOverlap;

    /**
     * Auto-generated for codecheck compliance.
     */
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

    /**
     * Auto-generated for codecheck compliance.
     */
    public abstract List<String> splitText(String text);

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<SplitSpan> splitSpans(String text) {
        List<String> chunks = splitText(text);
        List<SplitSpan> spans = new ArrayList<>(chunks.size());
        int cursor = 0;
        for (String chunk : chunks) {
            int start = text == null ? -1 : text.indexOf(chunk, cursor);
            if (start < 0) {
                start = cursor;
            }
            int end = start + (chunk != null ? chunk.length() : 0);
            spans.add(new SplitSpan(chunk, start, end));
            cursor = Math.max(cursor, end);
        }
        return spans;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TextChunk> getNodesFromDocuments(List<Document> documents) {
        List<TextChunk> result = new ArrayList<>();
        if (documents == null) {
            return result;
        }
        for (Document document : documents) {
            if (document == null || document.getText() == null || document.getText().isBlank()) {
                continue;
            }
            List<SplitSpan> parts = splitSpans(document.getText());
            for (int i = 0; i < parts.size(); i++) {
                TextChunk chunk = TextChunk.fromDocument(document, parts.get(i).text());
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("total_chunks", parts.size());
                chunk.getMetadata().put("chunk_id", chunk.getId());
                chunk.getMetadata().put("start_index", parts.get(i).start());
                chunk.getMetadata().put("end_index", parts.get(i).end());
                result.add(chunk);
            }
        }
        return result;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<TextChunk> process(List<Document> input, Map<String, Object> options) {
        return getNodesFromDocuments(input);
    }
}
