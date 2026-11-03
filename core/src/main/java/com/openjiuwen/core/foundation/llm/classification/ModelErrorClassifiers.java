/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Loads the effective {@link ModelErrorClassifier}. A custom implementation
 * registered via {@code META-INF/services} wins; otherwise the default
 * rule-based classifier is used. The loader deliberately passes this class's
 * own class loader to {@link ServiceLoader#load(Class, ClassLoader)} instead
 * of relying on the thread context class loader: inside fat jars the TCCL is
 * often the system class loader, which cannot see SPI descriptors nested in
 * application jars.
 *
 * @since 2026-10-19
 */
public final class ModelErrorClassifiers {
    private ModelErrorClassifiers() {
    }

    /**
     * Returns the effective classifier, cached after first resolution.
     *
     * @return custom classifier when registered, otherwise the default rule-based one
     */
    public static ModelErrorClassifier defaultClassifier() {
        return Holder.INSTANCE;
    }

    /**
     * Resolves the classifier without caching; mainly for tests.
     *
     * @return custom classifier when registered, otherwise the default rule-based one
     */
    public static ModelErrorClassifier resolve() {
        return findCustom().orElseGet(RuleBasedModelErrorClassifier::new);
    }

    private static Optional<ModelErrorClassifier> findCustom() {
        ServiceLoader<ModelErrorClassifier> loader = ServiceLoader.load(
            ModelErrorClassifier.class, ModelErrorClassifier.class.getClassLoader());
        for (ModelErrorClassifier classifier : loader) {
            return Optional.of(classifier);
        }
        return Optional.empty();
    }

    private static final class Holder {
        private static final ModelErrorClassifier INSTANCE = resolve();
    }
}
