package com.openjiuwen.core.multiagent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamConfigTest {

    @Test
    void shouldExposePythonDefaultValues() {
        TeamConfig config = new TeamConfig();

        assertThat(config.getMaxAgents()).isEqualTo(10);
        assertThat(config.getMaxConcurrentMessages()).isEqualTo(100);
        assertThat(config.getMessageTimeout()).isEqualTo(30.0);
        assertThat(config.getExtraFields()).isEmpty();
    }

    @Test
    void shouldSupportFluentConfiguration() {
        TeamConfig config = new TeamConfig();

        TeamConfig configured = config
                .configureMaxAgents(16)
                .configureConcurrency(48)
                .configureTimeout(12.5);

        assertThat(configured).isSameAs(config);
        assertThat(config.getMaxAgents()).isEqualTo(16);
        assertThat(config.getMaxConcurrentMessages()).isEqualTo(48);
        assertThat(config.getMessageTimeout()).isEqualTo(12.5);
    }

    @Test
    void shouldCaptureExtraFieldsForPydanticExtraAllowParity() {
        TeamConfig config = new TeamConfig();

        config.putExtraField("custom_limit", 7);
        config.putExtraField("mode", "debug");

        assertThat(config.getExtraFields())
                .containsEntry("custom_limit", 7)
                .containsEntry("mode", "debug");
    }
}
