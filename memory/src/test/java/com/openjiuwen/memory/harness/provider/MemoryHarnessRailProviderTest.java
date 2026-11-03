package com.openjiuwen.memory.harness.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigBuilder;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.rails.memory.ExternalMemoryRail;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.spi.memory.HarnessMemoryRails;
import com.openjiuwen.spi.memory.HarnessMemoryRailsResolver;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class MemoryHarnessRailProviderTest {

    @Test
    void serviceLoaderRegistersMemoryRailProviders() {
        Set<String> names = StreamSupport.stream(
                        ServiceLoader.load(HarnessConfigBuilder.HarnessRailProvider.class).spliterator(),
                        false
                )
                .map(HarnessConfigBuilder.HarnessRailProvider::name)
                .collect(Collectors.toSet());
        assertThat(names).contains("memory", "coding_memory", "external_memory");
    }

    @Test
    void createOptionalRailBuildsCodingMemoryWithWorkspaceRelativeDir() {
        Path workspace = Path.of("workspace-root").toAbsolutePath();
        DeepAgentRail rail = HarnessConfigBuilder.createOptionalRail(
                "coding_memory",
                workspace,
                Map.of("coding_memory_dir", "notes", "language", "en")
        ).orElseThrow();
        assertThat(rail).isInstanceOf(CodingMemoryRail.class);
        assertThat(((CodingMemoryRail) rail).getCodingMemoryDir())
                .isEqualTo(workspace.resolve("notes").toString());
    }

    @Test
    void harnessMemoryRailsSpiCreatesMemoryAndCodingRails() {
        HarnessMemoryRails rails = HarnessMemoryRailsResolver.find().orElseThrow();
        assertThat(rails.createMemoryRail(Map.of("model_name", "text-embedding-3-small")))
                .isInstanceOf(MemoryRail.class);
        assertThat(rails.createCodingMemoryRail("notes", Map.of(), "en"))
                .isInstanceOf(CodingMemoryRail.class);
    }

    @Test
    void resolveRailsLoadsBuiltinMemoryRailsFromSpi() {
        HarnessConfig.ResourcesSchema resources = HarnessConfig.ResourcesSchema.builder()
                .rails(List.of(
                        HarnessConfig.RailResourceSchema.builder().type("builtin").name("memory").build(),
                        HarnessConfig.RailResourceSchema.builder().type("builtin").name("coding_memory").build(),
                        HarnessConfig.RailResourceSchema.builder()
                                .type("builtin")
                                .name("external_memory")
                                .config(Map.of("provider", "mem0"))
                                .build()
                ))
                .build();
        List<DeepAgentRail> rails = HarnessConfigBuilder.resolveRails(resources, Path.of(""));
        assertThat(rails).hasSize(3);
        assertThat(rails.get(0)).isInstanceOf(MemoryRail.class);
        assertThat(rails.get(1)).isInstanceOf(CodingMemoryRail.class);
        assertThat(rails.get(2)).isInstanceOf(ExternalMemoryRail.class);
    }
}
