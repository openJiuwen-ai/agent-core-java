/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.splitter;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;
import com.openjiuwen.core.retrieval.indexing.processor.Processor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Mirrors Python's {@code Splitter} in
 * {@code openjiuwen/core/retrieval/indexing/processor/splitter/splitter.py}.
 */
public abstract class Splitter implements Processor<List<TextChunk>> {

    protected final int chunkSize;
    protected final int chunkOverlap;
    protected final Object tokenizer;
    protected final Function<String, Object> tokenizerEnc;
    protected final Function<Object, String> tokenizerDec;

    private static final LoggerProtocol LOGGER = Loggers.RETRIEVAL;

    protected Splitter() {
        this(null, 512, 50);
    }

    protected Splitter(Object tokenizer, int chunkSize, int chunkOverlap) {
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
                    "chunk_overlap (" + chunkOverlap + ") must be less than chunk_size (" + chunkSize + ")"
            );
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        validateTokenizer(tokenizer);
        this.tokenizer = tokenizer;
        if (tokenizer instanceof TokenizerAdapter adapter) {
            this.tokenizerEnc = adapter::encode;
            this.tokenizerDec = adapter::decode;
        } else if (tokenizer instanceof Function<?, ?> function) {
            @SuppressWarnings("unchecked")
            Function<String, Object> encoder = (Function<String, Object>) function;
            this.tokenizerEnc = encoder;
            this.tokenizerDec = null;
        } else {
            this.tokenizerEnc = null;
            this.tokenizerDec = null;
        }
    }

    protected void validateTokenizer(Object tokenizer) {
        if (tokenizer == null) {
            return;
        }
        if (!(tokenizer instanceof TokenizerAdapter) && !(tokenizer instanceof Function<?, ?>)) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_TOKENIZER_PROCESS_ERROR,
                    "error_msg",
                    "Tokenizer must have encode method or be callable"
            );
        }
    }

    public abstract List<SplitChunk> split(String doc);

    public List<TextChunk> getNodesFromDocuments(List<Document> docs) {
        List<TextChunk> returnedNodes = new ArrayList<>();
        if (docs == null) {
            return returnedNodes;
        }
        for (Document doc : docs) {
            if (doc == null || doc.getText() == null || doc.getText().isBlank()) {
                LOGGER.warning("Skipping empty document: {}", doc);
                continue;
            }
            List<SplitChunk> chunkTuples = split(doc.getText());
            for (SplitChunk chunk : chunkTuples) {
                returnedNodes.add(TextChunk.fromDocument(doc, chunk.text()));
            }
        }
        LOGGER.info("Generated {} text chunks from {} documents", returnedNodes.size(), docs.size());
        return returnedNodes;
    }

    public List<String> splitText(String text) {
        List<SplitChunk> chunks = split(text);
        List<String> values = new ArrayList<>(chunks.size());
        for (SplitChunk chunk : chunks) {
            values.add(chunk.text());
        }
        return values;
    }

    protected int getTokenCount(String text) {
        if (tokenizerEnc != null) {
            Object tokens = tokenizerEnc.apply(text);
            if (tokens instanceof Collection<?> collection) {
                return collection.size();
            }
            if (tokens instanceof Object[] array) {
                return array.length;
            }
            return String.valueOf(tokens).length();
        }
        return text.length();
    }

    public CompletableFuture<List<TextChunk>> process(List<Document> docs) {
        return CompletableFuture.completedFuture(getNodesFromDocuments(docs));
    }

    @Override
    public CompletableFuture<List<TextChunk>> process(Object... args) {
        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) args[0];
        return process(docs);
    }

    public interface TokenizerAdapter {
        Object encode(String text);

        default String decode(Object tokens) {
            return String.valueOf(tokens);
        }
    }

    public record SplitChunk(String text, int startIdx, int endIdx) {
    }
}
