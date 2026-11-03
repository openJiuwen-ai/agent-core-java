/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.security.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailEnumsTest {

    @Test
    void riskLevelRoundTripsStringValues() {
        assertThat(RiskLevel.SAFE.value()).isEqualTo("safe");
        assertThat(RiskLevel.fromValue("critical")).isEqualTo(RiskLevel.CRITICAL);
    }

    @Test
    void riskLevelRejectsUnknownValues() {
        assertThatThrownBy(() -> RiskLevel.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown risk level");
    }

    @Test
    void guardrailContentTypeRoundTripsStringValues() {
        assertThat(GuardrailContentType.TEXT.value()).isEqualTo("text");
        assertThat(GuardrailContentType.fromValue("tool_call")).isEqualTo(GuardrailContentType.TOOL_CALL);
    }

    @Test
    void guardrailContentTypeRejectsUnknownValues() {
        assertThatThrownBy(() -> GuardrailContentType.fromValue("binary"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown guardrail content type");
    }
}
