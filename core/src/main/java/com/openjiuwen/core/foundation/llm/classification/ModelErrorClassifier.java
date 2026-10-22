/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

/**
 * Unified classification entry point between model clients and the retry /
 * guardrail layers. Implementations must be total: every input is reduced to
 * one of the base categories or the context-length extension category, never
 * to an "unclassified" outcome. Custom implementations register via
 * {@code META-INF/services} and are loaded by {@link ModelErrorClassifiers}.
 *
 * @since 2026-10-19
 */
public interface ModelErrorClassifier {
    /**
     * Classifies one model call failure.
     *
     * @param facts failure facts gathered by the client layer, must not be null
     * @return the classification, never null
     */
    ModelCallClassification classify(ModelErrorFacts facts);
}
