/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential text preprocessing pipeline.
 */
public class PreprocessingPipeline implements TextPreprocessor {

    private final List<TextPreprocessor> preprocessors = new ArrayList<>();

    public PreprocessingPipeline() {
        this(null);
    }

    public PreprocessingPipeline(List<TextPreprocessor> preprocessors) {
        if (preprocessors != null) {
            this.preprocessors.addAll(preprocessors);
        }
    }

    public List<TextPreprocessor> getPreprocessors() {
        return List.copyOf(preprocessors);
    }

    public void addPreprocessor(TextPreprocessor preprocessor) {
        preprocessors.add(preprocessor);
    }

    @Override
    public String process(String text) {
        String current = text;
        for (TextPreprocessor preprocessor : preprocessors) {
            current = preprocessor.process(current);
        }
        return current;
    }

    @Override
    public String call(String text) {
        return process(text);
    }

    public int size() {
        return preprocessors.size();
    }
}
