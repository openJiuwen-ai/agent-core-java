/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.common.RetrievalValidation;
import com.openjiuwen.core.retrieval.common.RetrievalExceptions;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Text splitter abstraction with optional tokenizer support.
 * 
 * <p>Mirrors Python's openjiuwen.core.retrieval.indexing.processor.splitter.base.py.</p>
 */
public abstract class Splitter implements Processor<List<Document>, List<TextChunk>> {

    protected final int chunkSize;
    protected final int chunkOverlap;

    /**
     * Tokenizer for accurate token counting (optional).
     */
    protected final Tokenizer tokenizer;

    /**
     * Tokenizer encode function - converts text to tokens.
     */
    protected final Function<String, List<Integer>> tokenizerEnc;

    /**
     * Tokenizer decode function - converts tokens back to text (optional).
     */
    protected final Function<List<Integer>, String> tokenizerDec;

    /**
     * Constructor without tokenizer (backward compatible).
     *
     * @param chunkSize    Maximum chunk size
     * @param chunkOverlap Overlap between chunks
     */
    protected Splitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, null);
    }

    /**
     * Constructor with tokenizer support.
     *
     * @param chunkSize    Maximum chunk size
     * @param chunkOverlap Overlap between chunks
     * @param tokenizer    Tokenizer for accurate token counting (optional)
     */
    protected Splitter(int chunkSize, int chunkOverlap, Tokenizer tokenizer) {
        RetrievalValidation.requirePositive(chunkSize, "chunk_size", StatusCode.RETRIEVAL_INDEXING_CHUNK_SIZE_INVALID);
        RetrievalValidation.requireNonNegative(
                chunkOverlap,
                "chunk_overlap",
                StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID);
        if (chunkOverlap >= chunkSize) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_CHUNK_OVERLAP_INVALID,
                    "chunk_overlap must be smaller than chunk_size");
        }

        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;

        if (tokenizer != null) {
            validateTokenizer(tokenizer);
            this.tokenizer = tokenizer;
            this.tokenizerEnc = tokenizer::encode;
            this.tokenizerDec = tokenizer.canDecode() ? tokenizer::decode : null;
        } else {
            this.tokenizer = null;
            this.tokenizerEnc = null;
            this.tokenizerDec = null;
        }
    }

    /**
     * Validate tokenizer has required methods.
     *
     * @param tokenizer Tokenizer to validate
     */
    protected void validateTokenizer(Tokenizer tokenizer) {
        if (tokenizer == null) {
            return;
        }
        // Tokenizer interface ensures encode() exists, just check it's functional
        try {
            // Quick validation - try encoding empty string
            tokenizer.encode("");
        } catch (Exception e) {
            throw RetrievalExceptions.error(
                    StatusCode.RETRIEVAL_INDEXING_TOKENIZER_PROCESS_ERROR,
                    "Tokenizer encode method is not functional: " + e.getMessage());
        }
    }

    /**
     * Abstract method to split text into chunks.
     *
     * @param text Text to split
     * @return List of chunk strings
     */
    public abstract List<String> splitText(String text);

    /**
     * Get split nodes from document list.
     *
     * @param documents List of documents to split
     * @return List of text chunks
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

    /**
     * Get token count of text using tokenizer.
     * Falls back to character count if no tokenizer is available.
     *
     * @param text Text content
     * @return Token count (or character count if no tokenizer)
     */
    protected int getTokenCount(String text) {
        if (tokenizerEnc != null) {
            List<Integer> tokens = tokenizerEnc.apply(text);
            return tokens != null ? tokens.size() : text.length();
        }
        return text.length();
    }

    @Override
    public List<TextChunk> process(List<Document> input, Map<String, Object> options) {
        return getNodesFromDocuments(input);
    }

    /**
     * Get the configured tokenizer (if any).
     *
     * @return Tokenizer instance or null
     */
    public Tokenizer getTokenizer() {
        return tokenizer;
    }

    /**
     * Check if tokenizer is configured.
     *
     * @return true if tokenizer is available
     */
    public boolean hasTokenizer() {
        return tokenizer != null;
    }
}