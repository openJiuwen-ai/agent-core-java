package com.openjiuwen.harness.harness_config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.TaskPlanningRail;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

class HarnessRailProviderSpiTest {

    @Test
    void createOptionalRailIsEmptyWhenProviderIsMissing() {
        assertThat(HarnessConfigBuilder.createOptionalRail("coding_memory", Path.of(""), Map.of())).isEmpty();
        assertThat(HarnessConfigBuilder.createOptionalRail("memory", Path.of(""), Map.of())).isEmpty();
    }

    @Test
    void registeredProviderIsUsedByCreateOptionalRailAndResolveRails() {
        String name = "test_harness_rail_" + System.nanoTime();
        HarnessConfigBuilder.registerRailProvider(new HarnessConfigBuilder.HarnessRailProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public DeepAgentRail create() {
                return new TaskPlanningRail();
            }
        });

        assertThat(HarnessConfigBuilder.createOptionalRail(name, Path.of(""), Map.of()))
                .isPresent()
                .get()
                .isInstanceOf(TaskPlanningRail.class);

        HarnessConfig.ResourcesSchema resources = HarnessConfig.ResourcesSchema.builder()
                .rails(List.of(HarnessConfig.RailResourceSchema.builder().type("builtin").name(name).build()))
                .build();
        List<DeepAgentRail> rails = HarnessConfigBuilder.resolveRails(resources, Path.of(""));
        assertThat(rails).hasSize(1);
        assertThat(rails.get(0)).isInstanceOf(TaskPlanningRail.class);
    }

    @Test
    void unknownBuiltinRailWithoutProviderThrowsEntryPointError() {
        HarnessConfig.ResourcesSchema resources = HarnessConfig.ResourcesSchema.builder()
                .rails(List.of(HarnessConfig.RailResourceSchema.builder()
                        .type("builtin")
                        .name("not_a_registered_rail")
                        .build()))
                .build();
        assertThatThrownBy(() -> HarnessConfigBuilder.resolveRails(resources))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Harness rail entry point not found: not_a_registered_rail");
    }
}
