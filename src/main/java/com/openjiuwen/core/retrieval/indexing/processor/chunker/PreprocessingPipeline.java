/** Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.*/

package com.openjiuwen.core.retrieval.indexing.processor.chunker;

import java.util.ArrayList;
import java.util.List;

/**
 * Sequential text preprocessing pipeline.
 */
public class PreprocessingPipeline implements TextPreprocessor {

    private final List<TextPreprocessor> preprocessors = new ArrayList<>();

    public PreprocessingPipeline(List<TextPreprocessor> preprocessors) {
        if (preprocessors != null) {
            this.preprocessors.addAll(preprocessors);
        }
    }

    @Override
    public String process(String text) {
        String current = text == null ? "" : text;
        for (TextPreprocessor preprocessor : preprocessors) {
            current = preprocessor.process(current);
        }
        return current;
    }
}
