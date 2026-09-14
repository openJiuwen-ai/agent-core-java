/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergeRuntimeExtensionsResult;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.MergedExtensionError;
import com.openjiuwen.auto_harness.infra.RuntimeExtensionMerger.SourcePathKey;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Focused parity tests for runtime extension merging.
 * <p>
 * Mirrors Python's {@code tests/unit_tests/auto_harness/test_runtime_extension_merger.py}
 * for {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
 */
class RuntimeExtensionMergerTest {

    @TempDir
    private Path tempDir;

    @Test
    void zeroConflictKeepsFilesAndWritesMergedManifest() throws Exception {
        RuntimeExtensionArtifact extA = writeMinimalExtension(
                tempDir,
                "ext_a",
                Map.of("tools/a_only.py", "A=1\n"),
                false,
                false
        );
        RuntimeExtensionArtifact extB = writeMinimalExtension(
                tempDir,
                "ext_b",
                Map.of("tools/b_only.py", "B=1\n", "tools/helper_b.py", "VALUE_B='ok'\n"),
                false,
                false
        );
        Files.delete(Path.of(extB.getRuntimePath()).resolve("tools").resolve("helper.py"));

        MergeRuntimeExtensionsResult result = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir);

        Path mergedRoot = Path.of(result.runtimeExt().getRuntimePath());
        assertThat(result.runtimeExt().getExtensionName()).isEqualTo("merged_extensions");
        assertThat(result.renameMap()).isEmpty();
        assertThat(result.skillRenameMap()).isEmpty();
        assertThat(mergedRoot.resolve("harness_config.yaml")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("helper.py")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("a_only.py")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("b_only.py")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("helper_b.py")).isRegularFile();
        for (Path init : Files.walk(mergedRoot).filter(path -> path.getFileName().toString().equals("__init__.py")).toList()) {
            assertThat(Files.readAllBytes(init)).isEmpty();
        }
    }

    @Test
    void conflictingHelperFilesAreRenamedForEachExtension() throws Exception {
        RuntimeExtensionArtifact extA = writeMinimalExtension(
                tempDir,
                "ext_a",
                Map.of("tools/helper.py", "VALUE = \"from_a\"\n"),
                false,
                false
        );
        RuntimeExtensionArtifact extB = writeMinimalExtension(
                tempDir,
                "ext_b",
                Map.of("tools/helper.py", "VALUE = \"from_b\"\n"),
                false,
                false
        );

        MergeRuntimeExtensionsResult result = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir);

        Path mergedRoot = Path.of(result.runtimeExt().getRuntimePath());
        assertThat(mergedRoot.resolve("tools").resolve("helper__ext_a.py")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("helper__ext_b.py")).isRegularFile();
        assertThat(result.renameMap()).containsEntry(
                new SourcePathKey("ext_a", "tools/helper.py"),
                "tools/helper__ext_a.py"
        );
        assertThat(result.renameMap()).containsEntry(
                new SourcePathKey("ext_b", "tools/helper.py"),
                "tools/helper__ext_b.py"
        );
    }

    @Test
    void conflictingSourceFilesAreRenamedAndManifestTracksRenames() throws Exception {
        RuntimeExtensionArtifact extA = writeSharedToolExtension("ext_a", "ToolFromExtA");
        RuntimeExtensionArtifact extB = writeSharedToolExtension("ext_b", "ToolFromExtB");

        MergeRuntimeExtensionsResult result = RuntimeExtensionMerger.mergeRuntimeExtensions(
                List.of(extA, extB),
                tempDir.resolve("session")
        );

        Path mergedRoot = Path.of(result.runtimeExt().getRuntimePath());
        assertThat(mergedRoot.resolve("tools").resolve("shared_tool__ext_a.py")).isRegularFile();
        assertThat(mergedRoot.resolve("tools").resolve("shared_tool__ext_b.py")).isRegularFile();
        assertThat(result.renameMap()).containsEntry(
                new SourcePathKey("ext_a", "tools/shared_tool.py"),
                "tools/shared_tool__ext_a.py"
        );
        Map<?, ?> manifest = loadYamlMap(mergedRoot.resolve("harness_config.yaml"));
        List<String> modules = ((List<?>) ((Map<?, ?>) manifest.get("resources")).get("tools"))
                .stream()
                .map(item -> String.valueOf(((Map<?, ?>) item).get("module")))
                .toList();
        assertThat(modules).anySatisfy(module -> assertThat(module).endsWith(".shared_tool__ext_a"));
        assertThat(modules).anySatisfy(module -> assertThat(module).endsWith(".shared_tool__ext_b"));
    }

    @Test
    void skillConflictsAreCopiedWithExtensionSuffixAndManifestIncludesSkillsDir() throws Exception {
        RuntimeExtensionArtifact extA = writeSkillExtension("ext_a");
        RuntimeExtensionArtifact extB = writeSkillExtension("ext_b");

        MergeRuntimeExtensionsResult result = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir);

        Path mergedRoot = Path.of(result.runtimeExt().getRuntimePath());
        assertThat(mergedRoot.resolve("skills").resolve("my_skill__ext_a")).isDirectory();
        assertThat(mergedRoot.resolve("skills").resolve("my_skill__ext_b")).isDirectory();
        Map<?, ?> manifest = loadYamlMap(mergedRoot.resolve("harness_config.yaml"));
        Map<?, ?> skills = (Map<?, ?>) ((Map<?, ?>) manifest.get("resources")).get("skills");
        List<String> dirs = ((List<?>) skills.get("dirs")).stream()
                .map(String::valueOf)
                .toList();
        assertThat(dirs).containsExactly("skills/");
    }

    @Test
    void illegalSourceManifestPrefixRaisesAndCleansMergedDir() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("bad_ext"));
        Files.createDirectories(root.resolve("tools"));
        Files.writeString(root.resolve("__init__.py"), "");
        Files.writeString(root.resolve("tools").resolve("__init__.py"), "");
        Files.writeString(root.resolve("harness_config.yaml"), """
                schema_version: harness_config.v0.1
                name: bad_ext
                resources:
                  rails:
                    - type: package
                      module: some.other.prefix.rail
                      class: BadRail
                  tools: []
                """);
        RuntimeExtensionArtifact artifact = artifact("bad_ext", root);

        assertThatThrownBy(() -> RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(artifact), tempDir))
                .isInstanceOf(MergedExtensionError.class);
        assertThat(tempDir.resolve("merged_extensions")).doesNotExist();
    }

    @Test
    void absoluteImportsAreRewrittenToMergedPrefix() throws Exception {
        RuntimeExtensionArtifact extA = writeMinimalExtension(
                tempDir,
                "ext_a",
                Map.of("tools/demo_tool.py", "from openjiuwen.extensions.harness.ext_a.tools.helper import VALUE\n"),
                false,
                false
        );
        RuntimeExtensionArtifact extB = writeMinimalExtension(tempDir, "ext_b", Map.of("tools/other.py", "X=1\n"), false, false);
        Files.delete(Path.of(extB.getRuntimePath()).resolve("tools").resolve("helper.py"));

        MergeRuntimeExtensionsResult result = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir);

        String content = Files.readString(
                Path.of(result.runtimeExt().getRuntimePath()).resolve("tools").resolve("demo_tool.py")
        );
        assertThat(content).contains("openjiuwen.extensions.harness.merged_extensions.tools.helper");
        assertThat(content).doesNotContain("ext_a");
    }

    @Test
    void sameInputProducesDeterministicOutput() throws Exception {
        RuntimeExtensionArtifact extA = writeMinimalExtension(tempDir, "ext_a", Map.of("tools/a.py", "A=1\n"), false, false);
        RuntimeExtensionArtifact extB = writeMinimalExtension(tempDir, "ext_b", Map.of("tools/b.py", "B=1\n"), false, false);

        MergeRuntimeExtensionsResult first = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir.resolve("run1"));
        MergeRuntimeExtensionsResult second = RuntimeExtensionMerger.mergeRuntimeExtensions(List.of(extA, extB), tempDir.resolve("run2"));

        assertThat(directoryHash(Path.of(first.runtimeExt().getRuntimePath())))
                .isEqualTo(directoryHash(Path.of(second.runtimeExt().getRuntimePath())));
    }

    private RuntimeExtensionArtifact writeMinimalExtension(
            Path baseDir,
            String extensionName,
            Map<String, String> extraFiles,
            boolean extraRail,
            boolean extraTool
    ) throws Exception {
        Path root = Files.createDirectories(baseDir.resolve(extensionName));
        Files.createDirectories(root.resolve("tools"));
        Files.writeString(root.resolve("__init__.py"), "");
        Files.writeString(root.resolve("tools").resolve("__init__.py"), "");
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("tools/helper.py", "VALUE = \"ok\"\n");
        files.putAll(extraFiles);
        if (extraRail) {
            Files.createDirectories(root.resolve("rails"));
            Files.writeString(root.resolve("rails").resolve("__init__.py"), "");
            files.put("rails/demo_rail.py", "class DemoRail:\n    pass\n");
        }
        if (extraTool) {
            files.put("tools/demo_tool.py", "from .helper import VALUE\nclass DemoTool:\n    pass\n");
        }
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path file = root.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
        }
        String rails = extraRail
                ? "\n    - type: package\n      module: openjiuwen.extensions.harness." + extensionName + ".rails.demo_rail\n      class: DemoRail"
                : " []";
        String tools = extraTool
                ? "\n    - type: package\n      module: openjiuwen.extensions.harness." + extensionName + ".tools.demo_tool\n      class: DemoTool"
                : " []";
        Files.writeString(root.resolve("harness_config.yaml"), """
                schema_version: harness_config.v0.1
                name: %s
                resources:
                  rails:%s
                  tools:%s
                """.formatted(extensionName, rails, tools), StandardCharsets.UTF_8);
        return artifact(extensionName, root);
    }

    private RuntimeExtensionArtifact writeSharedToolExtension(String extensionName, String className) throws Exception {
        Path root = Files.createDirectories(tempDir.resolve(extensionName));
        Files.createDirectories(root.resolve("tools"));
        Files.writeString(root.resolve("__init__.py"), "");
        Files.writeString(root.resolve("tools").resolve("__init__.py"), "");
        Files.writeString(root.resolve("tools").resolve("shared_tool.py"), "class " + className + ":\n    pass\n");
        Files.writeString(root.resolve("harness_config.yaml"), """
                schema_version: harness_config.v0.1
                name: %s
                resources:
                  rails: []
                  tools:
                    - type: package
                      module: openjiuwen.extensions.harness.%s.tools.shared_tool
                      class: %s
                """.formatted(extensionName, extensionName, className));
        return artifact(extensionName, root);
    }

    private RuntimeExtensionArtifact writeSkillExtension(String extensionName) throws Exception {
        Path root = Files.createDirectories(tempDir.resolve(extensionName));
        Files.createDirectories(root.resolve("tools"));
        Files.createDirectories(root.resolve("skills").resolve("my_skill"));
        Files.writeString(root.resolve("__init__.py"), "");
        Files.writeString(root.resolve("tools").resolve("__init__.py"), "");
        Files.writeString(root.resolve("skills").resolve("my_skill").resolve("SKILL.md"), """
                ---
                name: my_skill
                description: demo
                ---
                body
                """);
        Files.writeString(root.resolve("harness_config.yaml"), """
                schema_version: harness_config.v0.1
                name: %s
                resources:
                  rails: []
                  tools: []
                  skills:
                    dirs:
                      - skills/
                """.formatted(extensionName));
        return artifact(extensionName, root);
    }

    private RuntimeExtensionArtifact artifact(String extensionName, Path root) {
        return RuntimeExtensionArtifact.builder()
                .extensionName(extensionName)
                .runtimePath(root.toString())
                .configPath(root.resolve("harness_config.yaml").toString())
                .build();
    }

    private Map<?, ?> loadYamlMap(Path path) throws Exception {
        return (Map<?, ?>) new Yaml().load(Files.readString(path, StandardCharsets.UTF_8));
    }

    private String directoryHash(Path root) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Path file : Files.walk(root)
                .filter(Files::isRegularFile)
                .sorted((a, b) -> root.relativize(a).toString().compareTo(root.relativize(b).toString()))
                .toList()) {
            digest.update(root.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(file));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
