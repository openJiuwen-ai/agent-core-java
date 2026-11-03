/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * UT-P: classifier SPI registration and default fallback.
 *
 * @since 2026-10-19
 */
class ModelErrorClassifiersSpiTest {
    @TempDir
    Path tempDir;

    @Test
    void spi_customClassifierRegisteredViaServiceLoader_wins() throws Exception {
        // Given a classifier registered in META-INF/services on an isolated class path,
        // When loaded with the two-argument ServiceLoader call used by the facade,
        // Then the custom implementation is picked up and drives the verdict
        Path services = tempDir.resolve(Path.of("META-INF", "services"));
        Files.createDirectories(services);
        Files.writeString(
            services.resolve("com.openjiuwen.core.foundation.llm.classification.ModelErrorClassifier"),
            AlwaysQuotaClassifier.class.getName() + System.lineSeparator(),
            StandardCharsets.UTF_8);

        URL[] urls = {tempDir.toUri().toURL()};
        try (URLClassLoader loader = new URLClassLoader(urls,
            ModelErrorClassifiersSpiTest.class.getClassLoader())) {
            ServiceLoader<ModelErrorClassifier> serviceLoader =
                ServiceLoader.load(ModelErrorClassifier.class, loader);
            Optional<ModelErrorClassifier> custom = serviceLoader.findFirst();

            assertThat(custom).isPresent();
            ModelCallClassification verdict = custom.get().classify(
                ModelErrorFacts.Builder.create().httpStatus(500).build());
            assertThat(verdict.category()).isEqualTo(ModelErrorCategory.QUOTA_EXCEEDED);
        }
    }

    @Test
    void spi_noCustomRegistered_fallsBackToRuleBasedDefault() {
        ModelErrorClassifier resolved = ModelErrorClassifiers.resolve();
        assertThat(resolved).isInstanceOf(RuleBasedModelErrorClassifier.class);
    }

    /**
     * Test classifier that always answers quota exhausted.
     */
    public static final class AlwaysQuotaClassifier implements ModelErrorClassifier {
        @Override
        public ModelCallClassification classify(ModelErrorFacts facts) {
            return new ModelCallClassification(ModelErrorCategory.QUOTA_EXCEEDED, false, null);
        }
    }
}
