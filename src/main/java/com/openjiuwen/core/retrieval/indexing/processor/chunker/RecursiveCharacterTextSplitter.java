/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Recursive character text splitter that splits text by characters.
 * <p>
 * Mirrors Python's {@code RecursiveCharacterTextSplitter} from LangChain.
 */
public class RecursiveCharacterTextSplitter extends TextSplitter {

    private final int chunkSize;
    private final int chunkOverlap;
    private final List<String> separators;

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap) {
        this(chunkSize, chunkOverlap, List.of("\n\n", "\n", " ", ""));
    }

    public RecursiveCharacterTextSplitter(int chunkSize, int chunkOverlap, List<String> separators) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.separators = separators;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    @Override
    public List<TextChunk> split(Document doc) {
        List<TextChunk> chunks = new ArrayList<>();
        String text = doc.getText();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        // Simple recursive splitting implementation
        splitTextRecursive(text, chunks, 0);
        return chunks;
    }

    /**
     * Split text into chunks using the chunkText method.
     */
    public List<String> chunkText(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            start = end - chunkOverlap;
            if (start < 0) start = 0;
            if (start >= end) start = end;
        }
        return result;
    }

    private void splitTextRecursive(String text, List<TextChunk> chunks, int separatorIndex) {
        if (text.length() <= chunkSize) {
            if (!text.isEmpty()) {
                chunks.add(new TextChunk(UUID.randomUUID().toString(), text, "split_chunk"));
            }
            return;
        }

        if (separatorIndex >= separators.size()) {
            // Split by characters when no separators work
            int start = 0;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                chunks.add(new TextChunk(UUID.randomUUID().toString(), text.substring(start, end), "split_chunk"));
                start = end - chunkOverlap;
                if (start <= 0) start = end;
            }
            return;
        }

        String separator = separators.get(separatorIndex);
        if (separator.isEmpty() || !text.contains(separator)) {
            splitTextRecursive(text, chunks, separatorIndex + 1);
            return;
        }

        String[] parts = text.split(separator);
        for (String part : parts) {
            if (part.length() <= chunkSize) {
                if (!part.isEmpty()) {
                    chunks.add(new TextChunk(UUID.randomUUID().toString(), part, "split_chunk"));
                }
            } else {
                splitTextRecursive(part, chunks, separatorIndex + 1);
            }
        }
    }
}