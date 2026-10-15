/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.runbudget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * UT-C02~C05 core side: {@link RunBudgetConfig} builder defaults, normalization and
 * fail-fast validation (illegal values rejected at build time).
 */
class RunBudgetConfigValidationTest {
    @Test
    void build_defaultConfig_succeedsWithDocumentedDefaults() {
        // Given a builder with only defaults
        RunBudgetConfig config = RunBudgetConfig.builder().build();

        // When / Then defaults match the L2 documented cadence; turn dimension defaults off
        // (the factory gating decides and passes it explicitly)
        assertThat(config.isTurnEnabled()).isFalse();
        assertThat(config.isTimeEnabled()).isFalse();
        assertThat(config.getSuggestedRounds()).isNull();
        assertThat(config.getDefaultGuaranteedRounds()).isEqualTo(30);
        assertThat(config.getProgressIntervalStep()).isEqualTo(10);
        assertThat(config.getMaxCheckpointInterval()).isEqualTo(50);
        assertThat(config.getStagnationEscalationThreshold()).isEqualTo(3);
        assertThat(config.getGentleReminderStartMultiplier()).isEqualTo(2);
        assertThat(config.getGentleReminderInterval()).isEqualTo(15);
        assertThat(config.getTotalBudgetSeconds()).isNull();
        assertThat(config.getNearDeadlineThresholdSeconds()).isEqualTo(60);
    }

    @Test
    void build_negativeSuggestedRounds_throws() {
        // Given a negative suggestion, When building, Then fail-fast
        assertThatThrownBy(() -> RunBudgetConfig.builder().suggestedRounds(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suggestedRounds");
    }

    @Test
    void build_hardLimitBelowGuarantee_throws() {
        // Given hard limit below the guarantee, When building, Then fail-fast
        assertThatThrownBy(() -> RunBudgetConfig.builder()
                .suggestedRounds(30).hardLimit(20).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hardLimit");
    }

    @Test
    void build_firstCheckpointBelowMinimum_throws() {
        // Given a first checkpoint piercing the guarantee zone, When building, Then fail-fast
        assertThatThrownBy(() -> RunBudgetConfig.builder()
                .suggestedRounds(30).firstCheckpoint(20).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("firstCheckpoint");
    }

    @Test
    void build_nonPositiveTimeBudget_throws() {
        // Given a negative total budget, When building, Then fail-fast
        assertThatThrownBy(() -> RunBudgetConfig.builder().totalBudgetSeconds(-5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalBudgetSeconds");
    }

    @Test
    void build_thresholdExceedsBudget_clampsToBudget() {
        // Given a threshold larger than the total budget
        RunBudgetConfig config = RunBudgetConfig.builder()
                .totalBudgetSeconds(30).nearDeadlineThresholdSeconds(60).build();

        // When built, Then the threshold is clamped to the total budget
        assertThat(config.getNearDeadlineThresholdSeconds()).isEqualTo(30);
    }

    @Test
    void build_hardLimitEqualsGuarantee_warnsButSucceeds() {
        // Given a degenerate equal limit, When building, Then allowed (WARN only)
        RunBudgetConfig config = RunBudgetConfig.builder()
                .suggestedRounds(30).hardLimit(30).build();
        assertThat(config.getHardLimit()).isEqualTo(30);
    }

    @Test
    void build_turnDisabled_stillValidatesTimeDimension() {
        // Given turn disabled with a legal time budget
        RunBudgetConfig config = RunBudgetConfig.builder()
                .turnEnabled(false).totalBudgetSeconds(120).build();

        // When / Then only the time dimension is active
        assertThat(config.isTurnEnabled()).isFalse();
        assertThat(config.isTimeEnabled()).isTrue();
    }
}
