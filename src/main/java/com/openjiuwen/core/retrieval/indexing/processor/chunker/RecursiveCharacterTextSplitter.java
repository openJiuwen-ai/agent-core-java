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
        this.chunkSize = Math.max(1, chunkSize);
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize - 1));
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

        splitTextRecursive(doc, text, chunks, 0);
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
        int step = chunkSize - chunkOverlap;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            result.add(text.substring(start, end));
            if (end == text.length()) {
                break;
            }
            start += step;
        }
        return result;
    }

    private void splitTextRecursive(Document doc, String text, List<TextChunk> chunks, int separatorIndex) {
        if (text.length() <= chunkSize) {
            if (!text.isEmpty()) {
                chunks.add(TextChunk.fromDocument(doc, text, UUID.randomUUID().toString()));
            }
            return;
        }

        if (separatorIndex >= separators.size()) {
            // Split by characters when no separators work
            int start = 0;
            int step = chunkSize - chunkOverlap;
            while (start < text.length()) {
                int end = Math.min(start + chunkSize, text.length());
                chunks.add(TextChunk.fromDocument(doc, text.substring(start, end), UUID.randomUUID().toString()));
                if (end == text.length()) {
                    break;
                }
                start += step;
            }
            return;
        }

        String separator = separators.get(separatorIndex);
        if (separator.isEmpty() || !text.contains(separator)) {
            splitTextRecursive(doc, text, chunks, separatorIndex + 1);
            return;
        }

        String[] parts = text.split(separator);
        for (String part : parts) {
            if (part.length() <= chunkSize) {
                if (!part.isEmpty()) {
                    chunks.add(TextChunk.fromDocument(doc, part, UUID.randomUUID().toString()));
                }
            } else {
                splitTextRecursive(doc, part, chunks, separatorIndex + 1);
            }
        }
    }
}
