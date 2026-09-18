/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.skills;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.protocal.BaseFsProtocal;
import com.openjiuwen.core.sysop.result.FileSystemData;
import com.openjiuwen.core.sysop.result.FileSystemItem;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ReadFileData;
import com.openjiuwen.core.sysop.result.ReadFileResult;

import org.yaml.snakeyaml.Yaml;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for skill metadata loaded from {@code SKILL.md} files.
 *
 * <p>Mirrors Python's {@code SkillManager} in
 * {@code openjiuwen/core/single_agent/skills/skill_manager.py}.</p>
 */
public class SkillManager {
    private final Map<String, Skill> registry = new LinkedHashMap<>();
    private final Map<String, Long> updateAtCache = new LinkedHashMap<>();
    private final List<String> skillOrder = new ArrayList<>();
    private final Function<String, BaseFsOperation> fsResolver;
    private String sysOperationId;
    private String description = "";

    public SkillManager(String sysOperationId) {
        this(sysOperationId, SkillManager::resolveFsOperation);
    }

    public SkillManager(String sysOperationId, Function<String, BaseFsOperation> fsResolver) {
        this.sysOperationId = sysOperationId;
        this.fsResolver = fsResolver == null ? SkillManager::resolveFsOperation : fsResolver;
    }

    public void setSysOperationId(String sysOperationId) {
        this.sysOperationId = sysOperationId;
    }

    public void set_sys_operation_id(String sysOperationId) {
        setSysOperationId(sysOperationId);
    }

    public Skill register(Path skillPath) throws IOException {
        List<Skill> registered = register(List.of(skillPath), false);
        return registered.isEmpty() ? null : registered.get(0);
    }

    public List<Skill> register(Path skillPath, boolean overwrite) throws IOException {
        return register(skillPath, overwrite, false);
    }

    public List<Skill> register(Path skillPath, boolean overwrite, boolean useMetadataName) throws IOException {
        if (skillPath == null) {
            return List.of();
        }
        return register(List.of(skillPath), overwrite, useMetadataName);
    }

    public List<Skill> register(List<Path> skillPaths, boolean overwrite) throws IOException {
        return register(skillPaths, overwrite, false);
    }

    public List<Skill> register(List<Path> skillPaths, boolean overwrite, boolean useMetadataName) throws IOException {
        List<Skill> registered = new ArrayList<>();
        if (skillPaths == null) {
            return registered;
        }
        BaseFsOperation fs = getFsOperation();
        for (Path path : skillPaths) {
            registered.addAll(registerRoot(fs, path, overwrite, useMetadataName));
        }
        return registered;
    }

    /**
     * Register skill(s) only when the real path is within a trusted skills root.
     *
     * @param skillPath path to the skill directory or file
     * @param skillsRoot trusted root containing loadable skills
     * @param overwrite whether to overwrite existing skills
     * @param useMetadataName whether to use metadata name from SKILL.md
     * @return registered skills
     * @throws IOException if the skill path cannot be resolved
     * @since 0.1.13
     */
    public List<Skill> register(String skillPath, Path skillsRoot, boolean overwrite, boolean useMetadataName)
            throws IOException {
        if (skillPath == null || skillPath.isBlank()) {
            return List.of();
        }
        return register(List.of(resolveSafeSkillPath(skillPath, skillsRoot)), overwrite, useMetadataName);
    }

    /**
     * Resolves a skill path against a trusted root, rejecting escapes via {@code ..} or symlinks.
     *
     * <p>Absolute paths are accepted when their canonical path remains under {@code skillsRoot}.
     * Relative paths are resolved against {@code skillsRoot}.</p>
     *
     * @param skillPath requested skill path
     * @param skillsRoot trusted skills root directory
     * @return canonical skill path within the root
     * @throws IOException if the path does not exist or is not a file/directory
     * @throws SecurityException if the path escapes the trusted root
     */
    public static Path resolveSafeSkillPath(String skillPath, Path skillsRoot) throws IOException {
        if (skillsRoot == null) {
            throw new IllegalArgumentException("Skills root must not be null.");
        }
        if (skillPath == null || skillPath.isBlank()) {
            throw new IllegalArgumentException("Skill path must not be blank.");
        }
        Path realSkillsRoot = skillsRoot.toRealPath();
        Path requestedPath = Path.of(skillPath);
        for (Path segment : requestedPath) {
            if ("..".equals(segment.toString())) {
                throw new SecurityException("Skill path must not contain '..': " + skillPath);
            }
        }
        Path candidate = requestedPath.isAbsolute()
                ? requestedPath.toAbsolutePath().normalize()
                : skillsRoot.toAbsolutePath().normalize().resolve(requestedPath).normalize();
        if (!candidate.startsWith(skillsRoot.toAbsolutePath().normalize())) {
            throw new SecurityException("Skill path is outside the configured skills root: " + skillPath);
        }
        Path realSkillPath = candidate.toRealPath();
        if (!realSkillPath.startsWith(realSkillsRoot)) {
            throw new SecurityException("Skill path is outside the configured skills root: " + skillPath);
        }
        if (!Files.isDirectory(realSkillPath) && !Files.isRegularFile(realSkillPath)) {
            throw new IOException("Skill path is not a file or directory: " + skillPath);
        }
        return realSkillPath;
    }

    public void unregister(String name) {
        registry.remove(name);
    }

    public Skill get(String name) {
        return registry.get(name);
    }

    public List<Skill> getAll() {
        return new ArrayList<>(registry.values());
    }

    public List<Skill> get_all() {
        return getAll();
    }

    /**
     * Incrementally refresh skills from given root directories.
     *
     * <p>Only loads new or mtime-changed skills, removes stale skills
     * (directories that no longer exist), and maintains traversal order.</p>
     *
     * @param roots list of skill root directories to scan
     */
    public void refreshIncrementally(List<Path> roots) {
        long startTime = System.currentTimeMillis();
        Set<String> discoveredKeys = new LinkedHashSet<>();
        List<String> orderedKeys = new ArrayList<>();
        if (roots == null || roots.isEmpty()) {
            removeStaleSkills(discoveredKeys);
            skillOrder.clear();
            return;
        }

        BaseFsOperation fs = getFsOperation();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            List<DiscoveredSkill> discovered = discoverSkillDirs(fs, root);
            discovered.sort(Comparator.comparing(d -> d.directory().getFileName().toString()));
            for (DiscoveredSkill item : discovered) {
                String key = item.directory().toAbsolutePath().normalize().toString();
                long mtime = skillMdMtime(item.skillMd());
                discoveredKeys.add(key);
                orderedKeys.add(key);

                Long cachedMtime = updateAtCache.get(key);
                if (cachedMtime == null || cachedMtime != mtime) {
                    try {
                        Optional<Skill> skill = createSkillFromPath(fs, item.skillMd(), false);
                        if (skill.isPresent()) {
                            Skill registered = skill.get();
                            registered.setUpdateAt(mtime);
                            // Earlier roots win on name collision (hot-load prepend / getAllInOrder).
                            Skill existing = registry.get(registered.getName());
                            if (existing == null
                                    || (existing.getDirectory() != null
                                    && existing.getDirectory().toAbsolutePath().normalize().toString().equals(key))) {
                                registry.put(registered.getName(), registered);
                            }
                            updateAtCache.put(key, mtime);
                        }
                    } catch (IOException | RuntimeException error) {
                        Loggers.AGENT.warning("Failed to refresh skill at {}: {}", key, error.getMessage());
                    }
                }
            }
        }

        removeStaleSkills(discoveredKeys);
        skillOrder.clear();
        skillOrder.addAll(orderedKeys);
        long elapsed = System.currentTimeMillis() - startTime;
        Loggers.AGENT.debug("refreshIncrementally completed in {} ms, skills count: {}", elapsed, registry.size());
    }

    /**
     * Build a snapshot signature of all visible skill directories and their SKILL.md mtimes.
     *
     * @param roots list of skill root directories to scan
     * @return list of (absolute-directory-path, mtime) entries
     */
    public List<Map.Entry<String, Long>> buildSnapshotSignature(List<Path> roots) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>();
        if (roots == null) {
            return entries;
        }
        BaseFsOperation fs = getFsOperation();
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            List<DiscoveredSkill> discovered = discoverSkillDirs(fs, root);
            discovered.sort(Comparator.comparing(d -> d.directory().getFileName().toString()));
            for (DiscoveredSkill item : discovered) {
                String key = item.directory().toAbsolutePath().normalize().toString();
                entries.add(Map.entry(key, skillMdMtime(item.skillMd())));
            }
        }
        return entries;
    }

    /**
     * Get all registered skills in directory traversal order, deduplicated by name.
     */
    public List<Skill> getAllInOrder() {
        List<Skill> result = new ArrayList<>();
        Set<String> seenNames = new LinkedHashSet<>();
        for (String key : skillOrder) {
            Skill skill = findSkillByDirectory(key);
            if (skill != null && !seenNames.contains(skill.getName())) {
                seenNames.add(skill.getName());
                result.add(skill);
            }
        }
        for (Skill skill : registry.values()) {
            if (!seenNames.contains(skill.getName())) {
                seenNames.add(skill.getName());
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * Find a registered skill by its absolute normalized directory path.
     */
    Skill findSkillByDirectory(String directoryPath) {
        if (directoryPath == null) {
            return null;
        }
        return registry.values().stream()
                .filter(s -> s.getDirectory() != null
                        && s.getDirectory().toAbsolutePath().normalize().toString().equals(directoryPath))
                .findFirst()
                .orElse(null);
    }

    public List<String> getNames() {
        return new ArrayList<>(registry.keySet());
    }

    public List<String> get_names() {
        return getNames();
    }

    public boolean has(String name) {
        return registry.containsKey(name);
    }

    public void clear() {
        registry.clear();
        updateAtCache.clear();
        skillOrder.clear();
    }

    public int count() {
        return registry.size();
    }

    public String getSysOperationId() {
        return sysOperationId;
    }

    public String getDescription() {
        return description;
    }

    private List<Skill> registerRoot(BaseFsOperation fs, Path root, boolean overwrite, boolean useMetadataName)
            throws IOException {
        List<Skill> registered = new ArrayList<>();
        if (root == null) {
            return registered;
        }

        ListDirsResult dirsResult = join(fs.listDirectories(
                root.toString(),
                false,
                null,
                BaseFsOperation.SortBy.NAME,
                false,
                null
        ));
        if (dirsResult.getCode() != 0) {
            Optional<Skill> direct = createSkillFromPath(fs, root, useMetadataName);
            if (direct.isPresent()) {
                addToRegistry(direct.get(), overwrite);
                registered.add(direct.get());
            }
            return registered;
        }

        Optional<Path> directSkillMd = findSkillMd(fs, root.toString());
        if (directSkillMd.isPresent()) {
            Optional<Skill> skill = createSkillFromPath(fs, directSkillMd.get(), useMetadataName);
            if (skill.isPresent()) {
                addToRegistry(skill.get(), overwrite);
                registered.add(skill.get());
            }
            return registered;
        }

        FileSystemData dirData = dirsResult.getData();
        List<FileSystemItem> dirItems = dirData == null ? null : dirData.getListItems();
        if (dirItems == null) {
            return registered;
        }

        for (FileSystemItem child : dirItems) {
            if (child == null || child.getPath() == null || child.getName() == null) {
                continue;
            }
            Optional<Path> childSkillMd = findSkillMd(fs, child.getPath());
            if (childSkillMd.isEmpty()) {
                continue;
            }
            Optional<Skill> skill = createSkillFromPath(fs, childSkillMd.get(), useMetadataName);
            if (skill.isPresent()) {
                addToRegistry(skill.get(), overwrite);
                registered.add(skill.get());
            }
        }
        return registered;
    }

    private Optional<Skill> createSkillFromPath(BaseFsOperation fs, Path path, boolean useMetadataName)
            throws IOException {
        SkillDocument skillDocument = loadSkillDocument(fs, path, useMetadataName);
        if (skillDocument.description() == null) {
            return Optional.empty();
        }
        Path skillDirectory = path.getParent();
        String folderName = skillDirectory == null ? "" : skillDirectory.getFileName().toString();
        String skillName = useMetadataName ? skillDocument.metadataName() : folderName;
        return Optional.of(new Skill(skillName, skillDocument.description(), skillDirectory));
    }

    private String loadDescription(BaseFsOperation fs, Path path) throws IOException {
        return loadSkillDocument(fs, path, false).description();
    }

    private SkillDocument loadSkillDocument(BaseFsOperation fs, Path path, boolean useMetadataName) throws IOException {
        description = "";
        ReadFileResult result = join(fs.readFile(
                path.toString(),
                BaseFsOperation.FileMode.TEXT,
                null,
                null,
                (BaseFsProtocal.LineRange) null,
                StandardCharsets.UTF_8.name(),
                BaseFsOperation.DEFAULT_READ_CHUNK_SIZE,
                null
        ));
        if (result.getCode() != 0) {
            throw new FileNotFoundException(result.getMessage());
        }
        ReadFileData data = result.getData();
        Object content = data == null ? null : data.getContent();
        if (content == null) {
            throw new FileNotFoundException("read_file is None: " + path);
        }

        String text = content instanceof String value ? value : String.valueOf(content);
        Map<String, Object> yamlData = loadYamlFrontMatter(text);
        if (yamlData == null || !yamlData.containsKey("description")) {
            throw new IllegalArgumentException("Skill.md file does not contain a description field");
        }
        Object rawMetadataName = yamlData.get("name");
        if (useMetadataName && (!(rawMetadataName instanceof String metadataName) || metadataName.isBlank())) {
            throw new IllegalArgumentException("Skill.md file does not contain a valid string name field");
        }
        description = String.valueOf(yamlData.get("description"));
        String metadataName = rawMetadataName instanceof String value && !value.isBlank() ? value : null;
        return new SkillDocument(description, metadataName);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYamlFrontMatter(String text) {
        if (text == null || !text.startsWith("---")) {
            return null;
        }
        int end = text.indexOf("---", 3);
        if (end < 0) {
            return null;
        }
        Object loaded = new Yaml().load(text.substring(3, end));
        return loaded instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private BaseFsOperation getFsOperation() {
        if (sysOperationId == null || sysOperationId.isBlank()) {
            throw new IllegalStateException("sys_operation is not available");
        }
        BaseFsOperation fs = fsResolver.apply(sysOperationId);
        if (fs == null) {
            throw new IllegalStateException("sys_operation is not available");
        }
        return fs;
    }

    private static Optional<Path> findSkillMd(BaseFsOperation fs, String directory) throws IOException {
        ListFilesResult filesResult = join(fs.listFiles(
                directory,
                false,
                null,
                BaseFsOperation.SortBy.NAME,
                false,
                null,
                null
        ));
        if (filesResult.getCode() != 0) {
            return Optional.empty();
        }
        FileSystemData fileData = filesResult.getData();
        List<FileSystemItem> fileItems = fileData == null ? null : fileData.getListItems();
        if (fileItems == null) {
            return Optional.empty();
        }
        for (FileSystemItem item : fileItems) {
            if (item == null || item.getName() == null || item.getPath() == null) {
                continue;
            }
            if ("skill.md".equals(item.getName().toLowerCase(Locale.ROOT))) {
                return Optional.of(Path.of(item.getPath()));
            }
        }
        return Optional.empty();
    }

    private void addToRegistry(Skill skill, boolean overwrite) {
        if (!overwrite && registry.containsKey(skill.getName())) {
            throw new IllegalArgumentException("Skill already exists: " + skill.getName());
        }
        registry.put(skill.getName(), skill);
    }

    private void removeStaleSkills(Set<String> discoveredKeys) {
        Set<String> staleKeys = new LinkedHashSet<>(updateAtCache.keySet());
        staleKeys.removeAll(discoveredKeys);
        for (String key : staleKeys) {
            Skill stale = findSkillByDirectory(key);
            if (stale != null) {
                registry.remove(stale.getName());
            }
            updateAtCache.remove(key);
        }
    }

    private List<DiscoveredSkill> discoverSkillDirs(BaseFsOperation fs, Path root) {
        List<DiscoveredSkill> discovered = new ArrayList<>();
        try {
            ListDirsResult dirsResult = join(fs.listDirectories(
                    root.toString(),
                    false,
                    null,
                    BaseFsOperation.SortBy.NAME,
                    false,
                    null
            ));
            if (dirsResult.getCode() != 0) {
                Optional<Path> directSkillMd = findSkillMd(fs, root.toString());
                if (directSkillMd.isPresent()) {
                    discovered.add(new DiscoveredSkill(root.toAbsolutePath().normalize(), directSkillMd.get()));
                }
                return discovered;
            }

            FileSystemData dirData = dirsResult.getData();
            List<FileSystemItem> dirItems = dirData == null ? null : dirData.getListItems();
            if (dirItems == null) {
                return discovered;
            }
            for (FileSystemItem child : dirItems) {
                if (child == null || child.getPath() == null || child.getName() == null) {
                    continue;
                }
                Optional<Path> childSkillMd = findSkillMd(fs, child.getPath());
                if (childSkillMd.isEmpty()) {
                    continue;
                }
                Path childDir = Path.of(child.getPath()).toAbsolutePath().normalize();
                discovered.add(new DiscoveredSkill(childDir, childSkillMd.get()));
            }
        } catch (IOException error) {
            Loggers.AGENT.warning("Failed to discover skills under {}: {}", root, error.getMessage());
        }
        return discovered;
    }

    private static long skillMdMtime(Path skillMd) {
        try {
            if (skillMd != null && Files.exists(skillMd)) {
                return Files.getLastModifiedTime(skillMd).toMillis();
            }
        } catch (IOException ignored) {
            // Fall through to 0.
        }
        return 0L;
    }

    private static BaseFsOperation resolveFsOperation(String sysOperationId) {
        try {
            Class<?> runnerType = Class.forName("com.openjiuwen.core.runner.Runner");
            Object resourceMgr = runnerType.getMethod("resourceMgr").invoke(null);
            Object sysOperation = resourceMgr.getClass()
                    .getMethod("getSysOperation", String.class)
                    .invoke(resourceMgr, sysOperationId);
            if (sysOperation == null) {
                return null;
            }
            Method fsMethod = sysOperation.getClass().getMethod("fs");
            Object fs = fsMethod.invoke(sysOperation);
            return fs instanceof BaseFsOperation operation ? operation : null;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("failed to resolve sys_operation " + sysOperationId, error);
        }
    }

    private static <T> T join(CompletableFuture<T> future) throws IOException {
        try {
            return future.join();
        } catch (CompletionException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException(cause);
        }
    }

    private record SkillDocument(String description, String metadataName) {
    }

    private record DiscoveredSkill(Path directory, Path skillMd) {
    }
}
