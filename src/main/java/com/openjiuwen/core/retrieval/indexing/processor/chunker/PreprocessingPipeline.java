/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential text preprocessing pipeline.
 * 
 * @since 0.1.7
 */
public class PreprocessingPipeline implements TextPreprocessor {
    private final List<TextPreprocessor> preprocessors = new ArrayList<>();

    /**
     * PreprocessingPipeline.
     * 
     * @param preprocessors preprocessors
     * @since 0.1.7
     */
    public PreprocessingPipeline(List<TextPreprocessor> preprocessors) {
        if (preprocessors != null) {
            this.preprocessors.addAll(preprocessors);
        }
    }

    /**
     * process.
     * 
     * @param text text
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String process(String text) {
        String current = text;
        for (TextPreprocessor preprocessor : preprocessors) {
            current = preprocessor.process(current);
        }
        return current;
    }

    /**
     * addPreprocessor.
     * 
     * @param preprocessor preprocessor
     * @since 0.1.7
     */
    public void addPreprocessor(TextPreprocessor preprocessor) {
        preprocessors.add(preprocessor);
    }

    /**
     * size.
     * 
     * @return the result
     * @since 0.1.7
     */
    public int size() {
        return preprocessors.size();
    }

    /**
     * getPreprocessors.
     * 
     * @return the result
     * @since 0.1.7
     */
    public List<TextPreprocessor> getPreprocessors() {
        return List.copyOf(preprocessors);
    }
}
