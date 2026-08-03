/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Mirrors Python's {@code HybridChunker} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/hybrid_chunker.py}.
 */
public class HybridChunker extends Chunker {

    private final Chunker innerChunker;
    private final Predicate<Document> noSplitWhen;

    public HybridChunker(Chunker innerChunker) {
        this(innerChunker, null);
    }

    public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen) {
        super(innerChunker.getChunkSize(), innerChunker.getChunkOverlap(), innerChunker.getLengthFunction());
        this.innerChunker = innerChunker;
        this.noSplitWhen = noSplitWhen == null ? HybridChunker::defaultNoSplit : noSplitWhen;
    }

    @Override
    public List<String> chunkText(String text) {
        return innerChunker.chunkText(text);
    }

    @Override
    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<TextChunk> chunks = new ArrayList<>();
        for (Document document : documents) {
            String text = document.getText();
            if (noSplitWhen.test(document) && text != null && !text.strip().isEmpty()) {
                String uid = UUID.randomUUID().toString();
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("chunk_index", 0);
                metadata.put("total_chunks", 1);
                metadata.put("chunk_id", uid);
                chunks.add(new TextChunk(uid, text.strip(), document.getId_(), metadata));
            } else {
                chunks.addAll(innerChunker.chunkDocuments(List.of(document)));
            }
        }
        return chunks;
    }

    public static boolean defaultNoSplit(Document document) {
        Object sourceType = document.getMetadata().get("source_type");
        return "row".equals(sourceType) || "column".equals(sourceType);
    }
}
