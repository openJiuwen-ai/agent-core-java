/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import com.openjiuwen.core.retrieval.common.Document;
import com.openjiuwen.core.retrieval.common.TextChunk;

import java.util.List;

/**
 * Abstract base class for text splitters.
 * <p>
 * Mirrors Python's {@code TextSplitter} in
 * {@code openjiuwen/core/retrieval/indexing/processor/chunker/text_splitter.py}.
 * </p>
 */
public abstract class TextSplitter {

    public abstract List<TextChunk> split(Document doc);

    public List<TextChunk> split(TextChunk chunk) {
        return split(new Document(chunk.getDocId(), chunk.getText(), chunk.getMetadata()));
    }
}
