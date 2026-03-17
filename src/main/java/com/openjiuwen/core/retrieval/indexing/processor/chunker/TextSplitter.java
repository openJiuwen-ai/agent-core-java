/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.List;

/**
 * Abstract base class for text splitters.
 * Corresponds to Python {@code text_splitter.py::TextSplitter}.
 */
public abstract class TextSplitter {

    /**
     * Split a document or text chunk into smaller text chunks.
     */
    public abstract List<TextChunk> split(Document doc);
}
