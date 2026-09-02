/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Composite chunker with preprocessing.
 * 
 * @since 0.1.7
 */
public class TextChunker extends Chunker {
    private final Chunker innerChunker;
    private final PreprocessingPipeline pipeline;

    /**
     * TextChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param chunkUnit chunkUnit
     * @since 0.1.7
     */
    public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit) {
        this(chunkSize, chunkOverlap, chunkUnit, null, "auto");
    }

    /**
     * TextChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param chunkUnit chunkUnit
     * @param tokenizer tokenizer
     * @param language language
     * @since 0.1.7
     */
    public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit, Function<String, List<String>> tokenizer,
            String language) {
        this(chunkSize, chunkOverlap, chunkUnit, tokenizer, language, List.of());
    }

    /**
     * TextChunker.
     * 
     * @param chunkSize chunkSize
     * @param chunkOverlap chunkOverlap
     * @param chunkUnit chunkUnit
     * @param tokenizer tokenizer
     * @param language language
     * @param preprocessors preprocessors
     * @since 0.1.7
     */
    public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit, Function<String, List<String>> tokenizer,
            String language, List<TextPreprocessor> preprocessors) {
        super(chunkSize, chunkOverlap);
        this.innerChunker = "char".equalsIgnoreCase(chunkUnit)
                ? new CharChunker(chunkSize, chunkOverlap)
                : new TokenizerChunker(chunkSize, chunkOverlap, tokenizer, language, null);
        this.pipeline = new PreprocessingPipeline(preprocessors);
    }

    /**
     * chunkText.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<String> chunkText(String text) {
        return innerChunker.chunkText(pipeline.process(text));
    }

    /**
     * chunkDocuments.
     * 
     * @param documents documents
     * @return the result
     * @since 0.1.7
     */
    @Override
    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<Document> normalized = new ArrayList<>();
        if (documents != null) {
            for (Document document : documents) {
                normalized.add(
                        new Document(document.getId(), pipeline.process(document.getText()), document.getMetadata()));
            }
        }
        return innerChunker.chunkDocuments(normalized);
    }
}
