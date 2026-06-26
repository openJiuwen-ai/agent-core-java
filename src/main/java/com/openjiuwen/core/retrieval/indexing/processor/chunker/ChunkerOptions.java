/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Typed options for package-level chunker factories.
 * <p>
 * Mirrors Python's keyword arguments for {@code get_chunker} and {@code _hybrid_factory} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py}.
 * </p>
 */
public final class ChunkerOptions {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_CHUNK_OVERLAP = 50;

    private final int chunkSize;
    private final int chunkOverlap;
    private final Chunker innerChunker;
    private final Predicate<Document> noSplitWhen;
    private final List<String> unknownKeywordNames;

    private ChunkerOptions(Builder builder) {
        this.chunkSize = builder.chunkSize;
        this.chunkOverlap = builder.chunkOverlap;
        this.innerChunker = builder.innerChunker;
        this.noSplitWhen = builder.noSplitWhen;
        this.unknownKeywordNames = List.copyOf(builder.unknownKeywordNames);
    }

    public static ChunkerOptions defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ChunkerOptions fromKeywordArgs(Map<String, ?> keywordArgs) {
        Builder builder = builder();
        if (keywordArgs == null) {
            return builder.build();
        }
        for (Map.Entry<String, ?> entry : keywordArgs.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            switch (key) {
                case "chunk_size" -> builder.chunkSize(asInt(key, value));
                case "chunk_overlap" -> builder.chunkOverlap(asInt(key, value));
                case "inner_chunker" -> builder.innerChunker(asChunker(key, value));
                case "no_split_when" -> builder.noSplitWhen(asPredicate(key, value));
                default -> builder.addUnknownKeywordName(key);
            }
        }
        return builder.build();
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public Chunker getInnerChunker() {
        return innerChunker;
    }

    public Predicate<Document> getNoSplitWhen() {
        return noSplitWhen;
    }

    public List<String> getUnknownKeywordNames() {
        return unknownKeywordNames;
    }

    public boolean hasInnerChunker() {
        return innerChunker != null;
    }

    private static int asInt(String key, Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        throw new IllegalArgumentException(key + " must be a number");
    }

    private static Chunker asChunker(String key, Object value) {
        if (value instanceof Chunker chunker) {
            return chunker;
        }
        throw new IllegalArgumentException(key + " must be a Chunker instance");
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Document> asPredicate(String key, Object value) {
        if (value instanceof Predicate<?> predicate) {
            return (Predicate<Document>) predicate;
        }
        throw new IllegalArgumentException(key + " must be a Predicate<Document>");
    }

    /**
     * Mirrors Python's accepted keyword names for {@code get_chunker(..., **kwargs)} in
     * {@code openjiuwen/core/retrieval/indexing/processor/chunker/__init__.py}.
     */
    public static final class Builder {

        private int chunkSize = DEFAULT_CHUNK_SIZE;
        private int chunkOverlap = DEFAULT_CHUNK_OVERLAP;
        private Chunker innerChunker;
        private Predicate<Document> noSplitWhen;
        private final List<String> unknownKeywordNames = new ArrayList<>();

        private Builder() {
        }

        public Builder chunkSize(int value) {
            this.chunkSize = value;
            return this;
        }

        public Builder chunkOverlap(int value) {
            this.chunkOverlap = value;
            return this;
        }

        public Builder innerChunker(Chunker value) {
            this.innerChunker = value;
            return this;
        }

        public Builder noSplitWhen(Predicate<Document> value) {
            this.noSplitWhen = value;
            return this;
        }

        public Builder addUnknownKeywordName(String value) {
            this.unknownKeywordNames.add(value);
            return this;
        }

        public ChunkerOptions build() {
            return new ChunkerOptions(this);
        }
    }
}
