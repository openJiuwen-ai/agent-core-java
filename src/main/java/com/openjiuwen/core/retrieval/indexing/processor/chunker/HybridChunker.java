/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Chunker that skips splitting for specific document types.
 */
public class HybridChunker extends Chunker {

    private final Chunker innerChunker;
    private final Predicate<Document> noSplitWhen;

    public HybridChunker(Chunker innerChunker) {
        this(innerChunker, doc -> {
            Object sourceType = doc.getMetadata().get("source_type");
            return "row".equals(sourceType) || "column".equals(sourceType);
        });
    }

    public HybridChunker(Chunker innerChunker, Predicate<Document> noSplitWhen) {
        super(innerChunker.chunkSize, innerChunker.chunkOverlap);
        this.innerChunker = innerChunker;
        this.noSplitWhen = noSplitWhen;
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
            if (noSplitWhen.test(document)) {
                result.add(TextChunk.fromDocument(document, document.getText()));
            } else {
                result.addAll(innerChunker.chunkDocuments(List.of(document)));
            }
        }
        return result;
    }
}
