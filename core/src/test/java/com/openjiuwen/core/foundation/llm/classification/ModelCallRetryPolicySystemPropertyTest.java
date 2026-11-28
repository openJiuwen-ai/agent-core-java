/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Map;

/**
 * Verifies JVM defaults for classified retry without overriding explicit configuration.
 *
 * @since 2026-11-28
 */
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class ModelCallRetryPolicySystemPropertyTest {
    private static final String MAX_RETRIES = "openjiuwen.llm.retry.max-retries";
    private static final String RATE_LIMIT = "openjiuwen.llm.retry.on-rate-limit";
    private static final String TEMPORARY = "openjiuwen.llm.retry.on-temporary";

    @Test
    void systemPropertiesSupplyOmittedRetrySettings() {
        String previousMaxRetries = System.getProperty(MAX_RETRIES);
        String previousRateLimit = System.getProperty(RATE_LIMIT);
        String previousTemporary = System.getProperty(TEMPORARY);
        try {
            System.setProperty(MAX_RETRIES, "4");
            System.setProperty(RATE_LIMIT, "false");
            System.setProperty(TEMPORARY, "false");

            ModelCallRetryPolicy policy = ModelCallRetryPolicy.from(new ModelClientConfig());

            assertThat(policy.maxRetries()).isEqualTo(4);
            assertThat(policy.allows(ModelErrorCategory.RATE_LIMITED)).isFalse();
            assertThat(policy.allows(ModelErrorCategory.TEMPORARY)).isFalse();
        } finally {
            restoreProperty(MAX_RETRIES, previousMaxRetries);
            restoreProperty(RATE_LIMIT, previousRateLimit);
            restoreProperty(TEMPORARY, previousTemporary);
        }
    }

    @Test
    void explicitConfigurationWinsOverSystemProperties() {
        String previousMaxRetries = System.getProperty(MAX_RETRIES);
        String previousRateLimit = System.getProperty(RATE_LIMIT);
        try {
            System.setProperty(MAX_RETRIES, "4");
            System.setProperty(RATE_LIMIT, "false");
            ModelClientConfig config = ModelClientConfig.builder()
                    .maxRetries(2).retryOnRateLimit(true).build();

            ModelCallRetryPolicy policy = ModelCallRetryPolicy.from(config);

            assertThat(policy.maxRetries()).isEqualTo(2);
            assertThat(policy.allows(ModelErrorCategory.RATE_LIMITED)).isTrue();
        } finally {
            restoreProperty(MAX_RETRIES, previousMaxRetries);
            restoreProperty(RATE_LIMIT, previousRateLimit);
        }
    }

    @Test
    void invalidSystemPropertiesRetainRetryDefaults() {
        String previousMaxRetries = System.getProperty(MAX_RETRIES);
        String previousRateLimit = System.getProperty(RATE_LIMIT);
        try {
            System.setProperty(MAX_RETRIES, "-1");
            System.setProperty(RATE_LIMIT, "invalid");
            ModelClientConfig config = new ModelClientConfig();

            ModelCallRetryPolicy policy = ModelCallRetryPolicy.from(config);

            assertThat(policy.maxRetries()).isEqualTo(2);
            assertThat(policy.allows(ModelErrorCategory.RATE_LIMITED)).isTrue();
        } finally {
            restoreProperty(MAX_RETRIES, previousMaxRetries);
            restoreProperty(RATE_LIMIT, previousRateLimit);
        }
    }

    @Test
    void extraFieldRetrySettingsOverrideSystemProperties() {
        String previousMaxRetries = System.getProperty(MAX_RETRIES);
        String previousRateLimit = System.getProperty(RATE_LIMIT);
        try {
            System.setProperty(MAX_RETRIES, "4");
            System.setProperty(RATE_LIMIT, "false");
            ModelClientConfig config = new ModelClientConfig();
            config.setExtraFields(Map.of("max_retries", 0, "retry_on_rate_limit", true));

            ModelCallRetryPolicy policy = ModelCallRetryPolicy.from(config);

            assertThat(policy.maxRetries()).isZero();
            assertThat(policy.allows(ModelErrorCategory.RATE_LIMITED)).isTrue();
        } finally {
            restoreProperty(MAX_RETRIES, previousMaxRetries);
            restoreProperty(RATE_LIMIT, previousRateLimit);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
