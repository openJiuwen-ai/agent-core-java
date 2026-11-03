/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import java.util.Optional;

/**
 * One ordered classification rule. Rules are evaluated in list order and the
 * first match wins; later endpoint-specific rules can be appended without
 * changing the category set.
 *
 * @since 2026-10-19
 */
@FunctionalInterface
public interface ClassificationRule {
    /**
     * Attempts to match the failure facts.
     *
     * @param facts failure facts, must not be null
     * @return the classification when this rule matches, otherwise empty
     */
    Optional<ModelCallClassification> match(ModelErrorFacts facts);
}
