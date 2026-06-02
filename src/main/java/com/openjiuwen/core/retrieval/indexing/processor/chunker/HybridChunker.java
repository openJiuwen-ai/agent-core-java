/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Chunker that skips splitting for specific document types.
 */
public class HybridChunker extends Chunker {

    private final Chunker innerChunker;
    private final Predicate<Document> noSplitWhen;

    public HybridChunker(Chunker innerChunker) {
        this(innerChunker, null);
    }

    public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen) {
        super(innerChunker.chunkSize, innerChunker.chunkOverlap);
        this.innerChunker = Objects.requireNonNull(innerChunker, "innerChunker");
        this.noSplitWhen = noSplitWhen != null ? noSplitWhen : HybridChunker::defaultNoSplit;
    }

    public static boolean defaultNoSplit(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return false;
        }
        Object sourceType = doc.getMetadata().get("source_type");
        return "row".equals(sourceType) || "column".equals(sourceType);
    }

    @Override
    public List<String> chunkText(String text) {
        return innerChunker.chunkText(text);
    }

    @Override
    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<TextChunk> result = new ArrayList<>();
        if (documents == null) {
            return result;
        }
        for (Document document : documents) {
            String text = document == null ? null : document.getText();
            if (document != null && noSplitWhen.test(document) && text != null && !text.trim().isEmpty()) {
                String chunkId = UUID.randomUUID().toString();
                TextChunk chunk = new TextChunk(
                        chunkId,
                        text.trim(),
                        document.getId(),
                        new LinkedHashMap<>(document.getMetadata()),
                        null);
                chunk.getMetadata().put("chunk_index", 0);
                chunk.getMetadata().put("total_chunks", 1);
                chunk.getMetadata().put("chunk_id", chunkId);
                result.add(chunk);
            } else {
                result.addAll(innerChunker.chunkDocuments(List.of(document)));
            }
        }
        return result;
    }
}
