package com.openjiuwen.harness.harness_config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's package re-exports in
 * {@code openjiuwen/harness/harness_config/__init__.py}.
 */
class HarnessConfigPackageTest {

    @Test
    void allMatchesPythonPackageExports() {
        assertThat(HarnessConfigPackage.ALL).containsExactly(
                "HarnessConfig",
                "HarnessConfigBuilder",
                "HarnessConfigInfo",
                "HarnessConfigLoader",
                "HarnessConfigRegistry",
                "ResolvedFileSection",
                "ResolvedHarnessConfig",
                "ResolvedSection",
                "generate_harness_config_yaml"
        );
    }

    @Test
    void getAttributeReturnsExportedClassesAndFunctionMarker() {
        assertThat(HarnessConfigPackage.getAttribute("HarnessConfig")).isEqualTo(HarnessConfig.class);
        assertThat(HarnessConfigPackage.getAttribute("HarnessConfigBuilder")).isEqualTo(HarnessConfigBuilder.class);
        assertThat(HarnessConfigPackage.getAttribute("HarnessConfigInfo")).isEqualTo(HarnessConfigInfo.class);
        assertThat(HarnessConfigPackage.getAttribute("HarnessConfigLoader")).isEqualTo(HarnessConfigLoader.class);
        assertThat(HarnessConfigPackage.getAttribute("HarnessConfigRegistry")).isEqualTo(HarnessConfigRegistry.class);
        assertThat(HarnessConfigPackage.getAttribute("ResolvedFileSection")).isEqualTo(ResolvedFileSection.class);
        assertThat(HarnessConfigPackage.getAttribute("ResolvedHarnessConfig")).isEqualTo(ResolvedHarnessConfig.class);
        assertThat(HarnessConfigPackage.getAttribute("ResolvedSection")).isEqualTo(ResolvedSection.class);
        assertThat(HarnessConfigPackage.getAttribute("generate_harness_config_yaml"))
                .isEqualTo("generate_harness_config_yaml");
    }

    @Test
    void generateHarnessConfigYamlDelegatesToBuilder() {
        assertThat(HarnessConfigPackage.generateHarnessConfigYaml())
                .isEqualTo(HarnessConfigBuilder.generateHarnessConfigYaml())
                .contains("schema_version: harness_config.v0.1");
    }

    @Test
    void getAttributeRejectsMissingNameLikePythonAttributeLookup() {
        assertThatThrownBy(() -> HarnessConfigPackage.getAttribute("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has no attribute 'missing'");
    }
}
