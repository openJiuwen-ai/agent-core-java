/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.auto_harness;

import com.openjiuwen.auto_harness.infra.RuntimeExtensionLoader;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.rails.DeepAgentRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.schema.DeepAgentConfig;
import com.openjiuwen.harness.tools.skills.SkillDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests/unit_tests/auto_harness/test_runtime_extension_loader.py}.</p>
 */
class RuntimeExtensionLoaderMissingTest {

    @TempDir
    private Path tempDir;

    @Test
    void loadRuntimeResourcesFromManifest() throws Exception {
        RuntimeExtensionArtifact artifact = writeRuntimeExtension(
                tempDir,
                "demo_tool_load",
                false,
                "runtime skill"
        );

        List<Class<?>> rails = RuntimeExtensionLoader.loadRuntimeRails(artifact, "session123");
        List<Class<?>> tools = RuntimeExtensionLoader.loadRuntimeTools(artifact, "session123");

        assertThat(rails).containsExactly(DemoRail.class);
        assertThat(tools).containsExactly(DemoTool.class);
        assertThat(((Tool) tools.get(0).getDeclaredConstructor().newInstance()).getCard().getDescription())
                .isEqualTo("runtime-ok");
    }

    @Test
    void deepAgentLoadsRuntimeExtensionConfig() throws Exception {
        RuntimeExtensionArtifact artifact = writeRuntimeExtension(
                tempDir,
                "demo_tool_agent",
                false,
                "runtime skill"
        );
        DeepAgent agent = configuredAgent("deep-agent-load");

        List<String> loaded = agent.loadHarnessConfig(artifact.getConfigPath());

        assertThat(loaded).contains("rail:DemoRail", "tool:DemoTool");
        assertThat(agent.getRails()).anyMatch(DemoRail.class::isInstance);
        assertThat(agent.getAbilityManager().getTools()).containsKey("demo_tool");
    }

    @Test
    void runtimeExtensionSkillsAreRefreshedAndPreferred() throws Exception {
        Path oldRoot = Files.createDirectories(tempDir.resolve("old_skills"));
        writeSkill(oldRoot, "shared_skill", "old skill", "old skill body");
        RuntimeExtensionArtifact artifact = writeRuntimeExtension(
                tempDir.resolve("runtime"),
                "demo_tool_skill",
                true,
                "new runtime skill body"
        );
        DeepAgent agent = configuredAgent("runtime-skill-agent");
        SkillUseRail oldRail = new SkillUseRail(List.of(oldRoot.toString()), SkillUseRail.SKILL_MODE_ALL);
        agent.registerRail(oldRail).join();
        oldRail.reloadSkills();

        List<String> loaded = agent.loadHarnessConfig(artifact.getConfigPath());

        assertThat(loaded).anyMatch(item -> item.startsWith("skill_dir:"));
        SkillUseRail skillRail = firstSkillRail(agent);
        List<String> skillDirs = skillRail.getSkillDirs();
        assertThat(Path.of(skillDirs.get(0)).toString()).endsWith(Path.of("demo_ext", "skills").toString());
        assertThat(skillRail.getSkillsMeta().get(0).name()).isEqualTo("shared_skill");
        assertThat(Path.of(skillRail.getSkillsMeta().get(0).directory()).toString())
                .endsWith(Path.of("demo_ext", "skills", "shared_skill").toString());
    }

    @Test
    void unloadHarnessConfigRemovesLoadedResources() throws Exception {
        RuntimeExtensionArtifact artifact = writeRuntimeExtension(
                tempDir,
                "demo_tool_unload",
                true,
                "test skill body"
        );
        DeepAgent agent = configuredAgent("unload-test-agent");

        List<String> loaded = agent.loadHarnessConfig(artifact.getConfigPath());
        assertThat(loaded).contains("rail:DemoRail", "tool:DemoTool");
        assertThat(loaded).anyMatch(item -> item.startsWith("skill_dir:"));
        assertThat(agent.getRails()).anyMatch(DemoRail.class::isInstance);
        assertThat(agent.getAbilityManager().getTools()).containsKey("demo_tool");

        List<String> unloaded = agent.unloadHarnessConfig(artifact.getConfigPath());

        assertThat(unloaded).contains("rail:DemoRail", "tool:demo_tool");
        assertThat(unloaded).anyMatch(item -> item.startsWith("tool_id:"));
        assertThat(unloaded).anyMatch(item -> item.startsWith("skill_dir:"));
        assertThat(agent.getRails()).noneMatch(DemoRail.class::isInstance);
        assertThat(agent.getAbilityManager().getTools()).doesNotContainKey("demo_tool");
        assertThat(firstSkillRail(agent).getSkillDirs())
                .noneMatch(path -> Path.of(path).toString().endsWith(Path.of("demo_ext", "skills").toString()));
    }

    @Test
    void unloadHarnessConfigReturnsEmptyForMissingFile() {
        DeepAgent agent = configuredAgent("missing-config-agent");
        Path missingConfig = tempDir.resolve("missing").resolve("harness_config.yaml");

        // Align with Python unload_harness_config: missing manifest -> empty list (no throw).
        assertThat(agent.unloadHarnessConfig(missingConfig.toString())).isEmpty();
    }

    @Test
    void unloadHarnessConfigRemovesSkillDirsFromSharedRail() throws Exception {
        Path oldRoot = Files.createDirectories(tempDir.resolve("old_skills"));
        writeSkill(oldRoot, "shared_skill", "old skill", "old skill body");
        RuntimeExtensionArtifact artifact = writeRuntimeExtension(
                tempDir.resolve("runtime"),
                "demo_tool_shared",
                true,
                "new runtime skill body"
        );
        DeepAgent agent = configuredAgent("skill-unload-agent");
        SkillUseRail oldRail = new SkillUseRail(List.of(oldRoot.toString()), SkillUseRail.SKILL_MODE_ALL);
        agent.registerRail(oldRail).join();
        oldRail.reloadSkills();

        agent.loadHarnessConfig(artifact.getConfigPath());
        SkillUseRail skillRail = firstSkillRail(agent);
        assertThat(skillRail.getSkillDirs()).hasSizeGreaterThanOrEqualTo(2);

        List<String> unloaded = agent.unloadHarnessConfig(artifact.getConfigPath());

        assertThat(unloaded).anyMatch(item -> item.startsWith("skill_dir:"));
        assertThat(skillRail.getSkillDirs()).contains(oldRoot.toAbsolutePath().normalize().toString());
        assertThat(skillRail.getSkillDirs())
                .noneMatch(path -> Path.of(path).toString().endsWith(Path.of("demo_ext", "skills").toString()));
        assertThat(skillRail.getSkillsMeta().stream().map(SkillDescriptor::name).toList())
                .containsExactly("shared_skill");
    }

    private RuntimeExtensionArtifact writeRuntimeExtension(
            Path baseDir,
            String toolId,
            boolean includeSkill,
            String skillBody
    ) throws Exception {
        Path root = Files.createDirectories(baseDir.resolve("demo_ext"));
        Files.createDirectories(root.resolve("tools"));
        Files.createDirectories(root.resolve("rails"));
        Files.writeString(root.resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("tools").resolve("__init__.py"), "", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("rails").resolve("__init__.py"), "", StandardCharsets.UTF_8);
        if (includeSkill) {
            writeSkill(root.resolve("skills"), "shared_skill", "shared runtime skill", skillBody);
        }
        Path configPath = root.resolve("harness_config.yaml");
        Files.writeString(configPath, """
                schema_version: harness_config.v0.1
                name: demo_ext
                resources:
                  rails:
                    - type: package
                      module: openjiuwen.extensions.harness.demo_ext.rails.demo_rail
                      class: %s
                  tools:
                    - type: package
                      module: openjiuwen.extensions.harness.demo_ext.tools.demo_tool
                      class: %s
                      name: %s
                  skills:
                    dirs:
                      - skills/
                """.formatted(DemoRail.class.getName(), DemoTool.class.getName(), toolId), StandardCharsets.UTF_8);
        return RuntimeExtensionArtifact.builder()
                .extensionName("demo_ext")
                .runtimePath(root.toString())
                .configPath(configPath.toString())
                .build();
    }

    private static Path writeSkill(Path root, String name, String description, String body) throws Exception {
        Path skillDir = Files.createDirectories(root.resolve(name));
        Path skillMd = skillDir.resolve("SKILL.md");
        Files.writeString(skillMd, """
                ---
                name: %s
                description: %s
                ---

                %s
                """.formatted(name, description, body), StandardCharsets.UTF_8);
        return skillMd;
    }

    private static DeepAgent configuredAgent(String id) {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setEnableTaskLoop(false);
        DeepAgent agent = new DeepAgent(new AgentCard(id, "deep", "test"));
        agent.configure(config);
        return agent;
    }

    private static SkillUseRail firstSkillRail(DeepAgent agent) {
        return agent.getRails().stream()
                .filter(SkillUseRail.class::isInstance)
                .map(SkillUseRail.class::cast)
                .findFirst()
                .orElseThrow();
    }

    /**
     * Mirrors Python's runtime {@code DemoRail} declared inside
     * {@code tests/unit_tests/auto_harness/test_runtime_extension_loader.py}.
     */
    public static final class DemoRail extends DeepAgentRail {
    }

    /**
     * Mirrors Python's runtime {@code DemoTool} declared inside
     * {@code tests/unit_tests/auto_harness/test_runtime_extension_loader.py}.
     */
    public static final class DemoTool extends Tool {
        public DemoTool() {
            super(new ToolCard("demo_tool_id", "demo_tool", "runtime-ok", Map.of()));
        }
    }
}
