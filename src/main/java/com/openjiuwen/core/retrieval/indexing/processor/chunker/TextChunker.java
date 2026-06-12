/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fixed-size text chunker with optional preprocessing and char/token chunk units.
 * <p>
 * Mirrors Python's {@code TextChunker} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/chunking.py}.
 * </p>
 */
public class TextChunker extends Chunker {

    private static final String CHAR_UNIT = "char";

    private final PreprocessingPipeline pipeline;
    private final Chunker chunker;
    private final String chunkUnit;

    public TextChunker() {
        this(512, 50);
    }

    public TextChunker(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, CHAR_UNIT, null, null);
    }

    public TextChunker(int chunkSize, int chunkOverlap, String chunkUnit) {
        this(chunkSize, chunkOverlap, chunkUnit, null, null);
    }

    public TextChunker(int chunkSize,
                       int chunkOverlap,
                       String chunkUnit,
                       IndexSentenceSplitter.TokenCodec tokenizer,
                       PreprocessOptions preprocessOptions) {
        super(chunkSize, chunkOverlap, null);
        this.chunkUnit = chunkUnit == null ? CHAR_UNIT : chunkUnit;
        this.pipeline = buildPipeline(preprocessOptions == null ? PreprocessOptions.none() : preprocessOptions);
        this.chunker = getChunker(chunkSize, chunkOverlap, this.chunkUnit, tokenizer);
    }

    public Chunker getChunker(int chunkSize,
                              int chunkOverlap,
                              String chunkUnit,
                              IndexSentenceSplitter.TokenCodec tokenizer) {
        if (CHAR_UNIT.equals(chunkUnit)) {
            return new CharChunker(chunkSize, chunkOverlap);
        }
        if (tokenizer == null) {
            throw ErrorHelper.buildError(
                    StatusCode.RETRIEVAL_INDEXING_TOKENIZER_PROCESS_ERROR,
                    "error_msg",
                    "chunk_unit='token' requires embed_model with tokenizer or tiktoken to be installed"
            );
        }
        int adjustedChunkSize = chunkSize;
        Integer maxTokenLength = tokenizer.maxTokenLength();
        if (maxTokenLength != null && maxTokenLength > 0 && chunkSize > maxTokenLength) {
            adjustedChunkSize = maxTokenLength;
        }
        return new TokenizerChunker(adjustedChunkSize, chunkOverlap, tokenizer);
    }

    @Override
    public List<String> chunkText(String text) {
        return chunker.chunkText(pipeline.process(text));
    }

    @Override
    public List<TextChunk> chunkDocuments(List<Document> documents) {
        List<TextChunk> chunks = new ArrayList<>();
        for (Document document : documents) {
            String docText = pipeline.process(document.getText());
            List<String> texts = chunker.chunkText(docText);
            for (int index = 0; index < texts.size(); index++) {
                String uid = UUID.randomUUID().toString();
                Map<String, Object> metadata = new LinkedHashMap<>(document.getMetadata());
                metadata.put("chunk_index", index);
                metadata.put("total_chunks", texts.size());
                metadata.put("chunk_id", uid);
                chunks.add(new TextChunk(uid, texts.get(index), document.getId_(), metadata));
            }
        }
        return chunks;
    }

    public PreprocessingPipeline getPipeline() {
        return pipeline;
    }

    public Chunker getInnerChunker() {
        return chunker;
    }

    public String getChunkUnit() {
        return chunkUnit;
    }

    private static PreprocessingPipeline buildPipeline(PreprocessOptions options) {
        List<TextPreprocessor> preprocessors = new ArrayList<>();
        if (options.isNormalizeWhitespace()) {
            preprocessors.add(new WhitespaceNormalizer());
        }
        if (options.isRemoveUrlEmail()) {
            preprocessors.add(new URLEmailRemover());
        }
        return new PreprocessingPipeline(preprocessors);
    }

    /**
     * Mirrors Python's {@code preprocess_options} dict in
     * {@code openjiuwen/core/retrieval/indexing/processor/chunker/chunking.py}.
     */
    public static final class PreprocessOptions {

        private final boolean normalizeWhitespace;
        private final boolean removeUrlEmail;

        public PreprocessOptions(boolean normalizeWhitespace, boolean removeUrlEmail) {
            this.normalizeWhitespace = normalizeWhitespace;
            this.removeUrlEmail = removeUrlEmail;
        }

        public static PreprocessOptions none() {
            return new PreprocessOptions(false, false);
        }

        public static PreprocessOptions fromKeywordArgs(Map<String, ?> preprocessOptions) {
            if (preprocessOptions == null) {
                return none();
            }
            return new PreprocessOptions(
                    Boolean.TRUE.equals(preprocessOptions.get("normalize_whitespace")),
                    Boolean.TRUE.equals(preprocessOptions.get("remove_url_email"))
            );
        }

        public boolean isNormalizeWhitespace() {
            return normalizeWhitespace;
        }

        public boolean isRemoveUrlEmail() {
            return removeUrlEmail;
        }
    }
}
