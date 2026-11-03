/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.auto_harness.infra;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.auto_harness.schema.RuntimeExtensionArtifact;
import com.openjiuwen.harness.harness_config.HarnessConfig;
import com.openjiuwen.harness.harness_config.HarnessConfigLoader;
import com.openjiuwen.harness.harness_config.ResolvedHarnessConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merge multiple verified runtime extensions into one runtime extension.
 * <p>
 * Mirrors Python's {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
 */
public final class RuntimeExtensionMerger {

    public static final String DEFAULT_MERGED_NAME = "merged_extensions";

    private static final String OFFICIAL_EXTENSION_PREFIX = "openjiuwen.extensions.harness.";
    private static final String PACKAGE_RESOURCE_TYPE = "package";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern IMPORT_FROM_PATTERN = Pattern.compile("^(\\s*)from\\s+([^\\s]+)\\s+import\\s+(.+)$");

    private RuntimeExtensionMerger() {
    }

    /**
     * Mirrors Python's fatal merge exception in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
     */
    public static class MergedExtensionError extends RuntimeException {
        public MergedExtensionError(String message) {
            super(message);
        }

        public MergedExtensionError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Mirrors Python's rename-map tuple key in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
     */
    public record SourcePathKey(String extensionName, String relativePath) {
    }

    /**
     * Mirrors Python's skill rename-map tuple key in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
     */
    public record SkillPathKey(String extensionName, String skillName) {
    }

    /**
     * Mirrors Python's {@code MergeRuntimeExtensionsResult} in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
     */
    public record MergeRuntimeExtensionsResult(
            RuntimeExtensionArtifact runtimeExt,
            Map<SourcePathKey, String> renameMap,
            Map<SkillPathKey, String> skillRenameMap,
            List<Map<String, String>> sourceExtsSummary
    ) {
    }

    /**
     * Mirrors Python's private {@code _SourceFileInfo} in
     * {@code openjiuwen/auto_harness/infra/runtime_extension_merger.py}.
     */
    record SourceFileInfo(String extensionName, String relativePath, Path absolutePath) {
    }

    public static MergeRuntimeExtensionsResult mergeRuntimeExtensions(
            List<RuntimeExtensionArtifact> artifacts,
            Path sessionRoot
    ) {
        return mergeRuntimeExtensions(artifacts, sessionRoot, DEFAULT_MERGED_NAME);
    }

    public static MergeRuntimeExtensionsResult mergeRuntimeExtensions(
            List<RuntimeExtensionArtifact> artifacts,
            Path sessionRoot,
            String mergedName
    ) {
        Path mergedRoot = sessionRoot.resolve(mergedName);
        if (artifacts == null || artifacts.isEmpty()) {
            throw new MergedExtensionError("no artifacts to merge");
        }

        List<Map<String, String>> sourceExtsSummary = validateSourceManifests(artifacts, mergedRoot);
        try {
            Files.createDirectories(mergedRoot);
        } catch (IOException e) {
            throw new MergedExtensionError("Cannot create merged directory: " + e.getMessage(), e);
        }

        try {
            return doMerge(artifacts, mergedRoot, sourceExtsSummary, mergedName);
        } catch (Exception e) {
            cleanup(mergedRoot);
            throw new MergedExtensionError("Merge error: " + e.getMessage(), e);
        }
    }

    static String buildMergedPrefix(String mergedName) {
        return OFFICIAL_EXTENSION_PREFIX + mergedName;
    }

    static String lookupRenamedRelativePath(
            String sourceExtension,
            String moduleRelativePath,
            Map<SourcePathKey, String> renameMap
    ) {
        if (moduleRelativePath == null || moduleRelativePath.isBlank()) {
            return moduleRelativePath == null ? "" : moduleRelativePath;
        }
        for (String candidate : List.of(
                moduleRelativePath,
                moduleRelativePath + ".py",
                moduleRelativePath + "/__init__.py"
        )) {
            String mapped = renameMap.get(new SourcePathKey(sourceExtension, candidate));
            if (mapped != null) {
                return mapped;
            }
        }
        return moduleRelativePath;
    }

    static String mergedFileRelativeToDotted(String mergedRelativePath) {
        String normalized = normalizePath(mergedRelativePath);
        if (normalized.endsWith(".py")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized.replace('/', '.');
    }

    private static List<Map<String, String>> validateSourceManifests(
            List<RuntimeExtensionArtifact> artifacts,
            Path mergedRoot
    ) {
        List<Map<String, String>> summaries = new ArrayList<>();
        for (RuntimeExtensionArtifact artifact : artifacts) {
            String extensionName = nullToEmpty(artifact.getExtensionName());
            ResolvedHarnessConfig resolved = HarnessConfigLoader.load(Path.of(artifact.getConfigPath()));
            HarnessConfig config = resolved.getConfig();
            HarnessConfig.ResourcesSchema resources = config.getResources();
            for (HarnessConfig.RailResourceSchema spec : resources == null ? List.<HarnessConfig.RailResourceSchema>of() : nullToEmpty(resources.getRails())) {
                validatePackageModule(mergedRoot, extensionName, spec == null ? "" : spec.getType(), spec == null ? "" : spec.getModule());
            }
            for (HarnessConfig.ToolResourceSchema spec : resources == null ? List.<HarnessConfig.ToolResourceSchema>of() : nullToEmpty(resources.getTools())) {
                validatePackageModule(mergedRoot, extensionName, spec == null ? "" : spec.getType(), spec == null ? "" : spec.getModule());
            }
            Map<String, String> summary = new LinkedHashMap<>();
            summary.put("name", extensionName);
            if (!isBlank(config.getDescription())) {
                summary.put("description", String.valueOf(config.getDescription()));
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private static void validatePackageModule(Path mergedRoot, String extensionName, String type, String module) {
        if (!PACKAGE_RESOURCE_TYPE.equals(type) || isBlank(module)) {
            return;
        }
        String expectedPrefix = OFFICIAL_EXTENSION_PREFIX + extensionName;
        if (module.equals(expectedPrefix) || module.startsWith(expectedPrefix + ".")) {
            return;
        }
        cleanup(mergedRoot);
        throw new MergedExtensionError(
                "Source extension '" + extensionName + "' has module '" + module
                        + "' not under expected prefix '" + expectedPrefix + "'"
        );
    }

    private static MergeRuntimeExtensionsResult doMerge(
            List<RuntimeExtensionArtifact> artifacts,
            Path mergedRoot,
            List<Map<String, String>> sourceExtsSummary,
            String mergedName
    ) throws IOException {
        List<SourceFileInfo> allFiles = collectSourceFiles(artifacts);
        Map<SourcePathKey, String> renameMap = buildRenameMap(allFiles);
        Map<SkillPathKey, String> skillRenameMap = buildSkillRenameMap(artifacts);

        copySourceFiles(allFiles, mergedRoot, renameMap);
        copySkillDirectories(artifacts, mergedRoot, skillRenameMap);
        mergeRequirementsFiles(mergedRoot);
        writeEmptyInits(mergedRoot);
        rewriteImports(mergedRoot, allFiles, renameMap, mergedName);
        writeMergedManifest(mergedRoot, artifacts, renameMap, skillRenameMap, mergedName);

        RuntimeExtensionArtifact runtimeExt = RuntimeExtensionArtifact.builder()
                .extensionName(mergedName)
                .runtimePath(mergedRoot.toString())
                .configPath(mergedRoot.resolve("harness_config.yaml").toString())
                .build();
        return new MergeRuntimeExtensionsResult(
                runtimeExt,
                Map.copyOf(renameMap),
                Map.copyOf(skillRenameMap),
                List.copyOf(sourceExtsSummary)
        );
    }

    private static List<SourceFileInfo> collectSourceFiles(List<RuntimeExtensionArtifact> artifacts) throws IOException {
        List<SourceFileInfo> allFiles = new ArrayList<>();
        for (RuntimeExtensionArtifact artifact : sortedArtifacts(artifacts)) {
            Path sourceRoot = Path.of(artifact.getRuntimePath()).toAbsolutePath().normalize();
            if (!Files.exists(sourceRoot)) {
                continue;
            }
            List<Path> files;
            try (var walk = Files.walk(sourceRoot)) {
                files = walk.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> normalizePath(sourceRoot.relativize(path).toString())))
                        .toList();
            }
            for (Path file : files) {
                String relative = normalizePath(sourceRoot.relativize(file).toString());
                if ("harness_config.yaml".equals(relative) || "__init__.py".equals(file.getFileName().toString())) {
                    continue;
                }
                allFiles.add(new SourceFileInfo(artifact.getExtensionName(), relative, file));
            }
        }
        return allFiles;
    }

    private static Map<SourcePathKey, String> buildRenameMap(List<SourceFileInfo> allFiles) {
        Map<String, Integer> counts = new HashMap<>();
        for (SourceFileInfo info : allFiles) {
            counts.merge(info.relativePath(), 1, Integer::sum);
        }
        Map<SourcePathKey, String> renameMap = new LinkedHashMap<>();
        for (SourceFileInfo info : allFiles) {
            if (counts.getOrDefault(info.relativePath(), 0) <= 1) {
                continue;
            }
            String parent = parentPath(info.relativePath());
            String filename = fileName(info.relativePath());
            int dot = filename.lastIndexOf('.');
            String stem = dot >= 0 ? filename.substring(0, dot) : filename;
            String suffix = dot >= 0 ? filename.substring(dot) : "";
            String renamed = stem + "__" + info.extensionName() + suffix;
            String newRelative = parent.isBlank() ? renamed : parent + "/" + renamed;
            renameMap.put(new SourcePathKey(info.extensionName(), info.relativePath()), newRelative);
        }
        return renameMap;
    }

    private static Map<SkillPathKey, String> buildSkillRenameMap(List<RuntimeExtensionArtifact> artifacts) throws IOException {
        Map<String, List<String>> skillDirsByName = new TreeMap<>();
        for (RuntimeExtensionArtifact artifact : sortedArtifacts(artifacts)) {
            Path skillsRoot = Path.of(artifact.getRuntimePath()).toAbsolutePath().normalize().resolve("skills");
            if (!Files.isDirectory(skillsRoot)) {
                continue;
            }
            for (Path skillDir : sortedChildren(skillsRoot)) {
                if (Files.isDirectory(skillDir)) {
                    skillDirsByName.computeIfAbsent(skillDir.getFileName().toString(), key -> new ArrayList<>())
                            .add(artifact.getExtensionName());
                }
            }
        }
        Map<SkillPathKey, String> renameMap = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : skillDirsByName.entrySet()) {
            if (entry.getValue().size() <= 1) {
                continue;
            }
            List<String> extensionNames = new ArrayList<>(entry.getValue());
            extensionNames.sort(String::compareTo);
            for (String extensionName : extensionNames) {
                renameMap.put(
                        new SkillPathKey(extensionName, entry.getKey()),
                        entry.getKey() + "__" + extensionName
                );
            }
        }
        return renameMap;
    }

    private static void copySourceFiles(
            List<SourceFileInfo> allFiles,
            Path mergedRoot,
            Map<SourcePathKey, String> renameMap
    ) throws IOException {
        for (SourceFileInfo info : allFiles) {
            String destRelative = renameMap.getOrDefault(
                    new SourcePathKey(info.extensionName(), info.relativePath()),
                    info.relativePath()
            );
            Path dest = mergedRoot.resolve(destRelative);
            Path parent = dest.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(info.absolutePath(), dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private static void copySkillDirectories(
            List<RuntimeExtensionArtifact> artifacts,
            Path mergedRoot,
            Map<SkillPathKey, String> skillRenameMap
    ) throws IOException {
        for (RuntimeExtensionArtifact artifact : sortedArtifacts(artifacts)) {
            Path sourceSkills = Path.of(artifact.getRuntimePath()).toAbsolutePath().normalize().resolve("skills");
            if (!Files.isDirectory(sourceSkills)) {
                continue;
            }
            for (Path skillDir : sortedChildren(sourceSkills)) {
                if (!Files.isDirectory(skillDir)) {
                    continue;
                }
                String newSkillName = skillRenameMap.getOrDefault(
                        new SkillPathKey(artifact.getExtensionName(), skillDir.getFileName().toString()),
                        skillDir.getFileName().toString()
                );
                Path dest = mergedRoot.resolve("skills").resolve(newSkillName);
                if (!Files.exists(dest)) {
                    copyTree(skillDir, dest);
                }
            }
        }
    }

    private static void mergeRequirementsFiles(Path mergedRoot) throws IOException {
        if (!Files.isDirectory(mergedRoot)) {
            return;
        }
        List<Path> requirementFiles;
        try (var stream = Files.list(mergedRoot)) {
            requirementFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().matches("requirements.*\\.txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (requirementFiles.isEmpty()) {
            return;
        }
        if (requirementFiles.size() == 1 && "requirements.txt".equals(requirementFiles.get(0).getFileName().toString())) {
            return;
        }

        List<String> dependencies = new ArrayList<>();
        Set<String> seenPackages = new LinkedHashSet<>();
        for (Path requirementFile : requirementFiles) {
            String content = readTextReplacingInvalidUtf8(requirementFile).strip();
            for (String rawLine : content.split("\\R")) {
                String line = rawLine.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String packageName = packageNameForDedup(line);
                if (!packageName.isBlank() && seenPackages.add(packageName)) {
                    dependencies.add(line);
                }
            }
        }

        String mergedContent = dependencies.isEmpty() ? "" : String.join("\n", dependencies) + "\n";
        Path mergedRequirement = mergedRoot.resolve("requirements.txt");
        Files.writeString(mergedRequirement, mergedContent, StandardCharsets.UTF_8);
        for (Path requirementFile : requirementFiles) {
            if (!Files.isSameFile(requirementFile, mergedRequirement)) {
                Files.deleteIfExists(requirementFile);
            }
        }
    }

    private static void writeEmptyInits(Path root) throws IOException {
        Set<Path> seenDirs = new LinkedHashSet<>();
        List<Path> pythonFiles;
        try (var stream = Files.walk(root)) {
            pythonFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".py"))
                    .sorted(Comparator.comparing(path -> normalizePath(root.relativize(path).toString())))
                    .toList();
        }
        for (Path pythonFile : pythonFiles) {
            Path packageDir = pythonFile.getParent();
            if (packageDir == null || !seenDirs.add(packageDir)) {
                continue;
            }
            Path init = packageDir.resolve("__init__.py");
            if (!Files.exists(init)) {
                Files.writeString(init, "", StandardCharsets.UTF_8);
            }
        }
        Path rootInit = root.resolve("__init__.py");
        if (!Files.exists(rootInit)) {
            Files.writeString(rootInit, "", StandardCharsets.UTF_8);
        }
    }

    private static void rewriteImports(
            Path root,
            List<SourceFileInfo> allFiles,
            Map<SourcePathKey, String> renameMap,
            String mergedName
    ) throws IOException {
        String mergedPrefix = buildMergedPrefix(mergedName);
        Map<String, String> mergedToSource = new HashMap<>();
        for (SourceFileInfo info : allFiles) {
            String destRelative = renameMap.getOrDefault(
                    new SourcePathKey(info.extensionName(), info.relativePath()),
                    info.relativePath()
            );
            mergedToSource.put(destRelative, info.extensionName());
        }

        List<Path> pythonFiles;
        try (var stream = Files.walk(root)) {
            pythonFiles = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".py"))
                    .filter(path -> !"__init__.py".equals(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> normalizePath(root.relativize(path).toString())))
                    .toList();
        }
        for (Path pythonFile : pythonFiles) {
            String relative = normalizePath(root.relativize(pythonFile).toString());
            String sourceExtension = mergedToSource.get(relative);
            if (sourceExtension == null) {
                continue;
            }
            String source = Files.readString(pythonFile, StandardCharsets.UTF_8);
            String relativeDot = stripPySuffix(relative).replace('/', '.');
            String rewritten = rewriteTreeImports(source, sourceExtension, relativeDot, renameMap, mergedPrefix);
            if (!Objects.equals(source, rewritten)) {
                Files.writeString(pythonFile, rewritten, StandardCharsets.UTF_8);
            }
        }
    }

    static String rewriteTreeImports(
            String source,
            String sourceExtension,
            String relativeDot,
            Map<SourcePathKey, String> renameMap,
            String mergedPrefix
    ) {
        String oldPrefix = OFFICIAL_EXTENSION_PREFIX + sourceExtension;
        List<String> out = new ArrayList<>();
        String[] lines = source.split("\\R", -1);
        for (String line : lines) {
            Matcher matcher = IMPORT_FROM_PATTERN.matcher(line);
            if (!matcher.matches()) {
                out.add(line);
                continue;
            }
            String indent = matcher.group(1);
            String module = matcher.group(2);
            String imported = matcher.group(3);
            String newModule = rewriteImportModule(module, sourceExtension, relativeDot, renameMap, mergedPrefix, oldPrefix);
            out.add(indent + "from " + newModule + " import " + imported);
        }
        return String.join("\n", out);
    }

    private static String rewriteImportModule(
            String module,
            String sourceExtension,
            String relativeDot,
            Map<SourcePathKey, String> renameMap,
            String mergedPrefix,
            String oldPrefix
    ) {
        if (module.equals(oldPrefix) || module.startsWith(oldPrefix + ".")) {
            String suffix = module.substring(oldPrefix.length());
            String relativePath = suffix.isEmpty() ? "" : suffix.substring(1).replace('.', '/');
            if (!relativePath.isBlank()) {
                String newRelative = lookupRenamedRelativePath(sourceExtension, relativePath, renameMap);
                if (!Objects.equals(newRelative, relativePath)) {
                    return mergedPrefix + "." + mergedFileRelativeToDotted(newRelative);
                }
                return mergedPrefix + suffix;
            }
            return mergedPrefix;
        }

        int level = leadingDotCount(module);
        if (level >= 1 && level < module.length()) {
            String relativeModule = module.substring(level);
            String targetPath = resolveRelativeTargetPath(relativeDot, level, relativeModule);
            if (targetPath != null) {
                String fileKey = targetPath.endsWith(".py") ? targetPath : targetPath + ".py";
                String newRelative = renameMap.get(new SourcePathKey(sourceExtension, fileKey));
                if (newRelative != null) {
                    return mergedPrefix + "." + mergedFileRelativeToDotted(newRelative);
                }
            }
        }
        return module;
    }

    private static String resolveRelativeTargetPath(String relativeDot, int level, String module) {
        if (level < 1 || module == null || module.isBlank()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (!relativeDot.isBlank()) {
            parts.addAll(List.of(relativeDot.split("\\.")));
        }
        int up = level - 1;
        int keep = Math.max(0, parts.size() - up);
        List<String> targetParts = new ArrayList<>(parts.subList(0, keep));
        targetParts.addAll(List.of(module.split("\\.")));
        return String.join("/", targetParts);
    }

    private static void writeMergedManifest(
            Path root,
            List<RuntimeExtensionArtifact> artifacts,
            Map<SourcePathKey, String> renameMap,
            Map<SkillPathKey, String> skillRenameMap,
            String mergedName
    ) throws IOException {
        String mergedPrefix = buildMergedPrefix(mergedName);
        List<Map<String, String>> mergedRails = new ArrayList<>();
        List<Map<String, String>> mergedTools = new ArrayList<>();
        boolean includeSkills = false;

        for (RuntimeExtensionArtifact artifact : sortedArtifacts(artifacts)) {
            ResolvedHarnessConfig resolved = HarnessConfigLoader.load(Path.of(artifact.getConfigPath()));
            HarnessConfig.ResourcesSchema resources = resolved.getConfig().getResources();
            if (resources == null) {
                continue;
            }
            for (HarnessConfig.RailResourceSchema spec : nullToEmpty(resources.getRails())) {
                if (spec == null || !PACKAGE_RESOURCE_TYPE.equals(spec.getType()) || isBlank(spec.getModule())) {
                    continue;
                }
                mergedRails.add(resourceMap(
                        rewriteManifestModule(artifact.getExtensionName(), spec.getModule(), renameMap, mergedPrefix),
                        spec.getClassName()
                ));
            }
            for (HarnessConfig.ToolResourceSchema spec : nullToEmpty(resources.getTools())) {
                if (spec == null || !PACKAGE_RESOURCE_TYPE.equals(spec.getType()) || isBlank(spec.getModule())) {
                    continue;
                }
                mergedTools.add(resourceMap(
                        rewriteManifestModule(artifact.getExtensionName(), spec.getModule(), renameMap, mergedPrefix),
                        spec.getClassName()
                ));
            }
            if (resources.getSkills() != null) {
                includeSkills = true;
            }
        }

        List<Map<String, String>> dedupedTools = dedupeResourceSpecs(mergedTools);
        List<Map<String, String>> dedupedRails = dedupeResourceSpecs(mergedRails);
        String manifest = formatMergedHarnessConfigYaml(
                new HarnessConfig().getSchemaVersion(),
                mergedName,
                dedupedTools,
                dedupedRails,
                includeSkills || !skillRenameMap.isEmpty()
        );
        Files.writeString(root.resolve("harness_config.yaml"), manifest, StandardCharsets.UTF_8);
        verifyM1(root, mergedPrefix);
    }

    static String rewriteManifestModule(
            String sourceExtension,
            String module,
            Map<SourcePathKey, String> renameMap,
            String mergedPrefix
    ) {
        String oldPrefix = OFFICIAL_EXTENSION_PREFIX + sourceExtension;
        if (!(module.equals(oldPrefix) || module.startsWith(oldPrefix + "."))) {
            throw new MergedExtensionError(
                    "Module '" + module + "' not under source extension prefix '" + oldPrefix + "'"
            );
        }
        String suffix = module.substring(oldPrefix.length());
        String relativePath = suffix.isEmpty() ? "" : suffix.substring(1).replace('.', '/');
        String newRelative = lookupRenamedRelativePath(sourceExtension, relativePath, renameMap);
        if (!Objects.equals(newRelative, relativePath)) {
            return mergedPrefix + "." + mergedFileRelativeToDotted(newRelative);
        }
        return mergedPrefix + suffix;
    }

    static String formatMergedHarnessConfigYaml(
            String schemaVersion,
            String name,
            List<Map<String, String>> dedupedTools,
            List<Map<String, String>> dedupedRails,
            boolean includeSkills
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("schema_version: " + yamlScalar(schemaVersion));
        lines.add("name: " + yamlScalar(name));
        if (dedupedTools.isEmpty() && dedupedRails.isEmpty() && !includeSkills) {
            return String.join("\n", lines) + "\n";
        }

        lines.add("resources:");
        appendManifestSection(lines, "tools", dedupedTools);
        appendManifestSection(lines, "rails", dedupedRails);
        if (includeSkills) {
            lines.add("  skills:");
            lines.add("    dirs:");
            lines.add("      - skills/");
        }
        return String.join("\n", lines) + "\n";
    }

    private static void verifyM1(Path root, String mergedPrefix) throws IOException {
        Object loaded = new Yaml().load(Files.readString(root.resolve("harness_config.yaml"), StandardCharsets.UTF_8));
        if (!(loaded instanceof Map<?, ?> data)) {
            return;
        }
        Object resourcesObject = data.get("resources");
        if (!(resourcesObject instanceof Map<?, ?> resources)) {
            return;
        }
        for (String kind : List.of("rails", "tools")) {
            Object specsObject = resources.get(kind);
            if (!(specsObject instanceof List<?> specs)) {
                continue;
            }
            for (Object item : specs) {
                if (!(item instanceof Map<?, ?> spec) || !PACKAGE_RESOURCE_TYPE.equals(String.valueOf(spec.get("type")))) {
                    continue;
                }
                Object moduleValue = spec.get("module");
                String module = moduleValue == null ? "" : String.valueOf(moduleValue);
                if (module.isBlank()) {
                    throw new MergedExtensionError("M1 violation: empty module in merged manifest");
                }
                if (!module.startsWith(mergedPrefix)) {
                    throw new MergedExtensionError(
                            "M1 violation: module '" + module + "' does not start with '" + mergedPrefix + "'"
                    );
                }
                String relativeDot = module.substring(mergedPrefix.length()).replaceFirst("^\\.", "");
                String relativePath = relativeDot.replace('.', '/');
                Path pythonPath = root.resolve(relativePath + ".py");
                Path packageInit = root.resolve(relativePath).resolve("__init__.py");
                if (!Files.isRegularFile(pythonPath) && !Files.isRegularFile(packageInit)) {
                    throw new MergedExtensionError(
                            "Manifest module '" + module + "' does not map to a real file (tried "
                                    + pythonPath + " and " + packageInit + ")"
                    );
                }
            }
        }
    }

    private static Map<String, String> resourceMap(String module, String className) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("module", module);
        map.put("class", nullToEmpty(className));
        return map;
    }

    private static List<Map<String, String>> dedupeResourceSpecs(List<Map<String, String>> specs) {
        List<Map<String, String>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, String> spec : specs) {
            String key = spec.getOrDefault("module", "") + "\u0000" + spec.getOrDefault("class", "");
            if (seen.add(key)) {
                result.add(spec);
            }
        }
        return result;
    }

    private static void appendManifestSection(List<String> lines, String sectionKey, List<Map<String, String>> specs) {
        if (specs.isEmpty()) {
            return;
        }
        lines.add("  " + sectionKey + ":");
        for (Map<String, String> spec : specs) {
            lines.add("    - type: package");
            lines.add("      module: " + yamlScalar(spec.get("module")));
            lines.add("      class: " + yamlScalar(spec.get("class")));
        }
    }

    private static String yamlScalar(String value) {
        String text = nullToEmpty(value);
        if (text.isEmpty()) {
            return "\"\"";
        }
        boolean safe = text.chars().allMatch(ch ->
                ch >= 'a' && ch <= 'z'
                        || ch >= 'A' && ch <= 'Z'
                        || ch >= '0' && ch <= '9'
                        || ch == '.'
                        || ch == '_');
        if (safe) {
            return text;
        }
        try {
            return JSON.writeValueAsString(text);
        } catch (JsonProcessingException e) {
            return '"' + text.replace("\"", "\\\"") + '"';
        }
    }

    private static String readTextReplacingInvalidUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        return StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
    }

    private static String packageNameForDedup(String requirementLine) {
        String value = requirementLine;
        for (String delimiter : List.of("==", ">=", "<=", "~=", "[")) {
            int idx = value.indexOf(delimiter);
            if (idx >= 0) {
                value = value.substring(0, idx);
            }
        }
        return value.strip();
    }

    private static List<RuntimeExtensionArtifact> sortedArtifacts(List<RuntimeExtensionArtifact> artifacts) {
        return artifacts.stream()
                .sorted(Comparator.comparing(RuntimeExtensionArtifact::getExtensionName))
                .toList();
    }

    private static List<Path> sortedChildren(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
    }

    private static void copyTree(Path source, Path dest) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dest.resolve(source.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(
                        file,
                        dest.resolve(source.relativize(file).toString()),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES
                );
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void cleanup(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Mirrors Python's ignore_errors=True cleanup.
        }
    }

    private static int leadingDotCount(String module) {
        int count = 0;
        while (count < module.length() && module.charAt(count) == '.') {
            count++;
        }
        return count;
    }

    private static String stripPySuffix(String path) {
        return path.endsWith(".py") ? path.substring(0, path.length() - 3) : path;
    }

    private static String parentPath(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private static String fileName(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
