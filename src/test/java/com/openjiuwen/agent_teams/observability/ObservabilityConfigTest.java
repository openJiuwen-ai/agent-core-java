/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityConfigTest {

    @Test
    void defaultsMatchPythonModel() {
        ObservabilityConfig config = new ObservabilityConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getServiceName()).isEqualTo("openjiuwen-agent-teams");
        assertThat(config.getExporter()).isEqualTo(ObservabilityExporter.OTLP_GRPC);
        assertThat(config.getEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(config.getSampleRate()).isEqualTo(1.0);
        assertThat(config.isRedactPrompts()).isFalse();
        assertThat(config.isRedactCompletions()).isFalse();
        assertThat(config.getAttributeValueMaxLength()).isEqualTo(8192);
        assertThat(config.getExportTimeoutMs()).isEqualTo(5000);
        assertThat(config.getTracesDir()).isEqualTo("./traces");
        assertThat(config.getFileRetentionDays()).isEqualTo(7);
    }

    @Test
    void exporterFallsBackToGrpcForUnknownValues() {
        assertThat(ObservabilityExporter.fromValue("otlp_http")).isEqualTo(ObservabilityExporter.OTLP_HTTP);
        assertThat(ObservabilityExporter.fromValue("console")).isEqualTo(ObservabilityExporter.CONSOLE);
        assertThat(ObservabilityExporter.fromValue("file")).isEqualTo(ObservabilityExporter.FILE);
        assertThat(ObservabilityExporter.fromValue("unknown")).isEqualTo(ObservabilityExporter.OTLP_GRPC);
    }

    @Test
    void sampleRateMustStayWithinClosedUnitInterval() {
        ObservabilityConfig config = new ObservabilityConfig();

        config.setSampleRate(0.5);
        assertThat(config.getSampleRate()).isEqualTo(0.5);

        assertThatThrownBy(() -> config.setSampleRate(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRate");
        assertThatThrownBy(() -> config.setSampleRate(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sampleRate");
    }
}
