/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.List;

/**
 * Abstract base class for text splitters.
 * Corresponds to Python {@code text_splitter.py::TextSplitter}.
 * 
 * @since 0.1.7
 */
public abstract class TextSplitter {
    /**
     * split.
     * 
     * @param doc doc
     * @return the result
     * @since 0.1.7
     */
    public abstract List<TextChunk> split(Document doc);
}
