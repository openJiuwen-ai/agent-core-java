package com.openjiuwen.auto_harness.pipelines;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutoHarnessPipelineNamesTest {

    @Test
    void normalizesLegacyAliasesOnly() {
        assertThat(AutoHarnessPipelineNames.normalizePipelineName("pr_pipeline"))
                .isEqualTo(AutoHarnessPipelineNames.META_EVOLVE_PIPELINE);
        assertThat(AutoHarnessPipelineNames.normalizePipelineName("extended_harness_pipeline"))
                .isEqualTo(AutoHarnessPipelineNames.EXTENDED_EVOLVE_PIPELINE);
        assertThat(AutoHarnessPipelineNames.normalizePipelineName("custom_pipeline"))
                .isEqualTo("custom_pipeline");
    }
}
