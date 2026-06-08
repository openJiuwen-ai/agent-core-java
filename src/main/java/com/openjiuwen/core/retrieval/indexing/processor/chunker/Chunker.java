/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.ToIntFunction;

/**
 * Mirrors Python's {@code Chunker} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/base.py}.
 */
public abstract class Chunker implements Processor<List<TextChunk>> {

    private final int chunkSize;
    private final int chunkOverlap;
    private final ToIntFunction<String> lengthFunction;

    protected Chunker() {
        this(512, 50, null);
    }

    protected Chunker(int chunkSize, int chunkOverlap, ToIntFunction<String> lengthFunction) {
        if (chunkSize <= 0) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID,
                    "error_msg",
                    "chunk_size must be greater than 0, current value: " + chunkSize
            );
        }
        if (chunkOverlap < 0) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "error_msg",
                    "chunk_overlap must be greater than or equal to 0, current value: " + chunkOverlap
            );
        }
        if (chunkOverlap >= chunkSize) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "error_msg",
                    "chunk_overlap must be less than chunk_size"
            );
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.lengthFunction = lengthFunction == null ? String::length : lengthFunction;
    }

    public abstract List<String> chunkText(String text);

    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<TextChunk> chunks = new ArrayList<>();
        for (Document doc : documents) {
            List<String> texts = chunkText(doc.getText());
            for (int i = 0; i < texts.size(); i++) {
                String uid = UUID.randomUUID().toString();
                Map<String, Object> metadata = new LinkedHashMap<>(doc.getMetadata());
                metadata.put("chunk_index", i);
                metadata.put("total_chunks", texts.size());
                metadata.put("chunk_id", uid);
                chunks.add(new TextChunk(uid, texts.get(i), doc.getId_(), metadata));
            }
        }
        return chunks;
    }

    public CompletableFuture<List<TextChunk>> process(List<Document> documents) {
        return CompletableFuture.completedFuture(chunkDocuments(documents));
    }

    @Override
    public CompletableFuture<List<TextChunk>> process(Object... args) {
        @SuppressWarnings("unchecked")
        List<Document> documents = (List<Document>) args[0];
        return process(documents);
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public ToIntFunction<String> getLengthFunction() {
        return lengthFunction;
    }
}
