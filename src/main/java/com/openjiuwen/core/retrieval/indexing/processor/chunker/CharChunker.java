/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.ArrayList;
import java.util.List;

/**
 * Character window chunker.
 */
public class CharChunker extends Chunker {

    /**
     * Auto-generated for codecheck compliance.
     */
    public CharChunker(int chunkSize, int chunkOverlap) {
        super(chunkSize, chunkOverlap);
    }

    @Override
    /**
     * Auto-generated for codecheck compliance.
     */
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int step = chunkSize - chunkOverlap;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + chunkSize, text.length());
            parts.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
        }
        return parts;
    }
}
